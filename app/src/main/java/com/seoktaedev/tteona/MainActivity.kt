package com.seoktaedev.tteona

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.seoktaedev.tteona.core.services.AppNotificationManager
import com.seoktaedev.tteona.core.services.DeepLinkHandler
import com.seoktaedev.tteona.features.root.AppRoot
import com.seoktaedev.tteona.ui.theme.TteonaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            TteonaTheme {
                AppRoot()
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
