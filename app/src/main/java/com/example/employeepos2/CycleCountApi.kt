package com.example.employeepos2

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

/**
 * Cycle count progress is stored on the backend so every device sees the same zone status,
 * counted items and hidden (non-inventory) items. ApiClient adds the auth header.
 * These calls are blocking: call them from a background thread.
 */
object CycleCountApi {
    private const val BASE = "https://integrity-backend-cr02.onrender.com"

    /** Returns the /cycle_count/status JSON, or null if the request failed. */
    fun fetchStatus(): JSONObject? {
        val request = Request.Builder().url("$BASE/cycle_count/status").build()
        return try {
            ApiClient.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) JSONObject(response.body?.string() ?: "{}") else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun markNonInventory(name: String): Boolean {
        val json = JSONObject().apply {
            put("name", name)
            put("techName", "Warehouse Team")
        }
        val body = RequestBody.create("application/json; charset=utf-8".toMediaType(), json.toString())
        val request = Request.Builder().url("$BASE/cycle_count/non_inventory").post(body).build()
        return try {
            ApiClient.client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    fun zoneObject(status: JSONObject, zoneName: String): JSONObject? {
        val zones = status.optJSONArray("zones") ?: return null
        for (i in 0 until zones.length()) {
            val z = zones.getJSONObject(i)
            if (z.optString("zone") == zoneName) return z
        }
        return null
    }
}
