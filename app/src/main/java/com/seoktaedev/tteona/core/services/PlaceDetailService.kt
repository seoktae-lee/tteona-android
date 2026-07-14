package com.seoktaedev.tteona.core.services

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.seoktaedev.tteona.core.network.ApiClient
import com.seoktaedev.tteona.core.network.PlaceCachePayload
import com.seoktaedev.tteona.core.network.PlaceCacheReview
import com.seoktaedev.tteona.core.network.PlaceCacheSaveRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.Locale

data class PlaceDetail(
    val placeKey: String,
    val photos: List<String>,
    val rating: Double?,
    val reviewCount: Int,
    val reviews: List<GooglePlaceReview>,
)

data class GooglePlaceReview(
    val authorName: String,
    val rating: Int,
    val text: String,
    val publishTime: String,
)

/**
 * 장소 상세(사진·평점·구글리뷰) — iOS Core/Services/PlaceDetailService.swift의 이식본.
 * 캐시 체인: 메모리 → WAS PostgreSQL → Firestore → Google Places (New) 직접 호출.
 */
object PlaceDetailService {
    private val db get() = FirebaseFirestore.getInstance()
    private val memoryCache = mutableMapOf<String, PlaceDetail>()
    private val mutex = Mutex()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val CACHE_TTL_MS = 7L * 24 * 3600 * 1000

    suspend fun fetchDetail(placeName: String, latitude: Double? = null, longitude: Double? = null): PlaceDetail? {
        // 사진·평점 캐시는 좌표 버킷을 키에 포함 — 동명 장소끼리 섞임 방지 (iOS와 동일)
        val key = detailCacheKey(placeName, latitude, longitude)

        mutex.withLock { memoryCache[key] }?.let { return it }

        fetchFromWas(key)?.let { detail ->
            mutex.withLock { memoryCache[key] = detail }
            return detail
        }

        fetchFromFirestore(key)?.let { detail ->
            mutex.withLock { memoryCache[key] = detail }
            ioScope.launch { saveToWas(detail, key) }
            return detail
        }

        // 4순위: Google Places 직접 호출 — 아무도 방문 안 한 새 장소 커버 (iOS와 동일).
        // 결과는 Firestore·WAS에 적재해 다른 사용자·플랫폼이 재사용하게 한다.
        fetchFromGoogle(placeName, key, latitude, longitude)?.let { detail ->
            mutex.withLock { memoryCache[key] = detail }
            ioScope.launch { saveToFirestore(detail, key) }
            ioScope.launch { saveToWas(detail, key) }
            return detail
        }

        return null
    }

    /** 리뷰(placeReviews) 문서 키 — 기존 데이터 호환을 위해 이름 기반 유지 (iOS cacheKey) */
    fun cacheKey(placeName: String): String =
        placeName.lowercase().trim().split(Regex("\\s+")).joinToString("_")

    /** 사진·평점 캐시 키 — 이름 + 좌표(약 1km 버킷)로 동명이소 구분 (iOS detailCacheKey) */
    fun detailCacheKey(placeName: String, latitude: Double?, longitude: Double?): String {
        val base = cacheKey(placeName)
        if (latitude == null || longitude == null) return base
        return String.format(Locale.US, "%s|%.2f|%.2f", base, latitude, longitude)
    }

    // ── WAS PostgreSQL 캐시 ──────────────────────────────────────────────

    private suspend fun fetchFromWas(key: String): PlaceDetail? =
        runCatching {
            val payload = ApiClient.api.getPlaceCache(key)
            payload.toDetail(key)
        }.getOrNull()

    private suspend fun saveToWas(detail: PlaceDetail, key: String) {
        runCatching {
            ApiClient.api.savePlaceCache(
                PlaceCacheSaveRequest(
                    cacheKey = key,
                    photos = detail.photos,
                    rating = detail.rating,
                    reviewCount = detail.reviewCount,
                    reviews = detail.reviews.map {
                        PlaceCacheReview(it.authorName, it.rating, it.text, it.publishTime)
                    },
                )
            )
        }.onFailure { Log.w("PlaceDetail", "WAS 캐시 저장 실패", it) }
    }

