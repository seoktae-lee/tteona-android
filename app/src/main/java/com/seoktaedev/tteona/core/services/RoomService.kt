package com.seoktaedev.tteona.core.services

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.seoktaedev.tteona.core.model.FeedComment
import com.seoktaedev.tteona.core.model.FeedItem
import com.seoktaedev.tteona.core.model.FeedType
import com.seoktaedev.tteona.core.model.Room
import com.seoktaedev.tteona.core.model.RoomMember
import com.seoktaedev.tteona.core.network.ApiClient
import com.seoktaedev.tteona.core.network.ModerationRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.UUID

/**
 * iOS Core/Services/RoomService.swift의 Kotlin 이식본.
 * rooms 컬렉션(Firestore) 기반 그룹 방/멤버/피드/댓글/위치 관리 — iOS와 데이터 완전 공유.
 */
object RoomService {
    private val db get() = Firebase.firestore

    private val _myRooms = MutableStateFlow<List<Room>>(emptyList())
    val myRooms: StateFlow<List<Room>> = _myRooms

    private val _currentRoomMembers = MutableStateFlow<List<RoomMember>>(emptyList())
    val currentRoomMembers: StateFlow<List<RoomMember>> = _currentRoomMembers

    private val _feedItems = MutableStateFlow<List<FeedItem>>(emptyList())
    val feedItems: StateFlow<List<FeedItem>> = _feedItems

    private val _unreadRoomIds = MutableStateFlow<Set<String>>(emptySet())
    val unreadRoomIds: StateFlow<Set<String>> = _unreadRoomIds

    private var roomsListener: ListenerRegistration? = null
    private var feedListener: ListenerRegistration? = null
    private var memberFeedListener: ListenerRegistration? = null

    class RoomNotFoundException : Exception("해당 초대 코드의 방을 찾을 수 없어요.")
    class InappropriateContentException : Exception("부적절한 표현이 포함되어 있어 등록할 수 없어요.")

    // MARK: - 방 생성
    suspend fun createRoom(name: String, userId: String, nickname: String): Room {
        val roomId = UUID.randomUUID().toString()
        val inviteCode = generateInviteCode()
        val data = mapOf(
            "roomId" to roomId,
            "name" to name,
            "inviteCode" to inviteCode,
            "creatorId" to userId,
            "memberIds" to listOf(userId),
            "createdAt" to FieldValue.serverTimestamp(),
        )
        db.collection("rooms").document(roomId).set(data).await()

        db.collection("rooms").document(roomId)
            .collection("members").document(userId)
            .set(
                mapOf(
                    "userId" to userId,
                    "nickname" to nickname,
                    "joinedAt" to FieldValue.serverTimestamp(),
                )
            ).await()

        // 기본 피드 생성 (댓글 작성 보장 — iOS와 동일)
        postFeed(roomId, FeedType.TRIP_START, userId, nickname, "system", "그룹 참여 여행")

        return Room(
            roomId = roomId,
            name = name,
            inviteCode = inviteCode,
            creatorId = userId,
            memberIds = listOf(userId),
            createdAt = System.currentTimeMillis(),
        )
    }

    // MARK: - 초대코드로 방 참여
    suspend fun joinRoom(inviteCode: String, userId: String, nickname: String): Room {
        val snapshot = db.collection("rooms")
            .whereEqualTo("inviteCode", inviteCode.uppercase())
            .get().await()
        val doc = snapshot.documents.firstOrNull() ?: throw RoomNotFoundException()
        val room = doc.toRoom() ?: throw RoomNotFoundException()

        if (userId in room.memberIds) return room

        db.collection("rooms").document(room.roomId)
            .update("memberIds", FieldValue.arrayUnion(userId)).await()
        db.collection("rooms").document(room.roomId)
            .collection("members").document(userId)
            .set(
                mapOf(
                    "userId" to userId,
                    "nickname" to nickname,
                    "joinedAt" to FieldValue.serverTimestamp(),
                )
            ).await()

        postFeed(room.roomId, FeedType.TRIP_START, userId, nickname, "system", "그룹 참여 여행")
        return room
    }

    // MARK: - 내 방 목록 실시간 구독
    fun startListeningMyRooms(userId: String) {
        roomsListener?.remove()
        roomsListener = db.collection("rooms")
            .whereArrayContains("memberIds", userId)
            .addSnapshotListener { snapshot, _ ->
                val docs = snapshot?.documents ?: return@addSnapshotListener
                _myRooms.value = docs.mapNotNull { it.toRoom() }.sortedByDescending { it.createdAt }
            }
    }

    fun stopListeningMyRooms() {
        roomsListener?.remove()
        roomsListener = null
    }

