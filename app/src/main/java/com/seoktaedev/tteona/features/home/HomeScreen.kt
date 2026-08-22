package com.seoktaedev.tteona.features.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.SubcomposeAsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.features.tutorial.tutorialGlow
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.model.Course
import com.seoktaedev.tteona.core.model.CourseTag
import com.seoktaedev.tteona.core.model.Place
import com.seoktaedev.tteona.core.services.CourseService
import com.seoktaedev.tteona.core.services.CourseThumbnailService
import com.seoktaedev.tteona.core.services.PlaceSearchService
import com.seoktaedev.tteona.core.services.PlacesPhotoService
import com.seoktaedev.tteona.core.services.UserService
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.log2

/**
 * 홈 탭 — iOS Features/Main/MainView.swift의 이식본.
 * 구글 지도 위에 코스 핀(태그별 이미지)을 띄우고, 검색/필터/미리보기 카드를 제공한다.
 * 세션 기능(나의 오늘·코스 이어하기)은 그룹/실시간 세션 이식 후 연결.
 */
private enum class CourseFilter { ALL, LIKED, MINE }

// MKCoordinateSpan(위도 델타) → 구글맵 zoom 변환 (iOS gmsCamera와 동일식)
private fun zoomFor(latDelta: Double): Float =
    log2(360.0 / latDelta.coerceAtLeast(0.0001)).toFloat().coerceIn(3f, 19f)

