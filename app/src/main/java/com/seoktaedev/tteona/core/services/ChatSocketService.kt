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
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/**
 * iOS Core/Services/ChatSocketService.swift의 Kotlin 이식본.
 * wss://tteona.kr/ws/location 순수 WebSocket + JSON 프로토콜 (Socket.IO 아님).
 * 히스토리는 REST(/api/rooms/{id}/messages)로 선로드 후 실시간 수신.
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

    private var webSocket: WebSocket? = null
    private var roomId: String? = null
    private var userId: String? = null
    private var nickname: String = ""
    private var reconnectJob: Job? = null
    private var closed = false

    private val wsUrl = "wss://tteona.kr/ws/location"

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
        val rows = runCatching { ApiClient.api.getChatHistory(roomId).messages }.getOrNull() ?: return
        val history = rows.mapNotNull { row ->
            val uid = row.userId ?: return@mapNotNull null
            val nick = row.nickname ?: return@mapNotNull null
            val text = row.text ?: return@mapNotNull null
            // message_id(uuid) 우선, 구 메시지는 srv_<dbid> 폴백 (iOS와 동일)
            val msgId = row.messageId ?: row.id?.let { "srv_$it" } ?: UUID.randomUUID().toString()
            val reactions = mutableMapOf<String, MutableSet<String>>()
            row.reactions?.forEach { r -> reactions.getOrPut(r.emoji) { mutableSetOf() }.add(r.userId) }
            ChatMessage(
                id = msgId, userId = uid, nickname = nick, text = text,
                createdAt = parseDate(row.createdAt) ?: System.currentTimeMillis(),
                replyToNickname = row.replyToNickname,
                replyToText = row.replyToText,
                reactions = reactions,
            )
        }
        _messages.value = history
    }

    private fun openSocket() {
        if (closed) return
        val request = Request.Builder().url(wsUrl).build()
        webSocket = ApiClient.wsClient.newWebSocket(request, listener)
        sendJson(
            JSONObject()
                .put("type", "join")
                .put("roomId", roomId ?: "")
                .put("userId", userId ?: "")
                .put("nickname", nickname)
        )
        _isConnected.value = true
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
        sendJson(payload)
    }

    // MARK: - 이모지 반응 토글 (낙관적)
    fun toggleReaction(messageId: String, emoji: String) {
        val uid = userId ?: return
        _messages.value = _messages.value.map { msg ->
            if (msg.id != messageId) return@map msg
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
    }

    private fun handle(json: JSONObject) {
        when (json.optString("type")) {
            "reaction" -> {
                val messageId = json.optString("messageId").ifEmpty { return }
                val emoji = json.optString("emoji").ifEmpty { return }
                val uid = json.optString("userId").ifEmpty { return }
                val added = json.optBoolean("added")
                _messages.value = _messages.value.map { msg ->
                    if (msg.id != messageId) return@map msg
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
                val ts = if (json.has("ts")) (json.optDouble("ts") / 1000).toLong() * 1000 else System.currentTimeMillis()
                val clientMsgId = json.optString("clientMsgId").takeIf { it.isNotEmpty() }
                val messageId = json.optString("messageId").takeIf { it.isNotEmpty() }
                    ?: clientMsgId?.let { "cli_$it" }
                    ?: "${uid}_$ts"

                // 내가 보낸 낙관적 메시지의 에코면 서버 messageId로 확정
                if (clientMsgId != null && uid == userId) {
                    val idx = _messages.value.indexOfFirst { it.id == clientMsgId }
                    if (idx >= 0) {
                        _messages.value = _messages.value.toMutableList().also {
                            it[idx] = it[idx].copy(id = messageId, pending = false)
                        }
                        return
                    }
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

    // MARK: - 재연결 (3초 후, iOS와 동일)
    private fun scheduleReconnect() {
        if (closed || roomId == null) return
        webSocket?.cancel()
        webSocket = null
        _isConnected.value = false
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(3000)
            openSocket()
        }
    }

    private fun sendJson(json: JSONObject) {
        webSocket?.send(json.toString())
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
