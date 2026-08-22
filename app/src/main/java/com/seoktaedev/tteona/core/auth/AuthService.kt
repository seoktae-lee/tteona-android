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
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
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

    /** 익명(게스트)으로 앱을 쓰는 중 — 서버에 쓸 신원은 있지만 계정은 없다 */
    private val _isGuest = MutableStateFlow(false)
    val isGuest: StateFlow<Boolean> = _isGuest

    /**
     * **로컬 저장 경로에 쓰는 신원 uid.** 화면 게이팅과는 다른 질문에 답한다.
     *
     * `isLoggedIn`은 "진짜 계정인가"를 묻고, 이 값은 "지금 이 기기에서 찍고 있는 사람이
     * 누구인가"를 묻는다. 둘을 하나로 쓰면 과도기에 저장 경로가 통째로 바뀐다 —
     * 이메일 가입 직후 인증 대기 상태에서 currentUser를 비우면 세션 경로가
     * `free_{uid}`에서 `free_`로 미끄러져 그날 찍은 클립을 잃는다.
     * 익명이든 인증 대기든 Firebase 유저가 있으면 유지한다.
     */
    private val _identityUid = MutableStateFlow("")
    val identityUid: StateFlow<String> = _identityUid

    /**
     * **진짜 계정**으로 로그인했는가. 익명은 false.
     *
     * 익명 인증을 켜면 `auth.currentUser`가 항상 채워져, 예전 정의(`!= null`)는
     * 언제나 true가 된다 — 탭 게이팅도 결제 게이트도 통째로 열린다. 아무것도 안 깨지고
     * 조용히 열리기 때문에 특히 위험하다.
     * 이름을 그대로 두고 정의만 좁혀, 이 값을 보고 있던 곳들이 자동으로 옳게 동작하게 한다.
     */
    val isLoggedIn: Boolean get() = _currentUser.value != null && !_isGuest.value

    /**
     * 게스트 신원을 새로 만들어도 되는 시점인지. 앱 시작과 명시적 로그아웃에서만 켠다.
     *
     * 리스너가 null을 볼 때마다 무조건 만들면, 화면 안에서 상태를 정리하려고 부른 signOut까지
     * 새 게스트 계정을 낳는다. 그러면 uid가 바뀌며 찍어둔 클립이 끊기고, 사용자는 가입도
     * 로그인도 아닌 상태로 튕긴다.
     */
    private var wantsGuestIdentity = true

    // 현지화 에러 메시지용 앱 컨텍스트 — initialize/abortInitialization에서 주입
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
        GuestVlogQuota.initialize(appContext)
        GuestTermsConsent.initialize(appContext)
        // 앱 재설치 시 남은 Firebase 토큰 제거 (iOS의 Keychain 잔존 토큰 처리와 동일)
        val prefs = context.getSharedPreferences("tteona", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("app_installed", false)) {
            auth.signOut()
            prefs.edit().putBoolean("app_installed", true).apply()
        }

        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            scope.launch {
                // 저장 경로용 신원은 게이팅과 별개로 유지한다 (익명·인증 대기 포함)
                _identityUid.value = user?.uid ?: ""
                if (user != null) {
                    // 게스트 — Firestore는 규칙상 익명을 막으므로 조회를 시도조차 하지 않는다.
                    // uid는 채워 둔다: 세션 폴더·서버 브이로그가 이걸 신원으로 쓴다.
                    if (user.isAnonymous) {
                        _isGuest.value = true
                        _currentUser.value = AppUser(uid = user.uid, email = "")
                        _onboardingComplete.value = false
                        _isInitializing.value = false
                        // 신원이 정해졌다 — 지난 게스트 세션 폴더를 치운다
                        SessionFileHousekeeping.purgeOrphanedGuestSessions(appContext, user.uid)
                        return@launch
                    }
                    _isGuest.value = false
                    // Android providerData에는 집계용 "firebase" 항목이 포함되므로 제외.
                    // 카카오(커스텀 토큰) 계정은 실제 provider가 없어 이메일 인증 대상이 아님
                    // (iOS의 allSatisfy 함정 처리와 동일한 의도).
                    val providerIds = user.providerData.map { it.providerId }.filter { it != "firebase" }
                    val isEmailPassword = providerIds.contains("password") && providerIds.all { it == "password" }
                    val needsVerification = isEmailPassword && !user.isEmailVerified
                    if (needsVerification) {
                        // 미인증 이메일 계정 → currentUser 설정하지 않음.
                        // 익명 계정을 승격시킨 직후라면 currentUser에 게스트 신원이 남아 있다.
                        // 그대로 두면 isGuest=false + currentUser≠null이 되어 인증도 안 한 계정이
                        // 로그인으로 잡힌다 — 명시적으로 비운다. (uid는 link로 보존되므로
                        // 인증을 마치면 같은 uid로 돌아온다)
                        _currentUser.value = null
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
                    SessionFileHousekeeping.purgeOrphanedGuestSessions(appContext, user.uid)
                } else {
                    _currentUser.value = null
                    _isGuest.value = false
                    _onboardingComplete.value = false
                    com.seoktaedev.tteona.core.services.ProManager.logOut()

                    // 로그인 상태가 없으면 게스트 신원을 만든다.
                    //
                    // 여기서 isInitializing을 내리면 uid가 빈 채로 화면이 잠깐 뜨고,
                    // 그 사이 세션 폴더가 `free_`로 잡혔다가 익명 uid를 받으면 `free_{uid}`로
                    // 바뀐다 — 그 순간 찍어둔 클립이 어긋난다. 신원이 정해질 때까지 기다린다.
                    if (auth.currentUser == null && wantsGuestIdentity) {
                        signInAnonymously()
                        return@launch   // 성공하면 리스너가 다시 불려 위 게스트 분기로 이어진다
                    }
                }
                _isInitializing.value = false
            }
        }
    }

    /** 게스트 신원 발급. 화면도 팝업도 없다 — 사용자는 이런 게 있는지 모른다. */
    private suspend fun signInAnonymously() {
        try {
            auth.signInAnonymously().await()
        } catch (e: Exception) {
            // 오프라인 첫 실행 등. 신원이 없어도 촬영·로컬 합성은 되어야 하므로
            // 여기서 막으면 스플래시에 갇힌다 — 신원 없이 진행시킨다.
            Log.w("Auth", "익명 로그인 실패(신원 없이 진행)", e)
            _isInitializing.value = false
        }
    }

    /**
     * 자격증명으로 계정에 들어간다. **게스트라면 갈아타지 않고 승격시킨다.**
     *
     * `signInWithCredential`은 "이 사람으로 갈아타라"는 명령이라 현재 익명 세션을 통째로
     * 버린다. 그러면 uid가 바뀌면서 게스트가 찍어둔 클립(`Sessions/free_{uid}`)과 브이로그
     * 쿼터가 함께 사라진다. `linkWithCredential`은 같은 uid에 로그인 수단만 붙이므로
     * 전부 그대로 이어진다.
     *
     * 이미 그 자격증명을 쓰는 계정이 있으면 link는 실패한다. 그때는 기존 계정으로 들어가야
     * 하므로 signIn으로 물러나고, 익명 계정은 버려진다 — 찍어둔 영상은 파일 이관으로 살린다.
     * (카카오는 커스텀 토큰이라 애초에 이 경로를 못 탄다)
     */
    private suspend fun signInOrLink(credential: com.google.firebase.auth.AuthCredential): com.google.firebase.auth.AuthResult {
        val user = auth.currentUser
        if (user == null || !user.isAnonymous) {
            return auth.signInWithCredential(credential).await()
        }
        val guestUid = user.uid
        return try {
            val result = user.linkWithCredential(credential).await()
            // link는 uid를 바꾸지 않아 **상태 리스너가 불리지 않는다**
            // (리스너는 로그인/로그아웃·uid 변경에만 반응한다).
            // 직접 내려주지 않으면 승격했는데도 isGuest가 true로 남아, 앱이 계속 게스트로
            // 취급한다 — 화면이 안 넘어가던 원인이었다.
            _isGuest.value = false
            _identityUid.value = result.user?.uid ?: guestUid
            GuestVlogQuota.reset()   // 계정이 생겼으니 게스트 제한은 사라진다
            result
        } catch (e: Exception) {
            val code = (e as? FirebaseAuthException)?.errorCode
            val alreadyInUse = e is FirebaseAuthUserCollisionException ||
                code == "ERROR_CREDENTIAL_ALREADY_IN_USE" ||
                code == "ERROR_EMAIL_ALREADY_IN_USE" ||
                code == "ERROR_PROVIDER_ALREADY_LINKED"
            if (!alreadyInUse) throw e
            // 이미 쓰이는 자격증명 — Firebase가 로그인에 쓸 최신 credential을 실어 준다
            val fallback = (e as? FirebaseAuthUserCollisionException)?.updatedCredential
            Log.d("Auth", "익명 승격 불가 — 기존 계정으로 로그인")
            val result = auth.signInWithCredential(fallback ?: credential).await()
            // uid가 바뀌었다 — 게스트로 찍어둔 영상을 새 계정 폴더로 옮겨 준다
            result.user?.uid?.let { SessionFileHousekeeping.migrateGuestSession(appContext, guestUid, it) }
            GuestVlogQuota.reset()
            result
        }
    }

    // MARK: - 이메일 로그인
    suspend fun signIn(email: String, password: String) {
        _isLoading.value = true
        _errorMessage.value = null
        try {
            if (!isValidEmail(email)) { _errorMessage.value = LocaleManager.string(appContext, R.string.auth_error_invalidEmail); return }
            if (password.length < 6) { _errorMessage.value = LocaleManager.string(appContext, R.string.auth_error_shortPassword); return }

            val result = signInOrLink(
                com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
            )
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

            // 게스트가 가입하는 중이면 새 계정을 만들지 않고 익명 계정에 이메일을 붙인다
            val guest = auth.currentUser?.takeIf { it.isAnonymous }
            val result = if (guest != null) {
                val linked = guest.linkWithCredential(
                    com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
                ).await()
                // link는 uid를 바꾸지 않아 리스너가 불리지 않는다. 게스트 상태를 내리되,
                // 이메일은 인증 전까지 로그인으로 치지 않으므로 currentUser는 비워 둔다.
                _isGuest.value = false
                _identityUid.value = linked.user?.uid ?: ""
                GuestVlogQuota.reset()
                _currentUser.value = null
                linked
            } else {
                auth.createUserWithEmailAndPassword(email, password).await()
            }
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
                    } else if (user != null) {
                        // 로그인은 이미 성공했다. 예전엔 여기서 로그아웃하고 "이미 쓰는 이메일"이라
                        // 안내했는데, 익명 인증이 켜진 지금은 그 로그아웃이 새 게스트 계정을 낳아
                        // 가입도 로그인도 아닌 상태로 튕긴다. 원하던 계정에 들어온 것이니 그대로 둔다.
                        _verificationEmailSent.value = false
                        _isGuest.value = false
                        _identityUid.value = user.uid
                        _currentUser.value = AppUser(uid = user.uid, email = user.email ?: "")
                        refreshOnboardingStatus(user.uid)
                        _errorMessage.value = null
                    } else {
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
                val authResult = signInOrLink(firebaseCredential)
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

            // 카카오는 uid가 kakao_{id}로 고정된 커스텀 토큰이라 link 자체가 불가능하다.
            // 익명 계정은 버려지므로, 그 전에 찍어둔 영상을 옮길 수 있게 uid를 붙잡아 둔다.
            val guestUid = auth.currentUser?.takeIf { it.isAnonymous }?.uid

            val authResult = auth.signInWithCustomToken(customToken).await()
            val user = authResult.user ?: return
            if (guestUid != null) {
                SessionFileHousekeeping.migrateGuestSession(appContext, guestUid, user.uid)
                GuestVlogQuota.reset()
            }
            _verificationEmailSent.value = false
            _isGuest.value = false
            _identityUid.value = user.uid
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

    enum class OnboardingResult { SUCCESS, NICKNAME_TAKEN, FAILED }

    // MARK: - 온보딩 완료 (users 문서 생성 — iOS OnboardingView.finishOnboarding에 대응)
    suspend fun completeOnboarding(nickname: String, preferredTag: String? = null): OnboardingResult {
        val user = auth.currentUser ?: return OnboardingResult.FAILED
        val trimmed = nickname.trim()
        _isLoading.value = true
        try {
            // 닉네임 원자적 예약 — 두 사람이 동시에 같은 닉네임으로 가입하는 레이스를 막는다.
            // 실패하면(그 사이 남이 선점) 닉네임 단계로 되돌려 다시 고르게 한다.
            val reserved = com.seoktaedev.tteona.core.services.UserService.reserveNickname(trimmed, user.uid)
            if (!reserved) return OnboardingResult.NICKNAME_TAKEN

            // email·isVerified는 공개 users 문서에 저장하지 않는다:
            //   · email — PII, Auth(currentUser)가 소유
            //   · isVerified — 관리자(Firebase 콘솔)만 설정, rules도 클라 쓰기 차단
            // merge=true — 온보딩을 다시 밟은 기존 유저의 좋아요·발자취·프로필사진 등이
            //   통째로 덮어써져 사라지는 것을 막는다.
            val data = buildMap {
                put("uid", user.uid)
                put("nickname", trimmed)
                put("createdAt", Timestamp.now())
                preferredTag?.let { put("preferredTag", it) }
            }
            db.collection("users").document(user.uid)
                .set(data, com.google.firebase.firestore.SetOptions.merge()).await()
            // 과거 버전이 공개 users 문서에 저장해 둔 email(PII)이 남아 있으면 제거한다.
            runCatching {
                db.collection("users").document(user.uid)
                    .update("email", FieldValue.delete()).await()
            }
            // 저장 성공 시에만 완료 처리 — 저장이 실패했는데 완료로 넘기면 닉네임 없는
            // 반쪽 계정으로 메인에 진입하고, 다음 실행 때 온보딩이 다시 뜬다.
            _currentUser.value = _currentUser.value?.copy(nickname = trimmed)
            _onboardingComplete.value = true
            return OnboardingResult.SUCCESS
        } catch (e: Exception) {
            // 저장 실패 시 예약 반납 (다음 시도·타인 선점 허용)
            runCatching { com.seoktaedev.tteona.core.services.UserService.releaseNickname(trimmed, user.uid) }
            _errorMessage.value = LocaleManager.string(appContext, R.string.auth_error_profileSaveFailed)
            return OnboardingResult.FAILED
        } finally {
            _isLoading.value = false
        }
    }

    // MARK: - 로그아웃
    fun signOut() {
        wantsGuestIdentity = true   // 명시적 로그아웃 — 게스트로 돌아간다
        // iOS RootView의 onChange(isLoggedIn) → clearUserData 대응
        com.seoktaedev.tteona.core.services.CourseService.clearUserData()
        com.seoktaedev.tteona.core.services.UserService.clear()
        com.seoktaedev.tteona.core.services.RoomService.clear()
        com.seoktaedev.tteona.core.services.FootprintService.clear()
        /*
         * **진행 중이던 세션은 로그아웃과 함께 끊는다.**
         *
         * 세션 저장소는 기기 단위인데 클립은 `free_{uid}` 아래에 있다. 지우지 않으면
         * 로그아웃 뒤 새로 발급된 게스트가 이전 계정의 장소 목록을 그대로 물려받고,
         * 그 항목이 가리키는 클립 파일은 남의 폴더에 있어 열리지 않는다 —
         * 촬영 탭 칩에 "1곳"이 뜨는데 영상은 없는 상태가 된다.
         * (iOS signOut도 두 저장소를 함께 비운다)
         */
        com.seoktaedev.tteona.core.services.ActiveSessionStore.clear()
        com.seoktaedev.tteona.core.services.ImpromptuSessionStore.clear()
        /*
         * 기기 푸시 토큰 해제를 **Firebase 세션이 살아 있을 때** 먼저 끝낸다.
         * 남겨두면 이 기기에 다음으로 로그인하는 사람에게 이전 계정의 알림이 배달된다.
         * 네트워크가 죽어 있어도 로그아웃 자체는 막히면 안 되므로 시간 제한을 둔다.
         */
        scope.launch {
            runCatching {
                kotlinx.coroutines.withTimeout(3_000) {
                    val token = com.google.firebase.messaging.FirebaseMessaging.getInstance()
                        .token.await()
                    com.seoktaedev.tteona.core.network.ApiClient.api.unregisterPush(
                        com.seoktaedev.tteona.core.network.PushUnregisterRequest(token),
                    )
                }
            }
            auth.signOut()
        }
    }

    // MARK: - 회원탈퇴 (iOS deleteAccount 대응)
    // 서버(Cloud Function)가 코스·그룹·계정을 일괄 삭제하고, 클라이언트는 세션 정리만 담당.
    suspend fun deleteAccount(context: Context): Boolean {
        _isLoading.value = true
        // 탈퇴가 끝날 때까지는 게스트 신원을 자동으로 만들지 않는다.
        //
        // 서버가 Auth 계정을 지우는 순간 로그아웃이 한 번 감지되고, 아래 signOut()에서 또
        // 한 번 감지된다. 그때마다 익명 계정이 새로 발급돼 **탈퇴 한 번에 두 개**가 생긴다.
        // 정리가 다 끝난 뒤 아래에서 딱 하나만 만든다.
        wantsGuestIdentity = false
        return try {
            // 1) WAS 측 개인정보(푸시토큰·통계·아바타·채팅닉네임·Vlog파일) 삭제 —
            //    Auth 계정이 지워지기 전, 토큰이 유효할 때 먼저 호출 (iOS와 동일)
            runCatching { com.seoktaedev.tteona.core.network.ApiClient.api.purgeMyData() }

            // 2) 서버(Cloud Function)에서 Firestore 데이터 + Auth 계정을 일괄 삭제
            com.google.firebase.functions.FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("deleteMyAccount")
                .call()
                .await()

            com.seoktaedev.tteona.core.services.CourseService.clearUserData()
            com.seoktaedev.tteona.core.services.UserService.clear()
            com.seoktaedev.tteona.core.services.RoomService.clear()
            com.seoktaedev.tteona.core.services.FootprintService.clear()
            // 로컬 세션·촬영 클립 정리 (iOS의 세션스토어 clear + Tteona 파일 삭제 대응)
            com.seoktaedev.tteona.core.services.ActiveSessionStore.clear()
            com.seoktaedev.tteona.core.services.ImpromptuSessionStore.clear()
            runCatching { java.io.File(context.filesDir, "Tteona").deleteRecursively() }
            // 구글 로그인 세션 완전 해제 (iOS GIDSignIn.disconnect 대응)
            runCatching {
                CredentialManager.create(context)
                    .clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
            }
            auth.signOut()
            // 게스트 기록도 함께 지운다 — 탈퇴한 사람이 새 게스트로 시작할 때
            // 옛 브이로그 쿼터가 남아 있으면 첫 브이로그부터 막힌다.
            GuestVlogQuota.reset()

            // 정리가 끝났다 — 게스트로 돌아간다.
            // 플래그를 먼저 되돌리면, signOut으로 대기 중이던 리스너 콜백이 그 값을 보고
            // 하나를 더 만든다(같은 초에 두 개가 생긴다). 여기서는 직접 하나만 발급하고,
            // 플래그는 finally에서 되돌린다.
            signInAnonymously()
            true
        } catch (e: Exception) {
            Log.w("Auth", "회원탈퇴 실패", e)
            _errorMessage.value = LocaleManager.string(appContext, R.string.settings_deleteFailed_message)
            false
        } finally {
            wantsGuestIdentity = true
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
        // 기존 가입 유저는 Firestore users 문서가 이미 존재하므로 온보딩을 다시 하지 않도록 처리.
        // ⚠️ 네트워크 오류로 조회가 실패했을 때 onboardingComplete=false로 떨어뜨리면,
        //    기존 유저가 온보딩 화면으로 밀려나고 거기서 저장 시 프로필이 덮어써질 위험이 있다.
        //    따라서 "문서 없음"(정상 조회 후 exists=false)과 "조회 실패"(예외)를 반드시 구분한다.
        try {
            val doc = db.collection("users").document(uid).get().await()
            _onboardingComplete.value = doc.exists()
        } catch (e: Exception) {
            // 조회 실패 — 문서 존재 여부를 확신할 수 없으므로 상태를 함부로 바꾸지 않는다.
            // (기존 유저를 온보딩으로 되돌리지 않는다. 실제 신규 유저면 users 문서가 없어
            //  이후 온라인 복구 시 재조회로 자연히 온보딩이 이어진다.)
            Log.w("Auth", "refreshOnboardingStatus 조회 실패 — 상태 유지", e)
        }
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
