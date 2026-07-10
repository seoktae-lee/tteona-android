package com.seoktaedev.tteona.core.util

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView

/**
 * 코스 카드처럼 탭 가능한 카드의 눌림 피드백 (iOS Core/Utils/PressableCardStyle.swift 대응).
 * 누르는 동안 0.97배로 줄고, 탭이 확정되면 가벼운 햅틱을 울린다.
 *
 * LazyColumn/LazyVerticalGrid 안에서도 스크롤과 충돌하지 않는다 —
 * interactionSource의 pressed는 스크롤이 시작되면 자동으로 풀린다.
 */
fun Modifier.pressableCard(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "pressableCardScale",
    )
    val view = LocalView.current

    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
        ) {
            Haptics.light(view)
            onClick()
        }
}
