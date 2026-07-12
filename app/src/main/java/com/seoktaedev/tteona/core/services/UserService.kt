package com.seoktaedev.tteona.core.services

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.seoktaedev.tteona.core.model.AppUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/**
 * iOS Core/Services/UserService.swift의 Kotlin 이식본.
 * currentUser는 Firestore users 문서 기준의 프로필(닉네임·프로필사진·차단목록).
 */
object UserService {
    private val db get() = Firebase.firestore
    private val authorCache = mutableMapOf<String, AppUser>()

    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser: StateFlow<AppUser?> = _currentUser

    suspend fun fetchAuthor(uid: String): AppUser? {
        authorCache[uid]?.let { return it }
        val doc = runCatching { db.collection("users").document(uid).get().await() }.getOrNull() ?: return null
        val user = doc.toAppUser() ?: return null
        authorCache[uid] = user
        return user
    }

    suspend fun fetchUser(uid: String) {
        val doc = runCatching { db.collection("users").document(uid).get().await() }.getOrNull()
        _currentUser.value = doc?.toAppUser()
    }

    suspend fun updateNickname(uid: String, nickname: String) {
        db.collection("users").document(uid).update("nickname", nickname).await()
        _currentUser.value = _currentUser.value?.copy(nickname = nickname)
    }

    /** 선호 여행 태그 저장 (null이면 해제) — 탐색 탭 추천 개인화에 사용 (iOS updatePreferredTag) */
    suspend fun updatePreferredTag(uid: String, tag: String?) {
        db.collection("users").document(uid)
            .update("preferredTag", tag ?: FieldValue.delete()).await()
        _currentUser.value = _currentUser.value?.copy(preferredTag = tag)
    }

    // WAS 업로드 라우트가 Firestore profileImageUrl 필드도 함께 저장하므로 로컬 상태만 갱신 (iOS와 동일)
    fun setProfileImageUrl(url: String) {
        _currentUser.value = _currentUser.value?.copy(profileImageUrl = url)
    }

    /** 닉네임을 원자적으로 예약한다(중복 방지). 성공 true, 이미 남이 선점했으면 false.
     *  nicknames/{닉네임} 문서를 create-only 규칙으로 만들어, 동시 가입 레이스에서도 선점이 원자적.
     *  (기존 유저는 예약 문서가 없으므로 호출부에서 isNicknameTaken 검사도 함께 쓴다.) — iOS reserveNickname */
    suspend fun reserveNickname(nickname: String, uid: String): Boolean {
        val key = nickname.trim()
        if (key.isEmpty()) return false
        val ref = db.collection("nicknames").document(key)
        return try {
            ref.set(mapOf("uid" to uid, "createdAt" to FieldValue.serverTimestamp())).await()
            true
        } catch (e: Exception) {
            // 이미 존재 — 내가 소유한 예약이면(재시도 등) 성공으로 간주
            val doc = runCatching { ref.get().await() }.getOrNull()
            (doc?.get("uid") as? String) == uid
        }
    }

    /** 내 닉네임 예약을 반납한다(닉네임 변경 시 옛 닉네임 해제). — iOS releaseNickname */
    suspend fun releaseNickname(nickname: String, uid: String) {
        val key = nickname.trim()
        if (key.isEmpty()) return
        val ref = db.collection("nicknames").document(key)
        val doc = runCatching { ref.get().await() }.getOrNull() ?: return
        if ((doc.get("uid") as? String) == uid) runCatching { ref.delete().await() }
    }

    suspend fun isNicknameTaken(nickname: String): Boolean {
        val snapshot = runCatching {
            db.collection("users")
                .whereEqualTo("nickname", nickname.trim())
                .limit(1)
                .get()
                .await()
        }.getOrNull()
        return snapshot?.isEmpty == false
    }

    suspend fun blockUser(uid: String, blockedUid: String) {
        if (uid == blockedUid) return
        db.collection("users").document(uid)
            .update("blockedUserIds", FieldValue.arrayUnion(blockedUid)).await()
        _currentUser.value = _currentUser.value?.let { u ->
            if (u.uid == uid) {
                val list = (u.blockedUserIds ?: emptyList())
                if (blockedUid in list) u else u.copy(blockedUserIds = list + blockedUid)
            } else u
        }
    }

    suspend fun unblockUser(uid: String, blockedUid: String) {
        db.collection("users").document(uid)
            .update("blockedUserIds", FieldValue.arrayRemove(blockedUid)).await()
        _currentUser.value = _currentUser.value?.let { u ->
            if (u.uid == uid) u.copy(blockedUserIds = u.blockedUserIds?.filter { it != blockedUid }) else u
        }
    }

    fun clear() {
        _currentUser.value = null
    }
}

private fun DocumentSnapshot.toAppUser(): AppUser? {
    val d = data ?: return null
    val blocked = (d["blockedUserIds"] as? List<*>)?.filterIsInstance<String>()
    return AppUser(
        uid = d["uid"] as? String ?: id,
        // email은 공개 users 문서에서 읽지 않는다 (PII) — 내 이메일은 Auth(currentUser)가 소유,
        // 타인 이메일은 취급하지 않는다 (iOS AppUser와 동일). 레거시 잔존 값도 노출 안 함.
        email = "",
        nickname = d["nickname"] as? String ?: "",
        createdAt = (d["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L,
        isVerified = d["isVerified"] as? Boolean ?: false,
        creatorLabel = d["creatorLabel"] as? String,
        blockedUserIds = blocked,
        profileImageUrl = d["profileImageUrl"] as? String,
        preferredTag = d["preferredTag"] as? String,
    )
}
