package com.seoktaedev.tteona.core.services

import com.seoktaedev.tteona.core.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * iOS Core/Services/ChatSocketService.swift의 Kotlin 이식본.
 * wss://tteona.kr/ws/location 순수 WebSocket + JSON 프로토콜 (Socket.IO 아님).
 * 히스토리는 REST(/api/rooms/{id}/messages)로 선로드 후 실시간 수신.
 *
 * 견고성(iOS와 동일):
 *  - join은 Firebase ID 토큰을 함께 보낸다 (서버 AUTH_ENFORCE 시 필수). 서버의 "joined"
 *    확정(ack)을 받아야 isConnected=true가 되고, 그전엔 전송을 outbox에 쌓아 둔다.
 *  - 전송 실패(12초 타임아웃)는 failed로 표시해 사용자가 재전송할 수 있다.
 *  - 재연결/onClosed 시 joined를 내리고 재조인 후 outbox를 flush한다.
 *  - 위로 스크롤 시 before 커서로 이전 메시지를 페이지네이션한다.
 */
data class ChatMessage(
    val id: String,              // 서버 messageId(uuid). 낙관적 메시지는 확정 전까지 clientMsgId
    val userId: String,
    val nickname: String,
    val text: String,
    val createdAt: Long,
    val replyToNickname: String? = null,
    val replyToText: String? = null,
    val reactions: Map<String, Set<String>> = emptyMap(), // 이모지 → 반응한 userId 집합
    val pending: Boolean = false, // 서버 확정 전 낙관적 표시
    val failed: Boolean = false,  // 전송 실패(타임아웃) — 재전송 가능
) {
    val hasReply: Boolean get() = replyToNickname != null

    // 화면 표시용 반응 목록 (이모지, 개수, 내가 눌렀는지) — 개수 내림차순
    fun reactionChips(myUserId: String): List<Triple<String, Int, Boolean>> =
        reactions.filterValues { it.isNotEmpty() }
            .map { (emoji, users) -> Triple(emoji, users.size, myUserId in users) }
            .sortedByDescending { it.second }
}

