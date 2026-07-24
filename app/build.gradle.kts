import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Firebase 콘솔에서 Android 앱(com.seoktaedev.tteona) 등록 후 받은
// google-services.json을 app/ 에 넣으면 자동으로 적용된다.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// release 서명 keystore — 경로/비밀번호는 git 제외 대상인 local.properties에서만 읽는다
// (코드에 비밀번호를 박지 않기 위함). 미설정이면 release 빌드는 서명 없이(unsigned) 진행된다.
val releaseKeystore = localProps.getProperty("RELEASE_STORE_FILE")?.let { rootProject.file(it) }

android {
    namespace = "com.seoktaedev.tteona"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.seoktaedev.tteona"
        minSdk = 26
        targetSdk = 36
        versionCode = 22
        versionName = "1.3"

        // local.properties에 MAPS_API_KEY=... 형태로 저장 (VCS 제외 대상)
        manifestPlaceholders["MAPS_API_KEY"] = localProps.getProperty("MAPS_API_KEY") ?: ""

        // Google Places API (New) 직접 호출 키 (iOS GOOGLE_PLACES_API_KEY 대응).
        // 별도 키 미지정 시 Maps 키 재사용 — 같은 키에 Places API (New)가 활성화되어 있어야 한다.
        buildConfigField(
            "String", "GOOGLE_PLACES_API_KEY",
            "\"${localProps.getProperty("GOOGLE_PLACES_API_KEY") ?: localProps.getProperty("MAPS_API_KEY") ?: ""}\"",
        )

        // RevenueCat Google Play 공개 SDK 키(goog_...) — 미설정 시 무료 모드 (iOS REVENUECAT_API_KEY 대응)
        buildConfigField(
            "String", "REVENUECAT_API_KEY",
            "\"${localProps.getProperty("REVENUECAT_API_KEY") ?: ""}\"",
        )

        // 카카오 네이티브 앱 키 (클라이언트 공개 키 — Kakao Developers > 플랫폼 키 > tteona-ver.android)
        val kakaoNativeAppKey = "e6df456a5ce81d4a4bfb77d290a127d2"
        manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = kakaoNativeAppKey
        resValue("string", "kakao_native_app_key", kakaoNativeAppKey)
    }

    signingConfigs {
        // local.properties에 RELEASE_STORE_FILE 등이 있을 때만 release 서명 구성을 만든다.
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // keystore가 설정돼 있으면 release를 서명한다 (없으면 unsigned로 빌드).
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.functions)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Google 로그인 (Credential Manager)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    // 카카오 로그인
    implementation(libs.kakao.user)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.maps.compose)

    // 장소 촬영 (CameraX — iOS AVFoundation CameraService 대응)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.video)
    implementation(libs.camerax.view)
    implementation(libs.guava) // CameraX ListenableFuture 반환 타입 참조용

    // PRO 구독 (RevenueCat — iOS와 동일한 entitlement "pro" 공유)
    implementation(libs.revenuecat)
}
