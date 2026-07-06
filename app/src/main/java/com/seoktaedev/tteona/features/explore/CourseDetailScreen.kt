package com.seoktaedev.tteona.features.explore

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.model.AppUser
import com.seoktaedev.tteona.core.services.CourseService
import com.seoktaedev.tteona.core.util.Haptics
import com.seoktaedev.tteona.core.model.Course
import com.seoktaedev.tteona.core.model.Place
import com.seoktaedev.tteona.core.model.RouteInfo
import com.seoktaedev.tteona.core.model.WeatherInfo
import com.seoktaedev.tteona.core.services.PlacesPhotoService
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.launch

/**
 * 코스 상세 — iOS Features/Explore/ExploreDetailView.swift의 이식본.
 * TODO: 동선 미니 지도는 MAPS_API_KEY 설정 후 Google Maps Compose로 교체.
 * TODO: "이 코스 따라가기"는 RoomService/ActiveSession(그룹·실시간 위치 공유) 이식 후 연결 — 현재는 안내만 표시.
 */
@Composable
fun CourseDetailScreen(
    course: Course,
    thumbnailUrl: String?,
    onClose: () -> Unit,
    onStartCourse: ((Set<String>) -> Unit)? = null,
    viewModel: CourseDetailViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val likedIds by viewModel.likedCourseIds.collectAsState()
    val isLiked = course.courseId in likedIds
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    var showRoomSelect by remember { androidx.compose.runtime.mutableStateOf(false) }
    var showOverflowMenu by remember { androidx.compose.runtime.mutableStateOf(false) }
    var showReportDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    var showBlockConfirm by remember { androidx.compose.runtime.mutableStateOf(false) }
    var showDeleteConfirm by remember { androidx.compose.runtime.mutableStateOf(false) }
    var infoAlert by remember { androidx.compose.runtime.mutableStateOf<Pair<String, String>?>(null) }

    BackHandler(onBack = onClose)
    LaunchedEffect(course.courseId) { viewModel.load(course) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(bottom = 110.dp)) {
                item { HeaderImage(course, thumbnailUrl) }
                item {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        TitleBlock(course, state.author, isLiked) {
                            Haptics.light(view)
                            viewModel.toggleLike(course.courseId)
                        }
                        WeatherCard(state.weather)
                        TransportSection(state)
                        PlacesBlock(course)
                    }
                }
            }

            // 닫기 버튼
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 16.dp, top = 52.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(onClick = onClose),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "닫기", tint = Color.White)
            }

            // 우상단: 공유 + 더보기 (iOS ToolbarItem topBarTrailing)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = 52.dp),
            ) {
                val context = LocalContext.current
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable {
                            // iOS CourseShareHelper와 동일한 공유 URL
                            val url = android.net.Uri.Builder()
                                .scheme("https").authority("tteona.kr").path("/course")
                                .appendQueryParameter("id", course.courseId)
                                .appendQueryParameter("name", course.courseName)
                                .appendQueryParameter("places", course.places.size.toString())
                                .build().toString()
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, url)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "코스 공유"))
                        },
                ) {
                    Icon(Icons.Filled.Share, contentDescription = "공유", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Box {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable { showOverflowMenu = true },
                    ) {
                        Icon(Icons.Filled.MoreHoriz, contentDescription = "더보기", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                        if (course.authorId == AuthService.currentUser.value?.uid) {
                            DropdownMenuItem(
                                text = { Text("코스 삭제", color = Color.Red) },
                                onClick = { showOverflowMenu = false; showDeleteConfirm = true },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("코스 신고하기", color = Color.Red) },
                                onClick = { showOverflowMenu = false; showReportDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text("작성자 차단하기") },
                                onClick = { showOverflowMenu = false; showBlockConfirm = true },
                            )
                        }
                    }
                }
            }

            // 하단 시작 버튼 (iOS startButton)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                Button(
                    onClick = {
                        Haptics.light(view)
                        if (onStartCourse != null) showRoomSelect = true
                        else scope.launch { snackbarHostState.showSnackbar("그룹 여행 시작 기능은 준비 중이에요.") }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TteOrange),
                ) {
                    Text("이 코스 따라가기", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 신고/차단/삭제 다이얼로그 (iOS confirmationDialog·alert 대응)
            if (showReportDialog) {
                com.seoktaedev.tteona.features.common.ReportReasonDialog(
                    onSelect = { reason ->
                        val uid = AuthService.currentUser.value?.uid ?: return@ReportReasonDialog
                        scope.launch {
                            runCatching {
                                com.seoktaedev.tteona.core.services.ReportService.reportContent(
                                    reporterId = uid,
                                    targetType = "course",
                                    targetId = course.courseId,
                                    targetAuthorId = course.authorId,
                                    reason = reason,
                                )
                            }.onSuccess {
                                infoAlert = "신고 완료" to "신고가 정상 접수되었습니다. 24시간 이내에 검토 및 삭제 처리됩니다."
                            }.onFailure {
                                infoAlert = "오류" to "신고 접수에 실패했어요. 잠시 후 다시 시도해주세요."
                            }
                        }
                    },
                    onDismiss = { showReportDialog = false },
                )
            }
            if (showBlockConfirm) {
                com.seoktaedev.tteona.features.common.DestructiveConfirmDialog(
                    title = "작성자 차단",
                    message = "이 작성자를 차단하시겠어요? 차단하시면 이 작성자가 등록한 모든 코스와 후기가 숨겨집니다.",
                    confirmLabel = "차단",
                    onConfirm = {
                        val uid = AuthService.currentUser.value?.uid ?: return@DestructiveConfirmDialog
                        scope.launch {
                            runCatching { com.seoktaedev.tteona.core.services.UserService.blockUser(uid, course.authorId) }
                                .onSuccess {
                                    infoAlert = "차단 완료" to "작성자가 차단되었습니다."
                                }
                                .onFailure {
                                    infoAlert = "오류" to "차단에 실패했어요. 잠시 후 다시 시도해주세요."
                                }
                        }
                    },
                    onDismiss = { showBlockConfirm = false },
                )
            }
            if (showDeleteConfirm) {
                com.seoktaedev.tteona.features.common.DestructiveConfirmDialog(
                    title = "코스 삭제",
                    message = "이 코스를 삭제할까요? 되돌릴 수 없어요.",
                    confirmLabel = "삭제",
                    onConfirm = {
                        scope.launch {
                            runCatching { CourseService.deleteCourse(course) }
                                .onSuccess { onClose() }
                                .onFailure { infoAlert = "오류" to "코스 삭제에 실패했어요. 잠시 후 다시 시도해주세요." }
                        }
                    },
                    onDismiss = { showDeleteConfirm = false },
                )
            }
            infoAlert?.let { (title, message) ->
                com.seoktaedev.tteona.features.common.InfoAlert(title, message) { infoAlert = null }
            }

            // 공유할 그룹 선택 → 세션 시작 (iOS RoomSelectView 시트 대응)
            if (showRoomSelect && onStartCourse != null) {
                com.seoktaedev.tteona.features.session.RoomSelectSheet(
                    onConfirm = { roomIds ->
                        showRoomSelect = false
                        onStartCourse(roomIds)
                    },
                    onDismiss = { showRoomSelect = false },
                )
            }
        }
    }
}

