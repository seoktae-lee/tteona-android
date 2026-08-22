package com.seoktaedev.tteona.features.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteBackground
import com.seoktaedev.tteona.features.discover.DiscoverTabScreen
import com.seoktaedev.tteona.features.capture.CaptureTabScreen
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.model.Course
import com.seoktaedev.tteona.core.model.CourseSessionInfo
import com.seoktaedev.tteona.core.services.ActiveSessionStore
import com.seoktaedev.tteona.core.services.AppNotificationManager
import com.seoktaedev.tteona.core.services.CourseService
import com.seoktaedev.tteona.core.services.CourseThumbnailService
import com.seoktaedev.tteona.core.services.DeepLinkHandler
import com.seoktaedev.tteona.core.services.ImpromptuSessionStore
import com.seoktaedev.tteona.core.services.TteonaMessagingService
import com.seoktaedev.tteona.features.explore.CourseDetailScreen
import com.seoktaedev.tteona.features.explore.ExploreScreen
import com.seoktaedev.tteona.features.group.GroupListScreen
import com.seoktaedev.tteona.features.home.HomeScreen
import com.seoktaedev.tteona.features.session.ActiveSessionScreen
import kotlinx.coroutines.launch

// iOS MainTabView와 동일한 4탭 구성: 촬영 / 발견(지도+목록) / 채팅(그룹) / 프로필
private data class TabItem(val labelRes: Int, val icon: ImageVector)

/**
 * 탭 인덱스를 숫자로 흩어 두면 탭이 하나 늘 때마다 알림 라우팅이 조용히 어긋난다.
 * 여기서만 정의하고 전부 이걸 참조한다.
 */
object Tab {
    const val CAPTURE = 0
    const val DISCOVER = 1
    const val CHAT = 2
    const val PROFILE = 3
}

private val tabs = listOf(
    TabItem(R.string.tab_capture, Icons.Filled.PhotoCamera),
    TabItem(R.string.tab_discover, Icons.Filled.Map),
    TabItem(R.string.tab_chat, Icons.AutoMirrored.Filled.Chat),
    TabItem(R.string.tab_profile, Icons.Filled.AccountCircle),
)

// 코스 상세 표시용 선택 상태 (iOS의 sheet(item:) 대응)
private data class CourseSelection(val course: Course, val thumbnailUrl: String?)

