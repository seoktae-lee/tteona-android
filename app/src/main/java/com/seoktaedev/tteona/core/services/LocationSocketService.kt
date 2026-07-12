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

/**
 * iOS Core/Services/LocationSocketService.swift의 Kotlin 이식본.
 * 동행 세션 실시간 위치 공유 (wss://tteona.kr/ws/location).
 *
 * 한 번의 나들이를 여러 그룹 방에 동시에 공유할 수 있도록, 방마다 독립 WebSocket 연결을
 * 열고(RoomConnection) 위치를 모두에게 브로드캐스트한다. 여러 방의 멤버 위치는 userId
 * 기준으로 합쳐(최신 우선) 지도에 한 번만 찍는다.
 *
 * join에는 Firebase ID 토큰을 함께 보낸다 — 서버 AUTH_ENFORCE 시 필수(없으면 즉시 끊긴다).
 */
data class WsMemberLocation(
    val userId: String,
    val nickname: String,
    val latitude: Double,
    val longitude: Double,
    val updatedAt: Long,
)

object LocationSocketService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _memberLocations = MutableStateFlow<List<WsMemberLocation>>(emptyList())
    val memberLocations: StateFlow<List<WsMemberLocation>> = _memberLocations

    private val connections = mutableMapOf<String, RoomConnection>() // roomId → 연결
    private var currentUserId: String = ""
    private var currentNickname: String = ""

    private const val WS_URL = "wss://tteona.kr/ws/location"

    // MARK: - 연결
    /** 여러 방에 동시에 실시간 위치를 공유한다. 이미 연결된 방은 유지, 빠진 방은 정리한다. */
    fun connect(roomIds: Set<String>, userId: String, nickname: String) {
        currentUserId = userId
        currentNickname = nickname

        // 더 이상 공유하지 않는 방 연결 정리
        connections.keys.filter { it !in roomIds }.forEach { rid ->
            connections.remove(rid)?.close()
        }
        // 새로 공유할 방 연결
        roomIds.filter { connections[it] == null }.forEach { rid ->
            val conn = RoomConnection(rid, userId, nickname) { rebuildMemberLocations() }
            connections[rid] = conn
            conn.open()
        }
        rebuildMemberLocations()
    }

    /** 하위호환 단일 방 연결 */
    fun connect(roomId: String, userId: String, nickname: String) {
        connect(setOf(roomId), userId, nickname)
    }

    // MARK: - 위치 전송 (모든 공유 방에)
    fun sendLocation(latitude: Double, longitude: Double) {
        connections.values.forEach { it.sendLocation(latitude, longitude) }
    }

    // MARK: - 연결 해제
    fun disconnect() {
        connections.values.forEach { it.close() }
        connections.clear()
        _memberLocations.value = emptyList()
    }

    /** 여러 방의 멤버 위치를 합친다 — 같은 유저가 여러 방에 있으면 최신 위치 하나만 남긴다. */
    private fun rebuildMemberLocations() {
        val merged = mutableMapOf<String, WsMemberLocation>()
        for (conn in connections.values) {
            for (m in conn.members) {
                val existing = merged[m.userId]
                if (existing != null && existing.updatedAt >= m.updatedAt) continue
                merged[m.userId] = m
            }
        }
        _memberLocations.value = merged.values.toList()
    }

    // MARK: - 방 단위 WebSocket 연결
    private class RoomConnection(
        val roomId: String,
        private val userId: String,
        private val nickname: String,
        private val onUpdate: () -> Unit,
    ) {
        val members = mutableListOf<WsMemberLocation>()

        private var webSocket: WebSocket? = null
        private var reconnectJob: Job? = null
        private var closed = false

        fun open() {
            if (closed) return
            scope.launch {
                if (closed) return@launch
                val request = Request.Builder().url(WS_URL).build()
                webSocket = ApiClient.wsClient.newWebSocket(request, listener)
                // 서버가 join 시 Firebase ID 토큰으로 본인·방 멤버십을 검증한다 (AUTH_ENFORCE 시 필수)
                val token = fetchIdToken()
                send(
                    JSONObject()
                        .put("type", "join")
                        .put("roomId", roomId)
                        .put("userId", userId)
                        .put("nickname", nickname)
                        .put("idToken", token ?: "")
                )
            }
        }

        fun sendLocation(latitude: Double, longitude: Double) {
            send(
                JSONObject()
                    .put("type", "location")
                    .put("latitude", latitude)
                    .put("longitude", longitude)
            )
        }

        fun close() {
            closed = true
            reconnectJob?.cancel()
            reconnectJob = null
            send(JSONObject().put("type", "leave").put("roomId", roomId).put("userId", userId))
            webSocket?.close(1000, null)
            webSocket = null
            members.clear()
            onUpdate()
        }

        private val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                scope.launch { handle(json) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scope.launch { scheduleReconnect() }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scope.launch { scheduleReconnect() }
            }
        }

        private fun handle(json: JSONObject) {
            when (json.optString("type")) {
                "location" -> {
                    val uid = json.optString("userId").ifEmpty { return }
                    val nick = json.optString("nickname").ifEmpty { return }
                    if (!json.has("latitude") || !json.has("longitude")) return
                    val loc = WsMemberLocation(
                        userId = uid,
                        nickname = nick,
                        latitude = json.optDouble("latitude"),
                        longitude = json.optDouble("longitude"),
                        updatedAt = System.currentTimeMillis(),
                    )
                    val idx = members.indexOfFirst { it.userId == uid }
                    if (idx >= 0) members[idx] = loc else members.add(loc)
                    onUpdate()
                }

                "left" -> {
                    val uid = json.optString("userId").ifEmpty { return }
                    members.removeAll { it.userId == uid }
                    onUpdate()
                }
            }
        }

        private fun scheduleReconnect() {
            if (closed) return
            webSocket?.cancel()
            webSocket = null
            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(3000)
                if (!closed) open()
            }
        }

        private fun send(json: JSONObject) {
            webSocket?.send(json.toString())
        }
    }

    private suspend fun fetchIdToken(): String? = withContext(Dispatchers.IO) {
        runCatching {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.let { user ->
                com.google.android.gms.tasks.Tasks.await(user.getIdToken(false))?.token
            }
        }.getOrNull()
    }
}
