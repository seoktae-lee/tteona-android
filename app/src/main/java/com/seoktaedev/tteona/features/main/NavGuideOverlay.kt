package com.seoktaedev.tteona.features.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.util.Haptics
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import com.seoktaedev.tteona.ui.theme.glowCircle
import kotlin.math.sin

/**
 * 첫 실행 내비게이션 가이드 — iOS Features/Main/NavGuideOverlay.swift의 이식본.
 * 온보딩 직후 메인 화면 위에서 나루가 탭 구성을 스포트라이트로 안내하는 코치마크.
 * 스텝이 넘어가면 실제로 해당 탭으로 전환해 화면을 직접 보여준다.
 */
@Composable
fun NavGuideOverlay(
    tabBounds: (Int) -> androidx.compose.ui.geometry.Rect? = { null },
    onSelectTab: (Int) -> Unit,
    onFinish: () -> Unit,
) {
    val view = LocalView.current
    var stepIndex by remember { mutableIntStateOf(0) }

    data class GuideStep(val mascotRes: Int, val title: String, val message: String, val tabIndex: Int?)

    val steps = remember {
        listOf(
            GuideStep(R.drawable.tteoni_guide, "안녕하세요, 나루예요!", "떠나를 30초만에 둘러볼까요?\n제가 안내해드릴게요", null),
            GuideStep(R.drawable.tteoni_travel, "홈 — 지도에서 떠나기", "지도에서 마음에 드는 코스를 골라\n'떠나기'를 누르면 여행이 시작돼요", 0),
            GuideStep(R.drawable.tteoni_wink, "탐색 — 코스 모아보기", "전 세계 코스를 카드로 넘겨보고\n인기 코스를 발견해보세요", 1),
            GuideStep(R.drawable.tteoni_jump, "채팅 — 함께 떠나기", "그룹을 만들어 친구·가족과\n코스와 '나의 오늘'을 공유해요", 2),
            GuideStep(R.drawable.tteoni_thumbsup, "준비 완료!", "이제 지도에서 첫 코스를 골라\n떠나볼 일만 남았어요", null),
        )
    }
    val step = steps[stepIndex]
    val isLast = stepIndex == steps.size - 1

    fun advance() {
        if (isLast) {
            Haptics.success(view)
            onFinish()
        } else {
            Haptics.light(view)
            stepIndex += 1
            steps[stepIndex].tabIndex?.let(onSelectTab)
            if (stepIndex == steps.size - 1) onSelectTab(0)
        }
    }

    // 마스코트 플로팅 (iOS FloatingEffect)
    val infinite = rememberInfiniteTransition(label = "float")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing)),
        label = "float-t",
    )

    Box(Modifier.fillMaxSize()) {
        // 딤 배경 + 탭 스포트라이트 컷아웃 (BlendMode.Clear)
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { advance() },
        ) {
            drawRect(Color.Black.copy(alpha = 0.6f))
            step.tabIndex?.let { tab ->
                val spot = spotlightRect(tab, size, tabBounds(tab))
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = spot.first,
                    size = spot.second,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(spot.second.height / 2),
                    blendMode = BlendMode.Clear,
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.9f),
                    topLeft = spot.first,
                    size = spot.second,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(spot.second.height / 2),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        // 가이드 카드 (나루 + 말풍선)
        AnimatedContent(
            targetState = stepIndex,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.95f)).togetherWith(fadeOut() + scaleOut(targetScale = 0.95f)) },
            label = "guide-card",
            modifier = Modifier
                .align(if (step.tabIndex == null) Alignment.Center else Alignment.BottomCenter)
                .padding(horizontal = 32.dp)
                .padding(bottom = if (step.tabIndex == null) 0.dp else 130.dp)
                .navigationBarsPadding(),
        ) { index ->
            val s = steps[index]
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.background),
            ) {
                // 카드 안쪽 주황 글로우가 천천히 일렁임 (iOS GuideCardBackground)
                Box(
                    Modifier
                        .size(220.dp)
                        .offset(x = (-90 + 160 * t / (2 * Math.PI).toFloat()).dp, y = (-80 + 60 * t / (2 * Math.PI).toFloat()).dp)
                        .glowCircle(TteOrange, 0.32f)
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(190.dp)
                        .offset(x = (60 - 160 * t / (2 * Math.PI).toFloat()).dp, y = (-20 + 50 * t / (2 * Math.PI).toFloat()).dp)
                        .glowCircle(Color(0xFFFFA159), 0.26f)
                )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .padding(24.dp),
            ) {
                Image(
                    painterResource(s.mascotRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(if (s.tabIndex == null) 150.dp else 100.dp)
                        .offset(y = (sin(t.toDouble()) * 6).dp),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TteDarkGray)
                    Text(
                        s.message,
                        fontSize = 15.sp,
                        color = TteMediumGray,
                        textAlign = TextAlign.Center,
                        lineHeight = 21.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (index != steps.size - 1) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(TteFieldBackground)
                                .clickable {
                                    Haptics.light(view)
                                    onFinish()
                                },
                        ) {
                            Text("건너뛰기", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TteMediumGray)
                        }
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(TteOrange)
                            .clickable { advance() },
                    ) {
                        Text(
                            if (index == steps.size - 1) "떠나볼까요!" else "다음 ${index + 1}/${steps.size}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }
            }
        }
    }
}

/**
 * 하단 내비게이션 탭 스포트라이트 위치 — (topLeft, size).
 * MainTabScreen이 onGloballyPositioned로 실측한 탭 bounds를 우선 사용해
 * 기기 해상도·밀도·제스처바 유무와 무관하게 항상 탭 정중앙에 맞춘다.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.spotlightRect(
    tab: Int,
    canvasSize: Size,
    measured: androidx.compose.ui.geometry.Rect?,
): Pair<Offset, Size> {
    if (measured != null && measured.width > 0f) {
        val spotW = measured.width * 0.86f
        val spotH = minOf(measured.height, 64.dp.toPx())
        return Offset(measured.center.x - spotW / 2, measured.center.y - spotH / 2) to Size(spotW, spotH)
    }
    // 폴백 — 실측 전이면 dp 기반 추정 (Material3 NavigationBar 80dp)
    val tabWidth = canvasSize.width / 4f
    val centerX = (tab + 0.5f) * tabWidth
    val spotW = tabWidth * 0.86f
    val spotH = 56.dp.toPx()
    val centerY = canvasSize.height - 44.dp.toPx()
    return Offset(centerX - spotW / 2, centerY - spotH / 2) to Size(spotW, spotH)
}