    // MARK: - 방 멤버 조회
    suspend fun fetchMembers(roomId: String) {
        val snapshot = runCatching {
            db.collection("rooms").document(roomId).collection("members").get().await()
        }.getOrNull()
        _currentRoomMembers.value = snapshot?.documents?.mapNotNull { it.toRoomMember() } ?: emptyList()
    }

    // 동행 세션 중 실시간 위치 공유는 LocationSocketService(WebSocket) 경로가 담당한다.

    // MARK: - 방 나가기 및 자동 파기
    suspend fun leaveRoom(roomId: String, userId: String) {
        val roomRef = db.collection("rooms").document(roomId)
        val room = roomRef.get().await().toRoom() ?: return

        if (room.memberIds.size <= 1) {
            // 마지막 멤버라면 방 전체 데이터 삭제
            deleteCollection(roomRef.collection("locations"))
            deleteCollection(roomRef.collection("members"))
            val feeds = roomRef.collection("feed").get().await()
            for (feedDoc in feeds.documents) {
                deleteCollection(feedDoc.reference.collection("comments"))
                feedDoc.reference.delete().await()
            }
            roomRef.delete().await()
        } else {
            roomRef.update("memberIds", FieldValue.arrayRemove(userId)).await()
            roomRef.collection("members").document(userId).delete().await()
        }
    }

    private suspend fun deleteCollection(ref: CollectionReference) {
        val snapshot = ref.get().await()
        for (doc in snapshot.documents) doc.reference.delete().await()
    }

    // MARK: - 피드 자동 기록
    fun postFeed(
        roomId: String,
        type: FeedType,
        userId: String,
        nickname: String,
        courseId: String,
        courseName: String,
        placeName: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ) {
        val feedId = UUID.randomUUID().toString()
        val data = buildMap<String, Any> {
            put("feedId", feedId)
            put("type", type.rawValue)
            put("userId", userId)
            put("nickname", nickname)
            put("courseId", courseId)
            put("courseName", courseName)
            put("commentCount", 0)
            put("createdAt", FieldValue.serverTimestamp())
            placeName?.let { put("placeName", it) }
            latitude?.let { put("latitude", it) }
            longitude?.let { put("longitude", it) }
        }
        db.collection("rooms").document(roomId)
            .collection("feed").document(feedId).set(data)
    }

