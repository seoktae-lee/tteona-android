package com.seoktaedev.tteona.features.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.seoktaedev.tteona.core.model.Course
import com.seoktaedev.tteona.core.model.CourseSessionInfo
import com.seoktaedev.tteona.core.services.ActiveSessionStore
import com.seoktaedev.tteona.core.services.AppNotificationManager
import com.seoktaedev.tteona.core.services.CourseService
import com.seoktaedev.tteona.core.services.CourseThumbnailService
import com.seoktaedev.tteona.core.services.DeepLinkHandler
import com.seoktaedev.tteona.core.services.TteonaMessagingService
import com.seoktaedev.tteona.features.explore.CourseDetailScreen
import com.seoktaedev.tteona.features.explore.ExploreScreen
import com.seoktaedev.tteona.features.group.GroupListScreen
import com.seoktaedev.tteona.features.home.HomeScreen
import com.seoktaedev.tteona.features.session.ActiveSessionScreen
import com.seoktaedev.tteona.features.settings.SettingsScreen

// iOS MainTabView와 동일한 3탭 구성: 홈(지도) / 탐색 / 설정
private data class TabItem(val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem("홈", Icons.Filled.Map),
    TabItem("탐색", Icons.Filled.GridView),
    TabItem("설정", Icons.Filled.Settings),
)

// 코스 상세 표시용 선택 상태 (iOS의 sheet(item:) 대응)
private data class CourseSelection(val course: Course, val thumbnailUrl: String?)

@Composable
fun MainTabScreen() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var courseSelection by remember { mutableStateOf<CourseSelection?>(null) }
    var showGroups by rememberSaveable { mutableStateOf(false) }
    var sessionInfo by remember { mutableStateOf<CourseSessionInfo?>(null) }

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

    // 채팅 푸시 탭 → 그룹 화면 자동 오픈 (iOS pendingChatRoom → showGroups)
    LaunchedEffect(pendingChatRoom) {
        if (pendingChatRoom != null) showGroups = true
    }

    // 그룹 초대 딥링크 → 그룹 화면(코드 참여) 오픈
    LaunchedEffect(pendingRoomCode) {
        if (pendingRoomCode != null) showGroups = true
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
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
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
                        // 저장된 당일 코스 세션 이어하기 (iOS 코스 이어하기 버튼)
                        ActiveSessionStore.loadTodaySession()?.let { saved ->
                            sessionInfo = CourseSessionInfo(
                                course = saved.course,
                                roomIds = saved.roomIds.toSet(),
                                isResuming = true,
                            )
                        }
                    },
                )
                1 -> ExploreScreen(
                    modifier = modifier,
                    onCourseClick = { course, thumb -> courseSelection = CourseSelection(course, thumb) },
                    onOpenGroups = { showGroups = true },
                )
                2 -> SettingsScreen(modifier)
            }
        }

        // 그룹(피드) — iOS FeedTabView 시트 대응 풀스크린
        if (showGroups) {
            GroupListScreen(onClose = { showGroups = false })
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
    }
}
