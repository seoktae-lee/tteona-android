package com.seoktaedev.tteona.features.settings

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.model.TravelStats
import com.seoktaedev.tteona.core.services.StatsService
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange

/** 내 여행 통계 — iOS Features/Settings/TravelStatsView.swift의 이식본. */
@Composable
fun TravelStatsScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    var stats by remember { mutableStateOf<TravelStats?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val uid = AuthService.currentUser.value?.uid
        if (uid != null) stats = StatsService.fetchMyStats(uid)
        isLoading = false
    }

    Column(modifier.fillMaxSize()) {
        SubScreenTopBar(title = "내 여행 통계", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 헤더 (나루 + 카피)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.tteoni_wink),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                )
                Column {
                    Text("지금까지의 여정", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TteMediumGray)
                    Spacer(Modifier.height(4.dp))
                    Text("떠난 만큼 쌓여요", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TteDarkGray)
                }
            }

            when {
                isLoading -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = TteOrange)
                }
                stats != null -> {
                    val s = stats!!
                    // 2열 통계 카드 그리드 (iOS LazyVGrid 대응)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    ) {
                        StatRow(
                            StatItem(Icons.Filled.Map, "만든 코스", "${s.coursesCreated}개"),
                            StatItem(Icons.Filled.Place, "기록한 장소", "${s.placesInCourses}곳"),
                        )
                        StatRow(
                            StatItem(Icons.Filled.Favorite, "받은 좋아요", "${s.likesReceived}개"),
                            StatItem(Icons.Filled.Group, "함께한 그룹", "${s.groups}개"),
                        )
                        StatRow(
                            StatItem(Icons.AutoMirrored.Filled.DirectionsWalk, "방문한 장소", "${s.placesVisited}곳"),
                            StatItem(Icons.Filled.CalendarMonth, "활동한 날", "${s.activeDays}일"),
                        )
                    }
                    Text(
                        "방문한 장소와 활동한 날은 앱 업데이트 이후의 활동부터 집계돼요.",
                        fontSize = 12.sp,
                        color = TteMediumGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                    )
                }
                else -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp),
                ) {
                    Icon(Icons.Filled.WifiOff, contentDescription = null, tint = TteMediumGray, modifier = Modifier.size(32.dp))
                    Text("통계를 불러오지 못했어요", fontSize = 14.sp, color = TteMediumGray)
                }
            }
        }
    }
}

private data class StatItem(val icon: ImageVector, val title: String, val value: String)

@Composable
private fun StatRow(left: StatItem, right: StatItem) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(left, Modifier.weight(1f))
        StatCard(right, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(item: StatItem, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(TteFieldBackground)
            .padding(16.dp),
    ) {
        Icon(item.icon, contentDescription = null, tint = TteOrange, modifier = Modifier.size(20.dp))
        Text(item.value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TteDarkGray)
        Text(item.title, fontSize = 13.sp, color = TteMediumGray)
    }
}

/** 하위 화면 공용 상단 바 (iOS inline navigation title 대응) */
@Composable
internal fun SubScreenTopBar(title: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
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
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}
