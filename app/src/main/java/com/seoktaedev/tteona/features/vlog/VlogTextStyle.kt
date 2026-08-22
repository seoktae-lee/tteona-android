package com.seoktaedev.tteona.features.vlog

import androidx.annotation.FontRes
import androidx.annotation.StringRes
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.graphics.Color
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

/**
 * 자막에 무엇을 보여줄지. 키는 서버(job options.subtitleFields)·iOS와 공유한다.
 */
enum class VlogSubtitleFields(val key: String, @StringRes val labelRes: Int) {
    BOTH("both", R.string.vlog_subtitleFields_both),      // 장소 + 시각
    PLACE("place", R.string.vlog_subtitleFields_place),   // 장소만
    TIME("time", R.string.vlog_subtitleFields_time);      // 시각만

    val showsPlace: Boolean get() = this != TIME
    val showsTime: Boolean get() = this != PLACE

    companion object {
        fun from(key: String?): VlogSubtitleFields = entries.firstOrNull { it.key == key } ?: BOTH
    }
}

/**
 * 자막 강조색 프리셋.
 *
 * **서버로는 키만 보내고 실제 색값은 서버가 자기 표에서 찾는다.**
 * 색값을 그대로 넘기면 사용자 입력이 ffmpeg 필터 문자열에 직접 섞여 들어가는 통로가 된다
 * (`fontcolor=` 뒤는 필터 인자로 파싱되므로 `:`·`,` 한 글자로 체인을 조작할 수 있다).
 * 여기 Color는 **미리보기 렌더에만** 쓴다 — 서버 `VLOG_SUBTITLE_COLORS`와 같은 값을 유지할 것.
 */
enum class VlogSubtitleColor(val key: String, @StringRes val labelRes: Int, val color: Color) {
    ORANGE("orange", R.string.vlog_subtitleColor_orange, Color(0xFFFF6B35)),
    WHITE("white", R.string.vlog_subtitleColor_white, Color(0xFFFFFFFF)),
    YELLOW("yellow", R.string.vlog_subtitleColor_yellow, Color(0xFFFFD400)),
    MINT("mint", R.string.vlog_subtitleColor_mint, Color(0xFF3DDC97)),
    SKY("sky", R.string.vlog_subtitleColor_sky, Color(0xFF4FC3F7)),
    PINK("pink", R.string.vlog_subtitleColor_pink, Color(0xFFFF7AB6)),
    INK("ink", R.string.vlog_subtitleColor_ink, Color(0xFF1A1A1F));   // 밝은 영상용 먹색

    companion object {
        fun from(key: String?): VlogSubtitleColor = entries.firstOrNull { it.key == key } ?: ORANGE
    }
}

/**
 * 브이로그 자막 설정 한 묶음. iOS VlogSubtitleStyle의 이식본.
 *
 * 서체·크기만 있던 시절엔 파라미터 두 개를 그대로 넘겼는데, 표시항목·색·캡션이 붙으면서
 * 호출 시그니처가 함께 부풀었다. 한 덩어리로 넘겨 호출부가 옵션 개수를 신경 쓰지 않게 한다.
 */
