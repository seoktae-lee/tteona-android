package com.seoktaedev.tteona.features.root

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.services.NetworkMonitor
import com.seoktaedev.tteona.features.auth.LoginScreen
import com.seoktaedev.tteona.features.main.MainTabScreen
import com.seoktaedev.tteona.features.onboarding.OnboardingScreen
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteonaSplashBackground

/**
 * iOS Features/Auth/RootView.swift의 이식본.
 * 인증 상태에 따라 스플래시 / 로그인 / 온보딩 / 메인을 분기하고, 오프라인이면 상단 배너를 얹는다.
 */
@Composable
fun AppRoot(
    previewOnboarding: Boolean = false,
    previewOnboardingStep: Int = 0,
    previewProfileTab: Boolean = false,
    previewFootprintDemo: Boolean = false,
) {
    val isInitializing by AuthService.isInitializing.collectAsState()
    val currentUser by AuthService.currentUser.collectAsState()
    val verificationEmailSent by AuthService.verificationEmailSent.collectAsState()
    val onboardingComplete by AuthService.onboardingComplete.collectAsState()
    val isOnline by NetworkMonitor.isOnline.collectAsState()

    // 시각 검증용 — 인증 없이 온보딩을 바로 표시 (DEBUG 빌드에서만 MainActivity가 활성화)
    if (previewOnboarding) {
        OnboardingScreen(initialStep = previewOnboardingStep)
        return
    }

    // 시각 검증용 — 인증 없이 메인 탭 + 프로필 탭 진입
    if (previewProfileTab) {
        MainTabScreen(initialTab = 3, previewFootprintDemo = previewFootprintDemo)
        return
    }

    Box(Modifier.fillMaxSize()) {
        when {
            isInitializing -> {
                // 앱 진입 스플래시 — 주황 일렁임 배경 위 워드마크 로고 (iOS RootView.SplashView)
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    TteonaSplashBackground()
                    Image(
                        painter = painterResource(R.drawable.tteona_logo),
                        contentDescription = "tteona",
                        modifier = Modifier.height(46.dp),
                    )
                }
            }
            currentUser == null || verificationEmailSent -> LoginScreen()
            !onboardingComplete -> OnboardingScreen()
            else -> MainTabScreen()
        }

        // 오프라인 배너 (상단) — 스플래시 중엔 숨긴다 (iOS RootView.OfflineBanner)
        if (!isOnline && !isInitializing) {
            OfflineBanner(modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

// MARK: - 오프라인 배너 (상단, iOS OfflineBanner)
@Composable
private fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(TteDarkGray.copy(alpha = 0.92f))
            .statusBarsPadding()
            .padding(vertical = 8.dp),
    ) {
        Icon(
            Icons.Filled.WifiOff,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(15.dp),
        )
        Text(
            stringResource(R.string.network_offline),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}
