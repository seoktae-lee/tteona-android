package com.seoktaedev.tteona.core.services

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.firestore
import com.seoktaedev.tteona.core.model.AppUser
import kotlinx.coroutines.tasks.await

/**
 * iOS Core/Services/UserService.swift의 Kotlin 이식본 (축약).
 * 지금 필요한 작성자 조회만 우선 이식하고, 닉네임 변경/차단은 설정 화면 이식 시 추가한다.
 */
object UserService {
    private val db get() = Firebase.firestore
    private val authorCache = mutableMapOf<String, AppUser>()

    suspend fun fetchAuthor(uid: String): AppUser? {
        authorCache[uid]?.let { return it }
        val doc = runCatching { db.collection("users").document(uid).get().await() }.getOrNull() ?: return null
        val user = doc.toAppUser() ?: return null
        authorCache[uid] = user
        return user
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toAppUser(): AppUser? {
    val d = data ?: return null
    val blocked = (d["blockedUserIds"] as? List<*>)?.filterIsInstance<String>()
    return AppUser(
        uid = d["uid"] as? String ?: id,
        email = d["email"] as? String ?: "",
        nickname = d["nickname"] as? String ?: "",
        createdAt = (d["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L,
        isVerified = d["isVerified"] as? Boolean ?: false,
        creatorLabel = d["creatorLabel"] as? String,
        blockedUserIds = blocked,
        profileImageUrl = d["profileImageUrl"] as? String,
    )
}
