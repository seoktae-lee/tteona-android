package com.seoktaedev.tteona.core.services

import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.i18n.LocaleManager
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
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
import com.seoktaedev.tteona.core.network.RoomJoinRequest
import com.seoktaedev.tteona.core.network.RoomLeaveRequest
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

    // 첫 스냅샷 도착 여부 — 도착 전 myRooms.isEmpty()는 '그룹 없음'이 아니라 '아직 모름'.
    // 나의 오늘 방 선택 시트가 콜드 스타트 직후 그룹 없음으로 오판해 건너뛰는 레이스 방지.
    private val _roomsLoaded = MutableStateFlow(false)
    val roomsLoaded: StateFlow<Boolean> = _roomsLoaded

    private val _currentRoomMembers = MutableStateFlow<List<RoomMember>>(emptyList())
    val currentRoomMembers: StateFlow<List<RoomMember>> = _currentRoomMembers

    private val _feedItems = MutableStateFlow<List<FeedItem>>(emptyList())
    val feedItems: StateFlow<List<FeedItem>> = _feedItems

    private val _unreadRoomIds = MutableStateFlow<Set<String>>(emptySet())
    val unreadRoomIds: StateFlow<Set<String>> = _unreadRoomIds

    private var roomsListener: ListenerRegistration? = null
    private var feedListener: ListenerRegistration? = null
    private var memberFeedListener: ListenerRegistration? = null

    class RoomNotFoundException : Exception(LocaleManager.string(R.string.room_error_notFound))
    class InappropriateContentException : Exception(LocaleManager.string(R.string.room_error_inappropriate))

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
        postFeed(roomId, FeedType.TRIP_START, userId, nickname, "system", LocaleManager.string(R.string.room_groupTrip))

        return Room(
            roomId = roomId,
            name = name,
            inviteCode = inviteCode,
            creatorId = userId,
            memberIds = listOf(userId),
            createdAt = System.currentTimeMillis(),
        )
    }

    // MARK: - 초대코드로 방 참여 (서버 경유)
    // Firestore rules가 "멤버만 읽기"라 초대코드 검색을 클라가 할 수 없다 —
    // 서버 Admin SDK(POST /api/rooms/join)가 rules를 우회해 검색·멤버 추가를 처리한다.
    suspend fun joinRoom(inviteCode: String, userId: String, nickname: String): Room {
        val response = runCatching {
            ApiClient.api.joinRoom(RoomJoinRequest(inviteCode.uppercase(), userId, nickname))
        }.getOrElse { throw RoomNotFoundException() }

        if (response.code() == 404) throw RoomNotFoundException()
        val body = response.body()
        if (!response.isSuccessful || body == null) throw RoomNotFoundException()

        val room = Room(
            roomId = body.roomId,
            name = body.name.ifEmpty { LocaleManager.string(R.string.room_groupTrip) },
            inviteCode = body.inviteCode.ifEmpty { inviteCode.uppercase() },
            creatorId = body.creatorId,
            memberIds = body.memberIds.ifEmpty { listOf(userId) },
            createdAt = System.currentTimeMillis(),
        )

        // 기본 피드 생성 (댓글 작성 보장 — iOS와 동일)
        postFeed(room.roomId, FeedType.TRIP_START, userId, nickname, "system", LocaleManager.string(R.string.room_groupTrip))
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
                _roomsLoaded.value = true
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

    // MARK: - 방 나가기 및 자동 파기 (서버 경유)
    // 마지막 멤버의 방 정리는 다른 멤버가 남긴 피드/문서 삭제 권한이 클라이언트에
    // 없으므로(rules 차단) 서버 Admin SDK가 recursiveDelete로 처리한다.
    // 서버 실패 시 멤버 제거만 클라이언트에서 폴백한다 (iOS와 동일).
    suspend fun leaveRoom(roomId: String, userId: String) {
        val ok = runCatching {
            ApiClient.api.leaveRoom(roomId, RoomLeaveRequest(userId)).isSuccessful
        }.getOrDefault(false)
        if (ok) return

        // 폴백: 멤버 목록에서 나만 제거 (빈 방 정리는 서버 복구 후 재시도 가능)
        val roomRef = db.collection("rooms").document(roomId)
        runCatching {
            roomRef.update("memberIds", FieldValue.arrayRemove(userId)).await()
            roomRef.collection("members").document(userId).delete().await()
        }
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

    /** 방별 마지막 읽음 시각과 최신 피드 시각을 비교해 안읽음 방 집합 갱신 (iOS refreshUnreadStatus — 채팅 탭 배지 공용).
     *  서버 1회 집계(GET /api/rooms/unread) 우선, 실패 시에만 클라 팬아웃 폴백. */
    suspend fun refreshUnreadStatus(userId: String) {
        val serverIds = runCatching { ApiClient.api.getUnreadRooms().unreadRoomIds }.getOrNull()
        if (serverIds != null) {
            _unreadRoomIds.value = serverIds.toSet()
            return
        }
        refreshUnreadStatusLocal(userId)
    }

    // 폴백: 클라이언트에서 방별로 직접 계산 (서버 미배포/오류 대비 — 방×멤버 팬아웃)
    private suspend fun refreshUnreadStatusLocal(userId: String) {
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
        _roomsLoaded.value = false
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
        imageUrl = (d["imageUrl"] as? String)?.takeIf { it.isNotEmpty() },
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