@Composable
fun MainTabScreen(initialTab: Int = 0, previewFootprintDemo: Boolean = false) {
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    var courseSelection by remember { mutableStateOf<CourseSelection?>(null) }
    var sessionInfo by remember { mutableStateOf<CourseSessionInfo?>(null) }
    var impromptuRoomIds by remember { mutableStateOf<Set<String>?>(null) }
    var showImpromptuRoomSelect by remember { mutableStateOf(false) }
    var showCourseResumeSheet by remember { mutableStateOf(false) }
    /** 촬영 중 여부 — 탭바를 숨기는 데 쓴다 */
    var isRecordingClip by remember { mutableStateOf(false) }
    /** 게스트 게이트에서 '회원가입' → 로그인 화면을 전체 화면으로 덮는다 */
    var showAuth by remember { mutableStateOf(false) }
    /** 게스트도 언어·약관에는 닿을 수 있어야 한다 — 프로필 게이트의 설정 통로 */
    var showGuestSettings by remember { mutableStateOf(false) }
    /** 잠긴 클립 길이를 만졌을 때의 업셀 */
    var showPaywall by remember { mutableStateOf(false) }
    /** '나의 오늘'을 마무리 모드로 열었는가 (촬영 탭의 ✓) */
    var impromptuFinishMode by remember { mutableStateOf(false) }
    // 코치마크 스포트라이트용 탭 실측 위치 (기기별 해상도·내비바 높이 대응)
    val tabBounds = remember { mutableStateListOf<androidx.compose.ui.geometry.Rect?>(null, null, null, null) }

    val context = LocalContext.current
    val pendingChatRoom by AppNotificationManager.pendingChatRoom.collectAsState()
    val pendingCourseId by DeepLinkHandler.pendingCourseId.collectAsState()
    val pendingRoomCode by DeepLinkHandler.pendingRoomCode.collectAsState()

    // 알림 권한 요청 (Android 13+) + 채널 생성
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        TteonaMessagingService.ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 채팅 푸시 탭 → 채팅 탭 자동 전환 (iOS pendingChatRoom → selectedTab = 2)
    LaunchedEffect(pendingChatRoom) {
        if (pendingChatRoom != null) selectedTab = Tab.CHAT
    }

    // 좋아요·코스 따라가기 알림 탭 → 코스 상세 (딥링크와 같은 경로)
    val pendingNotifCourseId by AppNotificationManager.pendingCourseId.collectAsState()
    LaunchedEffect(pendingNotifCourseId) {
        val id = pendingNotifCourseId ?: return@LaunchedEffect
        AppNotificationManager.clearPendingCourseId()
        val course = CourseService.fetchCourse(id) ?: return@LaunchedEffect
        val thumb = runCatching { CourseThumbnailService.fetchAllThumbnails()[course.courseId] }.getOrNull()
        courseSelection = CourseSelection(course, thumb)
    }

    // 주간 리포트 알림 탭 → 프로필 탭
    val shouldOpenProfile by AppNotificationManager.shouldOpenProfile.collectAsState()
    LaunchedEffect(shouldOpenProfile) {
        if (shouldOpenProfile) {
            AppNotificationManager.clearShouldOpenProfile()
            selectedTab = Tab.PROFILE
        }
    }

    // 오후 8시 리마인더 알림 탭 → '나의 오늘' 세션 열기 (iOS shouldOpenTodaySession → handleImpromptuTap)
    val shouldOpenTodaySession by AppNotificationManager.shouldOpenTodaySession.collectAsState()
    LaunchedEffect(shouldOpenTodaySession) {
        if (shouldOpenTodaySession) {
            AppNotificationManager.clearShouldOpenTodaySession()
            selectedTab = Tab.CAPTURE
            ImpromptuSessionStore.loadTodaySession()?.let { saved ->
                impromptuRoomIds = saved.roomIds.toSet()
            }
        }
    }

    // Vlog 완성 알림 탭 → 완성본 재생. 완성본은 인증 없는 공개 정적 URL이라 시스템
    // 비디오 플레이어로 연다(안드로이드엔 iOS VlogPreviewView 같은 앱 내 재생 화면이 없다).
    val pendingVlogUrl by AppNotificationManager.pendingVlogUrl.collectAsState()
    LaunchedEffect(pendingVlogUrl) {
        val url = pendingVlogUrl ?: return@LaunchedEffect
        AppNotificationManager.clearPendingVlogUrl()
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(url), "video/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    // 그룹 초대 딥링크 → 채팅 탭(코드 참여) 전환
    LaunchedEffect(pendingRoomCode) {
        if (pendingRoomCode != null) selectedTab = Tab.CHAT
    }

    // 채팅 탭 안읽음 배지 갱신 (iOS refreshUnreadStatus)
    val authUser by com.seoktaedev.tteona.core.auth.AuthService.currentUser.collectAsState()
    val isGuest by com.seoktaedev.tteona.core.auth.AuthService.isGuest.collectAsState()
    val myRooms by com.seoktaedev.tteona.core.services.RoomService.myRooms.collectAsState()
    val unreadRoomIds by com.seoktaedev.tteona.core.services.RoomService.unreadRoomIds.collectAsState()
    // 게스트는 uid가 있어도 계정이 아니다 — Firestore 규칙이 익명을 막으므로 실시간 룸
    // 리스너는 권한 거부만 쌓으며 재시도를 돈다. 진짜 계정일 때만 켠다.
    val accountUid = authUser?.uid?.takeIf { !isGuest }
    LaunchedEffect(accountUid) {
        accountUid?.let { com.seoktaedev.tteona.core.services.RoomService.startListeningMyRooms(it) }
    }
    LaunchedEffect(myRooms, selectedTab) {
        accountUid?.let { com.seoktaedev.tteona.core.services.RoomService.refreshUnreadStatus(it) }
    }

    // 코스 공유 딥링크 → 코스 상세 오픈 (iOS deepLinkedCourse)
    LaunchedEffect(pendingCourseId) {
        val id = pendingCourseId ?: return@LaunchedEffect
        DeepLinkHandler.clearPendingCourse()
        val course = CourseService.fetchCourse(id) ?: return@LaunchedEffect
        val thumb = runCatching { CourseThumbnailService.fetchAllThumbnails()[course.courseId] }.getOrNull()
        courseSelection = CourseSelection(course, thumb)
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                // 촬영 중에는 탭바를 숨긴다 — 뷰파인더를 최대한 넓게 쓰고,
                // 찍는 도중 실수로 탭을 눌러 화면이 바뀌는 것도 막는다.
                if (!isRecordingClip) NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            modifier = Modifier.onGloballyPositioned { tabBounds[index] = it.boundsInRoot() },
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                val label = stringResource(tab.labelRes)
                                if (index == Tab.CHAT && unreadRoomIds.isNotEmpty()) {
                                    BadgedBox(badge = { Badge { Text("${unreadRoomIds.size}") } }) {
                                        Icon(tab.icon, contentDescription = label)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = label)
                                }
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        ) { innerPadding ->
            val modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
            val openResumeImpromptu = {
                ImpromptuSessionStore.loadTodaySession()?.let { saved ->
                    impromptuRoomIds = saved.roomIds.toSet()
                }
                Unit
            }
            when (selectedTab) {
                // 앱의 1번 기능 — 켜자마자 뷰파인더가 보이도록 기본 탭으로 둔다
                Tab.CAPTURE -> CaptureTabScreen(
                    modifier = modifier,
                    onRecordingChanged = { isRecordingClip = it },
                    onFinishToday = { roomIds ->
                        // 마무리는 기존 '나의 오늘' 화면이 그대로 받는다 —
                        // 종료 시트·브이로그 생성이 전부 거기에 있다.
                        // 다만 마무리 모드로 열어 지도·촬영 UI는 그리지 않는다.
                        impromptuRoomIds = roomIds
                        impromptuFinishMode = true
                    },
                    onRequestPaywall = { showPaywall = true },
                )
                // 지도 + 탐색 — 둘 다 "코스를 찾는" 화면이라 한 탭에 묶고 토글로 전환한다
                Tab.DISCOVER -> GuestGated(isGuest, R.drawable.tteoni_travel,
                    R.string.guest_discover_title, R.string.guest_discover_message,
                    onSignUp = { showAuth = true }, modifier = modifier) {
                    DiscoverTabScreen(
                        modifier = modifier,
                        onCourseClick = { course, thumb -> courseSelection = CourseSelection(course, thumb) },
                        onResumeCourse = { showCourseResumeSheet = true },
                        onResumeImpromptu = openResumeImpromptu,
                        onOpenGroups = { selectedTab = Tab.CHAT },
                    )
                }
                Tab.CHAT -> GuestGated(isGuest, R.drawable.tteoni_front,
                    R.string.guest_chat_title, R.string.guest_chat_message,
                    onSignUp = { showAuth = true }, modifier = modifier) {
                    Box(modifier) { GroupListScreen() }
                }
                // 설정으로 가는 유일한 통로가 프로필이라, 게스트도 약관·언어에 닿게 열어 둔다
                Tab.PROFILE -> GuestGated(isGuest, R.drawable.tteoni_thumbsup,
                    R.string.guest_profile_title, R.string.guest_profile_message,
                    onSignUp = { showAuth = true }, onOpenSettings = { showGuestSettings = true },
                    modifier = modifier) {
                    com.seoktaedev.tteona.features.profile.ProfileTabScreen(modifier, previewFootprintDemo = previewFootprintDemo)
                }
            }
        }

        // 게스트 게이트에서 넘어온 가입 화면 — 탭바 위를 전부 덮는다.
        // 가입에 성공하면 AuthService가 isGuest를 내리므로 게이트가 스스로 사라진다.
        if (showAuth) {
            Box(Modifier.fillMaxSize().background(TteBackground)) {
                com.seoktaedev.tteona.features.auth.LoginScreen()
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = TteDarkGray,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = 12.dp, top = 8.dp)
                        .size(28.dp)
                        .clickable { showAuth = false },
                )
            }
            // 게스트가 아니게 되면(가입·로그인 성공) 스스로 닫는다
            LaunchedEffect(isGuest) { if (!isGuest) showAuth = false }
        }

        if (showGuestSettings) {
            Box(Modifier.fillMaxSize().background(TteBackground)) {
                com.seoktaedev.tteona.features.settings.SettingsScreen(
                    onBack = { showGuestSettings = false },
                )
            }
        }

        if (showPaywall) {
            com.seoktaedev.tteona.features.pro.ProPaywallScreen(onDismiss = { showPaywall = false })
        }

        // 코스 상세 — 탭바 위를 전부 덮는 풀스크린 (iOS fullScreenCover 대응)
        courseSelection?.let { selection ->
            CourseDetailScreen(
                course = selection.course,
                thumbnailUrl = selection.thumbnailUrl,
                onClose = { courseSelection = null },
                onStartCourse = { roomIds ->
                    val course = selection.course
                    courseSelection = null
                    sessionInfo = CourseSessionInfo(course = course, roomIds = roomIds)
                },
            )
        }

        // 코스 진행 세션 (iOS ActiveSessionView fullScreenCover)
        sessionInfo?.let { info ->
            ActiveSessionScreen(
                course = info.course,
                roomIds = info.roomIds,
                isResuming = info.isResuming,
                onClose = { sessionInfo = null },
            )
        }

        // 코스 이어하기 시트 (iOS courseResumeSheet) — 이어서 기록 or 세션 삭제 후 새로
        if (showCourseResumeSheet) {
            CourseResumeSheet(
                onResume = {
                    showCourseResumeSheet = false
                    ActiveSessionStore.loadTodaySession()?.let { saved ->
                        sessionInfo = CourseSessionInfo(
                            course = saved.course,
                            roomIds = saved.roomIds.toSet(),
                            isResuming = true,
                        )
                    }
                },
                onStartNew = {
                    showCourseResumeSheet = false
                    ActiveSessionStore.clear()
                },
            )
        }

        // 즉흥 '나의 오늘' — 방 선택 시트 → 세션 (iOS showRoomSelect → ImpromptuSessionView)
        if (showImpromptuRoomSelect) {
            com.seoktaedev.tteona.features.session.RoomSelectSheet(
                onConfirm = { roomIds ->
                    showImpromptuRoomSelect = false
                    impromptuRoomIds = roomIds
                },
                onDismiss = { showImpromptuRoomSelect = false },
            )
        }
        impromptuRoomIds?.let { roomIds ->
            com.seoktaedev.tteona.features.session.ImpromptuSessionScreen(
                selectedRoomIds = roomIds,
                startInFinishMode = impromptuFinishMode,
                onRequestSignUp = {
                    impromptuRoomIds = null
                    impromptuFinishMode = false
                    showAuth = true
                },
                onClose = {
                    impromptuRoomIds = null
                    impromptuFinishMode = false
                },
            )
        }

        // 첫 진입 나루 내비게이션 가이드 — 계정별 1회 (iOS hasSeenNavGuide, 딥링크 진입 시 방해 안 함)
        val tutorialScope = rememberCoroutineScope()
        var showNavGuide by remember { mutableStateOf(false) }
        // 게스트용 안내와 가입 후 안내는 내용이 달라 따로 기억한다.
        // 하나로 두면 게스트로 짧은 안내를 본 사람이 가입해도 전체 안내를 영영 못 본다
        // (link 승계로 uid가 그대로이기 때문).
        val navGuideKey = authUser?.uid?.let {
            if (isGuest) "hasSeenNavGuideGuest_$it" else "hasSeenNavGuide_$it"
        }
        LaunchedEffect(authUser?.uid, isGuest) {
            val uid = authUser?.uid ?: return@LaunchedEffect
            val key = navGuideKey ?: return@LaunchedEffect
            // 딥링크로 진입한 경우엔 코치마크·튜토리얼로 방해하지 않는다
            if (DeepLinkHandler.pendingCourseId.value != null ||
                DeepLinkHandler.pendingRoomCode.value != null
            ) return@LaunchedEffect
            val prefs = context.getSharedPreferences("tteona", android.content.Context.MODE_PRIVATE)
            if (!prefs.getBoolean(key, false)) {
                kotlinx.coroutines.delay(800)
                showNavGuide = true
            } else {
                // 내비 가이드를 이미 본 계정(기존 유저 포함) — 진입 1.2초 후 첫 브이로그 튜토리얼 1회 노출
                kotlinx.coroutines.delay(1200)
                com.seoktaedev.tteona.features.tutorial.VlogTutorial.beginIfNeeded(context, uid)
            }
        }
        if (showNavGuide) {
            NavGuideOverlay(
                tabBounds = { i -> tabBounds.getOrNull(i) },
                onSelectTab = { selectedTab = it },
                showsAccountTabs = !isGuest,
                onFinish = {
                    authUser?.uid?.let { uid ->
                        navGuideKey?.let { key ->
                            context.getSharedPreferences("tteona", android.content.Context.MODE_PRIVATE)
                                .edit().putBoolean(key, true).apply()
                        }
                        // 내비 가이드 종료 0.6초 후 첫 브이로그 튜토리얼 시작 (iOS onFinish 후 0.6s)
                        tutorialScope.launch {
                            kotlinx.coroutines.delay(600)
                            com.seoktaedev.tteona.features.tutorial.VlogTutorial.beginIfNeeded(context, uid)
                        }
                    }
                    showNavGuide = false
                },
            )
        }
    }
}

/**
 * 게스트일 때는 게이트를, 계정일 때는 원래 화면을 그린다.
 *
 * 탭의 자식을 조건에 따라 서로 다른 타입으로 두면 상태가 엉키므로(iOS에서 SwiftUI
 * 갱신 사이클이 깨진 것과 같은 계열의 문제), 분기를 이 한 곳으로 모아 둔다.
 */
@Composable
private fun GuestGated(
    isGuest: Boolean,
    mascotRes: Int,
    titleRes: Int,
    messageRes: Int,
    onSignUp: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (isGuest) {
        com.seoktaedev.tteona.features.auth.GuestGateScreen(
            mascotRes = mascotRes,
            title = stringResource(titleRes),
            message = stringResource(messageRes),
            onSignUp = onSignUp,
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
    } else {
        content()
    }
}
