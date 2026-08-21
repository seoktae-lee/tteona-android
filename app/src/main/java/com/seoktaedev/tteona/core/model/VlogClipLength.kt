package com.seoktaedev.tteona.core.model

/**
 * 한 장소당 촬영 길이 선택지. iOS Core/Models/VlogClipLength.swift의 이식본.
 *
 * 총 예산(무료 30초 / PRO 5분)이 실제 문지기이고, 길이는 그 예산을 어떻게 쪼갤지의 문제다.
 * 그래서 짧은 길이는 무료로 열어 둔다 — 2초를 고르면 15곳까지 찍을 수 있어 오히려
 * 앱을 더 오래 쓰게 되고, 10초를 고르면 3곳에서 예산이 끝나 PRO 필요를 빨리 체감한다.
 */
enum class VlogClipLength(val key: String, val seconds: Double) {
    // 모든 길이가 자동 종료다 — 수동 종료를 두면 한 장소에서 예산을 다 태우는 사고가 나고,
    // "짧게 툭 찍으면 알아서 브이로그가 된다"는 컨셉과도 어긋난다.
    S2("s2", 2.0),
    S3("s3", 3.0),
    S5("s5", 5.0),
    S10("s10", 10.0),
    S15("s15", 15.0),
    S20("s20", 20.0);

    /** PRO 전용인가 — 10초 이상은 유료 */
    val requiresPro: Boolean get() = seconds >= 10.0

    companion object {
        /** 무료 기본값 — 5초(6곳)보다 3초(10곳)가 장소를 더 쌓게 해 브이로그가 좋아진다 */
        val FREE_DEFAULT = S3

        fun from(key: String?): VlogClipLength = entries.firstOrNull { it.key == key } ?: FREE_DEFAULT
    }
}
