package com.seoktaedev.tteona.core.services

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.seoktaedev.tteona.core.network.ApiClient
import com.seoktaedev.tteona.core.network.PlaceCachePayload
import com.seoktaedev.tteona.core.network.PlaceCacheReview
import com.seoktaedev.tteona.core.network.PlaceCacheSaveRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
 * 캐시 체인: 메모리 → WAS PostgreSQL → Firestore.
 * (iOS 4순위인 Google Places 직접 호출은 안드로이드 키 미설정으로 생략 —
 *  iOS 사용자가 조회한 장소는 WAS/Firestore 캐시에 이미 적재되어 있어 대부분 커버된다.
 *  TODO: GOOGLE_PLACES_API_KEY 설정 후 직접 호출 경로 추가.)
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
}
