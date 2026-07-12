package com.seoktaedev.tteona.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// iOS Core/Models/Course.swift의 Kotlin 이식본

@Serializable
data class Place(
    val order: Int,
    val placeName: String,
    val latitude: Double,
    val longitude: Double,
    val clipFileName: String? = null, // 나의 오늘 촬영 클립 파일명 — reorder와 무관하게 파일 추적
) {
    val id: String get() = "${order}_$placeName"
}

// 표시 전용 — 바로 연속되는 동일 장소(같은 곳에서 여러 번 촬영)를 하나로 접고 1부터 재번호.
// 떨어져서 다시 방문한 동일 장소는 그대로 남는다. 저장·Vlog 합성은 원본 places를 사용할 것.
fun List<Place>.mergedForDisplay(): List<Place> {
    val collapsed = mutableListOf<Place>()
    for (place in sortedBy { it.order }) {
        if (place.placeName == collapsed.lastOrNull()?.placeName) continue
        collapsed.add(place)
    }
    return collapsed.mapIndexed { idx, place -> place.copy(order = idx + 1) }
}

@Serializable
data class Course(
    val id: String? = null, // Firestore 문서 ID
    val courseId: String,
    val authorId: String,
    val courseName: String,
    val tag: CourseTag,
    val region: String,
    val likeCount: Int,
    val createdAt: Long, // epoch millis — Firestore Timestamp는 서비스 레이어에서 변환
    val places: List<Place>,
    val mainPlaceOrder: Int? = null, // 유저가 지정한 대표 장소의 order (미지정 시 자동 선택)
) {
    // 유저에게 보여줄 장소 목록 — 연속 중복이 병합된 표시용 (원본 places는 그대로 유지)
    val displayPlaces: List<Place> get() = places.mergedForDisplay()

    // 대표 장소 — 핀·썸네일·날씨·추천의 기준점.
    // 유저가 지정했으면 그 장소, 아니면 자동 선택(경유지 후순위), 그것도 없으면 첫 장소.
    val mainPlace: Place?
        get() = mainPlaceOrder?.let { order -> places.firstOrNull { it.order == order } }
            ?: autoPickMainPlace(places)

    companion object {
        // 경유지성 장소(역·주차장·터미널 등)를 후순위로 두고 명소성 장소를 대표로 자동 선택
        fun autoPickMainPlace(places: List<Place>): Place? {
            if (places.isEmpty()) return null
            return places.firstOrNull { !isTransitLike(it.placeName) } ?: places.first()
        }

        fun isTransitLike(name: String): Boolean {
            if (name.endsWith("역")) return true // OO역 (지하철/기차역)
            val keywords = listOf("주차장", "터미널", "정류장", "환승센터", "휴게소", "톨게이트", "공영주차")
            return keywords.any { name.contains(it) }
        }
    }
}

@Serializable
// label은 Firestore 저장·서버 전송용 한국어 원문(직렬화 값). 화면 표시는 labelRes를 사용할 것.
// pinRes는 홈 지도에 찍히는 태그별 커스텀 핀 — 취향 선택 UI도 같은 핀으로 보여준다 (iOS pinImageName).
enum class CourseTag(val label: String, val emoji: String, val labelRes: Int, val pinRes: Int) {
    @SerialName("커플") COUPLE("커플", "💑", com.seoktaedev.tteona.R.string.tag_couple, com.seoktaedev.tteona.R.drawable.pin_couple),
    @SerialName("친구") FRIENDS("친구", "👫", com.seoktaedev.tteona.R.string.tag_friends, com.seoktaedev.tteona.R.drawable.pin_friends),
    @SerialName("가족") FAMILY("가족", "👨‍👩‍👧‍👦", com.seoktaedev.tteona.R.string.tag_family, com.seoktaedev.tteona.R.drawable.pin_family),
    @SerialName("혼자") SOLO("혼자", "🧍", com.seoktaedev.tteona.R.string.tag_solo, com.seoktaedev.tteona.R.drawable.pin_solo),
}

val courseRegions = listOf("서울", "부산", "제주", "경주", "강릉", "전주", "기타")

// Firestore의 region 값은 한 가지 형태가 아니다: 초기 코스는 courseRegions의 한글 지역명을,
// 즉석 세션은 "37.5°N" 같은 좌표 문자열을 저장한다. 아는 지역명만 매핑하고 나머지는 null(원문 사용).
private val courseRegionResMap: Map<String, Int> = mapOf(
    "서울" to com.seoktaedev.tteona.R.string.region_seoul,
    "부산" to com.seoktaedev.tteona.R.string.region_busan,
    "제주" to com.seoktaedev.tteona.R.string.region_jeju,
    "경주" to com.seoktaedev.tteona.R.string.region_gyeongju,
    "강릉" to com.seoktaedev.tteona.R.string.region_gangneung,
    "전주" to com.seoktaedev.tteona.R.string.region_jeonju,
    "기타" to com.seoktaedev.tteona.R.string.region_other,
)

// 화면 표시용 지역명 리소스 — region을 그대로 그리면 영어/일본어 유저에게 한글이 노출된다.
// null이면 매핑에 없는 값(좌표 문자열 등)이므로 호출부가 course.region을 그대로 쓰면 된다.
val Course.regionLabelRes: Int? get() = courseRegionResMap[region]
