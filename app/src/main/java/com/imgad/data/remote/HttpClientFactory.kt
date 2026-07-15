package com.imgad.data.remote

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

object HttpClientFactory {
    fun create(timeoutMillis: Long = 30_000L): OkHttpClient = baseBuilder()
        .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .writeTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .build()

    fun createForImageGeneration(): OkHttpClient = baseBuilder()
        .connectTimeout(30_000L, TimeUnit.MILLISECONDS)
        .writeTimeout(120_000L, TimeUnit.MILLISECONDS)
        .readTimeout(600_000L, TimeUnit.MILLISECONDS)
        .callTimeout(600_000L, TimeUnit.MILLISECONDS)
        .build()

    private fun baseBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
}