private fun pinRes(tag: CourseTag): Int = tag.pinRes

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onCourseClick: (Course, String?) -> Unit = { _, _ -> },
    onResumeCourse: () -> Unit = {},
    onImpromptuTap: () -> Unit = {},
    onResumeImpromptu: () -> Unit = {},
    /**
     * 하단 정중앙 '나의 오늘' CTA를 보일지.
     * 촬영 탭이 생기면서 이 버튼이 할 일을 탭 자체가 하게 됐고, 그 빈 자리는
     * 발견 탭의 지도/목록 토글이 쓴다. 발견 탭에서는 꺼서 겹치지 않게 한다.
     */
    showImpromptuCta: Boolean = true,
    /**
     * 미리보기 카드가 떠 있는지 바깥에 알린다.
     * 발견 탭의 지도/목록 토글이 이 카드와 같은 자리를 쓰므로, 카드가 뜨면 토글을 접어야 한다.
     */
    onPreviewCardVisibilityChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val courses by CourseService.courses.collectAsState()
    val likedIds by CourseService.likedCourseIds.collectAsState()
    val authUser by AuthService.currentUser.collectAsState()

    var isLoadingCourses by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(CourseFilter.ALL) }
    var previewCourse by remember { mutableStateOf<Course?>(null) }
    LaunchedEffect(previewCourse) { onPreviewCardVisibilityChanged(previewCourse != null) }
    var thumbnails by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var locationGranted by remember { mutableStateOf(false) }
    var didMoveToUser by remember { mutableStateOf(false) }
    var showRegionSearch by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        // 최초 카메라: 한반도 전체 (iOS initialCamera와 동일)
        position = CameraPosition.fromLatLngZoom(LatLng(36.5, 127.8), zoomFor(5.0))
    }

    // 사용자 위치로 이동 — 국가 크기에 맞는 줌 (iOS moveToCountry 대응)
    suspend fun moveToUserLocation(animateDelta: Double? = null) {
        val loc = runCatching {
            LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
        }.getOrNull() ?: return
        val delta = animateDelta ?: withContext(Dispatchers.IO) {
            val code = runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context).getFromLocation(loc.latitude, loc.longitude, 1)
                    ?.firstOrNull()?.countryCode
            }.getOrNull() ?: ""
            countrySpan(code)
        }
        didMoveToUser = true
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), zoomFor(delta))
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        locationGranted = grants.values.any { it }
        if (locationGranted && !didMoveToUser) scope.launch { moveToUserLocation() }
    }

    LaunchedEffect(Unit) {
        // 코스 로드
        if (courses.isEmpty()) {
            isLoadingCourses = true
            CourseService.fetchCourses(UserService.currentUser.value?.blockedUserIds ?: emptyList())
            isLoadingCourses = false
        }
        authUser?.uid?.let { CourseService.fetchLikedCourseIds(it) }
        thumbnails = CourseThumbnailService.fetchAllThumbnails()

        // 위치 권한
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED) {
            locationGranted = true
            if (!didMoveToUser) moveToUserLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    val filteredCourses = remember(courses, likedIds, filter, searchText, authUser) {
        val base = when (filter) {
            CourseFilter.ALL -> courses
            CourseFilter.LIKED -> courses.filter { it.courseId in likedIds }
            CourseFilter.MINE -> courses.filter { it.authorId == authUser?.uid }
        }
        val q = searchText.trim().lowercase()
        val results = if (q.isEmpty()) base else base.filter { c ->
            c.courseName.lowercase().contains(q) ||
                c.region.lowercase().contains(q) ||
                c.places.any { it.placeName.lowercase().contains(q) }
        }
        results.sortedByDescending { it.likeCount }
    }

    // 지도가 멈추면 그 지역 코스를 보충한다 — 인기 상위 300에 못 든 동네 코스와
    // 초기 상한(500) 밖으로 밀린 큐레이션 코스가 여기서 채워진다.
    // 이미 훑은 밴드는 CourseService가 건너뛰므로 팬할 때마다 쿼리가 나가지는 않는다.
    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving) return@LaunchedEffect
        val target = cameraPositionState.position.target
        // 전국이 한눈에 들어오는 축척에서는 '주변'이라는 말이 성립하지 않는다
        if (cameraPositionState.position.zoom < 8f) return@LaunchedEffect
        CourseService.fetchCoursesNear(
            latitude = target.latitude,
            longitude = target.longitude,
            blockedUserIds = UserService.currentUser.value?.blockedUserIds ?: emptyList(),
        )
    }

    /**
     * 화면에 그릴 핀을 솎아낸다. **묶어서 숫자로 만들지 않는다.**
     *
     * iOS에서 숫자 클러스터를 먼저 시도했는데 지도가 주황 동그라미로 덮여 더 나빠졌다.
     * "이 근처에 157개"라는 정보는 어디로 갈지 정하는 데 아무 도움이 되지 않는다.
     * 실제 지도 앱들이 하는 대로 간다 — 멀리서는 아무것도 그리지 않고(확대 유도),
     * 가까이서는 화면에 담기는 만큼만 개별 핀으로 보여준다.
     *
     * 남길 것을 고르는 기준은 **화면 중심에서 가까운 순**이다. 사용자가 방금 가져다 놓은
     * 지점이 곧 관심사이므로, 가운데부터 채우는 편이 가장자리 것을 남기는 것보다 낫다.
     */
    val visibleCourses by remember(filteredCourses, searchText) {
        derivedStateOf {
            val pos = cameraPositionState.position
            // 검색 중에는 줌 문턱을 적용하지 않는다 — "이걸 보여달라"고 명시적으로 말한
            // 상황에서 축척을 이유로 아무것도 안 그리면 결과 개수만 있고 핀은 없는 꼴이 된다.
            val searching = searchText.isNotBlank()
            if (!searching && pos.zoom < PIN_MIN_ZOOM) return@derivedStateOf emptyList()
            val c = pos.target
            filteredCourses
                .mapNotNull { course -> course.mainPlace?.let { course to it } }
                .sortedBy { (_, main) ->
                    val dLat = main.latitude - c.latitude
                    // 경도는 위도에 따라 실제 거리가 달라진다 — 대략 보정해 동서로 치우치지 않게
                    val dLng = (main.longitude - c.longitude) * 0.8
                    dLat * dLat + dLng * dLng
                }
                .take(MAX_VISIBLE_PINS)
                .map { it.first }
        }
    }

    /**
     * 코스명 라벨을 보일지 — 줌 + **화면 내 밀도**로 정한다.
     *
     * 줌만으로는 부족하다: 같은 줌이라도 서울 도심은 핀이 몰리고 지방은 흩어져서
     * 줌 임계 하나로는 한쪽이 반드시 깨진다. iOS에서 큐레이션 코스를 넣으며 실제로 겪었다 —
     * 수도권이 라벨로 완전히 덮여 지도가 보이지 않았다.
     * 동네 단위까지 당겨봤다면 밀도를 따지지 않는다(그 줌에서 겹치는 건 사용자가 의도한 것이고,
     * 오히려 이름이 안 보이면 고를 수가 없다).
     */
    val showLabels by remember {
        derivedStateOf {
            val zoom = cameraPositionState.position.zoom
            zoom >= LABEL_ALWAYS_ZOOM ||
                (zoom >= 9f && visibleCourses.size <= LABEL_DENSITY_LIMIT)
        }
    }

    Box(modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = locationGranted),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
            ),
            onMapClick = { previewCourse = null },
        ) {
            visibleCourses.forEach { course ->
                val main = course.mainPlace ?: return@forEach
                key(course.courseId) {
                    MarkerComposable(
                        // showLabels가 바뀌면 마커 비트맵을 다시 그려야 라벨이 나타나거나 사라진다.
                        keys = arrayOf(course.courseId, showLabels),
                        state = rememberUpdatedMarkerState(
                            position = LatLng(main.latitude, main.longitude),
                        ),
                        onClick = {
                            previewCourse = course
                            true
                        },
                    ) {
                        CoursePin(course, showLabel = showLabels)
                    }
                }
            }
        }

        // 상단 검색 + 필터 (iOS topBar) + 검색 제안 카드
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        var searchFocused by remember { mutableStateOf(false) }

        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                // 검색 바
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.97f))
                        .padding(horizontal = 14.dp),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = TteMediumGray, modifier = Modifier.size(17.dp))
                    BasicTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = TteDarkGray),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focusManager.clearFocus()
                            scope.launch { geocodeAndMove(context, searchText, cameraPositionState) }
                        }),
                        decorationBox = { inner ->
                            // placeholder가 두 줄로 줄바꿈되며 아래로 처지던 문제 — iOS처럼
                            // 한 줄 말줄임 + 세로 중앙 정렬로 맞춘다.
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchText.isEmpty()) {
                                    Text(
                                        stringResource(R.string.main_searchPlaceholder),
                                        fontSize = 14.sp,
                                        color = TteMediumGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                inner()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { searchFocused = it.isFocused },
                    )
                    if (searchText.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Cancel,
                            contentDescription = stringResource(R.string.main_clearSearch),
                            tint = TteMediumGray,
                            modifier = Modifier
                                .size(17.dp)
                                .clickable { searchText = "" },
                        )
                    }
                    // 지역 검색 (iOS map.fill 버튼)
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(16.dp)
                            .background(TteMediumGray.copy(alpha = 0.4f))
                    )
                    Icon(
                        Icons.Filled.Map,
                        contentDescription = stringResource(R.string.region_title),
                        tint = TteOrange,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { showRegionSearch = true },
                    )
                }

                // 필터 캡슐 (전체/좋아요/내 코스)
                Row(
                    modifier = Modifier
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.97f)),
                ) {
                    listOf(
                        Triple(Icons.Filled.GridView, CourseFilter.ALL, stringResource(R.string.main_filter_all)),
                        Triple(Icons.Filled.Favorite, CourseFilter.LIKED, stringResource(R.string.main_filter_liked)),
                        Triple(Icons.Filled.Person, CourseFilter.MINE, stringResource(R.string.main_filter_mine)),
                    ).forEach { (icon, f, desc) ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (filter == f) TteOrange else Color.Transparent)
                                .clickable { filter = f },
                        ) {
                            Icon(
                                icon,
                                contentDescription = desc,
                                tint = if (filter == f) Color.White else TteOrange,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }

            // 검색 제안 카드 — "타이핑=코스 필터 / 지도 이동=명시적 선택"으로 이중 역할 분리 (iOS searchSuggestionCard)
            if (searchFocused && searchText.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .shadow(10.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White),
                ) {
                    // 현재 입력으로 필터된 코스 수 — 탭하면 키보드를 내리고 지도에서 확인
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { focusManager.clearFocus() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Icon(Icons.Filled.GridView, contentDescription = null, tint = TteOrange, modifier = Modifier.size(16.dp))
                        Text(
                            stringResource(R.string.main_courseResults, filteredCourses.size),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TteDarkGray,
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 40.dp)
                            .height(1.dp)
                            .background(TteMediumGray.copy(alpha = 0.15f))
                    )
                    // 지역/장소로 지도 이동 — 기존 검색 키 액션의 숨은 동작을 눈에 보이는 선택지로
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                focusManager.clearFocus()
                                scope.launch { geocodeAndMove(context, searchText, cameraPositionState) }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Icon(Icons.Filled.Map, contentDescription = null, tint = TteOrange, modifier = Modifier.size(16.dp))
                        Text(
                            stringResource(R.string.main_goToRegion, searchText.trim()),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TteDarkGray,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // 검색 결과 없음 오버레이 — 뜨오니 마스코트 (iOS emptySearchResultOverlay)
        if (searchText.isNotEmpty() && filteredCourses.isEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .shadow(12.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.95f))
                    .padding(horizontal = 12.dp, vertical = 36.dp),
            ) {
                com.seoktaedev.tteona.ui.components.TteEmptyState(
                    imageRes = R.drawable.tteoni_wink,
                    title = stringResource(R.string.main_noSearchResults),
                    subtitle = stringResource(R.string.main_tryOtherKeyword),
                    imageSize = 100.dp,
                )
            }
        }

        // 코스 로딩 배지
        if (isLoadingCourses) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.main_loadingCourses), fontSize = 13.sp, color = Color.White)
            }
        }

        // 하단 버튼 영역 (미리보기 카드가 없을 때만)
        if (previewCourse == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                // 나의 오늘 — 하단 정중앙 고정 CTA (iOS createCourseButton)
                // 첫 브이로그 튜토리얼 1단계 — 말풍선 안내 + 버튼 글로우/반짝임으로 '나의 오늘'을 누르도록 유도
                val tutorialStep by com.seoktaedev.tteona.features.tutorial.VlogTutorial.step.collectAsState()
                val tutOnMyToday = showImpromptuCta &&
                    tutorialStep == com.seoktaedev.tteona.features.tutorial.VlogTutorial.Step.TAP_MY_TODAY
                if (showImpromptuCta) Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    if (tutOnMyToday) {
                        com.seoktaedev.tteona.features.tutorial.TutorialBubble(
                            text = stringResource(R.string.tutorial_myToday_text),
                        ) { com.seoktaedev.tteona.features.tutorial.VlogTutorial.finish() }
                        Spacer(Modifier.height(8.dp))
                    }
                    Box(contentAlignment = Alignment.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .shadow(12.dp, CircleShape, spotColor = TteOrange)
                                .clip(CircleShape)
                                .background(TteOrange)
                                .clickable(onClick = onImpromptuTap)
                                .tutorialGlow(tutOnMyToday, cornerRadius = 27)
                                .padding(horizontal = 32.dp, vertical = 16.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.main_myToday), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        if (tutOnMyToday) com.seoktaedev.tteona.features.tutorial.TutorialSparkles(Modifier.matchParentSize())
                    }
                }

                // 좌측 — 이어하기 도크 (세로 스택 → 중앙 CTA와 겹침 방지, iOS miniDockButton)
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp),
                ) {
                    val hasImpromptuSession by com.seoktaedev.tteona.core.services.ImpromptuSessionStore.hasTodaySession.collectAsState()
                    if (hasImpromptuSession) {
                        MiniDockButton(
                            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                            label = stringResource(R.string.main_resume),
                            onClick = onResumeImpromptu,
                        )
                    }

                    // 코스 이어하기 (iOS activeSessionStore.hasTodaySession 버튼)
                    val hasTodaySession by com.seoktaedev.tteona.core.services.ActiveSessionStore.hasTodaySession.collectAsState()
                    if (hasTodaySession) {
                        MiniDockButton(
                            icon = Icons.Filled.Map,
                            label = stringResource(R.string.main_course),
                            onClick = onResumeCourse,
                        )
                    }
                }

                // 현재 위치 — 우측
                MiniDockButton(
                    icon = Icons.Filled.MyLocation,
                    label = null,
                    contentDescription = stringResource(R.string.main_moveToCurrentLocation),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp),
                ) {
                    if (locationGranted) {
                        scope.launch { moveToUserLocation(animateDelta = 0.05) }
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    }
                }
            }
        }

        // 핀 탭 미리보기 카드 (iOS CoursePreviewCard)
        previewCourse?.let { course ->
            CoursePreviewCard(
                course = course,
                modifier = Modifier.align(Alignment.BottomCenter),
                onTap = {
                    val c = course
                    previewCourse = null
                    onCourseClick(c, thumbnails[c.courseId])
                },
                onDismiss = { previewCourse = null },
            )
        }

        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))

        // 지역 검색 시트 (iOS RegionSearchView)
        if (showRegionSearch) {
            com.seoktaedev.tteona.features.main.RegionSearchSheet(
                onSelect = { _, lat, lng ->
                    scope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), zoomFor(0.05))
                        )
                    }
                },
                onDismiss = { showRegionSearch = false },
            )
        }
    }
}

