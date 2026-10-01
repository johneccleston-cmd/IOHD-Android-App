package com.example.employeepos2

import okhttp3.Interceptor
import okhttp3.OkHttpClient

object ApiClient {

    // 1. Create the Interceptor that injects the Secret Key
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()

        // Build a new request that includes the Authorization header
        val newRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer ${BuildConfig.API_SECRET}")
            .build()

        chain.proceed(newRequest)
    }

    // 2. Build the OkHttpClient using the Interceptor
    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        // You can also add timeouts here if your warehouse Wi-Fi is spotty!
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
}