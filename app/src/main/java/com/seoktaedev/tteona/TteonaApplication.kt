package com.seoktaedev.tteona

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.kakao.sdk.common.KakaoSdk
import com.seoktaedev.tteona.core.auth.AuthService

class TteonaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KakaoSdk.init(this, getString(R.string.kakao_native_app_key))
        com.seoktaedev.tteona.core.services.ActiveSessionStore.initialize(this)
        com.seoktaedev.tteona.core.services.ImpromptuSessionStore.initialize(this)
        if (FirebaseApp.initializeApp(this) == null) {
            Log.w("Tteona", "Firebase 미초기화: app/google-services.json을 추가하세요")
            return
        }
        AuthService.initialize(this)
        // PRO 구독 상태 동기화 (iOS tteonaApp의 ProManager.configure 대응)
        com.seoktaedev.tteona.core.services.ProManager.configure(
            this,
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid,
        )
    }
}
