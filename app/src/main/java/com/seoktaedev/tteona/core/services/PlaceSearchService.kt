package com.seoktaedev.tteona.core.services

import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.seoktaedev.tteona.core.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

/**
 * 장소/지역 통합 검색 — iOS Core/Services/PlaceSearchService.swift의 이식본.
 * 한국 좌표면 카카오 로컬 키워드 검색 우선(한국 지명 정확), 결과 없으면 Geocoder 폴백.
 */
object PlaceSearchService {

    data class SearchResult(
        val name: String,
        val address: String,
        val latitude: Double,
        val longitude: Double,
    )

    private const val KAKAO_API_KEY = "b31c03c128d37a877e6cb407f59b8911"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(
        context: Context,
        query: String,
        searchLatitude: Double = 36.5,
        searchLongitude: Double = 127.8,
    ): List<SearchResult> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        return if (isKorea(searchLatitude, searchLongitude)) {
            searchKakao(q).ifEmpty { searchGeocoder(context, q) }
        } else {
            searchGeocoder(context, q)
        }
    }

    private suspend fun searchKakao(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://dapi.kakao.com/v2/local/search/keyword.json?query=$encoded&size=15")
                .header("Authorization", "KakaoAK $KAKAO_API_KEY")
                .build()
            ApiClient.httpClient.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return@withContext emptyList()
                val body = res.body?.string() ?: return@withContext emptyList()
                json.decodeFromString<KakaoSearchResponse>(body).documents.map {
                    SearchResult(
                        name = it.placeName,
                        address = it.addressName,
                        latitude = it.y.toDoubleOrNull() ?: 0.0,
                        longitude = it.x.toDoubleOrNull() ?: 0.0,
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("PlaceSearch", "Kakao keyword search error", e)
            emptyList()
        }
    }

    /** iOS MapKit 검색 대응 — 안드로이드 Geocoder 폴백 */
    private suspend fun searchGeocoder(context: Context, query: String): List<SearchResult> {
        if (!Geocoder.isPresent()) return emptyList()
        val geocoder = Geocoder(context, Locale.getDefault())
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(query, 15) { addresses ->
                        cont.resume(addresses.toResults())
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    (geocoder.getFromLocationName(query, 15) ?: emptyList()).toResults()
                }
            }
        } catch (e: Exception) {
            Log.w("PlaceSearch", "Geocoder search error", e)
            emptyList()
        }
    }

    private fun List<android.location.Address>.toResults(): List<SearchResult> = mapNotNull { addr ->
        if (!addr.hasLatitude() || !addr.hasLongitude()) return@mapNotNull null
        val name = addr.featureName ?: addr.locality ?: addr.getAddressLine(0) ?: return@mapNotNull null
        val address = addr.getAddressLine(0) ?: listOfNotNull(addr.adminArea, addr.locality, addr.thoroughfare).joinToString(" ")
        SearchResult(name, address, addr.latitude, addr.longitude)
    }

    private fun isKorea(latitude: Double, longitude: Double): Boolean =
        latitude in 33.0..38.9 && longitude in 124.5..132.0

    @Serializable
    private data class KakaoSearchResponse(val documents: List<KakaoPlace> = emptyList())

    @Serializable
    private data class KakaoPlace(
        @SerialName("place_name") val placeName: String = "",
        @SerialName("address_name") val addressName: String = "",
        val x: String = "",
        val y: String = "",
    )
}
