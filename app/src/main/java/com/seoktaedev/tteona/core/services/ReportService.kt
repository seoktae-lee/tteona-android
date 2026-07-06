package com.seoktaedev.tteona.core.services

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * 콘텐츠 신고 — iOS Core/Services/ReportService.swift의 이식본.
 * Firestore reports 컬렉션에 적재하면 24시간 내 검토·삭제 정책으로 처리된다.
 */
object ReportService {
    private val db get() = FirebaseFirestore.getInstance()

    /** 신고 사유 선택지 (iOS confirmationDialog와 동일) */
    val REASONS = listOf("영리목적/홍보", "음란성/선정성", "욕설/비하", "아동 유해 콘텐츠", "기타")

    suspend fun reportContent(
        reporterId: String,
        targetType: String,
        targetId: String,
        targetAuthorId: String,
        reason: String,
    ) {
        val ref = db.collection("reports").document()
        val data = mapOf(
            "reportId" to ref.id,
            "reporterId" to reporterId,
            "targetType" to targetType,
            "targetId" to targetId,
            "targetAuthorId" to targetAuthorId,
            "reason" to reason,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        ref.set(data).await()
    }
}
