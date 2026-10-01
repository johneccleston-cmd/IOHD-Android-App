package com.example.employeepos2

import com.stripe.stripeterminal.external.callable.ConnectionTokenCallback
import com.stripe.stripeterminal.external.callable.ConnectionTokenProvider
import com.stripe.stripeterminal.external.models.ConnectionTokenException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class TokenProvider(private val tokenUrl: String) : ConnectionTokenProvider {

    private val client = OkHttpClient()

    override fun fetchConnectionToken(callback: ConnectionTokenCallback) {
        try {
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = "{}".toRequestBody(mediaType)

            val request = Request.Builder()
                .url(tokenUrl)
                .post(body)
                // 🔥 CRITICAL: Auth Header to bypass 401 Unauthorized
                .addHeader("Authorization", "Bearer 3cf46a8e52e497f9a0bd4fdf7aab227cb2291bcf084bfa9ff60bc88d58090cf7")
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    callback.onFailure(ConnectionTokenException("Network Error", e))
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val resBody = response.body?.string() ?: ""

                    if (response.isSuccessful && resBody.isNotEmpty()) {
                        try {
                            val json = JSONObject(resBody)
                            val secret = json.getString("secret")

                            if (secret.isNotEmpty()) {
                                callback.onSuccess(secret)
                            } else {
                                callback.onFailure(ConnectionTokenException("Empty secret"))
                            }
                        } catch (e: Exception) {
                            callback.onFailure(ConnectionTokenException("JSON Parse Error", e))
                        }
                    } else {
                        callback.onFailure(ConnectionTokenException("Backend Error: ${response.code}"))
                    }
                }
            })
        } catch (e: Exception) {
            callback.onFailure(ConnectionTokenException("Fatal Token Error", e))
        }
    }
}