class ChatSocketService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    /** 금칙어로 서버가 메시지를 차단했을 때 true — 뷰에서 안내 후 리셋 */
    private val _moderationBlocked = MutableStateFlow(false)
    val moderationBlocked: StateFlow<Boolean> = _moderationBlocked

    /** 더 이전 메시지가 남아 있는가 (페이지네이션) */
    private val _canLoadOlder = MutableStateFlow(false)
    val canLoadOlder: StateFlow<Boolean> = _canLoadOlder

    private val _isLoadingOlder = MutableStateFlow(false)
    val isLoadingOlder: StateFlow<Boolean> = _isLoadingOlder

    private var webSocket: WebSocket? = null
    private var roomId: String? = null
    private var userId: String? = null
    private var nickname: String = ""
    private var reconnectJob: Job? = null
    private var closed = false

    /** 서버의 join 확정(ack)을 받았는가 — 이게 true여야 실제 전송이 나간다. */
    private var joined = false
    /** 아직 서버가 확정하지 않은 내 채팅 페이로드 (clientMsgId → payload) */
    private val outbox = mutableMapOf<String, JSONObject>()
    /** clientMsgId별 전송 타임아웃 잡 — 확정되면 취소, 만료되면 실패 표시 */
    private val timeoutJobs = mutableMapOf<String, Job>()
    private val sendTimeoutMs = 12_000L

    private val wsUrl = "wss://tteona.kr/ws/location"
    private val historyLimit = 50
    private val pageLimit = 30

    // MARK: - 연결 (히스토리 로드 → WebSocket)
    fun connect(roomId: String, userId: String, nickname: String) {
        this.roomId = roomId
        this.userId = userId
        this.nickname = nickname
        closed = false
        scope.launch {
            loadHistory(roomId)
            openSocket()
        }
    }

    private suspend fun loadHistory(roomId: String) {
        val rows = runCatching { ApiClient.api.getChatHistory(roomId, historyLimit).messages }.getOrNull() ?: return
        _messages.value = rows.mapNotNull { it.toChatMessage() }
        _canLoadOlder.value = rows.size >= historyLimit // 꽉 찼으면 더 있을 수 있음
    }

    /** 위로 스크롤해 더 이전 메시지를 불러온다 (서버 before 커서 페이지네이션). */
    suspend fun loadOlderMessages() {
        val rid = roomId ?: return
        if (_isLoadingOlder.value || !_canLoadOlder.value) return
        val oldest = _messages.value.firstOrNull { !it.pending && !it.failed }?.createdAt ?: return
        _isLoadingOlder.value = true
        try {
            val beforeStr = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(oldest))
            val rows = runCatching {
                ApiClient.api.getChatHistory(rid, pageLimit, before = beforeStr).messages
            }.getOrNull() ?: return
            val older = rows.mapNotNull { it.toChatMessage() }
            val existingIds = _messages.value.map { it.id }.toSet()
            val newOnes = older.filter { it.id !in existingIds }
            if (newOnes.isEmpty() || rows.size < pageLimit) _canLoadOlder.value = false
            _messages.value = newOnes + _messages.value
        } finally {
            _isLoadingOlder.value = false
        }
    }

    private suspend fun openSocket() {
        if (closed) return
        val request = Request.Builder().url(wsUrl).build()
        webSocket = ApiClient.wsClient.newWebSocket(request, listener)
        // 서버가 join 시 Firebase ID 토큰으로 본인·방 멤버십을 검증한다 (AUTH_ENFORCE 시 필수).
        // isConnected는 낙관적으로 true로 두지 않는다 — "joined" 확정을 받아야 진짜 연결.
        val token = fetchIdToken()
        sendJson(
            JSONObject()
                .put("type", "join")
                .put("roomId", roomId ?: "")
                .put("userId", userId ?: "")
                .put("nickname", nickname)
                .put("idToken", token ?: "")
        )
    }

    private suspend fun fetchIdToken(): String? = withContext(Dispatchers.IO) {
        runCatching {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.let { user ->
                com.google.android.gms.tasks.Tasks.await(user.getIdToken(false))?.token
            }
        }.getOrNull()
    }

    // MARK: - 전송 (낙관적 추가)
    fun sendChat(raw: String, replyTo: ChatMessage? = null) {
        val text = raw.trim()
        val uid = userId
        if (text.isEmpty() || uid == null) return
        val clientMsgId = UUID.randomUUID().toString()
        _messages.value = _messages.value + ChatMessage(
            id = clientMsgId, userId = uid, nickname = nickname, text = text,
            createdAt = System.currentTimeMillis(),
            replyToNickname = replyTo?.nickname,
            replyToText = replyTo?.text,
            pending = true,
        )
        val payload = JSONObject()
            .put("type", "chat")
            .put("text", text)
            .put("clientMsgId", clientMsgId)
        replyTo?.let {
            payload.put("replyToNickname", it.nickname)
            payload.put("replyToText", it.text)
        }
        outbox[clientMsgId] = payload
        deliver(clientMsgId)
    }

    /** 실패 표시된 메시지를 사용자가 다시 보낸다. */
    fun resend(message: ChatMessage) {
        if (!message.failed || outbox[message.id] == null) return
        updateMessage(message.id) { it.copy(failed = false, pending = true) }
        deliver(message.id)
    }

    /** 조인 확정 상태면 즉시 전송, 아니면 outbox에 남겨 join 시 flush에 맡긴다.
     *  어느 경우든 타임아웃을 걸어 무한 pending을 막는다. */
    private fun deliver(clientMsgId: String) {
        val payload = outbox[clientMsgId] ?: return
        if (joined && webSocket != null) sendJson(payload)
        scheduleTimeout(clientMsgId)
    }

    private fun scheduleTimeout(clientMsgId: String) {
        timeoutJobs[clientMsgId]?.cancel()
        timeoutJobs[clientMsgId] = scope.launch {
            delay(sendTimeoutMs)
            markFailed(clientMsgId)
        }
    }

    private fun markFailed(clientMsgId: String) {
        // 아직 outbox에 남아 있으면(= 서버 확정 못 받음) 실패로 표시. 확정됐으면 무시.
        if (outbox[clientMsgId] == null) return
        updateMessage(clientMsgId) { it.copy(pending = false, failed = true) }
    }

    private fun confirmSent(clientMsgId: String) {
        outbox.remove(clientMsgId)
        timeoutJobs.remove(clientMsgId)?.cancel()
    }

    /** join 확정 시 아직 확정 못 받은 메시지를 모두 재전송한다. */
    private fun flushOutbox() {
        for ((clientMsgId, payload) in outbox) {
            sendJson(payload)
            updateMessage(clientMsgId) { it.copy(failed = false, pending = true) }
            scheduleTimeout(clientMsgId)
        }
    }

    // MARK: - 이모지 반응 토글 (낙관적)
    fun toggleReaction(messageId: String, emoji: String) {
        val uid = userId ?: return
        updateMessage(messageId) { msg ->
            val users = msg.reactions[emoji] ?: emptySet()
            val newUsers = if (uid in users) users - uid else users + uid
            val newReactions = msg.reactions.toMutableMap()
            if (newUsers.isEmpty()) newReactions.remove(emoji) else newReactions[emoji] = newUsers
            msg.copy(reactions = newReactions)
        }
        sendJson(JSONObject().put("type", "reaction").put("messageId", messageId).put("emoji", emoji))
    }

    // 답장 인용 블록 → 원본 메시지 id (닉네임+내용 일치, 해당 답장 이전 것 중 최신)
    fun originalMessageId(reply: ChatMessage): String? {
        val nick = reply.replyToNickname ?: return null
        val text = reply.replyToText ?: return null
        return _messages.value
            .filter { it.nickname == nick && it.text == text && it.createdAt <= reply.createdAt }
            .lastOrNull()?.id
    }

    fun clearModerationBlocked() {
        _moderationBlocked.value = false
    }

    // MARK: - 해제
    fun disconnect() {
        closed = true
        reconnectJob?.cancel()
        reconnectJob = null
        val rid = roomId
        val uid = userId
        if (rid != null && uid != null) {
            sendJson(JSONObject().put("type", "leave").put("roomId", rid).put("userId", uid))
        }
        webSocket?.close(1000, null)
        webSocket = null
        _isConnected.value = false
        joined = false
        // 화면을 떠나므로 대기 중인 전송 타임아웃도 정리
        timeoutJobs.values.forEach { it.cancel() }
        timeoutJobs.clear()
    }

    // MARK: - 수신
    private val listener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return
            scope.launch { handle(json) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            scope.launch { scheduleReconnect() }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            // 서버 정상 종료(재배포·auth_error 후 close 등)도 재연결 대상 — closed(사용자 이탈)만 예외
            scope.launch { scheduleReconnect() }
        }
    }

    private fun handle(json: JSONObject) {
        when (json.optString("type")) {
            // 서버 join 확정 — 이제부터 진짜 연결. 미연결 구간에 쌓인 메시지를 flush.
            "joined" -> {
                joined = true
                _isConnected.value = true
                flushOutbox()
            }

            // 인증/멤버십 검증 실패 — 연결 끊김으로 처리 (서버가 곧 close 4001/4003)
            "auth_error" -> {
                joined = false
                _isConnected.value = false
            }

            // 금칙어 차단 — 낙관적으로 띄웠던 내 메시지를 제거하고 안내
            "chat_blocked" -> {
                val clientMsgId = json.optString("clientMsgId").takeIf { it.isNotEmpty() }
                if (clientMsgId != null) {
                    _messages.value = _messages.value.filterNot { it.id == clientMsgId }
                    confirmSent(clientMsgId) // outbox·타임아웃 정리 (재전송 방지)
                }
                _moderationBlocked.value = true
            }

            "reaction" -> {
                val messageId = json.optString("messageId").ifEmpty { return }
                val emoji = json.optString("emoji").ifEmpty { return }
                val uid = json.optString("userId").ifEmpty { return }
                val added = json.optBoolean("added")
                updateMessage(messageId) { msg ->
                    val users = msg.reactions[emoji] ?: emptySet()
                    val newUsers = if (added) users + uid else users - uid
                    val newReactions = msg.reactions.toMutableMap()
                    if (newUsers.isEmpty()) newReactions.remove(emoji) else newReactions[emoji] = newUsers
                    msg.copy(reactions = newReactions)
                }
            }

            "chat" -> {
                val uid = json.optString("userId").ifEmpty { return }
                val nick = json.optString("nickname").ifEmpty { return }
                val text = json.optString("text").ifEmpty { return }
                val ts = if (json.has("ts")) json.optLong("ts") else System.currentTimeMillis()
                val clientMsgId = json.optString("clientMsgId").takeIf { it.isNotEmpty() }
                val messageId = json.optString("messageId").takeIf { it.isNotEmpty() }
                    ?: clientMsgId?.let { "cli_$it" }
                    ?: "${uid}_$ts"

                // 내가 보낸 낙관적 메시지의 에코면 서버 messageId로 확정
                if (clientMsgId != null && uid == userId) {
                    val idx = _messages.value.indexOfFirst { it.id == clientMsgId }
                    if (idx >= 0) {
                        _messages.value = _messages.value.toMutableList().also {
                            it[idx] = it[idx].copy(id = messageId, pending = false, failed = false)
                        }
                        confirmSent(clientMsgId)
                        return
                    }
                    // messages에 없더라도(재연결 등) outbox는 확정 처리
                    confirmSent(clientMsgId)
                }
                // 중복 방지 (재연결 시 서버 에코 등)
                if (_messages.value.any { it.id == messageId }) return
                _messages.value = _messages.value + ChatMessage(
                    id = messageId, userId = uid, nickname = nick, text = text, createdAt = ts,
                    replyToNickname = json.optString("replyToNickname").takeIf { it.isNotEmpty() },
                    replyToText = json.optString("replyToText").takeIf { it.isNotEmpty() },
                )
            }
        }
    }

    // MARK: - 재연결 (3초 후, iOS와 동일) — 재조인 후 outbox flush로 미확정 메시지 재전송
    private fun scheduleReconnect() {
        if (closed || roomId == null) return
        webSocket?.cancel()
        webSocket = null
        _isConnected.value = false
        joined = false // 재연결 후 "joined" ack를 다시 받아야 전송 재개(+outbox flush)
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(3000)
            if (!closed) openSocket()
        }
    }

    private fun sendJson(json: JSONObject) {
        webSocket?.send(json.toString())
    }

    private inline fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        _messages.value = _messages.value.map { if (it.id == id) transform(it) else it }
    }

    private fun com.seoktaedev.tteona.core.network.ChatHistoryRow.toChatMessage(): ChatMessage? {
        val uid = userId ?: return null
        val nick = nickname ?: return null
        val body = text ?: return null
        // message_id(uuid) 우선, 구 메시지는 srv_<dbid> 폴백 (iOS와 동일)
        val msgId = messageId ?: id?.let { "srv_$it" } ?: UUID.randomUUID().toString()
        val rx = mutableMapOf<String, MutableSet<String>>()
        reactions?.forEach { r -> rx.getOrPut(r.emoji) { mutableSetOf() }.add(r.userId) }
        return ChatMessage(
            id = msgId, userId = uid, nickname = nick, text = body,
            createdAt = parseDate(createdAt) ?: System.currentTimeMillis(),
            replyToNickname = replyToNickname,
            replyToText = replyToText,
            reactions = rx,
        )
    }

    companion object {
        // PostgreSQL ISO8601 (소수초 유무 모두 대응)
        fun parseDate(s: String?): Long? {
            s ?: return null
            return runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
        }
    }
}
