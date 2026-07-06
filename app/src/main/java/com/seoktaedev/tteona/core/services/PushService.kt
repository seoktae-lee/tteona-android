package com.seoktaedev.tteona.core.services

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.seoktaedev.tteona.core.network.ApiClient
import com.seoktaedev.tteona.core.network.CourseFollowedRequest
import java.util.UUID

/**
 * iOS FCMService.requestGroupNotification + PushService의 이식본.
 * fcmRequests 컬렉션에 요청을 쓰면 서버가 그룹 멤버에게 푸시를 발송한다.
 */
object PushService {
    private val db get() = Firebase.firestore

    enum class GroupNotificationType(val rawValue: String) {
        FREE_TRIP_START("free_trip_start"),   // 나의 오늘 시작
        FREE_TRIP_END("free_trip_end"),       // 나의 오늘 종료
        COURSE_TRIP_START("course_trip_start"), // 코스 여행 시작
        VIDEO_RECORDED("video_recorded"),     // 장소 영상 촬영
        FEED_COMMENT("feed_comment"),         // 피드 댓글
    }

    fun requestGroupNotification(
        type: GroupNotificationType,
        senderUserId: String,
        senderNickname: String,
        roomIds: List<String>,
        courseName: String? = null,
        placeName: String? = null,
    ) {
        if (roomIds.isEmpty()) return
        val data = buildMap<String, Any> {
            put("type", type.rawValue)
            put("senderUserId", senderUserId)
            put("senderNickname", senderNickname)
            put("roomIds", roomIds)
            courseName?.let { put("courseName", it) }
            placeName?.let { put("placeName", it) }
            put("createdAt", FieldValue.serverTimestamp())
            put("processed", false)
        }
        db.collection("fcmRequests").document(UUID.randomUUID().toString()).set(data)
    }

    // 코스 작성자에게 "누군가 내 코스를 따라가고 있어요" (iOS PushService.notifyCourseFollowed)
    suspend fun notifyCourseFollowed(courseOwnerId: String, followerNickname: String, courseName: String) {
        runCatching {
            ApiClient.api.notifyCourseFollowed(
                CourseFollowedRequest(courseOwnerId, followerNickname, courseName)
            )
        }
    }

    // 코스 좋아요 알림 트리거 (iOS PushService.notifyCourseLiked)
    suspend fun notifyCourseLiked(courseOwnerId: String, likerNickname: String, courseName: String) {
        runCatching {
            ApiClient.api.notifyCourseLiked(
                com.seoktaedev.tteona.core.network.CourseLikedRequest(courseOwnerId, likerNickname, courseName)
            )
        }
    }
}
