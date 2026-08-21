package com.seoktaedev.tteona.features.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.seoktaedev.tteona.ui.theme.TteonaSplashBackground
import com.seoktaedev.tteona.ui.theme.glowCircle
import kotlinx.coroutines.delay

/**
 * 게스트가 서버 기능(그룹·코스·발자취)에 닿았을 때 보여주는 안내.
 * iOS Features/Auth/GuestGateView.swift의 이식본.
 *
 * 막는 게 목적이 아니라 **왜 계정이 필요한지**를 말하고 그 자리에서 가입하게 하는 화면이다.
 * 촬영과 첫 브이로그는 게스트로도 되므로, 여기까지 온 유저는 이미 결과물을 손에 쥔 상태다.
 *
 * 화면 구성 원칙 두 가지:
 * - **떠니를 세운다.** 아이콘을 원형 배경에 올리면 어느 앱에나 어울리는 화면이 된다.
 *   우리 캐릭터가 탭마다 다른 표정으로 서 있어야 '떠나의 화면'이 된다.
 * - **가운데 정렬을 깬다.** 위아래 Spacer로 전부 가운데 띄우는 건 만들다 만 화면처럼 보인다.
 *   설명은 위에, 버튼은 엄지가 닿는 아래에 고정한다.
 *
 * @param mascotRes 탭마다 다른 떠니 포즈
 * @param onSignUp  가입 화면으로 보낸다
 * @param onOpenSettings 우상단 설정 통로. 프로필 탭 게이트만 넘긴다 —
 *   게스트도 언어·약관·개인정보처리방침에는 닿을 수 있어야 한다.
 */
@Composable
fun GuestGateScreen(
    mascotRes: Int,
    title: String,
    message: String,
    onSignUp: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val appeared = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(50)
        appeared.animateTo(1f, tween(550))
    }

    Box(modifier.fillMaxSize()) {
        // 로그인 화면과 같은 배경 — 버튼을 누르면 그 화면으로 넘어가므로
        // 배경이 이어져야 전환이 끊기지 않는다.
        TteonaSplashBackground()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            Spacer(Modifier.weight(0.5f))

            // 캐릭터가 허공에 뜨지 않도록 바닥에 옅은 그림자를 깐다
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(132.dp, 18.dp)
                        .padding(top = 0.dp)
                        .glowCircle(TteOrange, 0.10f, spread = 1.2f)
                        .align(Alignment.BottomCenter),
                )
                Image(
                    painter = painterResource(mascotRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(168.dp)
                        .alpha(appeared.value),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 44.dp)
                    .padding(top = 26.dp),
            ) {
                Text(
                    title,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = TteDarkGray,
                    textAlign = TextAlign.Center,
                )
                Text(
                    message,
                    fontSize = 14.5.sp,
                    color = TteMediumGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
            }

            Spacer(Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
            ) {
                Button(
                    onClick = {
                        Haptics.light(view)
                        onSignUp()
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TteOrange,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    Text(
                        stringResource(R.string.guest_signUp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // 촬영은 계정 없이도 된다는 걸 분명히 해 둔다 — 막힌 느낌을 줄인다
                Text(
                    stringResource(R.string.guest_captureStillFree),
                    fontSize = 12.5.sp,
                    color = TteMediumGray.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.height(28.dp))
        }

        if (onOpenSettings != null) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(R.string.settings_title),
                tint = TteDarkGray.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 8.dp, top = 4.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenSettings)
                    .padding(10.dp)
                    .size(22.dp),
            )
        }
    }
}
