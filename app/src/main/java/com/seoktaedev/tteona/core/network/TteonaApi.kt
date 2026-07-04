package com.seoktaedev.tteona.core.network

import com.seoktaedev.tteona.core.model.CreatorRank
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class RecommendResponse(val courseIds: List<String> = emptyList())

@Serializable
data class CreatorRankingResponse(val ranking: List<CreatorRank> = emptyList())

/**
 * tteona.kr REST API 정의 — iOS의 각 actor 서비스가 호출하는 엔드포인트와 동일.
 */
interface TteonaApi {
    // 전체 커스텀 썸네일 맵 {courseId: url} (iOS CourseThumbnailService.fetchAllThumbnails)
    @GET("courses/thumbnails")
    suspend fun getThumbnails(): Map<String, String>

    // 추천 코스 ID 목록 (iOS RecommendationService.fetchRecommended)
    @GET("courses/recommend")
    suspend fun getRecommended(
        @Query("limit") limit: Int = 20,
        @Query("userId") userId: String? = null,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("tag") tag: String? = null,
    ): RecommendResponse

    // 이번 주 인기 크리에이터 (iOS StatsService.fetchCreatorRanking)
    @GET("creators/ranking")
    suspend fun getCreatorRanking(): CreatorRankingResponse
}
