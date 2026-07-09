package com.seoktaedev.tteona

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.seoktaedev.tteona.core.i18n.LocaleManager
import com.seoktaedev.tteona.core.services.AppNotificationManager
import com.seoktaedev.tteona.core.services.DeepLinkHandler
import com.seoktaedev.tteona.features.root.AppRoot
import com.seoktaedev.tteona.ui.theme.TteonaTheme

class MainActivity : ComponentActivity() {
    // 선택 언어를 baseContext에 적용 — 앱 전역 리소스가 해당 언어로 로드된다
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        // 시각 검증용 (iOS -previewOnboarding 런치 아규먼트 대응, DEBUG 전용):
        //   adb shell am start -n com.seoktaedev.tteona/.MainActivity --ez previewOnboarding true \
        //     [--ei previewOnboardingStep N]
        val previewOnboarding = BuildConfig.DEBUG &&
            intent?.getBooleanExtra("previewOnboarding", false) == true
        val previewStep = if (previewOnboarding) intent?.getIntExtra("previewOnboardingStep", 0) ?: 0 else 0
        // 시각 검증용: 프로필 탭(발자취 지도) 바로 진입 + 가짜 발자취
        //   adb shell am start -n com.seoktaedev.tteona/.MainActivity --ez previewProfileTab true [--ez previewFootprintDemo true]
        val previewProfileTab = BuildConfig.DEBUG &&
            intent?.getBooleanExtra("previewProfileTab", false) == true
        val previewFootprintDemo = BuildConfig.DEBUG &&
            intent?.getBooleanExtra("previewFootprintDemo", false) == true
        setContent {
            TteonaTheme {
                AppRoot(
                    previewOnboarding = previewOnboarding,
                    previewOnboardingStep = previewStep,
                    previewProfileTab = previewProfileTab,
                    previewFootprintDemo = previewFootprintDemo,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    // 딥링크(tteona.kr 링크) + 알림 탭(FCM data extras) 라우팅
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        DeepLinkHandler.handle(intent.data)
        AppNotificationManager.handleNotificationExtras(intent.extras)
    }
}
