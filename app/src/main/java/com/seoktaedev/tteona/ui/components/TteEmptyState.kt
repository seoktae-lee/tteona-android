package com.seoktaedev.tteona.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import kotlin.math.sin

/**
 * 마스코트(뜨오니)가 등장하는 공용 빈 화면 뷰 — iOS TteEmptyState의 이식본.
 * 회색 머티리얼 아이콘 대신 브랜드 캐릭터를 노출해 빈 화면에서도 앱의 개성을 유지한다.
 * - 사용처: 탐색 탭 빈 목록, 그룹 탭 빈 목록, 홈 검색 결과 없음 등
 */
@Composable
fun TteEmptyState(
    imageRes: Int = R.drawable.tteoni_front,
    title: String,
    subtitle: String? = null,
    imageSize: Dp = 130.dp,
    modifier: Modifier = Modifier,
) {
    // 둥실 애니메이션 (iOS bounce offset과 동일한 느낌)
    val infinite = rememberInfiniteTransition(label = "empty-float")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "empty-float-t",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 32.dp),
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            modifier = Modifier
                .size(imageSize)
                .graphicsLayer { translationY = sin(t.toDouble()).toFloat() * 6f * density },
        )
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = TteDarkGray,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                fontSize = 14.sp,
                color = TteMediumGray,
                textAlign = TextAlign.Center,
            )
        }
    }
}
