package com.example.employeepos2

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkClient {
    // This instance stays alive even when you switch screens
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Keep the "pipe" open for 5 minutes so the next click is instant
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}