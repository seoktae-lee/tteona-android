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
enum class CourseTag(val label: String, val emoji: String) {
    @SerialName("커플") COUPLE("커플", "💑"),
    @SerialName("친구") FRIENDS("친구", "👫"),
    @SerialName("가족") FAMILY("가족", "👨‍👩‍👧‍👦"),
    @SerialName("혼자") SOLO("혼자", "🧍"),
}

val courseRegions = listOf("서울", "부산", "제주", "경주", "강릉", "전주", "기타")
