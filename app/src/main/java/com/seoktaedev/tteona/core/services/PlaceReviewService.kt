package com.seoktaedev.tteona.core.services

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Date

data class TteonaPlaceReview(
    val id: String,
    val userId: String,
    val nickname: String,
    val rating: Int,
    val comment: String?,
    val createdAt: Date,
)

/**
 * 떠나 사용자 장소 후기 — iOS Core/Services/PlaceReviewService.swift의 이식본.
 * placeReviews/{placeKey}/reviews/{userId} 구조 (1인당 장소별 1개, 재방문 시 업데이트).
 */
object PlaceReviewService {
    private val db get() = FirebaseFirestore.getInstance()

    data class ReviewResult(val reviews: List<TteonaPlaceReview>, val visitCount: Int)

    suspend fun fetchReviews(placeKey: String, blockedUserIds: List<String> = emptyList()): ReviewResult {
        val snapshot = runCatching {
            db.collection("placeReviews").document(placeKey)
                .collection("reviews")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .get().await()
        }.getOrNull() ?: return ReviewResult(emptyList(), 0)

        val all = snapshot.documents.mapNotNull { doc ->
            val userId = doc.getString("userId") ?: return@mapNotNull null
            val nickname = doc.getString("nickname") ?: return@mapNotNull null
            val rating = (doc.get("rating") as? Number)?.toInt() ?: return@mapNotNull null
            val createdAt = (doc.get("createdAt") as? Timestamp)?.toDate() ?: return@mapNotNull null
            TteonaPlaceReview(
                id = doc.id,
                userId = userId,
                nickname = nickname,
                rating = rating,
                comment = doc.getString("comment"),
                createdAt = createdAt,
            )
        }
        val reviews = all.filter { it.userId !in blockedUserIds }
        return ReviewResult(reviews, reviews.size)
    }

    suspend fun saveReview(placeKey: String, userId: String, nickname: String, rating: Int, comment: String?) {
        if (rating <= 0) return
        val data = mutableMapOf<String, Any>(
            "userId" to userId,
            "nickname" to nickname,
            "rating" to rating,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        comment?.trim()?.takeIf { it.isNotEmpty() }?.let { data["comment"] = it }
        runCatching {
            db.collection("placeReviews").document(placeKey)
                .collection("reviews").document(userId)
                .set(data).await()
        }
    }
}