// MARK: - 지도 위 48dp 원형 보조 버튼 — 이어하기·현재위치 공용 (iOS miniDockButton)
@Composable
private fun MiniDockButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = label,
    onClick: () -> Unit,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        modifier = modifier
            .size(48.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White)
            .clickable {
                com.seoktaedev.tteona.core.util.Haptics.light(view)
                onClick()
            },
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = TteOrange,
            modifier = Modifier.size(if (label == null) 20.dp else 16.dp),
        )
        if (label != null) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = TteOrange)
        }
    }
}

// iOS performMapSearch 대응 — 카카오 우선(한국 지명 정확) → 없으면 Geocoder 폴백 (PlaceSearchService 내부 처리)
private suspend fun geocodeAndMove(
    context: Context,
    query: String,
    cameraPositionState: com.google.maps.android.compose.CameraPositionState,
) {
    val q = query.trim()
    if (q.isEmpty()) return
    val first = PlaceSearchService.search(context, q).firstOrNull() ?: return
    cameraPositionState.animate(
        CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), zoomFor(0.1))
    )
}

// 국가 코드 → 지도 표시 범위(위도 델타) — iOS countrySpan 그대로
private fun countrySpan(isoCode: String): Double = when (isoCode) {
    "SG", "MC", "LI", "SM", "VA", "MV", "BH", "HK", "MO" -> 0.5
    "KR", "JP", "GB", "DE", "FR", "IT", "ES", "NL", "BE",
    "CH", "AT", "CZ", "SK", "HU", "PT", "SE", "NO", "DK",
    "FI", "PL", "GR", "TH", "VN", "MY", "PH", "NZ", "TW" -> 8.0
    "MX", "SA", "IR", "MN", "ID", "PE", "CO", "ZA", "EG",
    "TR", "NG", "ET", "TZ", "KZ" -> 20.0
    "US", "CN", "RU", "CA", "BR", "AU", "IN", "AR" -> 40.0
    else -> 10.0
}

