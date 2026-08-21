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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.seoktaedev.tteona.core.auth.GuestTermsConsent
import com.seoktaedev.tteona.core.services.NetworkMonitor
import com.seoktaedev.tteona.features.auth.GuestTermsGate
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
    val isGuest by AuthService.isGuest.collectAsState()
    val verificationEmailSent by AuthService.verificationEmailSent.collectAsState()
    val onboardingComplete by AuthService.onboardingComplete.collectAsState()
    val isOnline by NetworkMonitor.isOnline.collectAsState()

    // 진짜 계정인가 — 익명은 제외한다. 익명 인증을 켜면 currentUser가 항상 채워지므로
    // `currentUser != null`만으로는 게이팅이 통째로 열린다.
    val isLoggedIn = currentUser != null && !isGuest

    // 게스트 약관 동의 여부 — SharedPreferences 직접 읽기로는 화면이 갱신되지 않아 상태로 들고 있는다
    var guestTermsAgreed by rememberSaveable { mutableStateOf(GuestTermsConsent.isAgreed) }

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
            // 로그인은 더 이상 앱을 여는 조건이 아니다.
            // 촬영·브이로그 합성·앨범 저장은 전부 기기 안에서 끝나므로 게스트로 완주할 수 있고,
            // 계정은 그룹·코스·발자취처럼 서버가 필요한 순간에만 요구한다.
            verificationEmailSent -> LoginScreen()   // 이메일 인증 대기 중에는 그 화면을 지킨다
            isLoggedIn && !onboardingComplete -> OnboardingScreen()
            // 계정 온보딩은 가입한 사람만 거치는데 그 안에 약관 동의가 들어 있다.
            // 게스트도 촬영·브이로그·앨범 저장까지 서비스를 온전히 쓰므로 동의를 받는다.
            // 넘어갈 수 없는 화면이어야 해서 내비 가이드의 한 단계로 넣지 않았다.
            !isLoggedIn && !guestTermsAgreed -> GuestTermsGate {
                GuestTermsConsent.record()
                guestTermsAgreed = true
            }
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
