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
    // 이 **장소**의 출처 — 방송·유튜브에 소개된 곳이면 채운다.
    // 코스가 아니라 장소에 다는 이유: 큐레이션 코스의 '점심식사' 자리 하나만 방송 맛집으로
    // 바뀌는 게 실제 쓰임새다. 코스 전체가 한 프로그램에서 온 경우는 오히려 드물다.
    // 사람 이름·얼굴은 넣지 않는다 — 프로그램/채널명만. (성명·초상 무단사용 회피)
    val source: String? = null,          // 예: "또간집", "전현무계획"
    val sourceVideoId: String? = null,   // 유튜브 영상 ID. 재호스팅하지 않고 공식 임베드로만 재생
) {
    val id: String get() = "${order}_$placeName"
}

/**
 * 코스 근처의 추천 식당.
 *
 * **코스의 places에 끼워 넣지 않는다.** 이용약관 11조에 "원 데이터를 임의로 수정하지 않는다"고
 * 명시했고, 장소를 추가하는 것은 표기 정리의 범위를 넘는다. 원본 코스는 그대로 두고
 * 곁들임으로만 보여주며, 실제로 넣을지는 사용자가 세션을 시작할 때 고른다.
 */
@Serializable
data class NearbyFood(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** 코스의 어느 장소 근처인지 — "무엇의 곁인지"를 알아야 사용자가 동선을 가늠한다 */
    val nearPlaceName: String = "",
    val distanceM: Int = 0,
    /**
     * 구글 평점과 리뷰 수. 추천 목록을 고를 때 이미 걸렀지만(3.7 미만 제외),
     * 화면에도 보여줘야 사용자가 들를지 스스로 판단할 수 있다.
     * 구글에 등재되지 않은 지역 노포는 null이며, 그것만으로 나쁜 집은 아니다.
     */
    val rating: Double? = null,
    val ratingCount: Int? = null,
    /** 방송·유튜브 출처. 지금은 비어 있고, 방송 맛집 DB가 붙는 단계에서 채운다. */
    val source: String? = null,
    val sourceVideoId: String? = null,
) {
    val id: String get() = name

    /**
     * 장소 상세 시트가 Place를 받으므로 그대로 넘길 수 있게 변환한다.
     * order는 코스 동선의 순번인데 추천 맛집은 동선에 없으므로 0으로 둔다.
     */
    val asPlace: Place get() = Place(order = 0, placeName = name, latitude = latitude, longitude = longitude)
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

    /**
     * 공식 큐레이션 코스인가. **지도 쿼리·핀·정렬의 기준**이라 source 유무로 대신하지 않는다.
     * (관광공사 코스 / 제휴 크리에이터 코스는 출처 성격이 달라 한 필드로 겸할 수 없다)
     */
    val curated: Boolean = false,
    /**
     * 큐레이션 출처 표기 — "한국관광공사", "제휴:○○채널". 화면에 그대로 노출된다.
     * 공공데이터 이용조건상 출처 표기 의무가 있으므로 curated면 반드시 채운다.
     */
    val curationSource: String? = null,
    /**
     * 대표 장소의 위도 밴드("37.5°N", 0.1° 단위 ≈ 11km). **큐레이션 코스의 지역 검색 키다.**
     *
     * UGC 코스는 region에 위도 문자열이 들어가 있어 그걸 쓰지만, 큐레이션 코스는 화면에
     * "서울"·"경기 파주시"처럼 읽히는 지역명을 보여줘야 해서 region을 좌표로 쓸 수 없다.
     * 그래서 검색용 밴드를 별도 필드로 둔다.
     */
    val latBand: String? = null,
    /** 코스 근처 추천 식당 */
    val nearbyFood: List<NearbyFood> = emptyList(),
) {
    // 유저에게 보여줄 장소 목록 — 연속 중복이 병합된 표시용 (원본 places는 그대로 유지)
    val displayPlaces: List<Place> get() = places.mergedForDisplay()

    // 대표 장소 — 핀·썸네일·날씨·추천의 기준점.
    // 유저가 지정했으면 그 장소, 아니면 자동 선택(경유지 후순위), 그것도 없으면 첫 장소.
    val mainPlace: Place?
        get() = mainPlaceOrder?.let { order -> places.firstOrNull { it.order == order } }
            ?: autoPickMainPlace(places)

    companion object {
        /** 위도 → 밴드 문자열. 주입 스크립트와 앱이 같은 규칙을 써야 하므로 여기에 둔다. */
        fun latBand(latitude: Double): String =
            String.format(java.util.Locale.US, "%.1f°N", Math.round(latitude * 10) / 10.0)

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
