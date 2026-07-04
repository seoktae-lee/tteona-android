package com.seoktaedev.tteona.features.main

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.seoktaedev.tteona.core.model.Course
import com.seoktaedev.tteona.features.explore.CourseDetailScreen
import com.seoktaedev.tteona.features.explore.ExploreScreen
import com.seoktaedev.tteona.features.home.HomeScreen
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
                0 -> HomeScreen(modifier)
                1 -> ExploreScreen(
                    modifier = modifier,
                    onCourseClick = { course, thumb -> courseSelection = CourseSelection(course, thumb) },
                )
                2 -> SettingsScreen(modifier)
            }
        }

        // 코스 상세 — 탭바 위를 전부 덮는 풀스크린 (iOS fullScreenCover 대응)
        courseSelection?.let { selection ->
            CourseDetailScreen(
                course = selection.course,
                thumbnailUrl = selection.thumbnailUrl,
                onClose = { courseSelection = null },
            )
        }
    }
}
