package com.seoktaedev.tteona.core.model

import kotlinx.serialization.Serializable

// iOS Core/Models/Footprint.swift의 Kotlin 이식본.

/**
 * 발자취 기록 — 브이로그를 만들 때마다 1건씩 쌓이는 여행 기록.
 * users/{uid}/footprints/{sessionId} — 같은 세션에서 재생성해도 중복 적재되지 않는다.
 */
@Serializable
data class FootprintRecord(
    val id: String? = null,
    val courseId: String = "",
    val courseName: String = "",
    val date: Long = 0L,               // epoch millis — Firestore Timestamp는 서비스 레이어에서 변환
    val placeCount: Int = 0,
    val sigCodes: List<String> = emptyList(),        // 색칠된 한국 시군구 코드
    val provinceCodes: List<String> = emptyList(),   // 색칠된 세계 주/도 코드 (ISO 3166-2)
    val countryCodes: List<String> = emptyList(),    // 방문 국가 ISO3 (국가 카운트용)
    val regionNames: List<String> = emptyList(),     // 표시용 지역 이름 ("서울 종로구", "Ōsaka" 등)
    val points: List<FootprintPoint> = emptyList(),  // 경로 보조 표시용 장소 좌표 (순서대로)
)

/** 경로 표시용 좌표 한 점 */
@Serializable
data class FootprintPoint(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
)

/**
 * 발자취 요약 (지도 색칠용) — 유저 문서에 누적되는 방문 지역 집합.
 * 지도 렌더링은 이것만 있으면 된다.
 */
data class FootprintSummary(
    val sigCodes: Set<String> = emptySet(),
    val provinceCodes: Set<String> = emptySet(),
    val countryCodes: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = sigCodes.isEmpty() && provinceCodes.isEmpty() && countryCodes.isEmpty()
}
