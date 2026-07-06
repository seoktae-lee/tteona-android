package com.seoktaedev.tteona.features.pro

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.ui.theme.TteOrange

/**
 * 촬영 한도 도달 팝업 — iOS Features/Pro/VlogLimitPopupView.swift의 이식본.
 * 무료 유저에게는 PRO 업그레이드 CTA, PRO 유저에게는 안내만 표시한다.
 */
@Composable
fun VlogLimitPopup(
    isPro: Boolean,
    onUpgrade: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 30.dp)
                .widthIn(max = 330.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1F1C29), Color(0xFF12101A))))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(26.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* 카드 내부 탭은 닫히지 않음 */ }
                .padding(horizontal = 20.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(top = 28.dp)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(TteOrange.copy(alpha = 0.35f), TteOrange.copy(alpha = 0.1f)))
                    ),
            ) {
                Icon(
                    if (isPro) Icons.Filled.Verified else Icons.Filled.Timer,
                    contentDescription = null,
                    tint = TteOrange,
                    modifier = Modifier.size(30.dp),
                )
            }

            Text(
                "촬영 한도에 도달했어요",
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                if (isPro) "브이로그 영상은 최대 5분까지 담을 수 있어요.\n지금까지 담은 순간들로 멋진 영상을 만들어드릴게요."
                else "무료로는 장소당 5초, 총 30초까지 담을 수 있어요.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
            )

            if (!isPro) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, TteOrange.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Image(
                        painterResource(R.drawable.tteona_pro_logo),
                        contentDescription = "tteona PRO",
                        modifier = Modifier.height(20.dp),
                    )
                    Text("장소당 제한 없이 최대 5분까지 자유롭게", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(TteOrange)
                        .clickable(onClick = onUpgrade),
                ) {
                    Text("tteona PRO 알아보기", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(top = if (isPro) 20.dp else 6.dp, bottom = 14.dp)
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable(onClick = onDismiss),
            ) {
                Text("확인", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.65f))
            }
        }
    }
}
