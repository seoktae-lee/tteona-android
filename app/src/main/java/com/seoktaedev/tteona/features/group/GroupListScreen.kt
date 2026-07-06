package com.seoktaedev.tteona.features.group

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.model.Room
import com.seoktaedev.tteona.core.services.RoomService
import com.seoktaedev.tteona.core.services.UserService
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.launch

/**
 * 그룹(피드) 목록 — iOS Features/Group/FeedTabView.swift의 이식본.
 * 탐색 탭 우측 상단 버튼으로 여는 전체 화면 (iOS sheet 대응).
 */
private enum class GroupSubScreen { LIST, CREATE, JOIN }

@Composable
fun GroupListScreen(onClose: (() -> Unit)? = null) {
    val authUser by AuthService.currentUser.collectAsState()
    val profileUser by UserService.currentUser.collectAsState()
    val myRooms by RoomService.myRooms.collectAsState()
    val uid = authUser?.uid ?: ""
    val nickname = profileUser?.nickname?.takeIf { it.isNotEmpty() } ?: "멤버"

    var subScreen by rememberSaveable { mutableStateOf(GroupSubScreen.LIST) }
    var selectedRoom by remember { mutableStateOf<Room?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var joinInitialCode by rememberSaveable { mutableStateOf("") }

    // 채팅 푸시 탭 → 해당 방 자동 오픈 (iOS openChatRoom)
    val pendingChatRoom by com.seoktaedev.tteona.core.services.AppNotificationManager
        .pendingChatRoom.collectAsState()
    LaunchedEffect(pendingChatRoom, myRooms) {
        val pending = pendingChatRoom ?: return@LaunchedEffect
        val room = myRooms.firstOrNull { it.roomId == pending.roomId } ?: return@LaunchedEffect
        com.seoktaedev.tteona.core.services.AppNotificationManager.clearPendingChatRoom()
        selectedRoom = room
    }

    // 그룹 초대 딥링크 → 코드 참여 화면 자동 오픈 (iOS deepLinkedRoomCode)
    val pendingRoomCode by com.seoktaedev.tteona.core.services.DeepLinkHandler
        .pendingRoomCode.collectAsState()
    LaunchedEffect(pendingRoomCode) {
        val code = pendingRoomCode ?: return@LaunchedEffect
        com.seoktaedev.tteona.core.services.DeepLinkHandler.clearPendingRoom()
        joinInitialCode = code
        subScreen = GroupSubScreen.JOIN
    }

    // 새 피드 dot 판별용 (roomId → millis)
    var roomLastReadAt by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var roomLatestFeedAt by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var isLoadingStatus by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        if (uid.isEmpty()) return@LaunchedEffect
        if (profileUser == null) UserService.fetchUser(uid)
        RoomService.startListeningMyRooms(uid)
    }

    // 방 목록이 바뀔 때마다 읽음 상태 재계산 (iOS loadReadStatus)
    LaunchedEffect(myRooms) {
        if (uid.isEmpty() || myRooms.isEmpty()) return@LaunchedEffect
        isLoadingStatus = true
        val reads = mutableMapOf<String, Long>()
        val latests = mutableMapOf<String, Long>()
        for (room in myRooms) {
            RoomService.fetchMyMemberDoc(room.roomId, uid)?.lastReadAt?.let { reads[room.roomId] = it }
            val latest = RoomService.fetchLatestFeedPerMember(room.roomId, room.memberIds)
                .values.maxOfOrNull { it.createdAt }
            latest?.let { latests[room.roomId] = it }
        }
        roomLastReadAt = reads
        roomLatestFeedAt = latests
        isLoadingStatus = false
    }

    fun hasNewFeed(room: Room): Boolean {
        val latestAt = roomLatestFeedAt[room.roomId] ?: return false
        val readAt = roomLastReadAt[room.roomId] ?: return true
        return latestAt > readAt
    }

    // 하위 화면 분기
    selectedRoom?.let { room ->
        // 방이 삭제/탈퇴되면 목록으로 복귀
        LaunchedEffect(myRooms) {
            if (myRooms.none { it.roomId == room.roomId }) selectedRoom = null
        }
        RoomDetailScreen(room = room, onBack = { selectedRoom = null })
        return
    }
    when (subScreen) {
        GroupSubScreen.CREATE -> {
            CreateRoomScreen(
                uid = uid, nickname = nickname,
                onDone = { subScreen = GroupSubScreen.LIST },
            )
            return
        }
        GroupSubScreen.JOIN -> {
            JoinRoomScreen(
                uid = uid, nickname = nickname,
                onDone = {
                    subScreen = GroupSubScreen.LIST
                    joinInitialCode = ""
                },
                initialCode = joinInitialCode,
            )
            return
        }
        GroupSubScreen.LIST -> Unit
    }

    // 탭 임베드 시(onClose == null)에는 뒤로가기/닫기 버튼 없이 표시 (iOS 채팅 탭 대응)
    onClose?.let { BackHandler(onBack = it) }
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
            ) {
                if (onClose != null) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "닫기",
                        tint = TteDarkGray,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                            .size(24.dp)
                            .clickable(onClick = onClose),
                    )
                }
                Text(
                    "채팅",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center),
                )
                Box(Modifier.align(Alignment.CenterEnd)) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "추가",
                        tint = TteOrange,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(26.dp)
                            .clickable { showMenu = true },
                    )
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("방 만들기") },
                            leadingIcon = { Icon(Icons.Filled.AddCircleOutline, contentDescription = null) },
                            onClick = { showMenu = false; subScreen = GroupSubScreen.CREATE },
                        )
                        DropdownMenuItem(
                            text = { Text("코드로 참여") },
                            leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                            onClick = { showMenu = false; subScreen = GroupSubScreen.JOIN },
                        )
                    }
                }
            }

            when {
                isLoadingStatus && myRooms.isEmpty() -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = TteOrange)
                }

                myRooms.isEmpty() -> GroupEmptyState(
                    onCreate = { subScreen = GroupSubScreen.CREATE },
                    onJoin = { subScreen = GroupSubScreen.JOIN },
                )

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                ) {
                    items(myRooms, key = { it.roomId }) { room ->
                        RoomCard(
                            room = room,
                            hasNewFeed = hasNewFeed(room),
                            onClick = {
                                RoomService.markRoomAsRead(room.roomId, uid)
                                roomLastReadAt = roomLastReadAt + (room.roomId to System.currentTimeMillis())
                                selectedRoom = room
                            },
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Room Card (iOS RoomCard — 그라데이션 아바타 + NEW 뱃지)
private val roomAvatarGradients = listOf(
    listOf(TteOrange, Color(0xFFFF9E4D)),
    listOf(Color(0xFFFA7373), Color(0xFFFFA673)),
    listOf(Color(0xFF59A6F2), Color(0xFF73D1E6)),
    listOf(Color(0xFF8C73F2), Color(0xFFBF8CFA)),
    listOf(Color(0xFF40BF9E), Color(0xFF80DE99)),
    listOf(Color(0xFFF28CBF), Color(0xFFFFB89E)),
)

@Composable
private fun RoomCard(room: Room, hasNewFeed: Boolean, onClick: () -> Unit) {
    // 방마다 고정되는 아바타 그라데이션 (roomId 해시 기반 — iOS와 동일 규칙)
    val hash = room.roomId.sumOf { it.code }
    val gradient = roomAvatarGradients[hash % roomAvatarGradients.size]
    val initial = room.name.trim().take(1)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(TteFieldBackground)
            .border(
                1.2.dp,
                if (hasNewFeed) TteOrange.copy(alpha = 0.35f) else Color.Transparent,
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // 그라데이션 아바타 + 방 이름 첫 글자 + 새 소식 dot
        Box {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(gradient)),
            ) {
                Text(initial, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            if (hasNewFeed) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-3).dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                )
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    room.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TteDarkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (hasNewFeed) {
                    Text(
                        "NEW",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(TteOrange)
                            .padding(horizontal = 6.dp, vertical = 2.5.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = TteMediumGray, modifier = Modifier.size(12.dp))
                Text("멤버 ${room.memberIds.size}명", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TteMediumGray)
            }
        }

        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TteMediumGray.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp),
        )
    }
}

