package com.seoktaedev.tteona.core.services

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.seoktaedev.tteona.core.model.AppUser
import com.seoktaedev.tteona.core.model.Course
import com.seoktaedev.tteona.core.model.FootprintPoint
import com.seoktaedev.tteona.core.model.FootprintRecord
import com.seoktaedev.tteona.core.model.FootprintSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * iOS Core/Services/FootprintService.swift의 Kotlin 이식본.
 * 브이로그 생성 시 방문 지역을 기록하고, 프로필 지도에 쓸 발자취를 조회한다.
 * - 색칠 집합: users/{uid} 문서의 visitedSigCodes / visitedCountryCodes (arrayUnion 누적)
 * - 타임라인: users/{uid}/footprints/{sessionId} 서브컬렉션
 */
object FootprintService {
    private val db get() = Firebase.firestore

    /** 내 발자취 요약 — 기록 직후 프로필 탭이 즉시 반영되도록 공유 상태로 보관 */
    private val _mySummary = MutableStateFlow(FootprintSummary())
    val mySummary: StateFlow<FootprintSummary> = _mySummary

    /** 마지막으로 새로 칠해진 지역 코드 — 프로필 탭 진입 시 하이라이트 연출용 */
    private val _lastNewCodes = MutableStateFlow<Set<String>>(emptySet())
    val lastNewCodes: StateFlow<Set<String>> = _lastNewCodes

    /** 그중 대표(최다 체류) 신규 지역 코드 — 카메라가 여기로 날아간다 */
    @Volatile
    private var lastPrimaryNewCode: String? = null

    /** 연출용으로 신규 코드와 대표 코드를 한 번에 꺼낸다 */
    fun consumeLastNew(): Pair<Set<String>, String?> {
        val codes = _lastNewCodes.value
        val primary = lastPrimaryNewCode
        _lastNewCodes.value = emptySet()
        lastPrimaryNewCode = null
        return codes to primary
    }

    // MARK: 기록 (브이로그 생성 성공 시)

    /** 코스의 장소들을 지역으로 판정해 발자취를 적재한다. 실패해도 앱 흐름을 막지 않는다. */
    /** 코스의 장소별 지역 집계 결과 — "가장 많이 머문 지역"이 앞에 오도록 체류순 정렬 */
    private data class Aggregated(
        val sigCodes: List<String>,
        val provinceCodes: List<String>,
        val countryCodes: List<String>,
        val regionNames: List<String>,
        val points: List<Map<String, Double>>,
    )

    /**
     * 코스의 장소들을 지역으로 판정·집계한다. 지역이 하나도 안 잡히면 null.
     * 첫 장소가 아니라 체류 빈도가 기준이라 잠깐 스친 환승지가 대표로 뽑히지 않는다.
     * 한국은 시군구, 해외는 주/도(admin-1) 단위.
     */
    private suspend fun aggregate(context: Context, course: Course): Aggregated? {
        val resolved = withContext(Dispatchers.Default) {
            FootprintAtlas.ensureLoaded(context)
            course.places.map { FootprintAtlas.resolve(context, it.latitude, it.longitude) }
        }

        val sigCount = mutableMapOf<String, Int>()
        val sigName = mutableMapOf<String, String>()
        val provCount = mutableMapOf<String, Int>()
        val provName = mutableMapOf<String, String>()
        val countryCount = mutableMapOf<String, Int>()
        for (region in resolved) {
            if (region.sig != null) {
                sigCount[region.sig.code] = (sigCount[region.sig.code] ?: 0) + 1
                sigName[region.sig.code] = region.sig.name
            } else if (region.province != null) {
                provCount[region.province.code] = (provCount[region.province.code] ?: 0) + 1
                provName[region.province.code] = region.province.name
            }
            region.countryCode?.let { c -> countryCount[c] = (countryCount[c] ?: 0) + 1 }
        }
        val byStay = compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
        val sigCodes = sigCount.entries.sortedWith(byStay).map { it.key }
        val provinceCodes = provCount.entries.sortedWith(byStay).map { it.key }
        val countryCodes = countryCount.entries.sortedWith(byStay).map { it.key }
        if (sigCodes.isEmpty() && provinceCodes.isEmpty()) return null

        val regionNames = sigCodes.mapNotNull { sigName[it] } + provinceCodes.mapNotNull { provName[it] }
        val points = course.places.sortedBy { it.order }
            .map { mapOf("lat" to it.latitude, "lng" to it.longitude) }
        return Aggregated(sigCodes, provinceCodes, countryCodes, regionNames, points)
    }

