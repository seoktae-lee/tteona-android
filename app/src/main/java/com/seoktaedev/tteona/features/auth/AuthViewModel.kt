package com.seoktaedev.tteona.features.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seoktaedev.tteona.core.auth.AuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 로그인 화면 ViewModel — 전역 AuthService(StateFlow)의 얇은 래퍼.
 * 화면 전용 상태(회원가입 모드 전환, 재설정 메일 발송 알림)만 직접 가진다.
 */
class AuthViewModel : ViewModel() {
    val isLoading = AuthService.isLoading
    val errorMessage = AuthService.errorMessage
    val verificationEmailSent = AuthService.verificationEmailSent

    private val _isSignUp = MutableStateFlow(false)
    val isSignUp: StateFlow<Boolean> = _isSignUp

    private val _resetEmailSent = MutableStateFlow(false)
    val resetEmailSent: StateFlow<Boolean> = _resetEmailSent

    fun toggleMode() {
        _isSignUp.value = !_isSignUp.value
        AuthService.clearError()
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch { AuthService.signIn(email.trim(), password) }
    }

    fun signUp(email: String, password: String, confirmPassword: String, passwordMismatchMessage: String) {
        if (password != confirmPassword) {
            // iOS와 동일한 사전 검증 — 현지화 문자열은 컴포저블에서 전달받는다
            viewModelScope.launch { AuthService.clearError() }
            _validationError.value = passwordMismatchMessage
            return
        }
        _validationError.value = null
        viewModelScope.launch { AuthService.signUp(email.trim(), password) }
    }

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch { AuthService.signInWithGoogle(context) }
    }

    fun signInWithKakao(context: Context) {
        viewModelScope.launch { AuthService.signInWithKakao(context) }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            if (AuthService.sendPasswordReset(email.trim())) _resetEmailSent.value = true
        }
    }

    fun dismissResetAlert() {
        _resetEmailSent.value = false
    }

    fun verifyAndLogin(email: String, password: String) {
        viewModelScope.launch { AuthService.verifyAndLogin(email.trim(), password) }
    }

    fun resendVerificationEmail(email: String, password: String, onSent: () -> Unit) {
        viewModelScope.launch {
            if (AuthService.resendVerificationEmail(email.trim(), password)) onSent()
        }
    }
}
