package com.seoktaedev.tteona.features.group

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.model.Room
import com.seoktaedev.tteona.core.services.RoomService
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.launch

/**
 * 방 상세 — iOS Features/Group/RoomDetailView.swift의 이식본.
 * 초대코드 배너 + 단톡(GroupChatSection) + 나가기 메뉴.
 */
@Composable
fun RoomDetailScreen(room: Room, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showLeaveAlert by remember { mutableStateOf(false) }
    val uid = AuthService.currentUser.value?.uid ?: ""

    LaunchedEffect(room.roomId) {
        RoomService.fetchMembers(room.roomId)
        RoomService.startListeningFeed(room.roomId)
    }
    DisposableEffect(room.roomId) {
        onDispose { RoomService.stopListeningFeed() }
    }

    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 타이틀 바
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로",
                    tint = TteDarkGray,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .size(24.dp)
                        .clickable(onClick = onBack),
                )
                Text(room.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = TteDarkGray)
                Box(Modifier.align(Alignment.CenterEnd)) {
                    Icon(
                        Icons.Filled.MoreHoriz,
                        contentDescription = "메뉴",
                        tint = TteDarkGray,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(24.dp)
                            .clickable { showMenu = true },
                    )
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("그룹 나가기", color = Color.Red) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color.Red)
                            },
                            onClick = { showMenu = false; showLeaveAlert = true },
                        )
                    }
                }
            }

            // 초대코드 배너
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TteFieldBackground)
                    .padding(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("초대 코드", fontSize = 11.sp, color = TteMediumGray)
                    Text(
                        room.inviteCode,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TteOrange,
                        letterSpacing = 4.sp,
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TteOrange)
                        .clickable {
                            // iOS ShareSheet 대응 — 초대 링크 공유
                            val url = "https://tteona.kr/room?code=${room.inviteCode}&name=${android.net.Uri.encode(room.name)}"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            }
                            context.startActivity(Intent.createChooser(intent, "초대 링크 공유"))
                        },
                ) {
                    Icon(Icons.Filled.Share, contentDescription = "공유", tint = Color.White, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.size(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = TteMediumGray, modifier = Modifier.size(13.dp))
                    Text("${room.memberIds.size}명", fontSize = 12.sp, color = TteMediumGray)
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(TteMediumGray.copy(alpha = 0.25f))
            )

            // 단톡
            GroupChatSection(room = room, modifier = Modifier.weight(1f))
        }
    }

    if (showLeaveAlert) {
        AlertDialog(
            onDismissRequest = { showLeaveAlert = false },
            title = { Text("그룹 나가기") },
            text = { Text("그룹을 나가면 다시 초대 코드로 참여할 수 있어요.") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveAlert = false
                    scope.launch {
                        runCatching { RoomService.leaveRoom(room.roomId, uid) }
                        onBack()
                    }
                }) { Text("나가기", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showLeaveAlert = false }) { Text("취소") } },
        )
    }
}
