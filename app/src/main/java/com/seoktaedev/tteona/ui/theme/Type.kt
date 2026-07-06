package com.seoktaedev.tteona.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.seoktaedev.tteona.R

/**
 * 떠나 기본 서체 — Pretendard (iOS Font+Tteona.swift 대응).
 * FontFamily에 굵기별 폰트를 모두 등록해 두면 Text(fontWeight = ...)가
 * 자동으로 가장 가까운 굵기의 Pretendard를 선택한다.
 * sp 단위는 시스템 글자 크기 설정에 비례해 스케일된다(iOS Dynamic Type 대응).
 */
val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold),
    Font(R.font.pretendard_extrabold, FontWeight.Black),
)

private val Default = Typography()

private fun TextStyle.pretendard() = copy(fontFamily = Pretendard)

/** Material3 Typography 전체를 Pretendard로 교체 — 모든 Text가 기본으로 상속받는다. */
val TteonaTypography = Typography(
    displayLarge = Default.displayLarge.pretendard(),
    displayMedium = Default.displayMedium.pretendard(),
    displaySmall = Default.displaySmall.pretendard(),
    headlineLarge = Default.headlineLarge.pretendard(),
    headlineMedium = Default.headlineMedium.pretendard(),
    headlineSmall = Default.headlineSmall.pretendard(),
    titleLarge = Default.titleLarge.pretendard(),
    titleMedium = Default.titleMedium.pretendard(),
    titleSmall = Default.titleSmall.pretendard(),
    bodyLarge = Default.bodyLarge.pretendard(),
    bodyMedium = Default.bodyMedium.pretendard(),
    bodySmall = Default.bodySmall.pretendard(),
    labelLarge = Default.labelLarge.pretendard(),
    labelMedium = Default.labelMedium.pretendard(),
    labelSmall = Default.labelSmall.pretendard(),
)
