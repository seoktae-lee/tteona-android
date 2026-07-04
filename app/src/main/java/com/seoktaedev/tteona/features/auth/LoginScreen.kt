package com.seoktaedev.tteona.features.auth

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
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

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(100.dp))

            // 로고 섹션
            Text("tteona", fontSize = 52.sp, fontWeight = FontWeight.Bold, color = TteOrange)
            Spacer(Modifier.height(12.dp))
            Text("특별한 순간을 영상으로 기록하세요", fontSize = 15.sp, color = TteMediumGray)

            Spacer(Modifier.height(48.dp))

            // 소셜 로그인 섹션
            Text("소셜 계정으로 로그인", fontSize = 13.sp, color = TteMediumGray)
            Spacer(Modifier.height(16.dp))
            val context = LocalContext.current
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                SocialCircleButton(
                    background = Color.White,
                    border = Color(0x33000000),
                    enabled = !isLoading,
                    onClick = { viewModel.signInWithGoogle(context) },
                ) {
                    Text("G", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
                }
                SocialCircleButton(
                    background = Color(0xFFFEE500),
                    border = Color.Transparent,
                    enabled = !isLoading,
                    onClick = { viewModel.signInWithKakao(context) },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "카카오 로그인",
                        tint = Color(0xFF3A1D1D),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // 이메일 입력 섹션
            TteTextField(email, { email = it }, "이메일", KeyboardType.Email)
            Spacer(Modifier.height(14.dp))
            TteTextField(password, { password = it }, "비밀번호", isSecure = true)
            Spacer(Modifier.height(6.dp))
            Text(
                "6자 이상 입력해주세요",
                fontSize = 12.sp,
                color = TteMediumGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            )

            if (isSignUp) {
                Spacer(Modifier.height(14.dp))
                TteTextField(confirmPassword, { confirmPassword = it }, "비밀번호 확인", isSecure = true)
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
                    if (isSignUp) viewModel.signUp(email, password, confirmPassword)
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
                        if (isSignUp) "회원가입" else "로그인",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (!isSignUp) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "비밀번호를 잊으셨나요?",
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
                    if (isSignUp) "이미 계정이 있으신가요?" else "계정이 없으신가요?",
                    fontSize = 14.sp,
                    color = TteMediumGray,
                )
                Text(
                    if (isSignUp) "로그인" else "회원가입",
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
            title = { Text("비밀번호 재설정") },
            text = {
                Column {
                    Text("가입한 이메일 주소를 입력하면 비밀번호 재설정 링크를 보내드려요.")
                    Spacer(Modifier.height(12.dp))
                    TteTextField(resetEmail, { resetEmail = it }, "이메일 주소", KeyboardType.Email)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.sendPasswordReset(resetEmail)
                    showResetDialog = false
                }) { Text("전송", color = TteOrange) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("취소") }
            },
        )
    }

    if (resetEmailSent) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissResetAlert() },
            title = { Text("이메일을 보냈어요") },
            text = { Text("입력하신 이메일로 비밀번호 재설정 링크를 전송했어요. 메일함을 확인해주세요.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissResetAlert() }) { Text("확인", color = TteOrange) }
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

    Surface(modifier = Modifier.fillMaxSize()) {
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

            Text("이메일을 확인해주세요", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                "가입하신 이메일로 인증 링크를 보냈어요.\n링크를 클릭한 후 아래 버튼을 눌러주세요.",
                fontSize = 15.sp,
                color = TteMediumGray,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "메일이 보이지 않으면 스팸함도 확인해주세요.",
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
                    if (resendCooldown > 0) "재전송 ${resendCooldown}초 후 가능" else "인증 메일 재전송",
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
                    Text("인증 완료 후 시작하기", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }

    if (showResendAlert) {
        AlertDialog(
            onDismissRequest = { showResendAlert = false },
            title = { Text("인증 메일") },
            text = { Text("인증 메일을 다시 보냈어요. 메일함(스팸함 포함)을 확인해주세요.") },
            confirmButton = {
                TextButton(onClick = { showResendAlert = false }) { Text("확인", color = TteOrange) }
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
