package com.seoktaedev.tteona.core.services

import android.util.Log
import com.seoktaedev.tteona.core.model.CreatorRank
import com.seoktaedev.tteona.core.network.ApiClient

// iOS의 소형 actor 서비스 3종 이식본 — 실패 시 기본값 반환 (iOS와 동일한 방어적 동작)

object CourseThumbnailService {
    /** 전체 커스텀 썸네일 맵 {courseId: url} (탐색 그리드용) */
    suspend fun fetchAllThumbnails(): Map<String, String> =
        runCatching { ApiClient.api.getThumbnails() }
            .onFailure { Log.w("ThumbnailService", "fetch 실패", it) }
            .getOrDefault(emptyMap())
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
}
