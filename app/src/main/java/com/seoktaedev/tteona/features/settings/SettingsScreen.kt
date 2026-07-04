package com.seoktaedev.tteona.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.ui.theme.TteMediumGray

/**
 * 설정 탭 — iOS SettingsView에 해당.
 * 프로필/알림/계정 관리 전체 이식 전까지는 계정 정보 + 로그아웃만 제공.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val currentUser by AuthService.currentUser.collectAsState()

    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("설정", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        currentUser?.let { user ->
            Text(user.email.ifEmpty { "이메일 없음" }, fontSize = 14.sp, color = TteMediumGray)
            Spacer(Modifier.height(24.dp))
        }
        OutlinedButton(onClick = { AuthService.signOut() }) {
            Text("로그아웃")
        }
    }
}
