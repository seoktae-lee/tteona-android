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
                    path == "/course" || path.startsWith("/course/") -> {
                        // ?id= 우선, 경로형(/course/{id})도 지원 (iOS와 동일)
                        val id = uri.getQueryParameter("id")
                            ?: path.removePrefix("/course/").takeIf { it.isNotEmpty() && it != path }
                        if (!id.isNullOrEmpty()) _pendingCourseId.value = id
                    }

                    path == "/room" || path.startsWith("/room/") -> {
                        val code = uri.getQueryParameter("code")
                            ?: path.removePrefix("/room/").takeIf { it.isNotEmpty() && it != path }
                        if (!code.isNullOrEmpty()) _pendingRoomCode.value = code
                    }
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
