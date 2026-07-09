package com.seoktaedev.tteona.core.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.i18n.LocaleManager
import com.seoktaedev.tteona.core.model.AppUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * iOS Core/Services/AuthService.swift의 Kotlin 이식본.
 * 앱 전역 싱글턴 — Application.onCreate에서 initialize() 호출.
 */
object AuthService {
    private val auth get() = Firebase.auth
    private val db get() = Firebase.firestore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser: StateFlow<AppUser?> = _currentUser

    private val _isInitializing = MutableStateFlow(true)
    val isInitializing: StateFlow<Boolean> = _isInitializing

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _verificationEmailSent = MutableStateFlow(false)
    val verificationEmailSent: StateFlow<Boolean> = _verificationEmailSent

    private val _onboardingComplete = MutableStateFlow(false)
    val onboardingComplete: StateFlow<Boolean> = _onboardingComplete

    val isLoggedIn: Boolean get() = _currentUser.value != null

    // 현지화 에러 메시지용 앱 컨텍스트 — initialize/abortInitialization에서 주입
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
        // 앱 재설치 시 남은 Firebase 토큰 제거 (iOS의 Keychain 잔존 토큰 처리와 동일)
        val prefs = context.getSharedPreferences("tteona", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("app_installed", false)) {
            auth.signOut()
            prefs.edit().putBoolean("app_installed", true).apply()
        }

        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            scope.launch {
                if (user != null) {
                    // Android providerData에는 집계용 "firebase" 항목이 포함되므로 제외.
                    // 카카오(커스텀 토큰) 계정은 실제 provider가 없어 이메일 인증 대상이 아님
                    // (iOS의 allSatisfy 함정 처리와 동일한 의도).
                    val providerIds = user.providerData.map { it.providerId }.filter { it != "firebase" }
                    val isEmailPassword = providerIds.contains("password") && providerIds.all { it == "password" }
                    val needsVerification = isEmailPassword && !user.isEmailVerified
                    if (needsVerification) {
                        // 미인증 이메일 계정 → currentUser 설정하지 않음
                        _isInitializing.value = false
                        return@launch
                    }
                    _verificationEmailSent.value = false
                    _currentUser.value = AppUser(uid = user.uid, email = user.email ?: "")
                    refreshOnboardingStatus(user.uid)
                    // FCM 토큰 등록 (iOS RootView의 saveFCMToken 대응)
                    com.seoktaedev.tteona.core.services.TteonaMessagingService.registerCurrentToken(user.uid)
                    // PRO 구독 계정 동기화 (iOS ProManager.logIn 대응)
                    com.seoktaedev.tteona.core.services.ProManager.logIn(user.uid)
                } else {
                    _currentUser.value = null
                    _onboardingComplete.value = false
                    com.seoktaedev.tteona.core.services.ProManager.logOut()
                }
                _isInitializing.value = false
            }
        }
    }

    // MARK: - 이메일 로그인
    suspend fun signIn(email: String, password: String) {
        _isLoading.value = true
        _errorMessage.value = null
        try {
            if (!isValidEmail(email)) { _errorMessage.value = LocaleManager.string(appContext, R.string.auth_error_invalidEmail); return }
            if (password.length < 6) { _errorMessage.value = LocaleManager.string(appContext, R.string.auth_error_shortPassword); return }

            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: return
            if (!user.isEmailVerified) {
                _verificationEmailSent.value = true
                _errorMessage.value = null
            } else {
                _verificationEmailSent.value = false
                _currentUser.value = AppUser(uid = user.uid, email = user.email ?: "")
                refreshOnboardingStatus(user.uid)
            }
        } catch (e: Exception) {
            _errorMessage.value = firebaseErrorMessage(e)
        } finally {
            _isLoading.value = false
        }
    }

    // MARK: - 이메일 회원가입 (인증 메일 발송)
    suspend fun signUp(email: String, password: String) {
        _isLoading.value = true
        _errorMessage.value = null
        try {
            if (!isValidEmail(email)) { _errorMessage.value = LocaleManager.string(appContext, R.string.auth_error_invalidEmail); return }
            if (password.length < 6) { _errorMessage.value = LocaleManager.string(appContext, R.string.auth_error_shortPassword); return }

            val result = auth.createUserWithEmailAndPassword(email, password).await()
            result.user?.sendEmailVerification()?.await()
            _verificationEmailSent.value = true
        } catch (e: FirebaseAuthException) {
            if (e.errorCode == "ERROR_EMAIL_ALREADY_IN_USE") {
                // 미인증 계정으로 재가입 시도 → 로그인해서 인증 여부 확인 (iOS와 동일)
                try {
                    val result = auth.signInWithEmailAndPassword(email, password).await()
                    val user = result.user
                    if (user != null && !user.isEmailVerified) {
                        runCatching { user.sendEmailVerification().await() }
                        _verificationEmailSent.value = true
                        _errorMessage.value = null
                    } else {
                        auth.signOut()
                        _errorMessage.value = LocaleManager.string(appContext, R.string.auth_error_emailInUse)
                    }
                } catch (_: Exception) {
                    // 비밀번호가 달라 로그인 실패한 경우
                    _errorMessage.value =
                        LocaleManager.string(appContext, R.string.auth_error_signupInProgress)
                }
            } else {
                _errorMessage.value = firebaseErrorMessage(e)
            }
        } catch (e: Exception) {
            _errorMessage.value = firebaseErrorMessage(e)
        } finally {
            _isLoading.value = false
        }
    }

    // MARK: - 인증 완료 확인 후 로그인 (인증 메일 화면의 "인증 완료 후 시작하기")
    suspend fun verifyAndLogin(email: String, password: String) {
        _isLoading.value = true
        try {
            var user = auth.currentUser
            if (user == null) {
                // 앱 재실행 등으로 세션이 없으면 입력된 계정으로 로그인 후 확인
                if (email.isEmpty() || password.isEmpty()) {
                    _errorMessage.value = LocaleManager.string(appContext, R.string.auth_reenterForVerify)
                    return
                }
                user = auth.signInWithEmailAndPassword(email, password).await().user
            }
            user ?: return
            runCatching { user.reload().await() }
            val refreshed = auth.currentUser
            if (refreshed != null && refreshed.isEmailVerified) {
                _errorMessage.value = null
                _currentUser.value = AppUser(uid = refreshed.uid, email = refreshed.email ?: "")
                refreshOnboardingStatus(refreshed.uid)
                _verificationEmailSent.value = false
            } else {
                if (auth.currentUser?.isEmailVerified == false && email.isNotEmpty()) auth.signOut()
                _errorMessage.value = LocaleManager.string(appContext, R.string.auth_notVerifiedYet)
            }
        } catch (e: Exception) {
            _errorMessage.value = LocaleManager.string(appContext, R.string.auth_signInFailed)
        } finally {
            _isLoading.value = false
        }
    }

    // MARK: - 인증 메일 재전송
    suspend fun resendVerificationEmail(email: String, password: String): Boolean {
        try {
            val user = auth.currentUser
            if (user != null) {
                user.sendEmailVerification().await()
                _errorMessage.value = null
                return true
            }
            if (email.isNotEmpty() && password.isNotEmpty()) {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                result.user?.sendEmailVerification()?.await()
                auth.signOut()
                _errorMessage.value = null
                return true
            }
            _errorMessage.value = LocaleManager.string(appContext, R.string.auth_reenterForResend)
            return false
        } catch (e: Exception) {
            _errorMessage.value = LocaleManager.string(appContext, R.string.auth_resendFailed)
            return false
        }
    }

    // MARK: - 비밀번호 재설정
    suspend fun sendPasswordReset(email: String): Boolean {
        if (!isValidEmail(email)) {
            _errorMessage.value = LocaleManager.string(appContext, R.string.auth_error_invalidEmail)
            return false
        }
        return try {
            auth.sendPasswordResetEmail(email).await()
            true
        } catch (e: Exception) {
            _errorMessage.value = firebaseErrorMessage(e)
            false
        }
    }

    // MARK: - Google 로그인 (Credential Manager)
    suspend fun signInWithGoogle(context: Context) {
        _isLoading.value = true
        _errorMessage.value = null
        try {
            val credentialManager = CredentialManager.create(context)
            val serverClientId = context.getString(R.string.default_web_client_id)
            val result = try {
                // 1차: 기기에 로그인된 구글 계정 선택 UI
                credentialManager.getCredential(
                    context,
                    GetCredentialRequest.Builder()
                        .addCredentialOption(
                            GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(serverClientId)
                                .build()
                        )
                        .build()
                )
            } catch (_: NoCredentialException) {
                // 기기에 구글 계정이 없음 → 계정 추가까지 가능한 전체 로그인 UI로 폴백
                credentialManager.getCredential(
                    context,
                    GetCredentialRequest.Builder()
                        .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
                        .build()
                )
            }
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                val user = authResult.user ?: return
                _verificationEmailSent.value = false
                _currentUser.value = AppUser(uid = user.uid, email = user.email ?: "")
                refreshOnboardingStatus(user.uid)
            } else {
                _errorMessage.value = LocaleManager.string(appContext, R.string.auth_error_googleToken)
            }
        } catch (_: GetCredentialCancellationException) {
            // 사용자가 로그인 창을 닫음 — 에러 표시하지 않음
        } catch (e: NoCredentialException) {
            Log.w("Auth", "Google 로그인 실패 — 기기에 계정 없음", e)
            _errorMessage.value = LocaleManager.string(appContext, R.string.auth_error_googleNoAccount)
        } catch (e: Exception) {
            Log.w("Auth", "Google 로그인 실패", e)
            _errorMessage.value = LocaleManager.string(appContext, R.string.auth_error_googleFailedAndroid, e.javaClass.simpleName)
        } finally {
            _isLoading.value = false
        }
    }

    // MARK: - 카카오 로그인 (iOS runKakaoSignIn과 동일 플로우)
    // Kakao SDK 로그인 → createKakaoCustomToken(Functions) → Firebase 커스텀 토큰 로그인
    suspend fun signInWithKakao(context: Context) {
        _isLoading.value = true
        _errorMessage.value = null
        try {
            val oauthToken = kakaoOAuthToken(context) ?: return // null = 사용자가 취소

            val result = com.google.firebase.functions.FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("createKakaoCustomToken")
                .call(mapOf("kakaoAccessToken" to oauthToken.accessToken))
                .await()
            val customToken = (result.data as? Map<*, *>)?.get("customToken") as? String
            if (customToken.isNullOrEmpty()) {
                _errorMessage.value = LocaleManager.string(appContext, R.string.auth_error_invalidResponse)
                return
            }

            val authResult = auth.signInWithCustomToken(customToken).await()
            val user = authResult.user ?: return
            _verificationEmailSent.value = false
            _currentUser.value = AppUser(uid = user.uid, email = user.email ?: "")
            refreshOnboardingStatus(user.uid)
        } catch (e: Exception) {
            Log.w("Auth", "카카오 로그인 실패", e)
            _errorMessage.value = LocaleManager.string(appContext, R.string.auth_error_kakaoFailedSimple)
        } finally {
            _isLoading.value = false
        }
    }

    // 카카오톡 앱 로그인 우선, 미설치/실패 시 카카오계정(웹) 로그인 폴백 — 공식 권장 패턴.
    // 사용자가 직접 취소한 경우 null을 반환해 에러 표시 없이 조용히 끝낸다 (iOS와 동일).
    private suspend fun kakaoOAuthToken(context: Context): com.kakao.sdk.auth.model.OAuthToken? =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val client = com.kakao.sdk.user.UserApiClient.instance
            val accountCallback: (com.kakao.sdk.auth.model.OAuthToken?, Throwable?) -> Unit = { token, error ->
                if (cont.isActive) {
                    when {
                        error is com.kakao.sdk.common.model.ClientError &&
                            error.reason == com.kakao.sdk.common.model.ClientErrorCause.Cancelled ->
                            cont.resume(null) {}
                        error != null -> cont.resumeWith(Result.failure(error))
                        token != null -> cont.resume(token) {}
                        else -> cont.resume(null) {}
                    }
                }
            }
            if (client.isKakaoTalkLoginAvailable(context)) {
                client.loginWithKakaoTalk(context) { token, error ->
                    when {
                        error is com.kakao.sdk.common.model.ClientError &&
                            error.reason == com.kakao.sdk.common.model.ClientErrorCause.Cancelled ->
                            if (cont.isActive) cont.resume(null) {}
                        // 카카오톡 앱 로그인 실패(계정 미연동 등) → 카카오계정 로그인 폴백
                        error != null -> client.loginWithKakaoAccount(context, callback = accountCallback)
                        token != null -> if (cont.isActive) cont.resume(token) {}
                        else -> if (cont.isActive) cont.resume(null) {}
                    }
                }
            } else {
                client.loginWithKakaoAccount(context, callback = accountCallback)
            }
        }

    // MARK: - 온보딩 완료 (users 문서 생성 — iOS OnboardingView의 저장 로직에 대응)
    suspend fun completeOnboarding(nickname: String, preferredTag: String? = null) {
        val user = auth.currentUser ?: return
        _isLoading.value = true
        try {
            val data = buildMap {
                put("uid", user.uid)
                put("email", user.email ?: "")
                put("nickname", nickname)
                put("createdAt", Timestamp.now())
                put("isVerified", false)
                // 온보딩 여행 취향 (건너뛰면 미저장) — 추천 개인화 시드
                preferredTag?.let { put("preferredTag", it) }
            }
            db.collection("users").document(user.uid).set(data).await()
            _currentUser.value = _currentUser.value?.copy(nickname = nickname)
            _onboardingComplete.value = true
        } catch (e: Exception) {
            _errorMessage.value = LocaleManager.string(appContext, R.string.auth_error_profileSaveFailed)
        } finally {
            _isLoading.value = false
        }
    }

    // MARK: - 로그아웃
    fun signOut() {
        // iOS RootView의 onChange(isLoggedIn) → clearUserData 대응
        com.seoktaedev.tteona.core.services.CourseService.clearUserData()
        com.seoktaedev.tteona.core.services.UserService.clear()
        com.seoktaedev.tteona.core.services.RoomService.clear()
        com.seoktaedev.tteona.core.services.FootprintService.clear()
        auth.signOut()
    }

    // MARK: - 회원탈퇴 (iOS deleteAccount 대응)
    // 서버(Cloud Function)가 코스·그룹·계정을 일괄 삭제하고, 클라이언트는 세션 정리만 담당.
    suspend fun deleteAccount(context: Context): Boolean {
        _isLoading.value = true
        return try {
            com.google.firebase.functions.FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("deleteMyAccount")
                .call()
                .await()

            com.seoktaedev.tteona.core.services.CourseService.clearUserData()
            com.seoktaedev.tteona.core.services.UserService.clear()
            com.seoktaedev.tteona.core.services.RoomService.clear()
            // 구글 로그인 세션 완전 해제 (iOS GIDSignIn.disconnect 대응)
            runCatching {
                CredentialManager.create(context)
                    .clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
            }
            auth.signOut()
            true
        } catch (e: Exception) {
            Log.w("Auth", "회원탈퇴 실패", e)
            _errorMessage.value = LocaleManager.string(appContext, R.string.settings_deleteFailed_message)
            false
        } finally {
            _isLoading.value = false
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Firebase 초기화 실패(google-services.json 누락 등) 시 호출.
     * 스플래시(isInitializing) 무한 로딩을 막고 로그인 화면으로 빠지게 한다.
     */
    fun abortInitialization(context: Context) {
        appContext = context.applicationContext
        _isInitializing.value = false
        _errorMessage.value = LocaleManager.string(appContext, R.string.auth_error_initFailed)
    }

    // MARK: - Helpers
    private suspend fun refreshOnboardingStatus(uid: String) {
        // 기존 가입 유저는 Firestore users 문서가 이미 존재하므로 온보딩을 다시 하지 않음
        val doc = runCatching { db.collection("users").document(uid).get().await() }.getOrNull()
        _onboardingComplete.value = doc?.exists() == true
    }

    private fun isValidEmail(email: String): Boolean =
        Regex("^[A-Z0-9a-z._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$").matches(email)

    private fun firebaseErrorMessage(e: Exception): String {
        if (e is FirebaseNetworkException) return LocaleManager.string(appContext, R.string.auth_error_network)
        val code = (e as? FirebaseAuthException)?.errorCode
        return when (code) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> LocaleManager.string(appContext, R.string.auth_error_emailInUse)
            "ERROR_INVALID_EMAIL" -> LocaleManager.string(appContext, R.string.auth_error_invalidEmail)
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> LocaleManager.string(appContext, R.string.auth_error_wrongPassword)
            "ERROR_USER_NOT_FOUND" -> LocaleManager.string(appContext, R.string.auth_error_userNotFound)
            "ERROR_WEAK_PASSWORD" -> LocaleManager.string(appContext, R.string.auth_error_shortPassword)
            else -> LocaleManager.string(appContext, R.string.auth_error_generic)
        }
    }
}
