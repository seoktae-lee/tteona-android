package com.seoktaedev.tteona.core.services

import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * iOS Core/Services/AppNotificationManager.swift의 이식본 (알림 → 화면 라우팅).
 * 알림 탭 시 어느 채팅방을 열지(pendingChatRoom) 상태로 전달한다.
 */
data class PendingChatRoom(val roomId: String, val targetUserId: String)

object AppNotificationManager {
    private val _pendingChatRoom = MutableStateFlow<PendingChatRoom?>(null)
    val pendingChatRoom: StateFlow<PendingChatRoom?> = _pendingChatRoom

    // 현재 보고 있는 채팅방 — 이 방의 알림은 포그라운드에서 표시하지 않음 (iOS activeChatRoom)
    @Volatile
    var activeChatRoomId: String? = null

    fun clearPendingChatRoom() {
        _pendingChatRoom.value = null
    }

    // 알림 탭으로 앱 진입 시 인텐트 extras에서 라우팅 정보 추출
    // (iOS didReceive response 대응 — 서버 data 페이로드: type/roomId/senderUserId/targetUserId)
    fun handleNotificationExtras(extras: Bundle?) {
        extras ?: return
        val type = extras.getString("type") ?: return
        val roomId = extras.getString("roomId")?.takeIf { it.isNotEmpty() } ?: return
        val senderUserId = extras.getString("senderUserId") ?: return
        val targetUserId = when (type) {
            "feed_comment" -> extras.getString("targetUserId") ?: senderUserId
            else -> senderUserId
        }
        _pendingChatRoom.value = PendingChatRoom(roomId, targetUserId)
    }
}