@Composable
private fun HeaderImage(course: Course, thumbnailUrl: String?) {
    Box(modifier = Modifier
        .fillMaxWidth()
        .height(300.dp)) {
        if (thumbnailUrl != null) {
            SubcomposeAsyncImage(
                model = thumbnailUrl,
                contentDescription = course.courseName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = { DefaultCourseThumbnail() },
            )
        } else {
            DefaultCourseThumbnail()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
        ) {
            Text(
                "${course.tag.emoji} ${course.tag.label} · ${course.region}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.9f),
            )
            Spacer(Modifier.height(6.dp))
            Text(course.courseName, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun TitleBlock(
    course: Course,
    author: AppUser?,
    isLiked: Boolean,
    onLikeClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(TteOrange.copy(alpha = 0.15f)),
        ) {
            Text(
                (author?.nickname?.takeIf { it.isNotEmpty() } ?: "?").take(1),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TteOrange,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    author?.nickname?.takeIf { it.isNotEmpty() } ?: "여행자",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TteDarkGray,
                )
                if (author?.isVerified == true) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Verified, contentDescription = "인증됨", tint = TteOrange, modifier = Modifier.size(12.dp))
                }
            }
            Text(
                "장소 ${course.places.size}곳 · ♥ ${course.likeCount}",
                fontSize = 13.sp,
                color = TteMediumGray,
            )
        }
        Icon(
            if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = "좋아요",
            tint = TteOrange,
            modifier = Modifier
                .size(26.dp)
                .clickable(onClick = onLikeClick),
        )
    }
}

