package com.example.employeepos2.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class SiteCheckSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SiteCheckSyncWorker"

        // Your secure Render API Token
        private const val API_TOKEN =
            "3cf46a8e52e497f9a0bd4fdf7aab227cb2291bcf084bfa9ff60bc88d58090cf7"

        private const val SCRIPT_URL =
            "https://integrity-backend-cr02.onrender.com/record_site_check"

        // Dedicated cache folder for site check photos
        private const val PHOTO_CACHE_FOLDER =
            "sitecheck_photos"

        // Shared OkHttp client to save memory across multiple queued jobs
        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }

    override suspend fun doWork(): Result {

        val fileName = inputData.getString("FILE_NAME")

        if (fileName.isNullOrBlank()) {
            Log.e(TAG, "Missing FILE_NAME input data")
            return Result.failure()
        }

        val file = File(applicationContext.filesDir, fileName)

        // Already synced or missing
        if (!file.exists()) {
            Log.d(TAG, "File does not exist: $fileName")
            return Result.success()
        }

        return try {

            val jsonPayload = file
                .bufferedReader()
                .use { it.readText() }

            // Prevent sending empty/corrupt files
            if (jsonPayload.isBlank()) {
                Log.e(TAG, "Payload blank or corrupt. Deleting file: $fileName")

                if (!file.delete()) {
                    Log.w(TAG, "Failed deleting corrupt file: $fileName")
                }

                return Result.failure()
            }

            Log.d(TAG, "Uploading file: $fileName")

            val requestBody = jsonPayload.toRequestBody(
                "application/json; charset=utf-8".toMediaType()
            )

            val request = Request.Builder()
                .url(SCRIPT_URL)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $API_TOKEN")
                .build()

            client.newCall(request).execute().use { response ->

                if (response.isSuccessful) {

                    Log.d(TAG, "Upload successful: $fileName")

                    // Delete synced JSON file
                    if (!file.delete()) {
                        Log.w(TAG, "Failed deleting synced file: $fileName")
                    }

                    // Clean only THIS worker's photo cache folder
                    val photoCacheDir = File(
                        applicationContext.cacheDir,
                        PHOTO_CACHE_FOLDER
                    )

                    photoCacheDir.listFiles()?.forEach { cachedFile ->
                        if (cachedFile.isFile) {
                            if (!cachedFile.delete()) {
                                Log.w(TAG, "Failed deleting cached photo: ${cachedFile.name}")
                            }
                        }
                    }

                    Result.success()

                } else {

                    Log.e(TAG, "Server error ${response.code}: ${response.message}")
                    Result.retry()
                }
            }

        } catch (e: Exception) {

            Log.e(TAG, "Sync failed for $fileName", e)

            // Retry automatically when network comes back (using your Exponential Backoff)
            Result.retry()
        }
    }
}