// MARK: - 코스 핀 (태그별 이미지 + 코스명 라벨)
// showLabel이 false면 핀만 그린다 — 지도를 충분히 확대했을 때만 코스명을 얹는다.
@SuppressLint("UnrememberedMutableState")
@Composable
private fun CoursePin(course: Course, showLabel: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(pinRes(course.tag)),
            contentDescription = course.courseName,
            modifier = Modifier.size(46.dp),
        )
        if (showLabel) {
            // 큐레이션 코스는 **주황 테두리**로 구분한다.
            // 캡슐을 통째로 주황으로 채웠더니 라벨이 전부 강하게 튀어 지도를 덮어버렸다
            // (iOS에서 수도권 182개를 넣고 실제로 확인). 바탕은 흰색으로 두고 테두리만 바꾼다.
            Text(
                course.courseName,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = TteDarkGray,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthLimit()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.85f))
                    .then(
                        if (course.curated) Modifier.border(1.5.dp, TteOrange, RoundedCornerShape(6.dp))
                        else Modifier
                    )
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

private fun Modifier.widthLimit() = this.width(96.dp)

// MARK: - 미리보기 카드
@Composable
private fun CoursePreviewCard(
    course: Course,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .clickable(onClick = onTap),
    ) {
        // 핸들 + 닫기
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 12.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(width = 36.dp, height = 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(TteMediumGray.copy(alpha = 0.3f))
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(TteFieldBackground)
                    .clickable(onClick = onDismiss),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close), tint = TteMediumGray, modifier = Modifier.size(13.dp))
            }
        }

        // 장소 썸네일 가로 스크롤
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            // 연속 중복 장소는 하나로 병합해 표시 (저장 데이터는 원본 유지)
            items(course.displayPlaces, key = { it.id }) { place ->
                PlacePhotoThumbnail(place)
            }
        }

        // 코스 정보
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 18.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    course.courseName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TteDarkGray,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(course.tag.labelRes),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TteOrange,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(TteOrange.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    Text(stringResource(R.string.main_placeCount, course.displayPlaces.size), fontSize = 12.sp, color = TteMediumGray)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = TteMediumGray, modifier = Modifier.size(11.dp))
                        Text("${course.likeCount}", fontSize = 12.sp, color = TteMediumGray)
                    }
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TteOrange, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PlacePhotoThumbnail(place: Place) {
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(place.id) {
        photoUrl = PlacesPhotoService.photoUrl(place.placeName, place.latitude, place.longitude)
        category = PlacesPhotoService.placeCategory(place.placeName, place.latitude, place.longitude)
        isLoading = false
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TteOrange.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLoading -> CircularProgressIndicator(
                        color = TteOrange, modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                    )
                    photoUrl != null -> SubcomposeAsyncImage(
                        model = photoUrl,
                        contentDescription = place.placeName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> Image(
                        painter = painterResource(R.drawable.tteoni_wink),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                    )
                }
            }
            // 순서 배지
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(TteOrange),
            ) {
                Text(
                    "${place.order}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
        Text(
            place.placeName,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = TteDarkGray,
            maxLines = 1,
            modifier = Modifier.width(80.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            category ?: " ",
            fontSize = 10.sp,
            color = TteMediumGray,
            maxLines = 1,
            modifier = Modifier.width(80.dp),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 이 줌보다 멀리 보면 핀을 아예 그리지 않는다.
 * 광역 화면에서 전국의 코스를 뿌려봐야 서로 겹쳐 읽히지 않고, 그 상태의 핀은
 * 누를 대상도 되지 못한다. 네이버·카카오 지도가 그렇듯 확대를 유도한다.
 */
private const val PIN_MIN_ZOOM = 9.5f

/**
 * 한 화면에 그릴 핀의 최대 개수. 넘으면 화면 중심에 가까운 것만 남긴다.
 * (에어비앤비가 화면에 20개 남짓만 띄우는 것과 같은 방식)
 *
 * **라벨 밀도 한계와 같은 값으로 맞춘다.** 핀만 잔뜩 있고 이름이 안 보이면
 * 무엇을 누를지 고를 수 없어 아무 소용이 없다. 상한 안에서는 항상 이름이 보이게 한다.
 */
private const val MAX_VISIBLE_PINS = 20
private const val LABEL_DENSITY_LIMIT = 20

/** 이 줌 이상이면 밀도와 무관하게 라벨을 보여준다(동네 단위로 당겨본 상태) */
private const val LABEL_ALWAYS_ZOOM = 14f
