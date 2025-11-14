package com.alex.studydemo.network.request

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

class DemoInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        val modified: Request = original.newBuilder()
            .header("X-From-Interceptor", "true")
            .method(original.method, original.body)
            .build()

        val auxClient = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
        try {
            val extraReq = Request.Builder()
                .url("https://postman-echo.com/get?from=interceptor")
                .get()
                .build()
            auxClient.newCall(extraReq).execute().use { }
        } catch (_: Throwable) {
        }

        return chain.proceed(modified)
    }
}
