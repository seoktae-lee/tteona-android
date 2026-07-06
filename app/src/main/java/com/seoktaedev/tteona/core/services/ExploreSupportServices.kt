package com.seoktaedev.tteona.core.services

import android.util.Log
import com.seoktaedev.tteona.core.model.CreatorRank
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import com.seoktaedev.tteona.core.model.StatsEvent
import com.seoktaedev.tteona.core.model.TravelStats
import com.seoktaedev.tteona.core.network.ApiClient
import com.seoktaedev.tteona.core.network.StatsEventRequest

// iOS의 소형 actor 서비스 3종 이식본 — 실패 시 기본값 반환 (iOS와 동일한 방어적 동작)

object CourseThumbnailService {
    /** 전체 커스텀 썸네일 맵 {courseId: url} (탐색 그리드용) */
    suspend fun fetchAllThumbnails(): Map<String, String> =
        runCatching { ApiClient.api.getThumbnails() }
            .onFailure { Log.w("ThumbnailService", "fetch 실패", it) }
            .getOrDefault(emptyMap())

    /** 코스 커스텀 썸네일 업로드 — 성공 시 url 반환 (iOS upload) */
    suspend fun upload(courseId: String, imageBytes: ByteArray): String? =
        runCatching {
            val part = okhttp3.MultipartBody.Part.createFormData(
                "image", "thumb.jpg",
                okhttp3.RequestBody.create("image/jpeg".toMediaTypeOrNull(), imageBytes),
            )
            ApiClient.api.uploadCourseThumbnail(courseId, part).url
        }.onFailure { Log.w("ThumbnailService", "upload 실패", it) }.getOrNull()
}

/**
 * iOS PlacesPhotoService.swift의 축약 이식본.
 * TourAPI(WAS 경유, 키 불필요) 경로만 우선 구현 — 대부분의 국내 관광지는 이걸로 커버된다.
 * TODO: GOOGLE_PLACES_API_KEY 설정 후 Google Places 폴백(iOS 2순위 경로) 추가.
 */
object PlacesPhotoService {
    private data class Info(val photoUrl: String?, val category: String?)
    private val cache = mutableMapOf<String, Info>()

    suspend fun photoUrl(placeName: String, latitude: Double? = null, longitude: Double? = null): String? {
        ensureFetched(placeName, latitude, longitude)
        return cache[placeName]?.photoUrl
    }

    suspend fun placeCategory(placeName: String, latitude: Double? = null, longitude: Double? = null): String? {
        ensureFetched(placeName, latitude, longitude)
        return cache[placeName]?.category
    }

    private suspend fun ensureFetched(placeName: String, latitude: Double?, longitude: Double?) {
        if (cache.containsKey(placeName)) return
        // 네트워크 실패(result == null)는 캐시하지 않는다 — 일시적 실패가 영구 빈칸으로 굳는 것을 방지.
        // 호출은 성공했으나 사진이 없는 경우(url == null)는 정상 결과이므로 캐시한다.
        val result = runCatching { ApiClient.api.getTourPhoto(placeName, latitude, longitude) }.getOrNull()
            ?: return
        cache[placeName] = Info(result.url?.takeIf { it.isNotEmpty() }, result.category)
    }
}

object RecommendationService {
    suspend fun fetchRecommended(
        userId: String?,
        lat: Double? = null,
        lng: Double? = null,
        limit: Int = 20,
    ): List<String> =
        runCatching { ApiClient.api.getRecommended(limit = limit, userId = userId, lat = lat, lng = lng).courseIds }
            .onFailure { Log.w("RecommendationService", "fetch 실패", it) }
            .getOrDefault(emptyList())
}

object StatsService {
    suspend fun fetchCreatorRanking(): List<CreatorRank> =
        runCatching { ApiClient.api.getCreatorRanking().ranking }
            .onFailure { Log.w("StatsService", "ranking 실패", it) }
            .getOrDefault(emptyList())

    /** 개인 누적 통계 (iOS StatsService.fetchMyStats) */
    suspend fun fetchMyStats(userId: String): TravelStats? =
        runCatching { ApiClient.api.getMyStats(userId) }
            .onFailure { Log.w("StatsService", "stats 실패", it) }
            .getOrNull()

    /** 통계 이벤트 적재 — 실패해도 무시 (iOS postEvent와 동일한 fire-and-forget) */
    suspend fun postEvent(event: StatsEvent, userId: String) {
        runCatching { ApiClient.api.postStatsEvent(StatsEventRequest(userId, event.type)) }
    }
}
