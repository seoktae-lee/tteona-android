package com.seoktaedev.tteona.features.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import kotlinx.coroutines.tasks.await
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import com.seoktaedev.tteona.core.model.Course
import com.seoktaedev.tteona.core.model.CreatorRank
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange

/**
 * 탐색 탭 — iOS Features/Explore/ExploreGridView.swift의 이식본.
 * 우측 상단 그룹 버튼 → 그룹(피드) 화면 (iOS showGroups 시트 대응).
 */
@Composable
fun ExploreScreen(
    modifier: Modifier = Modifier,
    onCourseClick: (Course, String?) -> Unit = { _, _ -> },
    onOpenGroups: () -> Unit = {},
    viewModel: ExploreViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val courses by viewModel.courses.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    // 위치를 처음 확보하면 위치 기반 추천으로 1회 재조회 (iOS didRefetchWithLocation)
    val exploreContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                exploreContext, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                com.google.android.gms.location.LocationServices
                    .getFusedLocationProviderClient(exploreContext).lastLocation.await()
            }.getOrNull()?.let { loc ->
                viewModel.refetchRecommendationsWithLocation(loc.latitude, loc.longitude)
            }
        }
    }

    val sorted = viewModel.sortedCourses(courses, state)

    Column(modifier = modifier) {
        // 타이틀 바 (iOS navigationTitle inline 대응)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("탐색", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Icon(
                Icons.Filled.Groups,
                contentDescription = "그룹",
                tint = TteOrange,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp)
                    .size(26.dp)
                    .clickable(onClick = onOpenGroups),
            )
        }

        SortChips(current = state.sortMode, onSelect = viewModel::setSortMode)

        when {
            state.isLoading && courses.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TteOrange)
                }
            }
            sorted.isEmpty() -> EmptyState()
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp
                    ),
                ) {
                    if (state.creatorRanking.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            CreatorRankingStrip(state.creatorRanking)
                        }
                    }
                    items(sorted, key = { it.courseId }) { course ->
                        val thumb = state.thumbnails[course.courseId]
                        GridCell(
                            course = course,
                            thumbnailUrl = thumb,
                            onClick = { onCourseClick(course, thumb) },
                        )
                    }
                }
            }
        }
    }
}

// MARK: - 정렬 칩 (iOS sortChips)
@Composable
private fun SortChips(
    current: ExploreViewModel.SortMode,
    onSelect: (ExploreViewModel.SortMode) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        ExploreViewModel.SortMode.entries.forEach { mode ->
            val selected = current == mode
            Text(
                text = mode.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.White else TteMediumGray,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) TteOrange else TteFieldBackground)
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

// MARK: - 크리에이터 랭킹 스트립 (iOS creatorRankingStrip)
@Composable
private fun CreatorRankingStrip(ranking: List<CreatorRank>) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            "이번 주 인기 크리에이터",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
        ) {
            ranking.forEach { creator ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(72.dp),
                ) {
                    Box {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(TteOrange.copy(alpha = 0.15f)),
                        ) {
                            if (creator.profileImageUrl != null) {
                                SubcomposeAsyncImage(
                                    model = creator.profileImageUrl,
                                    contentDescription = creator.nickname,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    error = { RankInitial(creator.nickname) },
                                )
                            } else {
                                RankInitial(creator.nickname)
                            }
                        }
                        // 순위 뱃지
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(if (creator.rank <= 3) TteOrange else TteMediumGray),
                        ) {
                            Text(
                                "${creator.rank}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            creator.nickname,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (creator.isVerified) {
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                Icons.Filled.Verified,
                                contentDescription = "인증됨",
                                tint = TteOrange,
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }
                    Text("♥ ${creator.likes}", fontSize = 10.sp, color = TteMediumGray)
                }
            }
        }
    }
}

@Composable
private fun RankInitial(nickname: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            nickname.take(1),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TteOrange,
        )
    }
}

// MARK: - 그리드 셀 (iOS GridCell) — 3:4 세로 카드, 하단 그라디언트 + 코스명/지역·태그
@Composable
private fun GridCell(course: Course, thumbnailUrl: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(14.dp))
            .background(TteFieldBackground)
            .clickable(onClick = onClick),
    ) {
        // 커스텀 썸네일 우선, 실패/부재 시 기본 썸네일
        // TODO: 장소 사진 폴백(PlacesPhotoService)은 해당 서비스 이식 후 연결
        if (thumbnailUrl != null) {
            SubcomposeAsyncImage(
                model = thumbnailUrl,
                contentDescription = course.courseName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = { DefaultCourseThumbnail(compact = true) },
            )
        } else {
            DefaultCourseThumbnail(compact = true)
        }

        // 하단 그라디언트
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(90.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.15f),
                            Color.Black.copy(alpha = 0.8f),
                        )
                    )
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
        ) {
            Text(
                course.courseName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "${course.region} · ${course.tag.label}",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
            )
        }
    }
}

// MARK: - 빈 상태 (iOS emptyState)
@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.GridView,
            contentDescription = null,
            tint = TteOrange.copy(alpha = 0.4f),
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("아직 등록된 코스가 없어요", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
