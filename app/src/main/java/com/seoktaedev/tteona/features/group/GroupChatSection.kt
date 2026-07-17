package com.seoktaedev.tteona.features.group

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.i18n.LocaleManager
import com.seoktaedev.tteona.core.model.FeedItem
import com.seoktaedev.tteona.core.model.FeedType
import com.seoktaedev.tteona.core.model.Room
import com.seoktaedev.tteona.core.services.ChatMessage
import com.seoktaedev.tteona.core.services.ChatSocketService
import com.seoktaedev.tteona.core.services.RoomService
import com.seoktaedev.tteona.core.services.UserService
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 그룹 단톡 — iOS Features/Group/GroupChatView.swift의 이식본.
 * 채팅 메시지 + 여행 활동(피드) 시스템 메시지를 시간순으로 합친 타임라인.
 */

// 채팅에 시스템 메시지로 노출할 활동 — 저빈도·고가치 마일스톤만 (iOS와 동일)
private val chatVisibleFeedTypes = setOf(
    FeedType.TRIP_START, FeedType.TRIP_END, FeedType.FREE_TRIP_START, FeedType.FREE_TRIP_END,
)

private val quickEmojis = listOf("👍", "❤️", "😂", "😮", "😢", "👏")

private sealed class TimelineEntry(val key: String, val date: Long) {
    class Message(val message: ChatMessage) : TimelineEntry("m_${message.id}", message.createdAt)
    class System(val feed: FeedItem) : TimelineEntry("s_${feed.feedId}", feed.createdAt)
}

