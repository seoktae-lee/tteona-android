package com.seoktaedev.tteona.features.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.launch

/**
 * 온보딩 — 닉네임 설정 후 Firestore users 문서 생성.
 * iOS OnboardingView(617줄)의 축약판. 전체 온보딩(가이드 페이지 등)은 추후 이식.
 */
@Composable
fun OnboardingScreen() {
    val isLoading by AuthService.isLoading.collectAsState()
    val errorMessage by AuthService.errorMessage.collectAsState()
    var nickname by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(120.dp))

            Text("환영해요! 👋", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                "떠나에서 사용할 닉네임을 정해주세요",
                fontSize = 15.sp,
                color = TteMediumGray,
            )

            Spacer(Modifier.height(40.dp))

            OutlinedTextField(
                value = nickname,
                onValueChange = { if (it.length <= 12) nickname = it },
                placeholder = { Text("닉네임 (2~12자)", color = TteMediumGray) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = TteFieldBackground,
                    focusedContainerColor = TteFieldBackground,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = TteOrange,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            errorMessage?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { scope.launch { AuthService.completeOnboarding(nickname.trim()) } },
                enabled = !isLoading && nickname.trim().length >= 2,
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
                    Text("시작하기", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
