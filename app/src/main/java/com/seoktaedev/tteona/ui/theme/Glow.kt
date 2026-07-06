package com.seoktaedev.tteona.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 주황 글로우(일렁임) 원 — iOS의 blur된 Circle 배경 이식.
 *
 * Modifier.blur는 (1) API 31 미만에서 아무 효과가 없고 (2) 기본 edgeTreatment가
 * Rectangle이라 블러가 레이어 사각형 경계에서 잘려 '각진 네모'가 보인다.
 * 대신 가우시안 블러와 유사한 감쇠의 radial gradient를 직접 그려
 * 모든 기기·API 레벨에서 동일하게 부드러운 글로우를 만든다.
 *
 * 기존 `.size(S).blur(B).background(color, CircleShape)` 자리에
 * `.size(S).glowCircle(color, alpha)`로 교체하는 드롭인 대체재.
 * (drawBehind는 부모가 clip하지 않는 한 경계 밖까지 그려져 blur 확산을 재현한다)
 */
fun Modifier.glowCircle(color: Color, alpha: Float, spread: Float = 1.5f): Modifier =
    drawBehind {
        val radius = size.minDimension / 2f * spread
        if (radius <= 0f) return@drawBehind
        drawCircle(
            brush = Brush.radialGradient(
                0.00f to color.copy(alpha = alpha),
                0.35f to color.copy(alpha = alpha * 0.85f),
                0.65f to color.copy(alpha = alpha * 0.38f),
                1.00f to color.copy(alpha = 0f),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
