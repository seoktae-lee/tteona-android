package com.seoktaedev.tteona.features.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
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

// iOS MainTabView와 동일한 4탭 구성: 홈(지도) / 탐색 / 채팅(그룹) / 프로필
private data class TabItem(val labelRes: Int, val icon: ImageVector)

private val tabs = listOf(
    TabItem(R.string.tab_home, Icons.Filled.Map),
    TabItem(R.string.tab_explore, Icons.Filled.GridView),
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
        if (pendingChatRoom != null) selectedTab = 2
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
            selectedTab = 3
        }
    }

    // 오후 8시 리마인더 알림 탭 → '나의 오늘' 세션 열기 (iOS shouldOpenTodaySession → handleImpromptuTap)
    val shouldOpenTodaySession by AppNotificationManager.shouldOpenTodaySession.collectAsState()
    LaunchedEffect(shouldOpenTodaySession) {
        if (shouldOpenTodaySession) {
            AppNotificationManager.clearShouldOpenTodaySession()
            selectedTab = 0
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
        if (pendingRoomCode != null) selectedTab = 2
    }

    // 채팅 탭 안읽음 배지 갱신 (iOS refreshUnreadStatus)
    val authUser by com.seoktaedev.tteona.core.auth.AuthService.currentUser.collectAsState()
    val myRooms by com.seoktaedev.tteona.core.services.RoomService.myRooms.collectAsState()
    val unreadRoomIds by com.seoktaedev.tteona.core.services.RoomService.unreadRoomIds.collectAsState()
    LaunchedEffect(authUser?.uid) {
        authUser?.uid?.let { com.seoktaedev.tteona.core.services.RoomService.startListeningMyRooms(it) }
    }
    LaunchedEffect(myRooms, selectedTab) {
        authUser?.uid?.let { com.seoktaedev.tteona.core.services.RoomService.refreshUnreadStatus(it) }
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
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            modifier = Modifier.onGloballyPositioned { tabBounds[index] = it.boundsInRoot() },
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                val label = stringResource(tab.labelRes)
                                if (index == 2 && unreadRoomIds.isNotEmpty()) {
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
            when (selectedTab) {
                0 -> HomeScreen(
                    modifier = modifier,
                    onCourseClick = { course, thumb -> courseSelection = CourseSelection(course, thumb) },
                    onResumeCourse = {
                        // 코스 버튼 → 이어서/새로 시작 시트 (iOS showCourseResumeSheet)
                        showCourseResumeSheet = true
                    },
                    onImpromptuTap = {
                        // iOS handleImpromptuTap — 저장 세션이 있거나 그룹이 없으면 바로 시작, 아니면 방 선택.
                        // 단, 방 목록 첫 스냅샷이 아직 안 왔으면(콜드 스타트 직후) '그룹 없음'으로
                        // 오판하지 않고 시트를 띄운다 — 시트가 myRooms를 구독하므로 도착 즉시 채워진다.
                        val saved = ImpromptuSessionStore.loadTodaySession()
                        val hasSaved = saved?.places?.isNotEmpty() == true
                        val knownNoRooms = com.seoktaedev.tteona.core.services.RoomService.roomsLoaded.value && myRooms.isEmpty()
                        if (hasSaved || knownNoRooms) {
                            impromptuRoomIds = saved?.roomIds?.toSet() ?: emptySet()
                        } else {
                            showImpromptuRoomSelect = true
                        }
                    },
                    onResumeImpromptu = {
                        ImpromptuSessionStore.loadTodaySession()?.let { saved ->
                            impromptuRoomIds = saved.roomIds.toSet()
                        }
                    },
                )
                1 -> ExploreScreen(
                    modifier = modifier,
                    onCourseClick = { course, thumb -> courseSelection = CourseSelection(course, thumb) },
                    onOpenGroups = { selectedTab = 2 },
                )
                2 -> Box(modifier) { GroupListScreen() }
                3 -> com.seoktaedev.tteona.features.profile.ProfileTabScreen(modifier, previewFootprintDemo = previewFootprintDemo)
            }
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
                onClose = { impromptuRoomIds = null },
            )
        }

        // 첫 진입 나루 내비게이션 가이드 — 계정별 1회 (iOS hasSeenNavGuide, 딥링크 진입 시 방해 안 함)
        var showNavGuide by remember { mutableStateOf(false) }
        LaunchedEffect(authUser?.uid) {
            val uid = authUser?.uid ?: return@LaunchedEffect
            val prefs = context.getSharedPreferences("tteona", android.content.Context.MODE_PRIVATE)
            if (!prefs.getBoolean("hasSeenNavGuide_$uid", false) &&
                DeepLinkHandler.pendingCourseId.value == null &&
                DeepLinkHandler.pendingRoomCode.value == null
            ) {
                kotlinx.coroutines.delay(800)
                showNavGuide = true
            }
        }
        if (showNavGuide) {
            NavGuideOverlay(
                tabBounds = { i -> tabBounds.getOrNull(i) },
                onSelectTab = { selectedTab = it },
                onFinish = {
                    authUser?.uid?.let { uid ->
                        context.getSharedPreferences("tteona", android.content.Context.MODE_PRIVATE)
                            .edit().putBoolean("hasSeenNavGuide_$uid", true).apply()
                    }
                    showNavGuide = false
                },
            )
        }
    }
}
