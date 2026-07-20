package com.seoktaedev.tteona.features.tutorial

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.util.Haptics
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import com.seoktaedev.tteona.ui.theme.glowCircle
import kotlin.math.sin

/**
 * 첫 브이로그 튜토리얼 UI — iOS Features/Tutorial/VlogTutorial.swift의 뷰 부분 이식본.
 * 상태머신은 [VlogTutorial], 시각 안내(말풍선·글로우·반짝임·축하카드)는 여기에 있다.
 *
 * 원칙 (iOS와 동일): 오버레이는 안내만 담당하고 터치를 가로채지 않는다.
 * 말풍선은 대상 버튼 바로 위 레이아웃 흐름에 놓여 버튼과 겹치지 않으므로,
 * X 버튼 외의 조작은 그대로 아래 버튼으로 전달된다.
 */

// ── 나루 말풍선 (iOS TutorialBubble) ──────────────────────────────────────
// 대상 버튼 바로 위에 놓는 컴팩트 안내 — 나루 + 한 줄 메시지 + 영구 종료 X.
@Composable
fun TutorialBubble(
    text: String,
    mascotRes: Int = R.drawable.tteoni_guide,
    modifier: Modifier = Modifier,
    onSkip: () -> Unit,
) {
    val view = LocalView.current
    // 마스코트 플로팅 (iOS floating amplitude 4)
    val infinite = rememberInfiniteTransition(label = "bubble-float")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400)),
        label = "bubble-float-t",
    )

    // 전 기종에서 좌우로 잘리지 않도록 가용 폭을 채우고 양옆 여백(20dp)을 둔다.
    // 말풍선 본체는 그 폭을 꽉 채우고, 텍스트는 마스코트를 제외한 남은 폭 안에서 줄바꿈한다.
    Box(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, TteOrange.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                .padding(start = 12.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        ) {
            Image(
                painter = painterResource(mascotRes),
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .offset(y = (sin(t.toDouble()) * 4).dp),
            )
            Text(
                text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TteDarkGray,
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f),
            )
        }

        // 영구 종료 X — 말풍선 우상단, 화면 밖으로 나가지 않도록 여백(20dp) 안쪽으로만 살짝 나오게
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-6).dp)
                .size(22.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    Haptics.light(view)
                    onSkip()
                },
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.tutorial_skip),
                tint = TteMediumGray,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

// ── '나의 오늘' 힌트 칩 (iOS chooseVlogOnly hint) ─────────────────────────
// 종료 시트에서 '브이로그만 생성하기' 위에 얹는 작은 나루 힌트.
@Composable
fun TutorialHintChip(text: String, mascotRes: Int = R.drawable.tteoni_wink) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(TteOrange.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Image(
            painter = painterResource(mascotRes),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TteOrange)
    }
}

// ── 대상 버튼 글로우 (iOS tutorialGlow) ───────────────────────────────────
// 눌러야 할 버튼 주위에 숨쉬는 흰 링 — 버튼 형태에 맞춰 cornerRadius(dp)를 받는다.
// active일 때만 링을 그리고, 평상시엔 뷰에 아무 영향이 없다.
fun Modifier.tutorialGlow(active: Boolean, cornerRadius: Int): Modifier =
    if (!active) this else composed {
        val infinite = rememberInfiniteTransition(label = "tut-glow")
        val pulse by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
            label = "tut-glow-pulse",
        )
        val ringAlpha = 0.35f + 0.60f * pulse
        Modifier
            .scale(1.0f + 0.05f * pulse)
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = Color.White.copy(alpha = ringAlpha),
                    cornerRadius = CornerRadius(cornerRadius.dp.toPx()),
                    style = Stroke(width = 2.5.dp.toPx()),
                )
            }
    }

// ── 반짝임 오버레이 (iOS TutorialSparkles) ────────────────────────────────
// '나의 오늘' CTA 주변에서 반짝이는 작은 별들.
// 반드시 크기에 영향을 주지 않는 modifier(예: Modifier.matchParentSize())로 얹어야 한다.
// (fillMaxSize를 쓰면 감싼 Box가 화면 전체로 부풀어 버튼이 가운데로 밀려 올라간다.)
@Composable
fun TutorialSparkles(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "sparkle")
    Box(modifier) {
        Sparkle(infinite, size = 14, x = -66, y = -26, delayMs = 0)
        Sparkle(infinite, size = 9, x = 64, y = -32, delayMs = 450)
        Sparkle(infinite, size = 11, x = 78, y = 12, delayMs = 900)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Sparkle(
    infinite: androidx.compose.animation.core.InfiniteTransition,
    size: Int,
    x: Int,
    y: Int,
    delayMs: Int,
) {
    val v by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(800, delayMillis = delayMs),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sparkle-$x-$y",
    )
    val scale = 0.4f + 0.75f * v
    val alpha = 0.2f + 0.8f * v
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .offset(x = x.dp, y = y.dp)
            .scale(scale)
            .size(size.dp)
            .glowCircle(Color.White.copy(alpha = alpha), alpha, spread = 2.2f),
    )
}

// ── 완성 축하 카드 (iOS TutorialCelebrateOverlay) ─────────────────────────
// 첫 브이로그 프리뷰 위에 한 번만 뜬다 — 무료 한도(장소 6곳×5초)를 알려주고 마무리.
@Composable
fun TutorialCelebrateOverlay(onFinish: () -> Unit) {
    val view = LocalView.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            // 뒤 화면 클릭 차단 (X 없이 버튼으로만 종료)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
    ) {
        val infinite = rememberInfiniteTransition(label = "celebrate-float")
        val t by infinite.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(1300)),
            label = "celebrate-float-t",
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(horizontal = 36.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.tteoni_thumbsup),
                contentDescription = null,
                modifier = Modifier
                    .size(130.dp)
                    .offset(y = (sin(t.toDouble()) * 6).dp),
            )
            Text(
                stringResource(R.string.tutorial_done_title),
                fontSize = 21.sp, fontWeight = FontWeight.Bold, color = TteDarkGray,
            )
            Text(
                stringResource(R.string.tutorial_done_message),
                fontSize = 14.5.sp, color = TteMediumGray,
                textAlign = TextAlign.Center, lineHeight = 21.sp,
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TteOrange)
                    .clickable {
                        Haptics.success(view)
                        onFinish()
                    },
            ) {
                Text(
                    stringResource(R.string.tutorial_done_button),
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                )
            }
        }
    }
}
