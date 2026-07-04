package com.seoktaedev.tteona.core.services

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * iOS Core/Services/DeepLinkHandler.swift의 이식본.
 * https://tteona.kr/course?id=... (코스 공유), /room?code=... (그룹 초대) 처리.
 */
object DeepLinkHandler {
    private val _pendingCourseId = MutableStateFlow<String?>(null)
    val pendingCourseId: StateFlow<String?> = _pendingCourseId

    private val _pendingRoomCode = MutableStateFlow<String?>(null)
    val pendingRoomCode: StateFlow<String?> = _pendingRoomCode

    fun handle(uri: Uri?) {
        uri ?: return
        when {
            uri.scheme == "tteona" -> {
                when (uri.host) {
                    "course" -> {
                        val id = uri.getQueryParameter("id")
                            ?: uri.pathSegments.firstOrNull()
                        if (!id.isNullOrEmpty()) _pendingCourseId.value = id
                    }

                    "room" -> uri.getQueryParameter("code")?.let { _pendingRoomCode.value = it }
                }
            }

            uri.host == "tteona.kr" -> {
                val path = uri.path ?: ""
                when {
                    path == "/course" || path.startsWith("/course/") ->
                        uri.getQueryParameter("id")?.let { _pendingCourseId.value = it }

                    path == "/room" ->
                        uri.getQueryParameter("code")?.let { _pendingRoomCode.value = it }
                }
            }
        }
    }

    fun clearPendingCourse() {
        _pendingCourseId.value = null
    }

    fun clearPendingRoom() {
        _pendingRoomCode.value = null
    }
}
