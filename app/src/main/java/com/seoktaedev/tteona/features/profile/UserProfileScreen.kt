package com.seoktaedev.tteona.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.model.AppUser
import com.seoktaedev.tteona.core.model.Course
import com.seoktaedev.tteona.core.model.FootprintPoint
import com.seoktaedev.tteona.core.model.FootprintSummary
import com.seoktaedev.tteona.core.services.CourseThumbnailService
import com.seoktaedev.tteona.core.services.FootprintService
import com.seoktaedev.tteona.core.services.PlacesPhotoService
import com.seoktaedev.tteona.core.services.UserService
import com.seoktaedev.tteona.features.explore.CourseDetailScreen
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.launch

// iOS Features/Profile/UserProfileView.swift의 Kotlin 이식본.

/**
 * 다른 유저 프로필 — 검색에서 진입하는 타인의 공간: 발자취 지도 + 올린 코스.
 * 여행 기록 타임라인(코스명·날짜)은 사생활이라 본인 프로필에서만 보여준다.
 */
@Composable
fun UserProfileScreen(
    user: AppUser,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authUser by AuthService.currentUser.collectAsState()

    var summary by remember { mutableStateOf(FootprintSummary()) }
    var routes by remember { mutableStateOf<List<List<FootprintPoint>>>(emptyList()) }
    var courses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var thumbnails by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoaded by remember { mutableStateOf(false) }
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showBlockAlert by remember { mutableStateOf(false) }
    var isBlocked by remember { mutableStateOf(false) }

    LaunchedEffect(user.uid) {
        summary = FootprintService.fetchSummary(user.uid)
        routes = FootprintService.fetchFootprints(user.uid).map { it.points }
        courses = FootprintService.fetchCourses(user.uid)
        thumbnails = runCatching { CourseThumbnailService.fetchAllThumbnails() }.getOrDefault(emptyMap())
        isLoaded = true
    }

    // 상대의 발자취가 있는 곳을 처음부터 보여준다 (한국 우선)
    val initialFocus = remember(summary) {
        when {
            summary.sigCodes.isNotEmpty() -> FootprintMapFocus.Korea
            summary.countryCodes.any { it != "KOR" } ->
                FootprintMapFocus.Country(summary.countryCodes.first { it != "KOR" })
            summary.countryCodes.contains("KOR") -> FootprintMapFocus.Korea
            else -> FootprintMapFocus.World
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // 타이틀 바 (뒤로 + 닉네임 + 메뉴)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = TteDarkGray)
                }
                Text(
                    user.nickname,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TteDarkGray,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null, tint = TteDarkGray)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profile_block), color = Color.Red) },
                            onClick = {
                                showMenu = false
                                showBlockAlert = true
                            },
                        )
                    }
                }
            }

            // 헤더
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProfileAvatar(
                    nickname = user.nickname,
                    imageUrl = user.profileImageUrl,
                    size = 84.dp,
                    fontSize = 32.sp,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(user.nickname, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = TteDarkGray)
                        if (user.isVerified) {
                            Icon(
                                Icons.Filled.Verified, contentDescription = null,
                                tint = TteOrange, modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    user.creatorLabel?.takeIf { it.isNotEmpty() }?.let { label ->
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TteOrange)
                    }
                    if (isBlocked) {
                        Text(
                            stringResource(R.string.profile_blocked),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Red,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Red.copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                // 발자취 요약 배지
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SummaryBadge(summary.sigCodes.size, stringResource(R.string.profile_stats_regions))
                    SummaryBadge(summary.countryCodes.size, stringResource(R.string.profile_stats_countries))
                    SummaryBadge(courses.size, stringResource(R.string.profile_stats_courses))
                }
            }

            Spacer(Modifier.height(24.dp))

            // 발자취 지도
            Text(
                stringResource(R.string.profile_footprint_of, user.nickname),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TteDarkGray,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            FootprintMapView(
                summary = summary,
                routes = routes,
                interactive = true,
                panZoom = false,   // 페이지 스크롤과 충돌 방지 — 탭만
                initialFocus = initialFocus,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(320.dp)
                    .clip(RoundedCornerShape(24.dp)),
            )

            Spacer(Modifier.height(24.dp))

            // 올린 코스
            Text(
                stringResource(R.string.profile_courses),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TteDarkGray,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            when {
                !isLoaded -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 30.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = TteOrange, modifier = Modifier.size(28.dp)) }
                courses.isEmpty() -> Text(
                    stringResource(R.string.profile_courses_empty),
                    fontSize = 13.sp,
                    color = TteMediumGray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 30.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                else -> {
                    // 2열 그리드 — 스크롤 중첩을 피하려고 chunked Row 사용
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    ) {
                        courses.chunked(2).forEach { rowCourses ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowCourses.forEach { course ->
                                    Box(Modifier.weight(1f)) {
                                        ProfileCourseCard(
                                            course = course,
                                            thumbnailUrl = thumbnails[course.courseId],
                                            onClick = { selectedCourse = course },
                                        )
                                    }
                                }
                                if (rowCourses.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }

        // 코스 상세 풀스크린
        selectedCourse?.let { course ->
            CourseDetailScreen(
                course = course,
                thumbnailUrl = thumbnails[course.courseId],
                onClose = { selectedCourse = null },
                onStartCourse = { selectedCourse = null },
            )
        }
    }

    if (showBlockAlert) {
        AlertDialog(
            onDismissRequest = { showBlockAlert = false },
            title = { Text(stringResource(R.string.profile_block)) },
            text = { Text(stringResource(R.string.profile_block_confirm, user.nickname)) },
            confirmButton = {
                TextButton(onClick = {
                    showBlockAlert = false
                    val myUid = authUser?.uid ?: return@TextButton
                    scope.launch {
                        runCatching { UserService.blockUser(myUid, user.uid) }
                        isBlocked = true
                    }
                }) { Text(stringResource(R.string.profile_block), color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showBlockAlert = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun SummaryBadge(count: Int, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(TteFieldBackground)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text("$count", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TteDarkGray)
        Text(label, fontSize = 12.sp, color = TteMediumGray)
    }
}

/** 프로필 코스 카드 (3:4, 탐색탭 카드 축소판) — iOS ProfileCourseCard */
@Composable
private fun ProfileCourseCard(
    course: Course,
    thumbnailUrl: String?,
    onClick: () -> Unit,
) {
    var placePhotoUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(course.courseId) {
        if (thumbnailUrl == null && placePhotoUrl == null) {
            course.mainPlace?.let { main ->
                placePhotoUrl = PlacesPhotoService.photoUrl(main.placeName, main.latitude, main.longitude)
            }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(14.dp))
            .background(TteFieldBackground)
            .clickable(onClick = onClick),
    ) {
        val url = thumbnailUrl ?: placePhotoUrl
        if (url != null) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = course.courseName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // 하단 그라디언트 + 코스 정보
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.8f))
                    )
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                course.courseName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${course.region} · ${stringResource(course.tag.labelRes)}",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.Favorite, contentDescription = null,
                    tint = Color.White.copy(alpha = 0.95f), modifier = Modifier.size(9.dp),
                )
                Spacer(Modifier.size(2.dp))
                Text("${course.likeCount}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}
