package com.seoktaedev.tteona.core.network

import com.seoktaedev.tteona.core.model.CreatorRank
import com.seoktaedev.tteona.core.model.TravelStats
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
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

@Serializable
data class StatsEventRequest(val userId: String, val type: String)

// 장소 상세 캐시 (iOS PlaceDetailService — WAS PostgreSQL 캐시)
@Serializable
data class PlaceCachePayload(
    val photos: List<String> = emptyList(),
    val rating: Double? = null,
    val reviewCount: Int = 0,
    val reviews: List<PlaceCacheReview> = emptyList(),
)

@Serializable
data class PlaceCacheReview(
    val authorName: String = "",
    val rating: Int = 0,
    val text: String = "",
    val publishTime: String = "",
)

@Serializable
data class PlaceCacheSaveRequest(
    val cacheKey: String,
    val photos: List<String> = emptyList(),
    val rating: Double? = null,
    val reviewCount: Int = 0,
    val reviews: List<PlaceCacheReview> = emptyList(),
)

@Serializable
data class UploadResponse(val url: String? = null)

@Serializable
data class ModerationRequest(val text: String)

@Serializable
data class ModerationResponse(val blocked: Boolean = false)

// 채팅 히스토리 (서버 PostgreSQL 컬럼명 그대로 snake_case)
@Serializable
data class ChatHistoryResponse(val messages: List<ChatHistoryRow> = emptyList())

@Serializable
data class ChatHistoryRow(
    val id: Long? = null,
    @kotlinx.serialization.SerialName("message_id") val messageId: String? = null,
    @kotlinx.serialization.SerialName("user_id") val userId: String? = null,
    val nickname: String? = null,
    val text: String? = null,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
    @kotlinx.serialization.SerialName("reply_to_nickname") val replyToNickname: String? = null,
    @kotlinx.serialization.SerialName("reply_to_text") val replyToText: String? = null,
    val reactions: List<ChatReactionRow>? = null,
)

@Serializable
data class ChatReactionRow(val emoji: String, val userId: String)

@Serializable
data class CourseFollowedRequest(
    val courseOwnerId: String,
    val followerNickname: String,
    val courseName: String,
)

@Serializable
data class CourseLikedRequest(
    val courseOwnerId: String,
    val likerNickname: String,
    val courseName: String,
)

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

    // 장소 상세 캐시 조회/저장 (iOS PlaceDetailService의 WAS 캐시 경로)
    @GET("places/cache")
    suspend fun getPlaceCache(@Query("key") key: String): PlaceCachePayload

    @POST("places/cache")
    suspend fun savePlaceCache(@Body body: PlaceCacheSaveRequest)

    // 개인 누적 통계 (iOS StatsService.fetchMyStats)
    @GET("users/{uid}/stats")
    suspend fun getMyStats(@Path("uid") uid: String): TravelStats

    // 통계 이벤트 적재 — fire-and-forget (iOS StatsService.postEvent)
    @POST("stats/event")
    suspend fun postStatsEvent(@Body body: StatsEventRequest)

    // 프로필 이미지 업로드 — 서버가 512px로 리샘플 후 Firestore profileImageUrl도 갱신
    // (iOS ProfileImageService.upload)
    @Multipart
    @POST("users/{uid}/avatar")
    suspend fun uploadAvatar(@Path("uid") uid: String, @Part image: MultipartBody.Part): UploadResponse

    // 코스 커스텀 썸네일 업로드 (iOS CourseThumbnailService.upload — 탐색탭 그리드용)
    @Multipart
    @POST("courses/{courseId}/thumbnail")
    suspend fun uploadCourseThumbnail(
        @Path("courseId") courseId: String,
        @Part image: MultipartBody.Part,
    ): UploadResponse

    // 콘텐츠 모더레이션 — 부적절 텍스트 검사 (iOS StatsService.isTextAllowed)
    @POST("moderate")
    suspend fun moderateText(@Body body: ModerationRequest): ModerationResponse

    // 그룹 채팅 히스토리 (iOS ChatSocketService.loadHistory)
    @GET("rooms/{roomId}/messages")
    suspend fun getChatHistory(
        @Path("roomId") roomId: String,
        @Query("limit") limit: Int = 50,
    ): ChatHistoryResponse

    // 코스 작성자 알림 (iOS PushService.notifyCourseFollowed)
    @POST("push/course-followed")
    suspend fun notifyCourseFollowed(@Body body: CourseFollowedRequest)

    // 코스 좋아요 알림 (iOS PushService.notifyCourseLiked)
    @POST("push/course-liked")
    suspend fun notifyCourseLiked(@Body body: CourseLikedRequest)
}
