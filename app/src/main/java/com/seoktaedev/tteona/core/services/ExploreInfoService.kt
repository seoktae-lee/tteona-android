package com.seoktaedev.tteona.core.services

import android.util.Log
import com.seoktaedev.tteona.core.model.Place
import com.seoktaedev.tteona.core.model.RouteInfo
import com.seoktaedev.tteona.core.model.WeatherInfo
import com.seoktaedev.tteona.core.network.ApiClient
import com.seoktaedev.tteona.core.network.LatLng
import com.seoktaedev.tteona.core.network.RouteRequest
import com.seoktaedev.tteona.core.network.TransitRouteRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * iOS Core/Services/ExploreInfoService.swift의 Kotlin 이식본.
 * Android는 온디바이스 경로 계산(MKDirections) 대신 서버(WAS) 경로 API를 그대로 사용한다 —
 * 서버가 이미 한국 자동차=카카오모빌리티 실측, 그 외=직선거리 추정 폴백을 처리해준다.
 */
object ExploreInfoService {
    private val weatherClient = OkHttpClient()

    suspend fun fetchWeather(lat: Double, lng: Double): WeatherInfo? = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&current=temperature_2m,weather_code"
        try {
            weatherClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val json = JSONObject(resp.body?.string() ?: return@withContext null)
                val current = json.getJSONObject("current")
                WeatherInfo(
                    tempC = current.getDouble("temperature_2m"),
                    code = current.getInt("weather_code"),
                )
            }
        } catch (e: Exception) {
            Log.w("ExploreInfoService", "weather error", e)
            null
        }
    }

    suspend fun computeRoute(places: List<Place>, mode: String): RouteInfo? {
        if (places.size < 2) return null
        return runCatching {
            val body = RouteRequest(mode = mode, places = places.map { LatLng(it.latitude, it.longitude) })
            val res = ApiClient.api.getRoute(body)
            if (res.distanceMeters <= 0) null else RouteInfo(res.distanceMeters, res.travelTimeSec)
        }.getOrNull()
    }

    suspend fun computeTransitRoute(places: List<Place>): RouteInfo? {
        if (places.size < 2) return null
        return runCatching {
            val body = TransitRouteRequest(places = places.map { LatLng(it.latitude, it.longitude) })
            val res = ApiClient.api.getTransitRoute(body)
            if (res.distanceMeters <= 0) null else RouteInfo(res.distanceMeters, res.travelTimeSec)
        }.getOrNull()
    }

    /** 서버 경로 실패 시 직선거리 × 평균속도 폴백 (iOS calculateLeg 폴백과 동일 상수) */
    fun fallbackRoute(places: List<Place>, walking: Boolean): RouteInfo {
        if (places.size < 2) return RouteInfo(0.0, 0.0)
        val speed = if (walking) 4500.0 / 3600.0 else 40000.0 / 3600.0
        var distance = 0.0
        for (i in 0 until places.size - 1) {
            distance += haversineMeters(places[i], places[i + 1])
        }
        return RouteInfo(distance, distance / speed)
    }

    private fun haversineMeters(a: Place, b: Place): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLng / 2) * sin(dLng / 2)
        return r * 2 * atan2(sqrt(h), sqrt(1 - h))
    }
}
