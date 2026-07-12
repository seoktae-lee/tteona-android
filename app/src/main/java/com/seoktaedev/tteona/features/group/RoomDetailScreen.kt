package com.seoktaedev.tteona.features.group

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.i18n.LocaleManager
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.seoktaedev.tteona.core.model.Room
import com.seoktaedev.tteona.core.model.RoomMember
import com.seoktaedev.tteona.core.services.RoomImageService
import com.seoktaedev.tteona.core.services.RoomService
import com.seoktaedev.tteona.core.util.Haptics
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.ContentScale
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * 방 상세 — iOS Features/Group/RoomDetailView.swift의 이식본.
 * 초대코드 배너 + 단톡(GroupChatSection) + 나가기 메뉴.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailScreen(room: Room, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showLeaveAlert by remember { mutableStateOf(false) }
    var showMembersSheet by remember { mutableStateOf(false) }
    var isUploadingImage by remember { mutableStateOf(false) }
    var imageUploadFailed by remember { mutableStateOf(false) }
    val uid = AuthService.currentUser.value?.uid ?: ""
    val members by RoomService.currentRoomMembers.collectAsState()

    // 대표 이미지 선택 → WAS 업로드 (iOS photosPicker + uploadRoomImage)
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val picked = uri ?: return@rememberLauncherForActivityResult
        isUploadingImage = true
        scope.launch {
            val url = RoomImageService.upload(context, room.roomId, picked)
            isUploadingImage = false
            if (url == null) imageUploadFailed = true else Haptics.success(view)
        }
    }

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
                    contentDescription = stringResource(R.string.common_back),
                    tint = TteDarkGray,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .size(24.dp)
                        .clickable(onClick = onBack),
                )
                Text(room.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = TteDarkGray)
                Box(Modifier.align(Alignment.CenterEnd)) {
                    if (isUploadingImage) {
                        CircularProgressIndicator(
                            color = TteOrange, strokeWidth = 2.dp,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(22.dp),
                        )
                    } else {
                        Icon(
                            Icons.Filled.MoreHoriz,
                            contentDescription = stringResource(R.string.common_menu),
                            tint = TteDarkGray,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(24.dp)
                                .clickable { showMenu = true },
                        )
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.room_membersTitle)) },
                            leadingIcon = { Icon(Icons.Filled.Group, contentDescription = null, tint = TteDarkGray) },
                            onClick = { showMenu = false; showMembersSheet = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.room_changeImage)) },
                            leadingIcon = { Icon(Icons.Filled.Photo, contentDescription = null, tint = TteDarkGray) },
                            onClick = {
                                showMenu = false
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.room_leave), color = Color.Red) },
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
                    Text(stringResource(R.string.room_inviteCode), fontSize = 11.sp, color = TteMediumGray)
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
                            context.startActivity(Intent.createChooser(intent, LocaleManager.string(context, R.string.room_shareInvite)))
                        },
                ) {
                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.common_share), tint = Color.White, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.size(10.dp))
                // 인원 칩 — 탭하면 참여 멤버 목록 시트 (iOS와 동일)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { showMembersSheet = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = TteMediumGray, modifier = Modifier.size(13.dp))
                    Text(stringResource(R.string.room_membersCount, room.memberIds.size), fontSize = 12.sp, color = TteMediumGray)
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TteMediumGray.copy(alpha = 0.6f), modifier = Modifier.size(13.dp))
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

    if (showMembersSheet) {
        RoomMembersSheet(room = room, members = members, myUserId = uid) { showMembersSheet = false }
    }

    if (imageUploadFailed) {
        AlertDialog(
            onDismissRequest = { imageUploadFailed = false },
            title = { Text(stringResource(R.string.common_notice)) },
            text = { Text(stringResource(R.string.room_imageUploadFailed)) },
            confirmButton = {
                TextButton(onClick = { imageUploadFailed = false }) { Text(stringResource(R.string.common_ok), color = TteOrange) }
            },
        )
    }

    if (showLeaveAlert) {
        AlertDialog(
            onDismissRequest = { showLeaveAlert = false },
            title = { Text(stringResource(R.string.room_leave)) },
            text = { Text(stringResource(R.string.room_leaveMessage)) },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveAlert = false
                    scope.launch {
                        runCatching { RoomService.leaveRoom(room.roomId, uid) }
                        onBack()
                    }
                }) { Text(stringResource(R.string.room_leaveButton), color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showLeaveAlert = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/**
 * 참여 멤버 목록 시트 — iOS RoomMembersSheet의 이식본.
 * 방장 먼저, 나머지는 참여 순. 아바타는 users 컬렉션의 profileImageUrl을 개별 조회.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomMembersSheet(
    room: Room,
    members: List<RoomMember>,
    myUserId: String,
    onDismiss: () -> Unit,
) {
    // 방장 먼저, 나머지는 참여 순 (iOS sortedMembers)
    val sortedMembers = remember(members) {
        members.sortedWith(
            compareByDescending<RoomMember> { it.userId == room.creatorId }.thenBy { it.joinedAt }
        )
    }

    // 멤버 문서에는 아바타가 없어 users 컬렉션에서 프로필 이미지를 개별 조회 (iOS loadAvatars)
    var avatarUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(members) {
        val db = Firebase.firestore
        val loaded = mutableMapOf<String, String>()
        for (member in members) {
            if (avatarUrls.containsKey(member.userId)) continue
            val url = runCatching {
                db.collection("users").document(member.userId).get().await()
            }.getOrNull()?.getString("profileImageUrl")
            if (!url.isNullOrEmpty()) loaded[member.userId] = url
        }
        if (loaded.isNotEmpty()) avatarUrls = avatarUrls + loaded
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            // 헤더
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text(
                    stringResource(R.string.room_membersTitle),
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TteDarkGray,
                    modifier = Modifier.align(Alignment.Center),
                )
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = TteMediumGray,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(20.dp)
                        .clickable(onClick = onDismiss),
                )
            }
            Spacer(Modifier.height(8.dp))

            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                items(sortedMembers, key = { it.userId }) { member ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        // 아바타 — 프로필 이미지 or 이니셜
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(TteOrange.copy(alpha = 0.1f)),
                        ) {
                            val url = avatarUrls[member.userId]
                            if (url != null) {
                                coil3.compose.AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text(
                                    member.nickname.take(1),
                                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TteOrange,
                                )
                            }
                        }

                        Text(
                            if (member.userId == myUserId) stringResource(R.string.member_me, member.nickname) else member.nickname,
                            fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TteDarkGray,
                        )
                        if (member.userId == room.creatorId) {
                            Text(
                                stringResource(R.string.room_creator),
                                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TteOrange,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(TteOrange.copy(alpha = 0.14f))
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