@Composable
private fun WeatherCard(weather: WeatherInfo?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TteFieldBackground)
            .padding(14.dp),
    ) {
        Text(weather?.emoji ?: "🌡️", fontSize = 22.sp)
        Column {
            Text("현재 날씨 (첫 장소 기준)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TteMediumGray)
            Text(
                weather?.let { "${it.tempC.toInt()}° ${it.description}" } ?: "-",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TteDarkGray,
            )
        }
    }
}

@Composable
private fun TransportSection(state: CourseDetailViewModel.UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("이동 정보", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TteDarkGray)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(TteFieldBackground)
                .padding(vertical = 4.dp),
        ) {
            TransportRow(Icons.Filled.DirectionsCar, "자동차", state.carRoute, state.isLoadingRoute)
            TransportRow(Icons.Filled.DirectionsBus, "대중교통", state.transitRoute, state.isLoadingTransit, "정보 없음")
            TransportRow(Icons.AutoMirrored.Filled.DirectionsWalk, "도보", state.walkRoute, state.isLoadingRoute)
        }
    }
}

@Composable
private fun TransportRow(
    icon: ImageVector,
    label: String,
    route: RouteInfo?,
    loading: Boolean,
    unavailableText: String = "-",
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = label, tint = TteOrange, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TteDarkGray, modifier = Modifier.weight(1f))
        when {
            loading -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = TteOrange)
            route != null -> Text(
                "${route.timeText} · ${route.distanceText}",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TteDarkGray,
            )
            else -> Text(unavailableText, fontSize = 14.sp, color = TteMediumGray)
        }
    }
}

@Composable
private fun PlacesBlock(course: Course) {
    // 장소 탭 → 상세 시트 (iOS CourseDetailView의 PlaceDetailSheet 진입)
    var selectedPlace by remember { androidx.compose.runtime.mutableStateOf<Place?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("코스 동선", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TteDarkGray)

        CourseRouteMap(course)

        course.places.sortedBy { it.order }.forEachIndexed { idx, place ->
            Box(Modifier.clickable { selectedPlace = place }) {
                PlaceCardRow(index = idx, place = place)
            }
        }
    }

    selectedPlace?.let { place ->
        PlaceDetailSheet(place = place, onDismiss = { selectedPlace = null })
    }
}

// iOS PlaceCardRow — 장소 사진(TourAPI) + 순번 뱃지 + 카테고리 캡슐
@Composable
private fun PlaceCardRow(index: Int, place: Place) {
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(place.placeName) {
        photoUrl = PlacesPhotoService.photoUrl(place.placeName, place.latitude, place.longitude)
        category = PlacesPhotoService.placeCategory(place.placeName, place.latitude, place.longitude)
        isLoading = false
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TteFieldBackground)
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TteOrange.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = TteOrange)
                photoUrl != null -> SubcomposeAsyncImage(
                    model = photoUrl,
                    contentDescription = place.placeName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = { PlacePhotoPlaceholder() },
                )
                else -> PlacePhotoPlaceholder()
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(TteOrange),
                ) {
                    Text("${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                category?.let {
                    Text(
                        it,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TteOrange,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(TteOrange.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Text(
                place.placeName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TteDarkGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlacePhotoPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Place, contentDescription = null, tint = TteOrange.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
    }
}
