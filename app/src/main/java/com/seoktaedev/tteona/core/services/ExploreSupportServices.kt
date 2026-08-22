package com.seoktaedev.tteona.core.services

import android.util.Log
import com.seoktaedev.tteona.core.model.CreatorRank
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import com.seoktaedev.tteona.core.model.StatsEvent
import com.seoktaedev.tteona.core.model.TravelStats
import com.seoktaedev.tteona.core.network.ApiClient
import kotlinx.serialization.json.Json
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
    @kotlinx.serialization.Serializable
    private data class Info(
        val photoUrl: String? = null,
        val category: String? = null,
        /** 만료 시각(epoch millis). 지나면 다시 조회한다. */
        val expiresAt: Long = 0L,
    )

    private val cache = mutableMapOf<String, Info>()

    /*
     * **디스크 캐시.**
     *
     * TourAPI 이름 규칙을 조인 뒤로 관광지가 아닌 장소는 대부분 Google Places로 넘어간다.
     * Google 텍스트 검색과 사진 요청은 **호출당 과금**이라, 앱을 껐다 켤 때마다 같은 코스를
     * 다시 조회하면 정확도를 돈으로 바꾸는 꼴이 된다. 조회 결과를 기기에 남겨 재사용한다.
     */
    private const val CACHE_FILE = "place-photos.json"
    private val cacheJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var appContext: android.content.Context? = null
    private var diskLoaded = false

    /** TourAPI 큐레이션 사진은 잘 바뀌지 않는다 */
    private const val TTL_TOUR_MS = 30L * 86_400_000
    /** Google 사진 URL은 서명이 붙어 있어 오래 두면 깨진다 — 짧게만 들고 있는다 */
    private const val TTL_GOOGLE_MS = 7L * 86_400_000
    /**
     * 사진을 못 찾은 것도 결과다. 남겨두지 않으면 사진 없는 장소를 볼 때마다
     * 유료 검색을 다시 때린다. 다만 짧게만 — 나중에 등록될 수 있으니.
     */
    private const val TTL_NEGATIVE_MS = 3L * 86_400_000

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

    private fun cacheFile(): java.io.File? =
        appContext?.let { java.io.File(it.cacheDir, CACHE_FILE) }

    private fun loadDiskCacheIfNeeded() {
        if (diskLoaded) return
        diskLoaded = true
        val f = cacheFile() ?: return
        if (!f.exists()) return
        val stored = runCatching {
            cacheJson.decodeFromString<Map<String, Info>>(f.readText())
        }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        stored.forEach { (k, info) -> if (info.expiresAt > now) cache[k] = info }
    }

    private fun persistDiskCache() {
        val f = cacheFile() ?: return
        val now = System.currentTimeMillis()
        // 만료된 항목은 남기지 않는다
        val keep = cache.filterValues { it.expiresAt > now }
        runCatching { f.writeText(cacheJson.encodeToString(keep)) }
    }

    /**
     * 캐시 키에 **좌표를 함께 넣는다.**
     *
     * 이름만 키로 쓰면 '스타벅스'·'본점' 같은 흔한 이름이 전국에서 한 칸을 공유해,
     * 먼저 조회된 다른 도시의 사진이 그대로 재사용된다 — 엉뚱한 사진의 한 원인이다.
     * 소수점 셋째 자리(약 100m 격자)로 뭉개 같은 장소는 계속 캐시를 맞히게 한다.
     */
    private fun key(placeName: String, latitude: Double?, longitude: Double?): String {
        val base = placeName.trim().lowercase()
        if (latitude == null || longitude == null) return base
        return String.format(java.util.Locale.US, "%s|%.3f|%.3f", base, latitude, longitude)
    }

    suspend fun photoUrl(placeName: String, latitude: Double? = null, longitude: Double? = null): String? {
        val k = key(placeName, latitude, longitude)
        ensureFetched(k, placeName, latitude, longitude)
        return cache[k]?.photoUrl
    }

    suspend fun placeCategory(placeName: String, latitude: Double? = null, longitude: Double? = null): String? {
        val k = key(placeName, latitude, longitude)
        ensureFetched(k, placeName, latitude, longitude)
        return cache[k]?.category
    }

    private suspend fun ensureFetched(k: String, placeName: String, latitude: Double?, longitude: Double?) {
        loadDiskCacheIfNeeded()
        if (cache.containsKey(k)) return
        // TourAPI 네트워크 실패(tour == null)와 "사진 없음"을 구분 — 사진이 없으면 Google 폴백 시도.
        val tour = runCatching { ApiClient.api.getTourPhoto(placeName, latitude, longitude) }.getOrNull()
        val tourUrl = tour?.url?.takeIf { it.isNotEmpty() }
        if (tourUrl != null) {
            cache[k] = Info(tourUrl, tour.category, System.currentTimeMillis() + TTL_TOUR_MS)
            persistDiskCache()
            return
        }

        // 2순위: Google Places 폴백 (iOS fetchAndCache와 동일 — 사진 1장 + 카테고리).
        // **좌표를 함께 넘긴다** — 이름만 던지면 전 세계에서 가장 유명한 동명 장소가
        // 1등으로 오고, 그 사진이 이 장소의 것으로 올라간다.
        val place = GooglePlacesService.searchTextFirstPlace(
            placeName, "places.photos,places.types", latitude, longitude,
        )
        if (place != null) {
            val photoName = place.optJSONArray("photos")?.optJSONObject(0)?.optString("name")
            val photoUrl = photoName?.takeIf { it.isNotEmpty() }?.let { GooglePlacesService.photoUri(it) }
            val types = place.optJSONArray("types")
                ?.let { arr -> (0 until arr.length()).map(arr::getString) } ?: emptyList()
            cache[k] = Info(
                photoUrl,
                GooglePlacesService.categoryText(types) ?: tour?.category,
                System.currentTimeMillis() + if (photoUrl != null) TTL_GOOGLE_MS else TTL_NEGATIVE_MS,
            )
            persistDiskCache()
        } else if (tour != null) {
            // TourAPI는 성공(사진 없음), Google은 실패/미설정 — 일시 실패가 아니므로 결과를 캐시.
            // 사진을 못 찾은 것도 결과다: 남겨두지 않으면 사진 없는 장소를 볼 때마다
            // 유료 검색을 다시 때린다.
            cache[k] = Info(null, tour.category, System.currentTimeMillis() + TTL_NEGATIVE_MS)
            persistDiskCache()
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

    /**
     * 코스가 세션까지 이어지는 각 단계.
     * 큐레이션 코스가 실제로 "떠나기"를 만드는지 알려면 **코스별로, 큐레이션 여부와 함께**
     * 세야 한다 — 컬럼을 증가시키는 방식(user_stats)으로는 답할 수 없는 질문이다.
     */
    enum class CourseFunnelStep(val event: String) {
        PIN_TAP("pin_tap"),
        COURSE_OPEN("course_open"),
        SESSION_START("session_start"),
        VLOG_COMPLETE("vlog_complete"),
    }

    /** 통계 전송이 사용자 흐름을 막아서는 안 된다 — 실패해도 조용히 넘어간다. */
    suspend fun postCourseEvent(step: CourseFunnelStep, course: com.seoktaedev.tteona.core.model.Course) {
        runCatching {
            ApiClient.api.postCourseEvent(
                com.seoktaedev.tteona.core.network.CourseEventRequest(
                    event = step.event,
                    courseId = course.courseId,
                    curated = course.curated,
                )
            )
        }
    }
}
