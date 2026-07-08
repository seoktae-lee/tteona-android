package com.seoktaedev.tteona.core.model

import kotlinx.serialization.Serializable

// iOS Core/Models/AppUser.swift의 Kotlin 이식본.
// 오래된 계정에 없는 필드(isVerified 등)가 있어도 디코딩이 깨지지 않도록
// 전 필드 기본값 부여 (iOS의 관대한 init(from:)과 동일한 효과).
@Serializable
data class AppUser(
    val uid: String = "",
    val email: String = "",
    val nickname: String = "",
    val createdAt: Long = 0L, // epoch millis — Firestore Timestamp는 서비스 레이어에서 변환
    val isVerified: Boolean = false,
    val creatorLabel: String? = null,
    val blockedUserIds: List<String>? = null,
    val profileImageUrl: String? = null,
    /** 선호 여행 태그 (CourseTag rawValue, 한글) — 온보딩/설정에서 선택, 코스 추천 개인화에 사용 */
    val preferredTag: String? = null,
)
