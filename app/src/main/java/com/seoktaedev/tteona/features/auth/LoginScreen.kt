package com.seoktaedev.tteona.features.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.delay

// iOS Features/Auth/AuthView.swift의 이식본 (Firebase Auth 실연동).
// 소셜 로그인: 구글(Credential Manager)·카카오(SDK 등록 후 활성화).
@Composable
fun LoginScreen(viewModel: AuthViewModel = viewModel()) {
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val validationError by viewModel.validationError.collectAsState()
    val passwordMismatchMessage = stringResource(R.string.auth_passwordMismatch)
    val verificationEmailSent by viewModel.verificationEmailSent.collectAsState()
    val isSignUp by viewModel.isSignUp.collectAsState()
    val resetEmailSent by viewModel.resetEmailSent.collectAsState()

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    if (verificationEmailSent) {
        VerificationSentView(
            email = email,
            password = password,
            isLoading = isLoading,
            errorMessage = errorMessage,
            viewModel = viewModel,
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 주황 일렁임 배경 (iOS AuthView의 TteonaSplashBackground)
        com.seoktaedev.tteona.ui.theme.TteonaSplashBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(130.dp))

            // 로고 섹션 (워드마크 이미지 — iOS와 동일 에셋)
            Image(
                painter = painterResource(R.drawable.tteona_logo),
                contentDescription = "tteona",
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(190.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.auth_tagline), fontSize = 15.sp, color = TteMediumGray)

            Spacer(Modifier.height(48.dp))

            // 소셜 로그인 섹션
            Text(stringResource(R.string.auth_socialLogin), fontSize = 13.sp, color = TteMediumGray)
            Spacer(Modifier.height(16.dp))
            val context = LocalContext.current
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // Google — iOS와 동일한 4색 그라데이션 G
                SocialCircleButton(
                    background = Color.White,
                    border = Color(0x33000000),
                    enabled = !isLoading,
                    onClick = { viewModel.signInWithGoogle(context) },
                ) {
                    Text(
                        "G",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(
                            brush = Brush.linearGradient(
                                listOf(
                                    Color(0xFF4285F4), // blue
                                    Color(0xFFEA4335), // red
                                    Color(0xFFFBBC05), // yellow
                                    Color(0xFF34A853), // green
                                )
                            )
                        ),
                    )
                }
                // 카카오 — iOS message.fill과 동일한 채워진 말풍선
                SocialCircleButton(
                    background = Color(0xFFFEE500),
                    border = Color.Transparent,
                    enabled = !isLoading,
                    onClick = { viewModel.signInWithKakao(context) },
                ) {
                    Icon(
                        Icons.Filled.ChatBubble,
                        contentDescription = stringResource(R.string.auth_kakaoLogin),
                        tint = Color(0xFF3A1D1D),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // 이메일 입력 섹션
            TteTextField(email, { email = it }, stringResource(R.string.auth_email), KeyboardType.Email)
            Spacer(Modifier.height(14.dp))
            TteTextField(password, { password = it }, stringResource(R.string.auth_password), isSecure = true)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.auth_passwordHint),
                fontSize = 12.sp,
                color = TteMediumGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            )

            if (isSignUp) {
                Spacer(Modifier.height(14.dp))
                TteTextField(confirmPassword, { confirmPassword = it }, stringResource(R.string.auth_confirmPassword), isSecure = true)
            }

            (validationError ?: errorMessage)?.let { error ->
                Spacer(Modifier.height(10.dp))
                Text(
                    error,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            // 액션 버튼
            Button(
                onClick = {
                    if (isSignUp) viewModel.signUp(email, password, confirmPassword, passwordMismatchMessage)
                    else viewModel.signIn(email, password)
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TteOrange),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        if (isSignUp) stringResource(R.string.auth_signUp) else stringResource(R.string.auth_signIn),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (!isSignUp) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.auth_forgotPassword),
                    fontSize = 13.sp,
                    color = TteMediumGray,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { showResetDialog = true },
                )
            }

            Spacer(Modifier.height(16.dp))

            // 모드 전환
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable {
                    viewModel.toggleMode()
                    confirmPassword = ""
                },
            ) {
                Text(
                    if (isSignUp) stringResource(R.string.auth_alreadyHaveAccount) else stringResource(R.string.auth_noAccount),
                    fontSize = 14.sp,
                    color = TteMediumGray,
                )
                Text(
                    if (isSignUp) stringResource(R.string.auth_signIn) else stringResource(R.string.auth_signUp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TteOrange,
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // 비밀번호 재설정 다이얼로그 (iOS의 재설정 alert 대응)
    if (showResetDialog) {
        var resetEmail by rememberSaveable { mutableStateOf(email) }
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.auth_resetPassword)) },
            text = {
                Column {
                    Text(stringResource(R.string.auth_resetPassword_message))
                    Spacer(Modifier.height(12.dp))
                    TteTextField(resetEmail, { resetEmail = it }, stringResource(R.string.auth_emailAddress), KeyboardType.Email)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.sendPasswordReset(resetEmail)
                    showResetDialog = false
                }) { Text(stringResource(R.string.auth_send), color = TteOrange) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (resetEmailSent) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissResetAlert() },
            title = { Text(stringResource(R.string.auth_emailSent_title)) },
            text = { Text(stringResource(R.string.auth_emailSent_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissResetAlert() }) { Text(stringResource(R.string.common_ok), color = TteOrange) }
            },
        )
    }
}

