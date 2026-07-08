package com.seoktaedev.tteona.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.services.RoomService
import com.seoktaedev.tteona.core.services.UserService
import com.seoktaedev.tteona.core.util.Haptics
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 설정 → 프로필에서 닉네임을 변경하는 시트 — iOS NicknameEditSheet의 이식본.
 * 온보딩과 동일한 검증 파이프라인(길이 → 부적절 표현 → 중복)을 거친다.
 */
private enum class EditState { IDLE, CHECKING, AVAILABLE, TAKEN, INAPPROPRIATE, UNCHANGED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NicknameEditSheet(onDismiss: () -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val profileUser by UserService.currentUser.collectAsState()
    val currentNickname = profileUser?.nickname ?: ""

    var nickname by remember { mutableStateOf(currentNickname) }
    var state by remember { mutableStateOf(EditState.UNCHANGED) }
    var isSaving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }

    // 온보딩과 동일 기준: 600ms 디바운스 → 금칙어 → 중복 검사. 현재 닉네임 그대로면 저장 불필요.
    LaunchedEffect(nickname) {
        saveFailed = false
        val trimmed = nickname.trim()
        if (trimmed == currentNickname) {
            state = EditState.UNCHANGED
            return@LaunchedEffect
        }
        if (trimmed.length < 2 || trimmed.length > 10) {
            state = EditState.IDLE
            return@LaunchedEffect
        }
        state = EditState.CHECKING
        delay(600)
        if (!RoomService.isTextAllowed(trimmed)) {
            state = EditState.INAPPROPRIATE
            return@LaunchedEffect
        }
        state = if (UserService.isNicknameTaken(trimmed)) EditState.TAKEN else EditState.AVAILABLE
    }

    val canSave = state == EditState.AVAILABLE && !isSaving

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.settings_editNickname),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TteDarkGray,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 20.dp),
            )

            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                placeholder = { Text(stringResource(R.string.onboarding_nickname_placeholder), color = TteMediumGray) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = TteFieldBackground,
                    focusedContainerColor = TteFieldBackground,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = TteOrange,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 8.dp)
                    .height(20.dp),
            ) {
                when (state) {
                    EditState.CHECKING -> {
                        CircularProgressIndicator(color = TteMediumGray, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                        Text(stringResource(R.string.onboarding_nickname_checking), fontSize = 12.sp, color = TteMediumGray)
                    }
                    EditState.AVAILABLE -> {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(13.dp))
                        Text(stringResource(R.string.onboarding_nickname_available), fontSize = 12.sp, color = Color(0xFF34C759))
                    }
                    EditState.TAKEN -> {
                        Icon(Icons.Filled.Cancel, contentDescription = null, tint = Color.Red, modifier = Modifier.size(13.dp))
                        Text(stringResource(R.string.onboarding_nickname_taken), fontSize = 12.sp, color = Color.Red)
                    }
                    EditState.INAPPROPRIATE -> {
                        Icon(Icons.Filled.Cancel, contentDescription = null, tint = Color.Red, modifier = Modifier.size(13.dp))
                        Text(stringResource(R.string.onboarding_nickname_inappropriate), fontSize = 12.sp, color = Color.Red)
                    }
                    EditState.UNCHANGED -> {
                        Text(stringResource(R.string.settings_nickname_unchanged), fontSize = 12.sp, color = TteMediumGray)
                    }
                    EditState.IDLE -> Unit
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${nickname.length}/10",
                    fontSize = 12.sp,
                    color = if (nickname.length > 10) Color.Red else TteMediumGray,
                )
            }

            if (saveFailed) {
                Text(
                    stringResource(R.string.settings_nickname_saveFailed),
                    fontSize = 12.sp,
                    color = Color.Red,
                    modifier = Modifier.padding(horizontal = 28.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            // 저장 버튼
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(TteOrange.copy(alpha = if (canSave) 1f else 0.4f))
                    .clickable(enabled = canSave) {
                        val uid = AuthService.currentUser.value?.uid ?: return@clickable
                        scope.launch {
                            isSaving = true
                            runCatching { UserService.updateNickname(uid, nickname.trim()) }
                                .onSuccess {
                                    Haptics.medium(view)
                                    onDismiss()
                                }
                                .onFailure { saveFailed = true }
                            isSaving = false
                        }
                    },
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text(stringResource(R.string.common_save), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}
