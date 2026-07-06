package com.seoktaedev.tteona.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// iOS Core/Models/Room.swift의 Kotlin 이식본.
// Date 필드는 epoch millis(Long)로 통일 — Firestore Timestamp는 서비스 레이어에서 변환.

@Serializable
data class Room(
    val id: String? = null, // Firestore 문서 ID
    val roomId: String,
    val name: String,
    val inviteCode: String,
    val creatorId: String,
    val memberIds: List<String>,
    val createdAt: Long,
)

@Serializable
data class RoomMember(
    val id: String? = null,
    val userId: String,
    val nickname: String,
    val joinedAt: Long,
    val lastReadAt: Long? = null,
    val lastReadPerMember: Map<String, Long>? = null,
)

@Serializable
enum class FeedType {
    @SerialName("tripStart") TRIP_START,
    @SerialName("tripEnd") TRIP_END,
    @SerialName("arrival") ARRIVAL,
    @SerialName("photo") PHOTO,
    @SerialName("freeTripStart") FREE_TRIP_START,
    @SerialName("freeCapture") FREE_CAPTURE,
    @SerialName("freeTripEnd") FREE_TRIP_END,
}

@Serializable
data class FeedItem(
    val id: String? = null,
    val feedId: String,
    val type: FeedType,
    val userId: String,
    val nickname: String,
    val courseId: String,
    val courseName: String,
    val placeName: String? = null,
    val imageUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val commentCount: Int = 0,
    val createdAt: Long,
)

@Serializable
data class FeedComment(
    val id: String? = null,
    val commentId: String,
    val userId: String,
    val nickname: String,
    val text: String,
    val replyToNickname: String? = null,
    val replyToText: String? = null,
    val createdAt: Long,
)