    // MARK: - 피드 실시간 구독
    fun startListeningFeed(roomId: String) {
        feedListener?.remove()
        feedListener = db.collection("rooms").document(roomId)
            .collection("feed")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, _ ->
                val docs = snapshot?.documents ?: return@addSnapshotListener
                _feedItems.value = docs.mapNotNull { it.toFeedItem() }
            }
    }

    fun stopListeningFeed() {
        feedListener?.remove()
        feedListener = null
        _feedItems.value = emptyList()
    }

    // MARK: - 멤버별 최신 피드 1개씩
    suspend fun fetchLatestFeedPerMember(roomId: String, memberIds: List<String>): Map<String, FeedItem> =
        coroutineScope {
            memberIds.map { userId ->
                async {
                    val snapshot = runCatching {
                        db.collection("rooms").document(roomId)
                            .collection("feed")
                            .whereEqualTo("userId", userId)
                            .orderBy("createdAt", Query.Direction.DESCENDING)
                            .limit(1)
                            .get().await()
                    }.getOrNull()
                    userId to snapshot?.documents?.firstOrNull()?.toFeedItem()
                }
            }.awaitAll()
                .mapNotNull { (userId, item) -> item?.let { userId to it } }
                .toMap()
        }

    // MARK: - 오늘 활동 중인 멤버
    suspend fun fetchActiveMemberIds(roomId: String): Set<String> {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val snapshot = runCatching {
            db.collection("rooms").document(roomId)
                .collection("feed")
                .whereGreaterThanOrEqualTo("createdAt", Timestamp(startOfDay))
                .get().await()
        }.getOrNull() ?: return emptySet()
        return snapshot.documents.mapNotNull { it.getString("userId") }.toSet()
    }

    // MARK: - 댓글
    suspend fun addComment(
        roomId: String,
        feedId: String,
        userId: String,
        nickname: String,
        text: String,
        replyToNickname: String? = null,
        replyToText: String? = null,
    ) {
        // 콘텐츠 모더레이션 (서버 검사, 네트워크 실패 시 통과 — iOS와 동일)
        if (!isTextAllowed(text)) throw InappropriateContentException()

        val commentId = UUID.randomUUID().toString()
        val data = buildMap<String, Any> {
            put("commentId", commentId)
            put("userId", userId)
            put("nickname", nickname)
            put("text", text)
            put("createdAt", FieldValue.serverTimestamp())
            replyToNickname?.let { put("replyToNickname", it) }
            replyToText?.let { put("replyToText", it) }
        }
        val feedRef = db.collection("rooms").document(roomId).collection("feed").document(feedId)
        feedRef.collection("comments").document(commentId).set(data).await()
        feedRef.update("commentCount", FieldValue.increment(1)).await()

        // 피드 작성자에게 댓글 알림 요청 (fcmRequests — 서버가 처리, iOS FCMService와 동일)
        val feedAuthorId = runCatching { feedRef.get().await().getString("userId") }.getOrNull()
        if (feedAuthorId != null && feedAuthorId != userId) {
            db.collection("fcmRequests").document(UUID.randomUUID().toString()).set(
                mapOf(
                    "type" to "feedComment",
                    "senderUserId" to userId,
                    "senderNickname" to nickname,
                    "targetUserId" to feedAuthorId,
                    "roomIds" to listOf(roomId),
                    "commentText" to text,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "processed" to false,
                )
            )
        }
    }

    suspend fun fetchComments(roomId: String, feedId: String): List<FeedComment> {
        val snapshot = runCatching {
            db.collection("rooms").document(roomId)
                .collection("feed").document(feedId)
                .collection("comments")
                .orderBy("createdAt")
                .get().await()
        }.getOrNull() ?: return emptyList()
        return snapshot.documents.mapNotNull { it.toFeedComment() }
    }

    suspend fun fetchAllCommentsForFeeds(roomId: String, feedIds: List<String>): Map<String, List<FeedComment>> =
        coroutineScope {
            feedIds.map { feedId ->
                async { feedId to fetchComments(roomId, feedId) }
            }.awaitAll().toMap()
        }

    // MARK: - 멤버 피드
    fun startListeningMemberFeed(roomId: String, userId: String, onChange: (List<FeedItem>) -> Unit) {
        memberFeedListener?.remove()
        memberFeedListener = db.collection("rooms").document(roomId)
            .collection("feed")
            .whereEqualTo("userId", userId)
            .orderBy("createdAt")
            .addSnapshotListener { snapshot, _ ->
                val docs = snapshot?.documents ?: return@addSnapshotListener
                onChange(docs.mapNotNull { it.toFeedItem() })
            }
    }

    fun stopListeningMemberFeed() {
        memberFeedListener?.remove()
        memberFeedListener = null
    }

    suspend fun fetchMemberFeedItems(roomId: String, userId: String): List<FeedItem> {
        val snapshot = runCatching {
            db.collection("rooms").document(roomId)
                .collection("feed")
                .whereEqualTo("userId", userId)
                .orderBy("createdAt")
                .get().await()
        }.getOrNull() ?: return emptyList()
        return snapshot.documents.mapNotNull { it.toFeedItem() }
    }

    suspend fun addCommentToLatestFeed(
        roomId: String,
        userId: String,
        commenterId: String,
        commenterNickname: String,
        text: String,
        replyToNickname: String? = null,
        replyToText: String? = null,
    ) {
        val latest = fetchMemberFeedItems(roomId, userId).lastOrNull() ?: return
        addComment(roomId, latest.feedId, commenterId, commenterNickname, text, replyToNickname, replyToText)
    }

    // MARK: - 읽음 처리
    fun markRoomAsRead(roomId: String, userId: String) {
        _unreadRoomIds.value = _unreadRoomIds.value - roomId
        db.collection("rooms").document(roomId)
            .collection("members").document(userId)
            .set(mapOf("lastReadAt" to FieldValue.serverTimestamp()), com.google.firebase.firestore.SetOptions.merge())
    }

    /** 방별 마지막 읽음 시각과 최신 피드 시각을 비교해 안읽음 방 집합 갱신 (iOS refreshUnreadStatus — 채팅 탭 배지 공용) */
    suspend fun refreshUnreadStatus(userId: String) {
        val rooms = _myRooms.value
        val unread = coroutineScope {
            rooms.map { room ->
                async {
                    val memberDoc = async { fetchMyMemberDoc(room.roomId, userId) }
                    val latestFeeds = async { fetchLatestFeedPerMember(room.roomId, room.memberIds) }
                    val latestDate = latestFeeds.await().values.maxOfOrNull { it.createdAt }
                        ?: return@async room.roomId to false
                    val readAt = memberDoc.await()?.lastReadAt
                        ?: return@async room.roomId to true
                    room.roomId to (latestDate > readAt)
                }
            }.awaitAll()
                .filter { it.second }
                .map { it.first }
                .toSet()
        }
        _unreadRoomIds.value = unread
    }

    fun markMemberFeedAsRead(roomId: String, userId: String, memberUserId: String) {
        db.collection("rooms").document(roomId)
            .collection("members").document(userId)
            .set(
                mapOf("lastReadPerMember" to mapOf(memberUserId to FieldValue.serverTimestamp())),
                com.google.firebase.firestore.SetOptions.merge(),
            )
    }

    suspend fun fetchMyMemberDoc(roomId: String, userId: String): RoomMember? =
        runCatching {
            db.collection("rooms").document(roomId)
                .collection("members").document(userId).get().await().toRoomMember()
        }.getOrNull()

    // MARK: - 모더레이션 (iOS StatsService.isTextAllowed)
    suspend fun isTextAllowed(text: String): Boolean =
        runCatching { !ApiClient.api.moderateText(ModerationRequest(text)).blocked }.getOrDefault(true)

    // 로그아웃 시 상태 초기화 (iOS RootView onChange 대응)
    fun clear() {
        stopListeningMyRooms()
        stopListeningFeed()
        stopListeningMemberFeed()
        _myRooms.value = emptyList()
        _currentRoomMembers.value = emptyList()
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}

