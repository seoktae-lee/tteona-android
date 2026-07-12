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

// UGC(코스 제목) 번역 프록시 (iOS TranslationService 대응 — 서버가 결과를 영구 캐시)
@Serializable
data class TranslateRequest(val texts: List<String>, val target: String)

@Serializable
data class TranslateResponse(val translations: List<String> = emptyList(), val translated: Boolean = false)

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
    // 수신자가 알림을 탭했을 때 이 코스를 열 수 있도록 함께 보낸다.
    val courseId: String,
)

@Serializable
data class CourseLikedRequest(
    val courseOwnerId: String,
    val likerNickname: String,
    val courseName: String,
    val courseId: String,
)

// 초대코드 방 참여 (iOS RoomService.joinRoom — 서버 Admin SDK가 rules 우회해 검색·추가)
@Serializable
data class RoomJoinRequest(
    val inviteCode: String,
    val userId: String,
    val nickname: String,
)

@Serializable
data class RoomJoinResponse(
    val roomId: String,
    val name: String = "",
    val inviteCode: String = "",
    val creatorId: String = "",
    val memberIds: List<String> = emptyList(),
)

// 방 나가기 (iOS RoomService.leaveRoom — 마지막 멤버면 서버가 recursiveDelete)
@Serializable
data class RoomLeaveRequest(val userId: String)

// 안읽음 방 집합 (iOS RoomService.refreshUnreadStatus — 서버 1회 집계로 팬아웃 대체)
@Serializable
data class UnreadResponse(val unreadRoomIds: List<String> = emptyList())

// 채팅 등 서버(server.js) 발송 푸시용 FCM 토큰 등록
// platform은 기본값을 두지 않는다 — kotlinx.serialization은 기본값과 같은 필드를
// JSON 직렬화에서 생략하므로, 기본값이 있으면 서버가 platform을 못 받아 'ios'로 오판한다.
@Serializable
data class PushRegisterRequest(
    val token: String,
    val platform: String,
    // 서버가 알림 문구를 어느 언어로 쓸지 정하는 값. 미전송 시 서버 기본값 'ko'가 되어
    // 언어를 바꿔도 항상 한국어 푸시가 온다.
    val lang: String,
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

    // 방 대표 이미지 업로드 — 서버가 512px 리샘플 후 rooms.imageUrl 갱신 (iOS RoomImageService.upload)
    @Multipart
    @POST("rooms/{roomId}/image")
    suspend fun uploadRoomImage(
        @Path("roomId") roomId: String,
        @Part image: MultipartBody.Part,
    ): UploadResponse

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

    // UGC 번역 — 원문+대상언어를 보내면 번역문을 돌려준다 (iOS TranslationService.fetchTranslations)
    @POST("translate")
    suspend fun translate(@Body body: TranslateRequest): TranslateResponse

    // 그룹 채팅 히스토리 (iOS ChatSocketService.loadHistory)
    // before: 이 시각 이전 메시지만 (위로 스크롤 페이지네이션). 미전송 시 최신 묶음.
    @GET("rooms/{roomId}/messages")
    suspend fun getChatHistory(
        @Path("roomId") roomId: String,
        @Query("limit") limit: Int = 50,
        @Query("before") before: String? = null,
    ): ChatHistoryResponse

    // 초대코드 방 참여 (iOS RoomService.joinRoom)
    @POST("rooms/join")
    suspend fun joinRoom(@Body body: RoomJoinRequest): retrofit2.Response<RoomJoinResponse>

    // 방 나가기 (iOS RoomService.leaveRoom)
    @POST("rooms/{roomId}/leave")
    suspend fun leaveRoom(
        @Path("roomId") roomId: String,
        @Body body: RoomLeaveRequest,
    ): retrofit2.Response<Unit>

    // 안읽음 방 집합 (iOS RoomService.refreshUnreadStatus)
    @GET("rooms/unread")
    suspend fun getUnreadRooms(): UnreadResponse

    // 회원탈퇴 시 WAS 개인정보(푸시토큰·통계·아바타·채팅닉네임·Vlog파일) 삭제
    // (iOS deleteAccount가 deleteMyAccount 호출 전에 부른다 — 토큰 유효 시점)
    @POST("users/me/purge")
    suspend fun purgeMyData(): retrofit2.Response<Unit>

    // 코스 작성자 알림 (iOS PushService.notifyCourseFollowed)
    @POST("push/course-followed")
    suspend fun notifyCourseFollowed(@Body body: CourseFollowedRequest)

    // 코스 좋아요 알림 (iOS PushService.notifyCourseLiked)
    @POST("push/course-liked")
    suspend fun notifyCourseLiked(@Body body: CourseLikedRequest)

    // 서버 직발송 푸시(채팅·Vlog 완성 등)용 디바이스 토큰 등록 (iOS PushService.registerDeviceToken 대응)
    @POST("push/register")
    suspend fun registerPush(@Body body: PushRegisterRequest)
}
