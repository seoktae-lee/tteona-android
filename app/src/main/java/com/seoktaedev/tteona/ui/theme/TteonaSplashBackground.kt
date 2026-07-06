package com.seoktaedev.tteona.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 앱 진입·로딩·로그인 화면용 배경 — 흰 바탕 위 주황색 글로우가 천천히 일렁이는 배경.
 * iOS Core/Utils/TteonaSplashBackground.swift의 이식본 (원 3개, 5초 easeInOut 왕복 반복).
 */
@Composable
fun TteonaSplashBackground(modifier: Modifier = Modifier) {
    // iOS는 5초 easeInOut autoreverse — Compose는 Reverse 반복 + 부드러운 이징으로 대응
    val transition = rememberInfiniteTransition(label = "splash-aurora")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splash-aurora-t",
    )

    Box(modifier.fillMaxSize().background(TteBackground)) {
        // Circle 1: tteOrange 0.22, 420dp — (130,-80) → (-110,-200)
        Box(
            Modifier
                .size(420.dp)
                .offset(x = (130 - 240 * t).dp, y = (-80 - 120 * t).dp)
                .glowCircle(TteOrange, 0.22f)
        )
        // Circle 2: 웜 오렌지 0.17, 360dp — (-100,300) → (130,180)
        Box(
            Modifier
                .size(360.dp)
                .offset(x = (-100 + 230 * t).dp, y = (300 - 120 * t).dp)
                .glowCircle(Color(0xFFFFA159), 0.17f)
        )
        // Circle 3: tteOrange 0.13, 320dp — (80,70) → (-60,300)
        Box(
            Modifier
                .size(320.dp)
                .offset(x = (80 - 140 * t).dp, y = (70 + 230 * t).dp)
                .glowCircle(TteOrange, 0.13f)
        )
    }
}