data class VlogSubtitleStyle(
    val font: VlogFont = VlogFont.GOWUN,
    val scale: VlogFontScale = VlogFontScale.MEDIUM,
    val fields: VlogSubtitleFields = VlogSubtitleFields.BOTH,
    val color: VlogSubtitleColor = VlogSubtitleColor.ORANGE,
    /**
     * 자막을 클립이 끝날 때까지 띄워 둘지.
     * 꺼두면 2.5초만 보이고 사라진다 — 장면을 가리지 않는 대신 놓치기도 쉽다.
     */
    val holdsSubtitle: Boolean = false,
    /**
     * 장소별 한 줄 문구. 키는 **클립 파일명** — 순번은 재정렬로 바뀔 수 있어 파일명에 묶는다.
     * 서체·크기·표시항목·색은 브이로그 전체에 공통이고, 이 문구만 장소마다 다르다.
     */
    val captions: Map<String, String> = emptyMap(),
) {
    /** 해당 클립에 적힌 문구 (없으면 빈 문자열) */
    fun caption(clipFileName: String?): String =
        if (clipFileName == null) "" else sanitize(captions[clipFileName] ?: "")

    companion object {
        /** 한 줄이라는 약속을 지키기 위한 상한. 서버도 같은 값을 다시 적용한다. */
        const val CAPTION_MAX_LENGTH = 20

        /** 줄바꿈·제어문자를 걷어내고 길이를 자른다 (클라이언트를 믿지 않는 서버와 같은 규칙) */
        fun sanitize(raw: String): String =
            raw.replace(Regex("[\\r\\n\\t\\u0000-\\u001F\\u007F]"), " ")
                .trim()
                .take(CAPTION_MAX_LENGTH)
    }
}

/**
 * 자막이 화면 폭을 넘지 않게 크기를 낮추고, 그래도 넘치면 말줄임으로 자르는 규칙.
 *
 * ffmpeg `drawtext`는 줄바꿈도 축소도 하지 않는다. `x=(w-text_w)/2`로 가운데를 맞추므로
 * 글자 폭이 화면보다 크면 x가 음수가 되어 양쪽 끝이 그대로 잘려 나간다.
 *
 * **미리보기와 서버가 같은 규칙을 따라야 한다.** 규칙이 다르면 미리보기에서 본 모습과
 * 결과물이 어긋난다. 서버 쪽 같은 구현은 server.js `fitSubtitle`,
 * iOS는 VlogTextStyle.swift `VlogSubtitleFit`.
 */
object VlogSubtitleFit {
    /** 화면 폭 중 실제로 쓰는 비율 — 가장자리에 여백을 남긴다 */
    const val USABLE_RATIO = 0.90

    /**
     * 고른 크기 대비 여기까지만 줄인다.
     * 바닥이 없으면 긴 이름에서 '보통'과 '크게'가 똑같은 크기로 수렴해 단계 구분이 사라진다.
     */
    const val FLOOR_RATIO = 0.72

    /**
     * 글자 폭 어림값. 한글·한자·가나는 한 칸(1em), 나머지는 대략 절반으로 본다.
     * 폰트마다 실제 자폭이 달라 정확한 계산이 아니라 '넘치지 않게' 하는 보수적 추정이다.
     */
    fun estimatedWidth(text: String, fontSize: Double): Double {
        var em = 0.0
        for (ch in text) {
            val c = ch.code
            val wide = (c in 0x1100..0x11FF) || (c in 0x3040..0x30FF) ||
                (c in 0x3130..0x318F) || (c in 0x4E00..0x9FFF) ||
                (c in 0xAC00..0xD7A3) || (c in 0xFF00..0xFF60)
            em += if (wide) 1.0 else 0.55
        }
        return em * fontSize
    }

    /** 담기는 크기와 (필요하면 잘린) 글자를 돌려준다. 넘치지 않으면 고른 크기를 그대로 쓴다. */
    fun fit(text: String, wanted: Double, frameWidth: Double): Pair<Double, String> {
        val maxW = frameWidth * USABLE_RATIO
        if (maxW <= 0 || text.isEmpty() || wanted <= 0) return wanted to text

        val needed = estimatedWidth(text, wanted)
        if (needed <= maxW) return wanted to text

        val size = maxOf(wanted * maxW / needed, wanted * FLOOR_RATIO)
        if (estimatedWidth(text, size) <= maxW) return size to text

        // 바닥까지 줄여도 넘친다 — 말줄임으로 자른다
        var truncated = text
        while (truncated.isNotEmpty() && estimatedWidth("$truncated…", size) > maxW) {
            truncated = truncated.dropLast(1)
        }
        return size to (if (truncated.isEmpty()) "…" else "$truncated…")
    }
}
