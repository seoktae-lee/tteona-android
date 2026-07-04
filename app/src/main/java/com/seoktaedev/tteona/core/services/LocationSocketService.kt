package com.seoktaedev.tteona.core.services

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
import com.seoktaedev.tteona.core.network.ApiClient

/**
 * iOS Core/Services/LocationSocketService.swift의 Kotlin 이식본.
 * 동행 세션 중 실시간 멤버 위치 공유 (wss://tteona.kr/ws/location).
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

    private var webSocket: WebSocket? = null
    private var currentRoomId: String? = null
    private var currentUserId: String? = null
    private var currentNickname: String = ""
    private var reconnectJob: Job? = null
    private var closed = false

    private const val WS_URL = "wss://tteona.kr/ws/location"

    // MARK: - 연결
    fun connect(roomId: String, userId: String, nickname: String) {
        if (webSocket != null) return
        currentRoomId = roomId
        currentUserId = userId
        currentNickname = nickname
        closed = false
        openSocket()
    }

    private fun openSocket() {
        if (closed) return
        val request = Request.Builder().url(WS_URL).build()
        webSocket = ApiClient.wsClient.newWebSocket(request, listener)
        send(
            JSONObject()
                .put("type", "join")
                .put("roomId", currentRoomId ?: "")
                .put("userId", currentUserId ?: "")
                .put("nickname", currentNickname)
        )
    }

    // MARK: - 위치 전송
    fun sendLocation(latitude: Double, longitude: Double) {
        send(
            JSONObject()
                .put("type", "location")
                .put("latitude", latitude)
                .put("longitude", longitude)
        )
    }

    // MARK: - 연결 해제
    fun disconnect() {
        closed = true
        reconnectJob?.cancel()
        reconnectJob = null
        val rid = currentRoomId
        val uid = currentUserId
        if (rid != null && uid != null) {
            send(JSONObject().put("type", "leave").put("roomId", rid).put("userId", uid))
        }
        webSocket?.close(1000, null)
        webSocket = null
        currentRoomId = null
        _memberLocations.value = emptyList()
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
            "location" -> {
                val userId = json.optString("userId").ifEmpty { return }
                val nickname = json.optString("nickname").ifEmpty { return }
                if (!json.has("latitude") || !json.has("longitude")) return
                val loc = WsMemberLocation(
                    userId = userId,
                    nickname = nickname,
                    latitude = json.optDouble("latitude"),
                    longitude = json.optDouble("longitude"),
                    updatedAt = System.currentTimeMillis(),
                )
                val list = _memberLocations.value.toMutableList()
                val idx = list.indexOfFirst { it.userId == userId }
                if (idx >= 0) list[idx] = loc else list.add(loc)
                _memberLocations.value = list
            }

            "left" -> {
                val userId = json.optString("userId").ifEmpty { return }
                _memberLocations.value = _memberLocations.value.filter { it.userId != userId }
            }
        }
    }

    // MARK: - 자동 재연결 (3초 후)
    private fun scheduleReconnect() {
        if (closed || currentRoomId == null) return
        webSocket?.cancel()
        webSocket = null
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(3000)
            openSocket()
        }
    }

    private fun send(json: JSONObject) {
        webSocket?.send(json.toString())
    }
}