    suspend fun record(context: Context, course: Course, sessionId: String, userId: String) {
        val agg = aggregate(context, course) ?: run {
            android.util.Log.d("Footprint", "no region resolved — skip")
            return
        }
        val sigCodes = agg.sigCodes
        val provinceCodes = agg.provinceCodes
        val countryCodes = agg.countryCodes
        val regionNames = agg.regionNames
        val points = agg.points

        runCatching {
            // 새로 칠해지는 지역 계산 (하이라이트 연출용) — 기존 요약과 비교
            val current = _mySummary.value
            val newCodes = (sigCodes.toSet() - current.sigCodes) + (provinceCodes.toSet() - current.provinceCodes)

            // 세션ID를 문서ID로 → 같은 세션 재생성 시 덮어쓰기(중복 방지)
            db.collection("users").document(userId)
                .collection("footprints").document(sessionId)
                .set(
                    mapOf(
                        "courseId" to course.courseId,
                        "courseName" to course.courseName,
                        "date" to Timestamp(Date()),
                        "placeCount" to course.places.size,
                        "sigCodes" to sigCodes,
                        "provinceCodes" to provinceCodes,
                        "countryCodes" to countryCodes,
                        "regionNames" to regionNames,
                        "points" to points,
                    )
                ).await()
            db.collection("users").document(userId).update(
                mapOf(
                    "visitedSigCodes" to FieldValue.arrayUnion(*sigCodes.toTypedArray()),
                    "visitedProvinceCodes" to FieldValue.arrayUnion(*provinceCodes.toTypedArray()),
                    "visitedCountryCodes" to FieldValue.arrayUnion(*countryCodes.toTypedArray()),
                )
            ).await()

            _mySummary.value = FootprintSummary(
                sigCodes = current.sigCodes + sigCodes,
                provinceCodes = current.provinceCodes + provinceCodes,
                countryCodes = current.countryCodes + countryCodes,
            )
            if (newCodes.isNotEmpty()) {
                _lastNewCodes.value = newCodes
                // 대표 신규 지역 = 체류순 정렬(시군구 우선)에서 처음으로 등장하는 새 지역
                lastPrimaryNewCode = (sigCodes + provinceCodes).firstOrNull { it in newCodes }
            }
            android.util.Log.d("Footprint", "recorded sig=$sigCodes prov=$provinceCodes country=$countryCodes")
        }.onFailure {
            android.util.Log.e("Footprint", "record failed: ${it.message}")
        }
    }

    // MARK: 백필 (과거 코스 소급 — Phase 1, 유저당 1회)

    /**
     * 발자취 기록 훅이 생기기 전에 만든 코스들을 발자취로 소급 반영한다.
     * 내가 만든 코스는 실제 촬영 세션에서 저장된 것이므로 "실제 방문"으로 간주.
     * 문서 ID course_{courseId}로 멱등 — 부분 실패 시 다음 진입에서 재시도해도 중복 없음.
     */
    suspend fun backfillFromMyCourses(context: Context, userId: String) {
        val userRef = db.collection("users").document(userId)
        val doc = runCatching { userRef.get().await() }.getOrNull()
        if (doc?.getBoolean("footprintBackfillV1") == true) return

        val courses = fetchCourses(userId)
        if (courses.isEmpty()) {
            // 코스가 없어도 플래그를 세팅해 재실행을 막는다
            runCatching { userRef.update("footprintBackfillV1", true).await() }
            return
        }

        val allSig = mutableSetOf<String>()
        val allProv = mutableSetOf<String>()
        val allCountry = mutableSetOf<String>()
        var wroteAny = false
        var allSucceeded = true

        for (course in courses) {
            val agg = aggregate(context, course) ?: continue
            val ok = runCatching {
                userRef.collection("footprints").document("course_${course.courseId}")
                    .set(
                        mapOf(
                            "courseId" to course.courseId,
                            "courseName" to course.courseName,
                            "date" to Timestamp(Date(course.createdAt)), // 여행 시점 보존 → 타임라인 순서 정확
                            "placeCount" to course.places.size,
                            "sigCodes" to agg.sigCodes,
                            "provinceCodes" to agg.provinceCodes,
                            "countryCodes" to agg.countryCodes,
                            "regionNames" to agg.regionNames,
                            "points" to agg.points,
                        )
                    ).await()
            }.isSuccess
            if (ok) {
                allSig += agg.sigCodes
                allProv += agg.provinceCodes
                allCountry += agg.countryCodes
                wroteAny = true
            } else {
                allSucceeded = false
            }
        }

        // 방문 지역 합산 반영 (하이라이트 연출은 발동하지 않음 — 수십 곳 펄스 방지)
        val fields = mutableMapOf<String, Any>()
        if (wroteAny) {
            fields["visitedSigCodes"] = FieldValue.arrayUnion(*allSig.toTypedArray())
            fields["visitedProvinceCodes"] = FieldValue.arrayUnion(*allProv.toTypedArray())
            fields["visitedCountryCodes"] = FieldValue.arrayUnion(*allCountry.toTypedArray())
        }
        // 모든 쓰기가 성공했을 때만 플래그 세팅 → 부분 실패는 다음 진입에 재시도(멱등)
        if (allSucceeded) fields["footprintBackfillV1"] = true
        if (fields.isNotEmpty()) runCatching { userRef.update(fields).await() }

        if (wroteAny) {
            val cur = _mySummary.value
            _mySummary.value = FootprintSummary(
                sigCodes = cur.sigCodes + allSig,
                provinceCodes = cur.provinceCodes + allProv,
                countryCodes = cur.countryCodes + allCountry,
            )
        }
        android.util.Log.d("Footprint", "backfilled courses=${courses.size} wrote=$wroteAny complete=$allSucceeded")
    }