// iOS verificationSentView의 이식본 — 인증 메일 발송 완료 화면
@Composable
private fun VerificationSentView(
    email: String,
    password: String,
    isLoading: Boolean,
    errorMessage: String?,
    viewModel: AuthViewModel,
) {
    var resendCooldown by rememberSaveable { mutableIntStateOf(60) }
    var showResendAlert by rememberSaveable { mutableStateOf(false) }

    // 재전송 쿨다운 (iOS startResendCooldown과 동일한 60초)
    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1000)
            resendCooldown -= 1
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 주황 일렁임 배경 (iOS AuthView와 동일)
        com.seoktaedev.tteona.ui.theme.TteonaSplashBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(160.dp)
                        .background(TteOrange.copy(alpha = 0.08f), CircleShape)
                )
                Box(
                    Modifier
                        .size(120.dp)
                        .background(TteOrange.copy(alpha = 0.14f), CircleShape)
                )
                Icon(
                    Icons.Filled.MarkEmailRead,
                    contentDescription = null,
                    tint = TteOrange,
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.height(40.dp))

            Text(stringResource(R.string.auth_checkEmail_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.auth_checkEmail_message),
                fontSize = 15.sp,
                color = TteMediumGray,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.auth_checkSpam),
                fontSize = 12.sp,
                color = TteMediumGray.copy(alpha = 0.7f),
            )

            Spacer(Modifier.weight(1f))

            errorMessage?.let {
                Text(
                    it,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable(enabled = resendCooldown <= 0) {
                    viewModel.resendVerificationEmail(email, password) {
                        showResendAlert = true
                        resendCooldown = 60
                    }
                },
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = if (resendCooldown > 0) TteMediumGray.copy(alpha = 0.5f) else TteOrange,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    if (resendCooldown > 0) stringResource(R.string.auth_resendIn, resendCooldown) else stringResource(R.string.auth_resendVerification),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (resendCooldown > 0) TteMediumGray.copy(alpha = 0.5f) else TteOrange,
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { viewModel.verifyAndLogin(email, password) },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TteOrange),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.auth_startAfterVerify), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }

    if (showResendAlert) {
        AlertDialog(
            onDismissRequest = { showResendAlert = false },
            title = { Text(stringResource(R.string.auth_verificationMail)) },
            text = { Text(stringResource(R.string.auth_resendDone)) },
            confirmButton = {
                TextButton(onClick = { showResendAlert = false }) { Text(stringResource(R.string.common_ok), color = TteOrange) }
            },
        )
    }
}

// iOS SocialCircleButton의 이식본
@Composable
private fun SocialCircleButton(
    background: Color,
    border: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(60.dp)
            .background(background, CircleShape)
            .border(1.dp, border, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        content()
    }
}

// iOS TteTextField의 이식본
@Composable
private fun TteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isSecure: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = TteMediumGray) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isSecure) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = TteFieldBackground,
            focusedContainerColor = TteFieldBackground,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = TteOrange,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