// 그룹핑 계산 결과 — 같은 발신자 연속 묶음의 첫 메시지에만 닉네임을,
// 같은 발신자·같은 분(分) 연속 묶음의 마지막 메시지에만 시간을 찍는다 (카톡식, iOS TimelineRow 대응)
private class TimelineRow(
    val entry: TimelineEntry,
    val showNickname: Boolean = false,
    val showTime: Boolean = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupChatSection(room: Room, modifier: Modifier = Modifier) {
    val uid = AuthService.currentUser.value?.uid ?: ""
    val context = LocalContext.current
    val myNickname = UserService.currentUser.value?.nickname?.takeIf { it.isNotEmpty() } ?: LocaleManager.string(context, R.string.session_member)

    val chat = remember(room.roomId) { ChatSocketService() }
    val messages by chat.messages.collectAsState()
    val feedItems by RoomService.feedItems.collectAsState()
    val canLoadOlder by chat.canLoadOlder.collectAsState()
    val isLoadingOlder by chat.isLoadingOlder.collectAsState()
    val moderationBlocked by chat.moderationBlocked.collectAsState()

    var draft by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    var highlightedId by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    DisposableEffect(room.roomId) {
        chat.connect(room.roomId, uid, myNickname)
        // 이 방을 보고 있는 동안엔 이 방의 푸시 표시 억제 (iOS activeChatRoom)
        com.seoktaedev.tteona.core.services.AppNotificationManager.activeChatRoomId = room.roomId
        onDispose {
            chat.disconnect()
            if (com.seoktaedev.tteona.core.services.AppNotificationManager.activeChatRoomId == room.roomId) {
                com.seoktaedev.tteona.core.services.AppNotificationManager.activeChatRoomId = null
            }
        }
    }

    // 차단한 유저의 메시지·활동은 숨긴다 (iOS entries 필터와 동일)
    val chatProfileUser by com.seoktaedev.tteona.core.services.UserService.currentUser.collectAsState()
    val entries = remember(messages, feedItems, chatProfileUser) {
        val blocked = chatProfileUser?.blockedUserIds?.toSet() ?: emptySet()
        val all = messages.filter { it.userId !in blocked }.map { TimelineEntry.Message(it) } +
            feedItems.filter { it.type in chatVisibleFeedTypes && it.userId !in blocked }
                .map { TimelineEntry.System(it) }
        all.sortedBy { it.date }
    }

    // 닉네임·시간 그룹핑 플래그 계산 (iOS rows 대응)
    val rows = remember(entries) {
        entries.mapIndexed { i, e ->
            if (e !is TimelineEntry.Message) return@mapIndexed TimelineRow(e)
            val m = e.message
            val prev = (entries.getOrNull(i - 1) as? TimelineEntry.Message)?.message
            val next = (entries.getOrNull(i + 1) as? TimelineEntry.Message)?.message
            TimelineRow(
                entry = e,
                showNickname = prev?.userId != m.userId,
                showTime = next == null || next.userId != m.userId ||
                    next.createdAt / 60_000 != m.createdAt / 60_000,
            )
        }
    }

    // 읽음 커서 + 라이브 멤버 목록 — 메시지별 안읽음 수(발신자 제외) 계산의 근거
    val readCursors by chat.readCursors.collectAsState()
    val myRooms by RoomService.myRooms.collectAsState()
    val memberIds = myRooms.firstOrNull { it.roomId == room.roomId }?.memberIds ?: room.memberIds

    // 백그라운드에서 수신한 메시지는 읽음 처리하지 않는다 — 복귀 시점에 한 번에 처리
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, room.roomId) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    chat.viewerActive = true
                    chat.markRead()
                }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> chat.viewerActive = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 새 메시지가 바닥에 추가될 때만 맨 아래로 스크롤한다.
    // (entries.size 기준으로 하면 이전 메시지 페이지네이션 prepend에도 바닥으로 튄다 —
    //  마지막 항목 key가 바뀔 때만 스크롤해 위로 로드한 과거 메시지 위치를 보존한다.)
    val lastEntryKey = entries.lastOrNull()?.key
    LaunchedEffect(lastEntryKey) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
    }

    // 위로 스크롤해 최상단에 닿으면 이전 메시지 페이지 로드 (iOS loadOlderMessages)
    val atTop by remember {
        androidx.compose.runtime.derivedStateOf {
            listState.firstVisibleItemIndex <= 1
        }
    }
    LaunchedEffect(atTop, canLoadOlder) {
        if (atTop && canLoadOlder && !isLoadingOlder) chat.loadOlderMessages()
    }

    // 키보드가 올라오면 리스트 뷰포트만 줄어들어 최신 메시지가 가려진다.
    // imePadding()은 입력바만 밀어줄 뿐 스크롤 위치는 보정하지 않으므로 여기서 바닥으로 되돌린다.
    // (iOS GroupChatView의 onChange(of: inputFocused) → scrollTo("BOTTOM") 대응)
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (imeVisible && entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
    }

    // 하이라이트 1.6초 후 해제 (iOS와 동일)
    LaunchedEffect(highlightedId) {
        if (highlightedId != null) {
            delay(1600)
            highlightedId = null
        }
    }

    Column(modifier.imePadding()) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(rows, key = { it.entry.key }) { row ->
                when (val entry = row.entry) {
                    is TimelineEntry.Message -> ChatBubbleRow(
                        message = entry.message,
                        isMine = entry.message.userId == uid,
                        myUserId = uid,
                        highlighted = highlightedId == entry.message.id,
                        showNickname = row.showNickname,
                        showTime = row.showTime,
                        unreadCount = memberIds.count {
                            it != entry.message.userId && (readCursors[it] ?: 0L) < entry.message.createdAt
                        },
                        onReply = { replyingTo = entry.message },
                        onReact = { emoji -> chat.toggleReaction(entry.message.id, emoji) },
                        onCopy = { copyToClipboard(context, entry.message.text) },
                        onResend = { chat.resend(entry.message) },
                        onQuoteTap = {
                            chat.originalMessageId(entry.message)?.let { targetId ->
                                highlightedId = targetId
                            }
                        },
                    )

                    is TimelineEntry.System -> SystemMessageRow(systemText(context, entry.feed))
                }
            }
        }

        // 원본 메시지 하이라이트 시 스크롤 이동
        LaunchedEffect(highlightedId) {
            val target = highlightedId ?: return@LaunchedEffect
            val idx = entries.indexOfFirst { it.key == "m_$target" }
            if (idx >= 0) listState.animateScrollToItem(idx)
        }

        // 입력 바
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(TteMediumGray.copy(alpha = 0.25f))
            )
            replyingTo?.let { reply ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 10.dp, bottom = 2.dp),
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TteOrange)
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.chat_replyTo, reply.nickname), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TteOrange)
                        Text(reply.text, fontSize = 12.sp, color = TteMediumGray, maxLines = 1)
                    }
                    Icon(
                        Icons.Filled.Cancel,
                        contentDescription = stringResource(R.string.group_cancelReply),
                        tint = TteMediumGray.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { replyingTo = null },
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    maxLines = 4,
                    textStyle = TextStyle(fontSize = 15.sp, color = TteDarkGray),
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(TteFieldBackground)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            if (draft.isEmpty()) Text(stringResource(R.string.chat_placeholder), fontSize = 15.sp, color = TteMediumGray)
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                val canSend = draft.trim().isNotEmpty()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (canSend) TteOrange else TteMediumGray.copy(alpha = 0.4f))
                        .clickable(enabled = canSend) {
                            chat.sendChat(draft, replyingTo)
                            draft = ""
                            replyingTo = null
                        },
                ) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.group_send), tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    // 금칙어 차단 안내 (iOS moderationBlocked 알림)
    if (moderationBlocked) {
        AlertDialog(
            onDismissRequest = { chat.clearModerationBlocked() },
            title = { Text(stringResource(R.string.chat_blocked_title)) },
            text = { Text(stringResource(R.string.chat_blocked_message)) },
            confirmButton = {
                TextButton(onClick = { chat.clearModerationBlocked() }) {
                    Text(stringResource(R.string.common_ok), color = TteOrange)
                }
            },
        )
    }
}

