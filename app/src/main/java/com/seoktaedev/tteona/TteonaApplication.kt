package com.seoktaedev.tteona

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.kakao.sdk.common.KakaoSdk
import com.seoktaedev.tteona.core.auth.AuthService

class TteonaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.seoktaedev.tteona.core.i18n.LocaleManager.init(this)
        com.seoktaedev.tteona.core.services.NetworkMonitor.start(this)
        KakaoSdk.init(this, getString(R.string.kakao_native_app_key))
        com.seoktaedev.tteona.core.services.ActiveSessionStore.initialize(this)
        com.seoktaedev.tteona.core.services.ImpromptuSessionStore.initialize(this)
        com.seoktaedev.tteona.core.services.GooglePlacesService.init(this)
        com.seoktaedev.tteona.core.services.PlacesPhotoService.init(this)
        com.seoktaedev.tteona.core.services.ProManager.initClipLength(this)
        /*
         * 지도 SDK 예열.
         *
         * Maps SDK는 프로세스에서 첫 지도를 만들 때 렌더러·셰이더·타일 캐시·네트워크를
         * 통째로 올린다. 예전엔 지도가 첫 탭이라 이 비용이 스플래시 뒤에서 조용히 끝나
         * 있었는데, **촬영 탭이 첫 화면이 되면서** 그 비용이 '발견'을 누른 그 순간으로
         * 밀려 사용자 눈앞에 그대로 노출됐다.
         *
         * 화면 구성은 그대로 두고 비용을 치르는 시점만 예전으로 되돌린다.
         * (iOS는 보이지 않는 GMSMapView를 하나 띄워 버리지만, 안드로이드는
         *  MapsInitializer가 같은 일을 공식 API로 해준다 — 뷰를 만들 필요가 없다)
         */
        runCatching {
            com.google.android.gms.maps.MapsInitializer.initialize(
                this,
                com.google.android.gms.maps.MapsInitializer.Renderer.LATEST,
            ) { }
        }
        // 게스트 기록(약관 동의·브이로그 쿼터)은 Firebase 초기화 실패와 무관하게 읽힌다 —
        // AppRoot가 첫 프레임에서 약관 동의 여부를 묻기 때문에 여기서 먼저 준비한다.
        com.seoktaedev.tteona.core.auth.GuestVlogQuota.initialize(this)
        com.seoktaedev.tteona.core.auth.GuestTermsConsent.initialize(this)
        if (FirebaseApp.initializeApp(this) == null) {
            Log.w("Tteona", "Firebase 미초기화: app/google-services.json을 추가하세요")
            // 초기화하지 않으면 AuthService.isInitializing이 계속 true라 스플래시가 무한 로딩된다.
            AuthService.abortInitialization(this)
            return
        }
        AuthService.initialize(this)
        // PRO 구독 상태 동기화 (iOS tteonaApp의 ProManager.configure 대응)
        // 게스트(익명)에게는 결제 신원을 붙이지 않는다 — 익명 상태로 결제가 이뤄지면
        // 기기를 바꿨을 때 복원되지 않는다. 가입하면 AuthService가 logIn으로 붙여 준다.
        com.seoktaedev.tteona.core.services.ProManager.configure(
            this,
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                ?.takeIf { !it.isAnonymous }?.uid,
        )
    }
}
