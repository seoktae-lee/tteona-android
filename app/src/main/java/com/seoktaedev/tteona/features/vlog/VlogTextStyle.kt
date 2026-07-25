package com.seoktaedev.tteona.features.vlog

import androidx.annotation.FontRes
import androidx.annotation.StringRes
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.seoktaedev.tteona.R

/**
 * 브이로그 장소 자막의 서체·크기 선택 모델.
 * key는 서버(server.js `VLOG_FONT_FILES`)·iOS(VlogFont)와 공유한다 — 값을 바꾸면 세 곳을 함께 맞춰야 한다.
 * 실제 합성은 서버가 하며, 여기 FontFamily는 선택 화면 미리보기 렌더에만 쓰인다.
 */
enum class VlogFont(
    val key: String,
    @StringRes val labelRes: Int,
    @FontRes val fontRes: Int,
) {
    // 선택 화면 노출 순서 (key는 그대로 — 저장값·기본값 호환 유지)
    KKUBULIM("kkubulim", R.string.vlog_font_kkubulim, R.font.bm_kkubulim),
    GOOLTOKKI("gooltokki", R.string.vlog_font_gooltokki, R.font.hs_gooltokki),
    BLACKHANSANS("blackhansans", R.string.vlog_font_blackhansans, R.font.black_han_sans),
    JUA("jua", R.string.vlog_font_jua, R.font.jua),
    GOWUN("gowun", R.string.vlog_font_gowun, R.font.gowun_batang),
    PRETENDARD("pretendard", R.string.vlog_font_pretendard, R.font.pretendard_bold),
    NANUMPEN("nanumpen", R.string.vlog_font_nanumpen, R.font.nanum_pen);

    val family: FontFamily get() = FontFamily(Font(fontRes))

    companion object {
        fun from(key: String?): VlogFont = entries.firstOrNull { it.key == key } ?: GOWUN
    }
}

/** 자막 크기 3단 — 서버 `VLOG_FONT_SCALE`와 배율을 맞춘다 (medium = 기존 동작). */
enum class VlogFontScale(
    val key: String,
    @StringRes val labelRes: Int,
    val multiplier: Float,
) {
    SMALL("small", R.string.vlog_fontScale_small, 1.0f),
    MEDIUM("medium", R.string.vlog_fontScale_medium, 1.28f),
    LARGE("large", R.string.vlog_fontScale_large, 1.64f);

    companion object {
        fun from(key: String?): VlogFontScale = entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}