// FeedType의 Firestore 문자열 값 (iOS rawValue와 동일)
val FeedType.rawValue: String
    get() = when (this) {
        FeedType.TRIP_START -> "tripStart"
        FeedType.TRIP_END -> "tripEnd"
        FeedType.ARRIVAL -> "arrival"
        FeedType.PHOTO -> "photo"
        FeedType.FREE_TRIP_START -> "freeTripStart"
        FeedType.FREE_CAPTURE -> "freeCapture"
        FeedType.FREE_TRIP_END -> "freeTripEnd"
    }

private fun feedTypeFrom(raw: String?): FeedType? = when (raw) {
    "tripStart" -> FeedType.TRIP_START
    "tripEnd" -> FeedType.TRIP_END
    "arrival" -> FeedType.ARRIVAL
    "photo" -> FeedType.PHOTO
    "freeTripStart" -> FeedType.FREE_TRIP_START
    "freeCapture" -> FeedType.FREE_CAPTURE
    "freeTripEnd" -> FeedType.FREE_TRIP_END
    else -> null
}

// MARK: - Firestore 매핑
private fun DocumentSnapshot.toRoom(): Room? {
    val d = data ?: return null
    return Room(
        id = id,
        roomId = d["roomId"] as? String ?: id,
        name = d["name"] as? String ?: return null,
        inviteCode = d["inviteCode"] as? String ?: "",
        creatorId = d["creatorId"] as? String ?: "",
        memberIds = (d["memberIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        createdAt = (d["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L,
    )
}

private fun DocumentSnapshot.toRoomMember(): RoomMember? {
    val d = data ?: return null
    val lastReadPerMember = (d["lastReadPerMember"] as? Map<*, *>)
        ?.mapNotNull { (k, v) ->
            val key = k as? String ?: return@mapNotNull null
            val ts = (v as? Timestamp)?.toDate()?.time ?: return@mapNotNull null
            key to ts
        }?.toMap()
    return RoomMember(
        id = id,
        userId = d["userId"] as? String ?: id,
        nickname = d["nickname"] as? String ?: "",
        joinedAt = (d["joinedAt"] as? Timestamp)?.toDate()?.time ?: 0L,
        lastReadAt = (d["lastReadAt"] as? Timestamp)?.toDate()?.time,
        lastReadPerMember = lastReadPerMember,
    )
}

private fun DocumentSnapshot.toFeedItem(): FeedItem? {
    val d = data ?: return null
    return FeedItem(
        id = id,
        feedId = d["feedId"] as? String ?: id,
        type = feedTypeFrom(d["type"] as? String) ?: return null,
        userId = d["userId"] as? String ?: return null,
        nickname = d["nickname"] as? String ?: "",
        courseId = d["courseId"] as? String ?: "",
        courseName = d["courseName"] as? String ?: "",
        placeName = d["placeName"] as? String,
        imageUrl = d["imageUrl"] as? String,
        latitude = (d["latitude"] as? Number)?.toDouble(),
        longitude = (d["longitude"] as? Number)?.toDouble(),
        commentCount = (d["commentCount"] as? Number)?.toInt() ?: 0,
        createdAt = (d["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L,
    )
}

private fun DocumentSnapshot.toFeedComment(): FeedComment? {
    val d = data ?: return null
    return FeedComment(
        id = id,
        commentId = d["commentId"] as? String ?: id,
        userId = d["userId"] as? String ?: return null,
        nickname = d["nickname"] as? String ?: "",
        text = d["text"] as? String ?: "",
        replyToNickname = d["replyToNickname"] as? String,
        replyToText = d["replyToText"] as? String,
        createdAt = (d["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L,
    )
}
