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

    /** 갤러리 URI에서 축소 JPEG로 썸네일 업로드 (프로필 탭 썸네일 꾸미기) */
    suspend fun upload(context: android.content.Context, courseId: String, uri: android.net.Uri): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val jpeg = ProfileImageService.downscaledJpeg(context, uri) ?: return@withContext null
            upload(courseId, jpeg)
        }
}

/**
 * iOS PlacesPhotoService.swift의 이식본.
 * 1순위: 관광공사 TourAPI(WAS 경유, 키 불필요) — 좌표 기반 큐레이션 사진.
 * 2순위: Google Places (New) 직접 폴백 — 캐시에 없는 새 장소 커버 (iOS와 동일).
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
        // TourAPI 네트워크 실패(tour == null)와 "사진 없음"을 구분 — 사진이 없으면 Google 폴백 시도.
        val tour = runCatching { ApiClient.api.getTourPhoto(placeName, latitude, longitude) }.getOrNull()
        val tourUrl = tour?.url?.takeIf { it.isNotEmpty() }
        if (tourUrl != null) {
            cache[placeName] = Info(tourUrl, tour.category)
            return
        }

        // 2순위: Google Places 폴백 (iOS fetchAndCache와 동일 — 사진 1장 + 카테고리)
        val place = GooglePlacesService.searchTextFirstPlace(placeName, "places.photos,places.types")
        if (place != null) {
            val photoName = place.optJSONArray("photos")?.optJSONObject(0)?.optString("name")
            val photoUrl = photoName?.takeIf { it.isNotEmpty() }?.let { GooglePlacesService.photoUri(it) }
            val types = place.optJSONArray("types")
                ?.let { arr -> (0 until arr.length()).map(arr::getString) } ?: emptyList()
            cache[placeName] = Info(photoUrl, GooglePlacesService.categoryText(types) ?: tour?.category)
        } else if (tour != null) {
            // TourAPI는 성공(사진 없음), Google은 실패/미설정 — 일시 실패가 아니므로 결과를 캐시
            cache[placeName] = Info(null, tour.category)
        }
    }
}

object RecommendationService {
    suspend fun fetchRecommended(
        userId: String?,
        lat: Double? = null,
        lng: Double? = null,
        tag: String? = null,
        limit: Int = 20,
    ): List<String> =
        runCatching {
            ApiClient.api.getRecommended(limit = limit, userId = userId, lat = lat, lng = lng, tag = tag).courseIds
        }
            .onFailure { Log.w("RecommendationService", "fetch 실패", it) }
            .getOrDefault(emptyList())
}

object StatsService {
    /**
     * 실패(네트워크 오류·코루틴 취소)와 "정말 랭킹이 비어 있음"을 구분한다.
     * null이면 호출부가 기존 목록을 유지해야 한다 — 빈 목록으로 덮으면 스트립이 통째로 사라진다.
     */
    suspend fun fetchCreatorRanking(): List<CreatorRank>? =
        runCatching { ApiClient.api.getCreatorRanking().ranking }
            .onFailure { Log.w("StatsService", "ranking 실패", it) }
            .getOrNull()

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