    private fun PlaceCachePayload.toDetail(key: String) = PlaceDetail(
        placeKey = key,
        photos = photos,
        rating = rating,
        reviewCount = reviewCount,
        reviews = reviews.map { GooglePlaceReview(it.authorName, it.rating, it.text, it.publishTime) },
    )

    // ── Firestore 캐시 ──────────────────────────────────────────────────

    private suspend fun fetchFromFirestore(key: String): PlaceDetail? {
        val doc = runCatching { db.collection("placeCache").document(key).get().await() }.getOrNull()
            ?: return null
        if (!doc.exists()) return null
        val cachedAt = (doc.get("cachedAt") as? Timestamp)?.toDate()?.time ?: return null
        if (System.currentTimeMillis() - cachedAt >= CACHE_TTL_MS) return null

        val photos = (doc.get("photos") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val rating = (doc.get("rating") as? Number)?.toDouble()
        val reviewCount = (doc.get("reviewCount") as? Number)?.toInt() ?: 0
        val reviews = (doc.get("reviews") as? List<*>)?.mapNotNull { raw ->
            val r = raw as? Map<*, *> ?: return@mapNotNull null
            val author = r["authorName"] as? String ?: return@mapNotNull null
            val stars = (r["rating"] as? Number)?.toInt() ?: return@mapNotNull null
            val text = r["text"] as? String ?: return@mapNotNull null
            GooglePlaceReview(author, stars, text, r["publishTime"] as? String ?: "")
        } ?: emptyList()

        return PlaceDetail(key, photos, rating, reviewCount, reviews)
    }

    private suspend fun saveToFirestore(detail: PlaceDetail, key: String) {
        val data = mutableMapOf<String, Any>(
            "photos" to detail.photos,
            "reviewCount" to detail.reviewCount,
            "reviews" to detail.reviews.map {
                mapOf("authorName" to it.authorName, "rating" to it.rating,
                    "text" to it.text, "publishTime" to it.publishTime)
            },
            "cachedAt" to FieldValue.serverTimestamp(),
        )
        detail.rating?.let { data["rating"] = it }
        runCatching { db.collection("placeCache").document(key).set(data).await() }
            .onFailure { Log.w("PlaceDetail", "Firestore 캐시 저장 실패", it) }
    }

    // ── Google Places (New) 직접 호출 ──────────────────────────────────

    private suspend fun fetchFromGoogle(
        placeName: String, key: String, latitude: Double?, longitude: Double?,
    ): PlaceDetail? {
        val place = GooglePlacesService.searchTextFirstPlace(
            placeName,
            "places.photos,places.types,places.rating,places.userRatingCount,places.reviews",
            latitude, longitude,
        ) ?: return null

        // 사진 URL 최대 5장 병렬 fetch (iOS withTaskGroup 대응)
        val photoNames = place.optJSONArray("photos")?.let { arr ->
            (0 until minOf(arr.length(), 5)).mapNotNull { i ->
                arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotEmpty() }
            }
        } ?: emptyList()
        val photos = coroutineScope {
            photoNames.map { name -> async { GooglePlacesService.photoUri(name) } }
                .awaitAll().filterNotNull()
        }

        val rating = place.optDouble("rating").takeIf { !it.isNaN() }
        val reviewCount = place.optInt("userRatingCount", 0)

        val reviews = place.optJSONArray("reviews")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val r = arr.optJSONObject(i) ?: return@mapNotNull null
                val author = r.optJSONObject("authorAttribution")?.optString("displayName")
                    ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                if (!r.has("rating")) return@mapNotNull null
                GooglePlaceReview(
                    authorName = author,
                    rating = r.optInt("rating"),
                    text = r.optJSONObject("text")?.optString("text") ?: "",
                    publishTime = r.optString("relativePublishTimeDescription"),
                )
            }
        } ?: emptyList()

        return PlaceDetail(key, photos, rating, reviewCount, reviews)
    }
}
