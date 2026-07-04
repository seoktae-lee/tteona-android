package com.seoktaedev.tteona.features.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.features.auth.LoginScreen
import com.seoktaedev.tteona.features.main.MainTabScreen
import com.seoktaedev.tteona.features.onboarding.OnboardingScreen
import com.seoktaedev.tteona.ui.theme.TteOrange

/**
 * iOS Features/Auth/RootView.swift의 이식본.
 * 인증 상태에 따라 스플래시 / 로그인 / 온보딩 / 메인을 분기한다.
 */
@Composable
fun AppRoot() {
    val isInitializing by AuthService.isInitializing.collectAsState()
    val currentUser by AuthService.currentUser.collectAsState()
    val verificationEmailSent by AuthService.verificationEmailSent.collectAsState()
    val onboardingComplete by AuthService.onboardingComplete.collectAsState()

    when {
        isInitializing -> {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("tteona", fontSize = 52.sp, fontWeight = FontWeight.Bold, color = TteOrange)
                }
            }
        }
        currentUser == null || verificationEmailSent -> LoginScreen()
        !onboardingComplete -> OnboardingScreen()
        else -> MainTabScreen()
    }
}