// MARK: - 빈 상태 (iOS emptyState)
@Composable
private fun GroupEmptyState(onCreate: () -> Unit, onJoin: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
    ) {
        Icon(
            Icons.Filled.Groups,
            contentDescription = null,
            tint = TteOrange.copy(alpha = 0.4f),
            modifier = Modifier.size(60.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text("아직 참여한 그룹이 없어요", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TteDarkGray)
        Spacer(Modifier.height(6.dp))
        Text("친구들과 함께 오늘을 공유해보세요!", fontSize = 14.sp, color = TteMediumGray)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TteOrange)
                    .clickable(onClick = onCreate),
            ) {
                Text("방 만들기", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TteOrange.copy(alpha = 0.12f))
                    .clickable(onClick = onJoin),
            ) {
                Text("코드 입력", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TteOrange)
            }
        }
    }
}

// MARK: - 방 만들기 (iOS CreateRoomView)
@Composable
private fun CreateRoomScreen(uid: String, nickname: String, onDone: () -> Unit) {
    var roomName by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onDone)
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            GroupTopBar(title = "그룹 만들기", onClose = onDone)

            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("그룹 이름", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TteMediumGray)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 17.sp, color = TteDarkGray),
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(TteFieldBackground)
                                .padding(14.dp),
                        ) {
                            if (roomName.isEmpty()) Text("예: 제주 여행 친구들", fontSize = 17.sp, color = TteMediumGray)
                            inner()
                        }
                    },
                )
                errorMessage?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, fontSize = 14.sp, color = Color.Red)
                }
            }

            Spacer(Modifier.weight(1f))

            val canCreate = roomName.trim().isNotEmpty() && !isLoading
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canCreate) TteOrange else Color.Gray.copy(alpha = 0.4f))
                    .clickable(enabled = canCreate) {
                        scope.launch {
                            isLoading = true
                            runCatching { RoomService.createRoom(roomName.trim(), uid, nickname) }
                                .onSuccess { onDone() }
                                .onFailure { errorMessage = "방 생성에 실패했어요. 다시 시도해주세요." }
                            isLoading = false
                        }
                    },
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("방 만들기", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

// MARK: - 코드로 참여 (iOS JoinRoomView — 5회 실패 시 1시간 잠금)
@Composable
private fun JoinRoomScreen(uid: String, nickname: String, onDone: () -> Unit, initialCode: String = "") {
    var inviteCode by rememberSaveable { mutableStateOf(initialCode.uppercase().take(6)) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var failCount by rememberSaveable { mutableStateOf(0) }
    var lockUntil by rememberSaveable { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    val maxAttempts = 5
    val lockMillis = 3600_000L
    val isLocked = lockUntil?.let { System.currentTimeMillis() < it } ?: false

    BackHandler(onBack = onDone)
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            GroupTopBar(title = "코드로 참여", onClose = onDone)

            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("초대 코드 6자리를 입력하세요", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TteMediumGray)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it.uppercase().take(6) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    textStyle = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TteDarkGray,
                        textAlign = TextAlign.Center,
                        letterSpacing = 6.sp,
                    ),
                    decorationBox = { inner ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(TteFieldBackground)
                                .padding(14.dp),
                        ) {
                            if (inviteCode.isEmpty()) Text("예: A3F7K2", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TteMediumGray.copy(alpha = 0.5f))
                            inner()
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                if (isLocked) {
                    val remainMin = ((lockUntil!! - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
                    Text(
                        "코드 입력 횟수를 초과했어요.\n${remainMin}분 후 다시 시도해주세요.",
                        fontSize = 14.sp, color = Color.Red, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else errorMessage?.let {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(it, fontSize = 14.sp, color = Color.Red)
                        Text("$failCount/${maxAttempts}회 실패", fontSize = 12.sp, color = TteMediumGray)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            val canJoin = inviteCode.length >= 6 && !isLoading && !isLocked
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canJoin) TteOrange else Color.Gray.copy(alpha = 0.4f))
                    .clickable(enabled = canJoin) {
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            runCatching { RoomService.joinRoom(inviteCode, uid, nickname) }
                                .onSuccess { onDone() }
                                .onFailure {
                                    failCount += 1
                                    if (failCount >= maxAttempts) {
                                        lockUntil = System.currentTimeMillis() + lockMillis
                                        errorMessage = null
                                    } else {
                                        errorMessage = "해당 초대 코드의 방을 찾을 수 없어요."
                                    }
                                }
                            isLoading = false
                        }
                    },
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("참여하기", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

@Composable
internal fun GroupTopBar(title: String, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Close,
            contentDescription = "취소",
            tint = TteDarkGray,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .size(24.dp)
                .clickable(onClick = onClose),
        )
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}
