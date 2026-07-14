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

    // 도착 알림 탭 → 해당 장소 카메라 열기 (iOS pendingPlaceName)
    private val _pendingPlaceName = MutableStateFlow<String?>(null)
    val pendingPlaceName: StateFlow<String?> = _pendingPlaceName

    fun clearPendingPlaceName() {
        _pendingPlaceName.value = null
    }

    // 좋아요·코스 따라가기 알림 탭 → 해당 코스 상세 (iOS pendingCourseId)
    private val _pendingCourseId = MutableStateFlow<String?>(null)
    val pendingCourseId: StateFlow<String?> = _pendingCourseId

    fun clearPendingCourseId() {
        _pendingCourseId.value = null
    }

    // Vlog 완성 알림 탭 → 완성본 재생 (iOS pendingVlogURL)
    private val _pendingVlogUrl = MutableStateFlow<String?>(null)
    val pendingVlogUrl: StateFlow<String?> = _pendingVlogUrl

    fun clearPendingVlogUrl() {
        _pendingVlogUrl.value = null
    }

    // 주간 리포트 알림 탭 → 프로필 탭 (iOS shouldOpenProfile)
    private val _shouldOpenProfile = MutableStateFlow(false)
    val shouldOpenProfile: StateFlow<Boolean> = _shouldOpenProfile

    fun clearShouldOpenProfile() {
        _shouldOpenProfile.value = false
    }

    // 오후 8시 리마인더 알림 탭 → '나의 오늘' 세션 열기 (iOS shouldOpenTodaySession)
    private val _shouldOpenTodaySession = MutableStateFlow(false)
    val shouldOpenTodaySession: StateFlow<Boolean> = _shouldOpenTodaySession

    fun clearShouldOpenTodaySession() {
        _shouldOpenTodaySession.value = false
    }

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
        // 도착 알림 (iOS action == "openCamera")
        if (extras.getString("action") == "openCamera") {
            extras.getString("placeName")?.let { _pendingPlaceName.value = it }
            return
        }
        // 오후 8시 리마인더 (iOS action == "openTodaySession")
        if (extras.getString("action") == "openTodaySession") {
            _shouldOpenTodaySession.value = true
            return
        }
        val type = extras.getString("type") ?: return

        // 방(roomId)과 무관한 알림 — 그동안 탭해도 앱만 열리고 아무 데도 가지 않았다.
        when (type) {
            "course_liked", "course_followed" -> {
                extras.getString("courseId")?.takeIf { it.isNotEmpty() }?.let { _pendingCourseId.value = it }
                return
            }
            "vlog_done" -> {
                extras.getString("url")?.takeIf { it.isNotEmpty() }?.let { _pendingVlogUrl.value = it }
                return
            }
            "weekly_report" -> {
                _shouldOpenProfile.value = true
                return
            }
        }

        // 이하 그룹/채팅 알림 — roomId로 열 화면을 정한다.
        val roomId = extras.getString("roomId")?.takeIf { it.isNotEmpty() } ?: return
        val senderUserId = extras.getString("senderUserId") ?: return
        val targetUserId = when (type) {
            "feed_comment" -> extras.getString("targetUserId") ?: senderUserId
            else -> senderUserId
        }
        _pendingChatRoom.value = PendingChatRoom(roomId, targetUserId)
    }
}
