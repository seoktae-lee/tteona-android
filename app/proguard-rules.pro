# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.seoktaedev.tteona.**$$serializer { *; }
-keepclassmembers class com.seoktaedev.tteona.** {
    *** Companion;
}
-keepclasseswithmembers class com.seoktaedev.tteona.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# @Serializable 데이터 클래스의 필드가 R8에 의해 이름 변경/제거되면 JSON 직렬화가 깨진다.
-keepclassmembers @kotlinx.serialization.Serializable class com.seoktaedev.tteona.** {
    <fields>;
}

# ── Retrofit / OkHttp (R8 full-mode 대응) ──────────────────────────────
# Retrofit이 인터페이스 메서드의 제네릭 반환타입·애노테이션을 리플렉션으로 읽으므로 보존한다.
-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
# API 인터페이스 자체 (suspend 반환타입 시그니처 포함)
-keep,allowobfuscation interface com.seoktaedev.tteona.core.network.TteonaApi { *; }
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
# Retrofit 2.x 공식 권장 규칙
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
# kotlin suspend 함수의 Continuation 파라미터 시그니처 보존
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# OkHttp / Okio — 플랫폼 옵셔널 참조 경고 억제
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Kakao SDK (R8 full-mode 대응) ──────────────────────────────────────
# Kakao SDK는 응답/에러 모델의 enum 상수와 필드를 "원래 이름"으로 리플렉션 조회한다
# (예: AccessTokenInterceptor가 ClientError 생성 시 ClientErrorCause.TokenNotFound 를
#  Class.getField("TokenNotFound") 로 찾음). R8이 필드/상수 이름을 난독화하면
#  java.lang.NoSuchFieldException 으로 앱이 실행 직후 크래시한다 → 모델 필드명 보존 필수.
# 카카오 공식 권장 규칙: https://developers.kakao.com/docs/latest/ko/android/getting-started
-keep class com.kakao.sdk.**.model.* { <fields>; }
-keep class * extends com.google.gson.TypeAdapter
-dontwarn com.kakao.sdk.**