    // MARK: 조회

    /** 유저의 방문 지역 요약 (본인이면 공유 상태도 갱신) */
    suspend fun fetchSummary(userId: String, isMe: Boolean = false): FootprintSummary {
        val doc = runCatching { db.collection("users").document(userId).get().await() }.getOrNull()
        val data = doc?.data
        val summary = FootprintSummary(
            sigCodes = (data?.get("visitedSigCodes") as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet(),
            provinceCodes = (data?.get("visitedProvinceCodes") as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet(),
            countryCodes = (data?.get("visitedCountryCodes") as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet(),
        )
        if (isMe) _mySummary.value = summary
        return summary
    }

    /** 발자취 타임라인 (최신순) */
    suspend fun fetchFootprints(userId: String, limit: Long = 60): List<FootprintRecord> {
        val snapshot = runCatching {
            db.collection("users").document(userId)
                .collection("footprints")
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
        }.getOrNull() ?: return emptyList()
        return snapshot.documents.mapNotNull { it.toFootprintRecord() }
    }

    /** 특정 유저가 올린 코스 (프로필의 코스 목록용) */
    suspend fun fetchCourses(authorId: String, limit: Long = 50): List<Course> {
        val snapshot = runCatching {
            db.collection("courses")
                .whereEqualTo("authorId", authorId)
                .limit(limit)
                .get()
                .await()
        }.getOrNull() ?: return emptyList()
        return snapshot.documents.mapNotNull { it.toCourse() }
            .sortedByDescending { it.createdAt }
    }

    /** 닉네임 prefix 검색 (유저 찾기) */
    suspend fun searchUsers(query: String, limit: Long = 20): List<AppUser> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val snapshot = runCatching {
            db.collection("users")
                .whereGreaterThanOrEqualTo("nickname", q)
                .whereLessThan("nickname", q + "\uf8ff")
                .limit(limit)
                .get()
                .await()
        }.getOrNull() ?: return emptyList()
        return snapshot.documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            AppUser(
                uid = d["uid"] as? String ?: doc.id,
                email = "", // PII — 공개 users 문서에서 읽지 않음 (iOS와 동일)
                nickname = d["nickname"] as? String ?: "",
                isVerified = d["isVerified"] as? Boolean ?: false,
                creatorLabel = d["creatorLabel"] as? String,
                profileImageUrl = d["profileImageUrl"] as? String,
            )
        }
    }

    /** 로그아웃/계정 전환 시 로컬 상태 정리 */
    fun clear() {
        _mySummary.value = FootprintSummary()
        _lastNewCodes.value = emptySet()
        lastPrimaryNewCode = null
    }

    /** 시각 검증용 — 가짜 요약 주입 (DEBUG 프리뷰 전용) */
    fun setDemoSummary(sigCodes: Set<String>, provinceCodes: Set<String>, countryCodes: Set<String>) {
        _mySummary.value = FootprintSummary(sigCodes = sigCodes, provinceCodes = provinceCodes, countryCodes = countryCodes)
    }
}

private fun DocumentSnapshot.toFootprintRecord(): FootprintRecord? {
    val d = data ?: return null
    val points = (d["points"] as? List<*>)?.mapNotNull { raw ->
        val m = raw as? Map<*, *> ?: return@mapNotNull null
        FootprintPoint(
            lat = (m["lat"] as? Number)?.toDouble() ?: return@mapNotNull null,
            lng = (m["lng"] as? Number)?.toDouble() ?: return@mapNotNull null,
        )
    } ?: emptyList()
    return FootprintRecord(
        id = id,
        courseId = d["courseId"] as? String ?: "",
        courseName = d["courseName"] as? String ?: "",
        date = (d["date"] as? Timestamp)?.toDate()?.time ?: 0L,
        placeCount = (d["placeCount"] as? Number)?.toInt() ?: 0,
        sigCodes = (d["sigCodes"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        provinceCodes = (d["provinceCodes"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        countryCodes = (d["countryCodes"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        regionNames = (d["regionNames"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        points = points,
    )
}
