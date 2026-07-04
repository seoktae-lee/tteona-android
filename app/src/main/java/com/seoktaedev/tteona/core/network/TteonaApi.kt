package com.seoktaedev.tteona.core.network

import com.seoktaedev.tteona.core.model.CreatorRank
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

@Serializable
data class RecommendResponse(val courseIds: List<String> = emptyList())

@Serializable
data class CreatorRankingResponse(val ranking: List<CreatorRank> = emptyList())

@Serializable
data class LatLng(val lat: Double, val lng: Double)

@Serializable
data class RouteRequest(val mode: String, val places: List<LatLng>)

@Serializable
data class TransitRouteRequest(val places: List<LatLng>)

@Serializable
data class RouteResponse(val distanceMeters: Double = 0.0, val travelTimeSec: Double = 0.0)

@Serializable
data class TourPhotoResponse(val url: String? = null, val category: String? = null)

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

    // 코스 이동 경로 — 자동차/도보 (iOS ExploreInfoService.computeServerRoute).
    // 서버가 한국 자동차는 카카오모빌리티 실측, 그 외엔 직선거리 추정으로 처리한다.
    @POST("route")
    suspend fun getRoute(@Body body: RouteRequest): RouteResponse

    // 대중교통 경로 (iOS ExploreInfoService.computeTransitRoute)
    @POST("courses/transit-route")
    suspend fun getTransitRoute(@Body body: TransitRouteRequest): RouteResponse

    // 장소 사진 — 관광공사 TourAPI 큐레이션 우선 (iOS PlacesPhotoService 1순위 경로)
    @GET("places/tour-photo")
    suspend fun getTourPhoto(
        @Query("name") name: String,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
    ): TourPhotoResponse
}
