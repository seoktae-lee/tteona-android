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

    /** 마지막으로 서버에서 받아온 시각 — 이 안에서는 다시 부르지 않는다 */
    private var lastFetchedAt: Long = 0L
    private const val CACHE_TTL_MS = 5 * 60 * 1000L

    /** 이미 지역 로드를 마친 위도 밴드 — 같은 곳을 오갈 때 같은 쿼리를 반복하지 않는다 */
    private val loadedBands = mutableSetOf<String>()

    /**
     * 지도·탐색 공용 코스 목록.
     *
     * 같은 데이터를 여러 화면이 각자 부르고(지도/탐색), 화면이 재생성될 때마다 또 불러
     * 진입할 때 300건을 두 번씩 받아오고 있었다. 최근에 받아둔 게 있으면 건너뛴다.
     * 당겨서 새로고침처럼 최신본이 꼭 필요할 때만 force로 강제한다.
     */
    suspend fun fetchCourses(blockedUserIds: List<String> = emptyList(), force: Boolean = false) {
        if (!force && _courses.value.isNotEmpty() &&
            System.currentTimeMillis() - lastFetchedAt < CACHE_TTL_MS
        ) return

        _isLoading.value = true
        try {
            val snapshots = popularAndCuratedQueries().map { it.get().await() }
            _courses.value = merge(snapshots.flatMap { it.documents }, blockedUserIds)
            lastFetchedAt = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.w("CourseService", "fetchCourses 실패", e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * 지도·탐색의 기본 로드는 **두 쿼리**다. 하나로 합칠 수 없다.
     *
     * 1. 인기순 상위 300 — 코스가 수천 개로 늘어도 진입이 느려지거나 읽기 비용이
     *    폭증하지 않게 둔 상한. 지도 특성상 수백 개 이상 핀은 사람이 구분하지 못한다.
     * 2. 큐레이션 전량 — **큐레이션 코스는 좋아요가 0에서 시작하므로 1번에 절대 들지
     *    못한다.** 이 쿼리가 없으면 코스를 넣어도 지도에 영원히 안 뜬다.
     *    실측(2026-08-21): 큐레이션 438개 중 274개만 잡히고 164개가 사라졌다.
     *    수백 개 규모라 상한 500이면 충분하고, 정렬을 걸지 않아 복합 인덱스도 필요 없다.
     *
     * (특정 지역의 비인기 UGC 코스는 fetchCoursesNear가 보완한다)
     */
    private fun popularAndCuratedQueries(): List<Query> = listOf(
        db.collection("courses").orderBy("likeCount", Query.Direction.DESCENDING).limit(300),
        db.collection("courses").whereEqualTo("curated", true).limit(500),
    )

    /**
     * 두 쿼리 결과를 courseId 기준으로 합치고 차단 유저를 걸러낸다.
     * 큐레이션 코스가 인기 상위 300에 들면 양쪽에 나타나므로 중복 제거가 필수다.
     */
    private fun merge(docs: List<DocumentSnapshot>, blockedUserIds: List<String>): List<Course> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<Course>()
        for (doc in docs) {
            val course = doc.toCourse() ?: continue
            if (course.authorId in blockedUserIds) continue
            if (!seen.add(course.courseId)) continue
            result += course
        }
        return result
    }

    /**
     * 좌표 주변 코스를 불러와 병합한다. 지도를 옮겼을 때, 인기 상위 300에 못 든
     * 그 동네 코스가 안 보이는 것을 메운다.
     *
     * Firestore에 지리 인덱스가 없다. 대신 UGC 코스는 생성 시 region이 대표 장소의
     * 위도로 만들어지고("37.4°N"), 큐레이션 코스는 별도 latBand를 갖는다 —
     * 사실상 유일한 좌표 단서라 이 둘을 쓴다.
     * 밴드는 0.1°(약 11km) 단위라 경계에서 놓치지 않도록 위아래 한 칸씩 같이 본다.
     * 위도 밴드는 경도를 가리지 못하므로, 받아온 뒤 실제 거리로 한 번 더 거른다.
     */
    suspend fun fetchCoursesNear(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 60.0,
        blockedUserIds: List<String> = emptyList(),
    ) {
        val bands = listOf(-0.1, 0.0, 0.1).map { Course.latBand(latitude + it) }
        // 이미 훑은 밴드는 건너뛴다 — 지도를 조금씩 미는 동안 같은 쿼리가 반복된다
        val fresh = bands.filter { loadedBands.add(it) }
        if (fresh.isEmpty()) return

        val fetched = mutableListOf<Course>()
        for (band in fresh) {
            // UGC 코스: region 자체가 위도 밴드다
            runCatching {
                db.collection("courses").whereEqualTo("region", band).limit(100).get().await()
            }.getOrNull()?.documents?.mapNotNullTo(fetched) { it.toCourse() }

            // 큐레이션 코스: region이 읽히는 지역명("서울")이라 별도 latBand로 찾는다.
            // 이게 없으면 전국 큐레이션이 초기 로드 상한(500) 밖으로 밀리는 순간
            // 그 지역으로 지도를 옮겨도 영영 나타나지 않는다.
            runCatching {
                db.collection("courses").whereEqualTo("latBand", band).limit(100).get().await()
            }.getOrNull()?.documents?.mapNotNullTo(fetched) { it.toCourse() }
        }

        val existing = _courses.value.map { it.courseId }.toSet()
        val merged = fetched.filter { course ->
            if (course.courseId in existing) return@filter false
            if (course.authorId in blockedUserIds) return@filter false
            val main = course.mainPlace ?: return@filter false
            distanceKm(latitude, longitude, main.latitude, main.longitude) <= radiusKm
        }.distinctBy { it.courseId }
        if (merged.isEmpty()) return
        _courses.value = _courses.value + merged
    }

    private fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
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
                    PushService.notifyCourseLiked(course.authorId, likerNickname, course.courseName, course.courseId)
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
        // 차단 목록이 계정마다 다르므로 캐시를 그대로 물려주면 안 된다 —
        // 로그아웃 뒤 남의 차단 결과가 새 계정에 그대로 보인다.
        _courses.value = emptyList()
        lastFetchedAt = 0L
        loadedBands.clear()
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
            curated = d["curated"] as? Boolean ?: false,
            curationSource = d["curationSource"] as? String,
            latBand = d["latBand"] as? String,
            nearbyFood = (d["nearbyFood"] as? List<*>)?.mapNotNull { toNearbyFood(it) } ?: emptyList(),
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
        source = m["source"] as? String,
        sourceVideoId = m["sourceVideoId"] as? String,
    )
}

private fun toNearbyFood(raw: Any?): com.seoktaedev.tteona.core.model.NearbyFood? {
    val m = raw as? Map<*, *> ?: return null
    val name = m["name"] as? String ?: return null
    return com.seoktaedev.tteona.core.model.NearbyFood(
        name = name,
        latitude = (m["latitude"] as? Number)?.toDouble() ?: return null,
        longitude = (m["longitude"] as? Number)?.toDouble() ?: return null,
        nearPlaceName = m["nearPlaceName"] as? String ?: "",
        distanceM = (m["distanceM"] as? Number)?.toInt() ?: 0,
        rating = (m["rating"] as? Number)?.toDouble(),
        ratingCount = (m["ratingCount"] as? Number)?.toInt(),
        source = m["source"] as? String,
        sourceVideoId = m["sourceVideoId"] as? String,
    )
}
