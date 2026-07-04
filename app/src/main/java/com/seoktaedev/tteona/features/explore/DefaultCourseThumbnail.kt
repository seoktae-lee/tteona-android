package com.seoktaedev.tteona.features.explore

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.ui.theme.TteOrange

/**
 * iOS Features/Explore/DefaultCourseThumbnail.swift의 Compose 이식본.
 * 커스텀 썸네일이 없는 코스에 쓰이는 기본 썸네일 —
 * 틸 그라데이션 + 지도 등고선 + 오렌지 굽은 길 + 나루 캐릭터.
 */
@Composable
fun DefaultCourseThumbnail(compact: Boolean = false, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1E3A40), Color(0xFF2C5A61), Color(0xFF3A7B7E))
                )
            ),
    ) {
        val base = minOf(maxWidth, maxHeight)

        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 지도 등고선 패턴 (은은하게) — iOS ContourLines와 동일 좌표
            val contour = Path().apply {
                repeat(3) { i ->
                    val inset = i * w * 0.14f
                    moveTo(w * 0.45f + inset, -h * 0.05f)
                    quadraticTo(w * 0.95f, -h * 0.02f, w * 1.05f, h * 0.42f + inset * 0.6f)
                }
                repeat(2) { i ->
                    val inset = i * w * 0.16f
                    moveTo(-w * 0.05f, h * 0.70f - inset * 0.5f)
                    quadraticTo(w * 0.02f, h * 1.02f, w * 0.42f - inset, h * 1.08f)
                }
            }
            drawPath(contour, Color.White.copy(alpha = 0.07f), style = Stroke(width = 1.5.dp.toPx()))

            // 오렌지 굽은 길 — iOS WindingRoad와 동일 좌표
            val road = Path().apply {
                moveTo(w * 0.15f, h * 0.05f)
                cubicTo(w * 0.55f, h * 0.12f, w * 0.05f, h * 0.28f, w * 0.30f, h * 0.45f)
                cubicTo(w * 0.52f, h * 0.60f, w * 0.80f, h * 0.48f, w * 0.75f, h * 0.70f)
                cubicTo(w * 0.72f, h * 0.88f, w * 0.48f, h * 0.92f, w * 0.60f, h * 1.02f)
            }
            drawPath(
                road,
                TteOrange,
                style = Stroke(width = (if (compact) 6 else 12).dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // 타이포그래피 — 중심 위치 (x, y) 비율을 BiasAlignment로 변환 (bias = 2p - 1)
        if (compact) {
            RoadLabel("TRAVEL", base.value * 0.12f, -14f, BiasAlignment(-0.32f, -0.52f))
        } else {
            RoadLabel("TRAVEL", base.value * 0.10f, -16f, BiasAlignment(-0.36f, -0.60f))
            RoadLabel("MOVE", base.value * 0.10f, 12f, BiasAlignment(-0.44f, 0.44f))
            RoadLabel("EXPLORE", base.value * 0.10f, -14f, BiasAlignment(0.40f, 0.00f))
        }

        // 나루 캐릭터 (우하단)
        Image(
            painter = painterResource(R.drawable.tteoni_wink),
            contentDescription = null,
            modifier = Modifier
                .width(maxWidth * if (compact) 0.30f else 0.24f)
                .align(BiasAlignment(0.60f, 0.66f)),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxWithConstraintsScope.RoadLabel(
    text: String,
    fontSize: Float,
    angle: Float,
    alignment: Alignment,
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .align(alignment)
            .rotate(angle),
    )
}
