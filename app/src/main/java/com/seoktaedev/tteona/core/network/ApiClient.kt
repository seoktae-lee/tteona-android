package com.seoktaedev.tteona.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * tteona.kr API 클라이언트.
 * iOS 서비스들이 사용하는 엔드포인트(server.js)와 동일한 백엔드를 공유한다.
 */
object ApiClient {
    private const val BASE_URL = "https://tteona.kr/api/"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .build()
    }

    // WebSocket 전용 클라이언트 — 무기한 수신 + 25초 ping (iOS pingTimer와 동일)
    val wsClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build()
    }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val api: TteonaApi by lazy { retrofit.create(TteonaApi::class.java) }
}