// MARK: - 말풍선 (iOS ChatBubble)
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubbleRow(
    message: ChatMessage,
    isMine: Boolean,
    myUserId: String,
    highlighted: Boolean,
    showNickname: Boolean,  // 같은 발신자 연속 묶음의 첫 메시지에만
    showTime: Boolean,      // 같은 발신자·같은 분(分) 묶음의 마지막 메시지에만
    unreadCount: Int,       // 아직 안 읽은 멤버 수 (발신자 제외, 0이면 숨김)
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onCopy: () -> Unit,
    onResend: () -> Unit,
    onQuoteTap: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }   // 브이로그 첨부 전체화면 재생

    Row(Modifier.fillMaxWidth()) {
        if (isMine) Spacer(Modifier.weight(1f, fill = true))
        Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
            if (!isMine && showNickname) {
                Text(
                    message.nickname,
                    fontSize = 11.sp,
                    color = TteMediumGray,
                    modifier = Modifier.padding(start = 4.dp, bottom = 3.dp),
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                // 전송 실패 시 재전송 버튼 (내 메시지 좌측)
                if (isMine && message.failed) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = stringResource(R.string.chat_resend),
                        tint = Color.Red,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(onClick = onResend),
                    )
                } else if (isMine) {
                    MetaColumn(unreadCount, showTime, message.createdAt, isMine = true)
                }
                Box {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isMine) TteOrange.copy(alpha = if (message.pending) 0.55f else 1f)
                                else TteFieldBackground
                            )
                            .then(
                                if (highlighted) Modifier.border(2.5.dp, TteOrange, RoundedCornerShape(16.dp))
                                else Modifier
                            )
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { showMenu = true },
                            )
                            .padding(horizontal = 13.dp, vertical = 9.dp),
                    ) {
                        // 인용된 원본 (답장인 경우) — 탭하면 원본으로 이동
                        if (message.hasReply) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isMine) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.05f))
                                    .clickable(onClick = onQuoteTap)
                                    .padding(horizontal = 9.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    message.replyToNickname ?: "",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isMine) Color.White.copy(alpha = 0.9f) else TteOrange,
                                )
                                Text(
                                    message.replyToText ?: "",
                                    fontSize = 12.sp,
                                    color = if (isMine) Color.White.copy(alpha = 0.75f) else TteMediumGray,
                                    maxLines = 2,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        if (message.kind == "vlog") {
                            VlogAttachment(message, isMine) { showPlayer = true }
                        } else {
                            Text(
                                message.text,
                                fontSize = 15.sp,
                                color = if (isMine) Color.White else TteDarkGray,
                            )
                        }
                    }

                    // 길게 눌러 반응/답장/복사 (iOS contextMenu 대응)
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            quickEmojis.forEach { emoji ->
                                Text(
                                    emoji,
                                    fontSize = 22.sp,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            showMenu = false
                                            onReact(emoji)
                                        }
                                        .padding(4.dp),
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_reply)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                            onClick = { showMenu = false; onReply() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_copy)) },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                            onClick = { showMenu = false; onCopy() },
                        )
                    }
                }
                if (!isMine) MetaColumn(unreadCount, showTime, message.createdAt, isMine = false)
            }

            // 브이로그 첨부 전체화면 재생
            if (showPlayer) {
                message.attachmentUrl?.let { url ->
                    VlogChatPlayerDialog(url) { showPlayer = false }
                }
            }

            // 이모지 반응 칩
            val chips = message.reactionChips(myUserId)
            if (chips.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 3.dp),
                ) {
                    chips.forEach { (emoji, count, mine) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (mine) TteOrange.copy(alpha = 0.15f) else TteFieldBackground)
                                .then(
                                    if (mine) Modifier.border(1.dp, TteOrange.copy(alpha = 0.5f), CircleShape)
                                    else Modifier
                                )
                                .clickable { onReact(emoji) }
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        ) {
                            Text(emoji, fontSize = 12.sp)
                            Text(
                                "$count",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (mine) TteOrange else TteMediumGray,
                            )
                        }
                    }
                }
            }
        }
        if (!isMine) Spacer(Modifier.weight(1f, fill = true))
    }
}

