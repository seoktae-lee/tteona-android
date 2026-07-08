package com.seoktaedev.tteona.features.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.services.ActiveSessionStore
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange

/**
 * 홈 '코스' 이어하기 버튼 탭 시 뜨는 시트 — iOS MainView.courseResumeSheet의 이식본.
 * "이어서 기록하기"는 저장 세션으로 ActiveSessionScreen을 재개, "새로 시작하기"는 세션 삭제.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseResumeSheet(
    onResume: () -> Unit,
    onStartNew: () -> Unit,
) {
    val saved = remember { ActiveSessionStore.loadTodaySession() }

    ModalBottomSheet(
        onDismissRequest = { }, // iOS interactiveDismissDisabled — 버튼으로만 닫기
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(72.dp).clip(CircleShape).background(TteOrange.copy(alpha = 0.12f)),
            ) {
                Icon(Icons.Filled.History, contentDescription = null, tint = TteOrange, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.main_activeCourseBanner), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TteDarkGray)
            saved?.let {
                Text(
                    stringResource(R.string.main_courseProgress, it.course.courseName, it.visitedPlaceOrders.size, it.orderedPlaces.size),
                    fontSize = 14.sp, color = TteMediumGray,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TteOrange)
                    .clickable(onClick = onResume),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.main_continueRecording), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TteFieldBackground)
                    .clickable(onClick = onStartNew),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = TteDarkGray, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.main_startFresh), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TteDarkGray)
            }
        }
    }
}
