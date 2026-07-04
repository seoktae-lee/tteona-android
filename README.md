# 떠나 (tteona) — Android

떠나 iOS 앱의 안드로이드 버전. 백엔드(tteona.kr server.js, Firebase `tteona-dev`)는 iOS와 공유한다.

## 스택

- Kotlin + Jetpack Compose (Material 3)
- minSdk 26 / targetSdk 36
- Firebase (Auth, Firestore, FCM) — iOS와 동일한 `tteona-dev` 프로젝트
- Retrofit + kotlinx.serialization (tteona.kr REST API)
- Socket.IO client (채팅·위치 공유)
- Google Maps Compose

## 빌드

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

시스템 Java(25)는 Gradle 8.13과 호환되지 않으므로 Android Studio 내장 JBR(21)을 사용한다.
Android Studio에서 열면 자동으로 내장 JDK를 쓴다.

## 최초 설정 (수동 작업 필요)

1. **Firebase**: [Firebase 콘솔](https://console.firebase.google.com/project/tteona-dev) → 프로젝트 설정 → Android 앱 추가
   (패키지명 `com.seoktaedev.tteona`) → `google-services.json` 다운로드 → `app/`에 넣기.
   파일이 있으면 빌드 시 자동 적용된다 (없어도 빌드는 됨).
2. **Google Maps**: Google Cloud 콘솔에서 **Maps SDK for Android** 활성화 + Android용 API 키 발급 →
   `local.properties`의 `MAPS_API_KEY=`에 입력.
3. **카카오 로그인**: Kakao Developers에 Android 플랫폼 등록 (키 해시 필요) — 로그인 구현 시점에 진행.

## 구조 (iOS 프로젝트와 대응)

| Android | iOS |
|---|---|
| `core/model/` | `Core/Models/` |
| `core/network/` | `Core/Services/` (URLSession 부분) |
| `features/home/` | `Features/Main/` |
| `features/explore/` | `Features/Explore/` |
| `features/auth/` | `Features/Auth/` |
| `features/settings/` | `Features/Settings/` |
| `ui/theme/` | `Core/Extensions/Color+Theme.swift` |

## 로드맵

1. ✅ 프로젝트 스켈레톤 (3탭 + 네비게이션 + 테마)
2. Firebase Auth 로그인 (카카오 커스텀 토큰 / 구글)
3. 탐색 그리드 — Firestore 코스 조회
4. 홈 지도 — 코스 핀 + 코스 상세
5. 세션 진행 (Foreground Service + 상시 알림 — iOS Live Activity 대응)
6. 채팅·위치 공유 (Socket.IO)
7. FCM 푸시, 딥링크, 위젯(Glance)
