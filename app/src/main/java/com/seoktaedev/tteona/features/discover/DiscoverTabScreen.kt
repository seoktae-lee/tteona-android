package com.seoktaedev.tteona.features.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.model.Course
import com.seoktaedev.tteona.core.util.Haptics
import com.seoktaedev.tteona.features.explore.ExploreScreen
import com.seoktaedev.tteona.features.home.HomeScreen
import com.seoktaedev.tteona.ui.theme.TteBackground
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteOrange

/**
 * 지도(HomeScreen)와 목록(ExploreScreen)을 한 탭으로 묶는 컨테이너.
 * iOS Features/Discover/DiscoverTabView.swift의 이식본.
 *
 * 둘 다 코스를 "찾는" 화면이라 탭을 나눠 둘 이유가 없었다. 하단 토글로 전환한다.
 *
 * **전환할 때 활성인 쪽만 컴포즈한다.** 둘 다 살려두고 alpha로 감추는 방식은 전환이 빠른
 * 대신 감춘 화면이 터치를 가로채고, 지도(AndroidView)가 보이지 않는 채로 계속 살아 있어
 * 카메라 이동·마커 갱신이 헛돈다. 재조회 비용이 훨씬 싸다.
 */
@Composable
fun DiscoverTabScreen(
    onCourseClick: (Course, String?) -> Unit,
    onResumeCourse: () -> Unit,
    onResumeImpromptu: () -> Unit,
    onOpenGroups: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isMapMode by rememberSaveable { mutableStateOf(true) }
    // 지도 미리보기 카드가 떠 있는 동안은 토글을 접는다. 카드를 보는 사람은 그 코스를
    // 판단하는 중이지 화면을 갈아탈 생각이 없고, 그대로 두면 카드 위에 겹쳐 앉는다.
    var mapCardShown by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        if (isMapMode) {
            HomeScreen(
                modifier = Modifier.fillMaxSize(),
                onCourseClick = onCourseClick,
                onResumeCourse = onResumeCourse,
                onResumeImpromptu = onResumeImpromptu,
                showImpromptuCta = false,
                onPreviewCardVisibilityChanged = { mapCardShown = it },
            )
        } else {
            ExploreScreen(
                modifier = Modifier.fillMaxSize(),
                onCourseClick = onCourseClick,
                onOpenGroups = onOpenGroups,
            )
        }

        AnimatedVisibility(
            visible = !isMapMode || !mapCardShown,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        ) {
            ModeToggle(
                isMapMode = isMapMode,
                onSelect = { isMapMode = it },
            )
        }
    }
}

@Composable
private fun ModeToggle(isMapMode: Boolean, onSelect: (Boolean) -> Unit) {
    val view = LocalView.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(TteBackground.copy(alpha = 0.96f))
            .padding(3.dp),
    ) {
        Segment(
            selected = isMapMode,
            icon = Icons.Filled.Map,
            label = stringResource(R.string.discover_mode_map),
        ) {
            if (!isMapMode) { Haptics.light(view); onSelect(true) }
        }
        Segment(
            selected = !isMapMode,
            icon = Icons.Filled.GridView,
            label = stringResource(R.string.discover_mode_grid),
        ) {
            if (isMapMode) { Haptics.light(view); onSelect(false) }
        }
    }
}

@Composable
private fun Segment(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(28.dp)
            .clip(CircleShape)
            .background(if (selected) TteOrange else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) Color.White else TteDarkGray,
            modifier = Modifier.size(13.dp),
        )
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else TteDarkGray,
        )
    }
}
