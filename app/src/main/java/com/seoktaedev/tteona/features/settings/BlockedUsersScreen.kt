package com.seoktaedev.tteona.features.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.model.AppUser
import com.seoktaedev.tteona.core.services.UserService
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.launch

/** 차단된 사용자 관리 — iOS Features/Settings/BlockedUsersView.swift의 이식본. */
@Composable
fun BlockedUsersScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val profileUser by UserService.currentUser.collectAsState()
    var blockedUsers by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(profileUser?.blockedUserIds) {
        val blockedIds = profileUser?.blockedUserIds
        if (blockedIds.isNullOrEmpty()) {
            blockedUsers = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        blockedUsers = blockedIds.map { uid ->
            UserService.fetchAuthor(uid) ?: AppUser(uid = uid, email = "", nickname = "탈퇴한 사용자")
        }
        isLoading = false
    }

    Column(modifier.fillMaxSize()) {
        SubScreenTopBar(title = "차단된 사용자 관리", onBack = onBack)

        when {
            isLoading -> Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = TteOrange)
            }
            blockedUsers.isEmpty() -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
            ) {
                Icon(
                    Icons.Filled.HowToReg,
                    contentDescription = null,
                    tint = TteMediumGray.copy(alpha = 0.4f),
                    modifier = Modifier.size(44.dp),
                )
                Text("차단한 사용자가 없습니다.", fontSize = 15.sp, color = TteMediumGray)
            }
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 20.dp, vertical = 8.dp
                ),
            ) {
                items(blockedUsers, key = { it.uid }) { user ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(TteFieldBackground)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.nickname, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TteDarkGray)
                            if (user.email.isNotEmpty()) {
                                Spacer(Modifier.height(3.dp))
                                Text(user.email, fontSize = 12.sp, color = TteMediumGray)
                            }
                        }
                        Text(
                            "차단 해제",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TteOrange,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(TteOrange.copy(alpha = 0.12f))
                                .clickable {
                                    val currentUid = AuthService.currentUser.value?.uid ?: return@clickable
                                    scope.launch {
                                        runCatching { UserService.unblockUser(currentUid, user.uid) }
                                            .onSuccess {
                                                blockedUsers = blockedUsers.filter { it.uid != user.uid }
                                            }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