@Composable
private fun TimeLabel(millis: Long) {
    Text(timeText(millis), fontSize = 10.sp, color = TteMediumGray.copy(alpha = 0.7f))
}

// 브이로그 첨부 (서버 자동 공유, kind == "vlog") — 썸네일 탭 → 전체화면 재생.
// 완성본은 서버 보존 기간(7일) 이후 만료된다 — 썸네일 로드 실패 시 플레이스홀더 표시.
@Composable
private fun VlogAttachment(message: ChatMessage, isMine: Boolean, onPlay: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(168.dp)
                .height(224.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.15f))
                .clickable(onClick = onPlay),
        ) {
            if (message.thumbUrl != null) {
                coil3.compose.AsyncImage(
                    model = message.thumbUrl,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Icon(
                    Icons.Filled.Movie,
                    contentDescription = null,
                    tint = if (isMine) Color.White.copy(alpha = 0.7f) else TteMediumGray,
                    modifier = Modifier.size(30.dp),
                )
            }
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(46.dp),
            )
        }
        Text(
            message.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (isMine) Color.White else TteDarkGray,
        )
    }
}

// 채팅방에서 공유된 브이로그 전체화면 재생 (iOS VlogChatPlayerView 대응)
@Composable
private fun VlogChatPlayerDialog(url: String, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        setVideoURI(android.net.Uri.parse(url))
                        setMediaController(android.widget.MediaController(ctx).also { it.setAnchorView(this) })
                        setOnPreparedListener { it.start() }
                        // 만료(7일 경과)·네트워크 오류 — 크래시 대신 시스템 오류 다이얼로그 억제
                        setOnErrorListener { _, _, _ -> true }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Icon(
                Icons.Filled.Cancel,
                contentDescription = stringResource(R.string.common_cancel),
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(30.dp)
                    .clickable(onClick = onDismiss),
            )
        }
    }
}

// 말풍선 바깥 메타 — 카톡처럼 안읽음 숫자(시그니처 컬러)를 시간 위에 세로로 쌓는다
@Composable
private fun MetaColumn(unreadCount: Int, showTime: Boolean, millis: Long, isMine: Boolean) {
    Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
        if (unreadCount > 0) {
            Text("$unreadCount", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TteOrange)
        }
        if (showTime) TimeLabel(millis)
    }
}

// MARK: - 시스템(활동) 메시지 (iOS SystemMessageRow)
@Composable
private fun SystemMessageRow(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text,
            fontSize = 12.sp,
            color = TteMediumGray,
            modifier = Modifier
                .clip(CircleShape)
                .background(TteFieldBackground.copy(alpha = 0.7f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

// MARK: - Helpers

private fun systemText(context: android.content.Context, item: FeedItem): String = when (item.type) {
    FeedType.TRIP_START -> LocaleManager.string(context, R.string.feed_tripStart, item.nickname, item.courseName)
    FeedType.TRIP_END -> LocaleManager.string(context, R.string.feed_tripEnd, item.nickname, item.courseName)
    FeedType.ARRIVAL -> LocaleManager.string(context, R.string.feed_arrival, item.nickname, item.placeName ?: "")
    FeedType.PHOTO -> LocaleManager.string(context, R.string.feed_photo, item.nickname)
    FeedType.FREE_TRIP_START -> LocaleManager.string(context, R.string.feed_freeTripStart, item.nickname)
    FeedType.FREE_CAPTURE -> LocaleManager.string(context, R.string.feed_freeCapture, item.nickname, item.placeName ?: LocaleManager.string(context, R.string.feed_here))
    FeedType.FREE_TRIP_END -> LocaleManager.string(context, R.string.feed_freeTripEnd, item.nickname, item.courseName)
}

private fun timeText(millis: Long): String =
    // 앱 선택 언어의 오전/오후 표기를 따른다 (영어 AM/PM·일본어 午前/午後)
    SimpleDateFormat("a h:mm", Locale(LocaleManager.current().code)).format(Date(millis))

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("chat", text))
}
