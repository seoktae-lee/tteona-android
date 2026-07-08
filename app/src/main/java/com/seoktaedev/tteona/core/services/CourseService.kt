package com.seoktaedev.tteona.core.services

import android.util.Log
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.i18n.LocaleManager
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.seoktaedev.tteona.core.model.Course
import com.seoktaedev.tteona.core.model.CourseTag
import com.seoktaedev.tteona.core.model.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/**
 * iOS Core/Services/CourseService.swift의 Kotlin 이식본.
 * Firestore courses 컬렉션 조회/좋아요 — iOS와 동일한 데이터를 공유한다.
 */
object CourseService {
    private val db get() = Firebase.firestore

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _likedCourseIds = MutableStateFlow<Set<String>>(emptySet())
    val likedCourseIds: StateFlow<Set<String>> = _likedCourseIds

    private var likedCourseIdsFetched = false

    suspend fun fetchCourses(blockedUserIds: List<String> = emptyList()) {
        _isLoading.value = true
        try {
            val snapshot = db.collection("courses")
                .orderBy("likeCount", Query.Direction.DESCENDING)
                .get()
                .await()
            _courses.value = snapshot.documents
                .mapNotNull { it.toCourse() }
                .filter { it.authorId !in blockedUserIds }
        } catch (e: Exception) {
            Log.w("CourseService", "fetchCourses 실패", e)
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun fetchCourse(courseId: String): Course? {
        _courses.value.firstOrNull { it.courseId == courseId }?.let { return it }
        return runCatching {
            db.collection("courses").document(courseId).get().await().toCourse()
        }.getOrNull()
    }

    suspend fun fetchLikedCourseIds(userId: String) {
        if (likedCourseIdsFetched) return
        val doc = runCatching { db.collection("users").document(userId).get().await() }.getOrNull()
        @Suppress("UNCHECKED_CAST")
        val ids = doc?.get("likedCourseIds") as? List<String> ?: emptyList()
        _likedCourseIds.value = ids.toSet()
        likedCourseIdsFetched = true
    }

    /** 좋아요 토글 — iOS와 동일한 낙관적 업데이트 + 실패 시 롤백 + 작성자 푸시·통계 이벤트 */
    suspend fun toggleLike(courseId: String, userId: String, likerNickname: String = "") {
        val alreadyLiked = courseId in _likedCourseIds.value
        val previousLiked = _likedCourseIds.value
        val previousCourses = _courses.value

        _likedCourseIds.value =
            if (alreadyLiked) _likedCourseIds.value - courseId else _likedCourseIds.value + courseId
        _courses.value = _courses.value.map { c ->
            if (c.courseId == courseId) {
                c.copy(likeCount = if (alreadyLiked) maxOf(0, c.likeCount - 1) else c.likeCount + 1)
            } else c
        }

        try {
            val batch = db.batch()
            val userRef = db.collection("users").document(userId)
            val courseRef = db.collection("courses").document(courseId)
            if (alreadyLiked) {
                batch.set(userRef, mapOf("likedCourseIds" to FieldValue.arrayRemove(courseId)), com.google.firebase.firestore.SetOptions.merge())
                batch.update(courseRef, "likeCount", FieldValue.increment(-1))
            } else {
                batch.set(userRef, mapOf("likedCourseIds" to FieldValue.arrayUnion(courseId)), com.google.firebase.firestore.SetOptions.merge())
                batch.update(courseRef, "likeCount", FieldValue.increment(1))
            }
            batch.commit().await()

            if (!alreadyLiked) {
                // 좋아요 시 코스 작성자에게 푸시 (본인 제외) + 통계 이벤트 (iOS와 동일)
                val course = _courses.value.firstOrNull { it.courseId == courseId }
                if (course != null && course.authorId != userId) {
                    PushService.notifyCourseLiked(course.authorId, likerNickname, course.courseName)
                }
                StatsService.postEvent(com.seoktaedev.tteona.core.model.StatsEvent.COURSE_LIKED, userId)
            }
        } catch (e: Exception) {
            // 실패 시 롤백
            _likedCourseIds.value = previousLiked
            _courses.value = previousCourses
            throw e
        }
    }

    /** 코스 저장 — 즉흥 세션의 "코스로 저장" (iOS saveCourse) */
    suspend fun saveCourse(course: Course) {
        val data = mapOf(
            "courseId" to course.courseId,
            "authorId" to course.authorId,
            "courseName" to course.courseName,
            "tag" to course.tag.label,
            "region" to course.region,
            "likeCount" to course.likeCount,
            "createdAt" to Timestamp(java.util.Date(course.createdAt)),
            "places" to course.places.map { p ->
                buildMap<String, Any> {
                    put("order", p.order)
                    put("placeName", p.placeName)
                    put("latitude", p.latitude)
                    put("longitude", p.longitude)
                    p.clipFileName?.let { put("clipFileName", it) }
                }
            },
        )
        db.collection("courses").document(course.courseId).set(data).await()
        _courses.value = listOf(course) + _courses.value
    }

    /** 내 코스 삭제 (iOS deleteCourse) */
    suspend fun deleteCourse(course: Course) {
        db.collection("courses").document(course.courseId).delete().await()
        _courses.value = _courses.value.filter { it.courseId != course.courseId }
        _likedCourseIds.value = _likedCourseIds.value - course.courseId
    }

    /** 작성자 차단 즉시 해당 작성자의 코스를 목록에서 숨김 (다음 재조회 전까지 노출 방지) */
    fun hideAuthorCourses(authorId: String) {
        _courses.value = _courses.value.filter { it.authorId != authorId }
    }

    fun clearUserData() {
        _likedCourseIds.value = emptySet()
        likedCourseIdsFetched = false
    }
}

// Firestore 문서 → Course 수동 매핑.
// (kotlinx.serialization은 Firestore Timestamp를 다루지 못하므로 직접 변환)
fun DocumentSnapshot.toCourse(): Course? {
    val d = data ?: return null
    val authorId = d["authorId"] as? String ?: return null
    return try {
        Course(
            id = id,
            courseId = d["courseId"] as? String ?: id,
            authorId = authorId,
            courseName = d["courseName"] as? String ?: "",
            tag = CourseTag.entries.firstOrNull { it.label == d["tag"] } ?: CourseTag.FRIENDS,
            region = d["region"] as? String ?: LocaleManager.string(R.string.region_other),
            likeCount = (d["likeCount"] as? Number)?.toInt() ?: 0,
            createdAt = (d["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L,
            places = (d["places"] as? List<*>)?.mapNotNull { toPlace(it) } ?: emptyList(),
            mainPlaceOrder = (d["mainPlaceOrder"] as? Number)?.toInt(),
        )
    } catch (e: Exception) {
        Log.w("CourseService", "코스 파싱 실패: $id", e)
        null
    }
}

private fun toPlace(raw: Any?): Place? {
    val m = raw as? Map<*, *> ?: return null
    val order = (m["order"] as? Number)?.toInt() ?: return null
    val placeName = m["placeName"] as? String ?: return null
    return Place(
        order = order,
        placeName = placeName,
        latitude = (m["latitude"] as? Number)?.toDouble() ?: 0.0,
        longitude = (m["longitude"] as? Number)?.toDouble() ?: 0.0,
        clipFileName = m["clipFileName"] as? String,
    )
}
