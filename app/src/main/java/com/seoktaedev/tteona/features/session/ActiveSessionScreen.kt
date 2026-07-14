package com.seoktaedev.tteona.features.session

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.i18n.LocaleManager
import com.seoktaedev.tteona.core.util.Haptics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.model.Course
import com.seoktaedev.tteona.core.model.FeedType
import com.seoktaedev.tteona.core.model.Place
import com.seoktaedev.tteona.core.services.ActiveSessionStore
import com.seoktaedev.tteona.core.services.LocationService
import com.seoktaedev.tteona.core.services.LocationSocketService
import com.seoktaedev.tteona.core.services.PushService
import com.seoktaedev.tteona.core.services.RoomService
import com.seoktaedev.tteona.core.services.SavedActiveSession
import com.seoktaedev.tteona.core.services.SessionForegroundService
import com.seoktaedev.tteona.core.services.StatsService
import com.seoktaedev.tteona.core.services.UserService
import com.seoktaedev.tteona.core.model.StatsEvent
import com.seoktaedev.tteona.ui.theme.BadgeNumberTextStyle
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import androidx.compose.foundation.border
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 코스 진행 세션 — iOS Features/ActiveSession/ActiveSessionView.swift의 이식본.
 * 위치 추적/도착 감지/그룹 실시간 위치 공유/피드 기록/당일 이어하기/장소 촬영·Vlog 생성.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(
    course: Course,
    roomIds: Set<String>,
    isResuming: Boolean = false,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val uid = AuthService.currentUser.value?.uid ?: ""
    val nickname = UserService.currentUser.value?.nickname?.takeIf { it.isNotEmpty() } ?: LocaleManager.string(context, R.string.session_member)

    // 위치 권한 없이 isMyLocationEnabled=true를 주면 지도가 SecurityException으로 크래시하므로 게이팅
    val hasLocationPermission = remember {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val locationService = remember { LocationService(context) }
    val currentLocation by locationService.currentLocation.collectAsState()
    val arrivedPlace by locationService.arrivedAtPlace.collectAsState()
    val memberLocations by LocationSocketService.memberLocations.collectAsState()

    var orderedPlaces by remember { mutableStateOf(course.places.sortedBy { it.order }) }
    var visitedPlaces by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var skippedPlaces by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var currentPlaceIndex by remember { mutableIntStateOf(0) }
    var showArrivalBanner by remember { mutableStateOf(false) }
    var showResumeSheet by remember { mutableStateOf(false) }
    var showPlaceEditor by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var showVlog by remember { mutableStateOf(false) }
    var showFarCaptureConfirm by remember { mutableStateOf(false) }
    var farCaptureDistance by remember { mutableStateOf<Float?>(null) }
    var showBudgetAlert by remember { mutableStateOf(false) }
    var showPaywall by remember { mutableStateOf(false) }
    var ratingPlace by remember { mutableStateOf<Place?>(null) }
    var didStart by remember { mutableStateOf(false) }

    // 세션 누적 촬영 길이(초) — 진행률 바용. 촬영·카메라 복귀 시 클립 파일 기준 재집계 (iOS recordedSeconds)
    var recordedSeconds by remember { mutableStateOf(0.0) }
    LaunchedEffect(visitedPlaces, showCamera) {
        recordedSeconds = com.seoktaedev.tteona.core.services.VlogClips.totalSeconds(context, course.courseId)
    }

    val currentPlace = orderedPlaces.getOrNull(currentPlaceIndex)
    val allVisited = orderedPlaces.all { it.order in visitedPlaces || it.order in skippedPlaces }

    fun saveSession() {
        ActiveSessionStore.save(
            SavedActiveSession(
                date = System.currentTimeMillis(),
                course = course,
                orderedPlaces = orderedPlaces,
                visitedPlaceOrders = visitedPlaces.toList(),
                skippedPlaceOrders = skippedPlaces.toList(),
                currentPlaceIndex = currentPlaceIndex,
                roomIds = roomIds.toList(),
            )
        )
    }

    fun moveToNextPending() {
        val next = orderedPlaces.indices.firstOrNull { idx ->
            val o = orderedPlaces[idx].order
            o !in visitedPlaces && o !in skippedPlaces
        }
        if (next != null) currentPlaceIndex = next
    }

    // 드래그 재정렬 확정 — 배열 순서 기준으로 order 재번호 + 방문/건너뜀 집합을 새 order로 재매핑 (iOS onMove)
    fun commitReorder() {
        val orderMap = orderedPlaces.mapIndexed { i, p -> p.order to i + 1 }.toMap()
        orderedPlaces = orderedPlaces.mapIndexed { i, p -> p.copy(order = i + 1) }
        visitedPlaces = visitedPlaces.mapNotNull { orderMap[it] }.toSet()
        skippedPlaces = skippedPlaces.mapNotNull { orderMap[it] }.toSet()
        moveToNextPending()
        saveSession()
    }

    fun startNewSession() {
        Haptics.medium(view)
        ActiveSessionStore.clear()
        orderedPlaces = course.places.sortedBy { it.order }
        visitedPlaces = emptySet()
        skippedPlaces = emptySet()
        currentPlaceIndex = 0
        locationService.startTracking(orderedPlaces)
        if (uid.isEmpty()) return
        PushService.requestGroupNotification(
            PushService.GroupNotificationType.COURSE_TRIP_START,
            uid, nickname, roomIds.toList(), courseName = course.courseName,
        )
        if (uid != course.authorId) {
            scope.launch { PushService.notifyCourseFollowed(course.authorId, nickname, course.courseName, course.courseId) }
        }
        if (roomIds.isNotEmpty()) {
            LocationSocketService.connect(roomIds, uid, nickname)
            roomIds.forEach { rid ->
                RoomService.postFeed(rid, FeedType.TRIP_START, uid, nickname, course.courseId, course.courseName)
            }
        }
    }

    // 세션 시작 — 저장된 당일 세션이 있으면 이어하기 분기 (iOS task와 동일)
    LaunchedEffect(Unit) {
        if (didStart) return@LaunchedEffect
        didStart = true
        val saved = ActiveSessionStore.loadTodaySession()
        if (saved != null && saved.course.courseId == course.courseId) {
            orderedPlaces = saved.orderedPlaces
            visitedPlaces = saved.visitedPlaceOrders.toSet()
            skippedPlaces = saved.skippedPlaceOrders.toSet()
            currentPlaceIndex = saved.currentPlaceIndex.coerceIn(0, (saved.orderedPlaces.size - 1).coerceAtLeast(0))
            locationService.startTracking(saved.orderedPlaces)
            if (isResuming) {
                if (roomIds.isNotEmpty() && uid.isNotEmpty()) {
                    LocationSocketService.connect(roomIds, uid, nickname)
                }
            } else {
                showResumeSheet = true
            }
        } else {
            startNewSession()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            locationService.stopTracking()
            if (roomIds.isNotEmpty()) LocationSocketService.disconnect()
            SessionForegroundService.stop(context)
        }
    }

    // 세션 진행 상시 알림 갱신 (iOS Live Activity 대응) — 진행률·다음 장소 표시,
    // location 타입 FGS라 앱이 백그라운드로 가도 위치 추적·소켓 공유가 유지된다
    LaunchedEffect(didStart, visitedPlaces, skippedPlaces, currentPlaceIndex) {
        if (!didStart) return@LaunchedEffect
        val done = orderedPlaces.count { it.order in visitedPlaces || it.order in skippedPlaces }
        val body = if (allVisited) {
            LocaleManager.string(context, R.string.session_allVisited)
        } else {
            LocaleManager.string(context, R.string.session_visitProgress, done, orderedPlaces.size) +
                (currentPlace?.let { LocaleManager.string(context, R.string.session_nextPlaceSuffix, it.placeName) } ?: "")
        }
        SessionForegroundService.start(context, course.courseName, body)
    }

    // 그룹 위치 공유 — 위치 변경 시 소켓 전송 (iOS onChange(currentLocation))
    LaunchedEffect(currentLocation) {
        val loc = currentLocation ?: return@LaunchedEffect
        if (roomIds.isNotEmpty()) LocationSocketService.sendLocation(loc.latitude, loc.longitude)
    }

    // 도착 알림 탭 → 해당 장소로 이동 후 카메라 열기 (iOS pendingPlaceName onChange)
    val pendingPlaceName by com.seoktaedev.tteona.core.services.AppNotificationManager.pendingPlaceName.collectAsState()
    LaunchedEffect(pendingPlaceName) {
        val placeName = pendingPlaceName ?: return@LaunchedEffect
        orderedPlaces.indexOfFirst { it.placeName == placeName }.takeIf { it >= 0 }?.let {
            currentPlaceIndex = it
        }
        showCamera = true
        com.seoktaedev.tteona.core.services.AppNotificationManager.clearPendingPlaceName()
    }

    // 도착 감지 배너 + 통계 이벤트
    LaunchedEffect(arrivedPlace) {
        val place = arrivedPlace ?: return@LaunchedEffect
        Haptics.success(view)
        showArrivalBanner = true
        if (uid.isNotEmpty()) StatsService.postEvent(StatsEvent.PLACE_VISITED, uid)
        delay(4000)
        showArrivalBanner = false
        locationService.clearArrival()
    }

    fun completeCurrentPlace() {
        val place = currentPlace ?: return
        Haptics.success(view)
        visitedPlaces = visitedPlaces + place.order
        if (roomIds.isNotEmpty() && uid.isNotEmpty()) {
            val lat = currentLocation?.latitude ?: place.latitude
            val lng = currentLocation?.longitude ?: place.longitude
            roomIds.forEach { rid ->
                RoomService.postFeed(
                    rid, FeedType.FREE_CAPTURE, uid, nickname,
                    course.courseId, course.courseName,
                    placeName = place.placeName, latitude = lat, longitude = lng,
                )
            }
            PushService.requestGroupNotification(
                PushService.GroupNotificationType.VIDEO_RECORDED,
                uid, nickname, roomIds.toList(), placeName = place.placeName,
            )
        }
        moveToNextPending()
        saveSession()
    }

    // 여행 종료 피드/알림만 발행 — 세션 정리는 Vlog 화면을 닫을 때 (iOS postTripEnd)
    fun finishTripFeeds() {
        if (roomIds.isNotEmpty() && uid.isNotEmpty()) {
            roomIds.forEach { rid ->
                RoomService.postFeed(rid, FeedType.TRIP_END, uid, nickname, course.courseId, course.courseName)
            }
        }
        SessionForegroundService.stop(context)
    }

    BackHandler {
        saveSession()
        onClose()
    }

    val cameraPositionState = rememberCameraPositionState {
        val first = orderedPlaces.firstOrNull()
        position = CameraPosition.fromLatLngZoom(
            LatLng(first?.latitude ?: 36.5, first?.longitude ?: 127.8), 13f,
        )
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
            ),
            onMapLoaded = {
                val points = orderedPlaces.map { LatLng(it.latitude, it.longitude) }
                if (points.size >= 2) {
                    val bounds = LatLngBounds.builder().apply { points.forEach { include(it) } }.build()
                    cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                }
            },
        ) {
            val points = orderedPlaces.map { LatLng(it.latitude, it.longitude) }
            if (points.size >= 2) {
                Polyline(points = points, color = TteOrange, width = 8f, pattern = listOf(Dash(24f), Gap(14f)), geodesic = true)
            }
            orderedPlaces.forEachIndexed { idx, place ->
                val visited = place.order in visitedPlaces
                val current = idx == currentPlaceIndex
                MarkerComposable(
                    keys = arrayOf(place.id, visited, current),
                    state = rememberUpdatedMarkerState(position = LatLng(place.latitude, place.longitude)),
                ) {
                    SessionPlacePin(order = idx + 1, isVisited = visited, isCurrent = current)
                }
            }
            // 동행 멤버 위치 (실시간 소켓)
            memberLocations.forEach { member ->
                MarkerComposable(
                    keys = arrayOf("member", member.userId, member.latitude, member.longitude),
                    state = rememberUpdatedMarkerState(position = LatLng(member.latitude, member.longitude)),
                ) {
                    MemberLocationPin(member.nickname)
                }
            }
        }

        // 상단 바
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable {
                        saveSession()
                        onClose()
                    },
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.session_exit), tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    )
                    Text(stringResource(R.string.session_courseInProgress), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                if (roomIds.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(TteOrange.copy(alpha = 0.85f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                        Text(stringResource(R.string.session_sharingLocation), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .size(width = 52.dp, height = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(TteOrange)
                    .clickable { showPlaceEditor = true },
            ) {
                Text("${visitedPlaces.size}/${orderedPlaces.size}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(stringResource(R.string.common_edit), fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }

        // 도착 배너 (iOS ArrivalBanner)
        if (showArrivalBanner) {
            arrivedPlace?.let { place ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 64.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(TteOrange)
                        .clickable {
                            // 도착한 장소로 현재 인덱스를 맞춘 뒤 카메라를 연다 —
                            // 순서상 아직 앞 장소를 가리키고 있으면 엉뚱한 장소로 촬영되던 문제 방지.
                            orderedPlaces.indexOfFirst { it.order == place.order }
                                .takeIf { it >= 0 }?.let { currentPlaceIndex = it }
                            showCamera = true
                        }
                        .padding(16.dp),
                ) {
                    Text("📍", fontSize = 20.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.session_arrivedAt, place.placeName), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text(stringResource(R.string.session_tapToCapture), fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }

        // 하단 패널 (iOS bottomPanel)
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color.White)
                .padding(20.dp),
        ) {
            // 방문 완료 칩
            val visitedList = orderedPlaces.filter { it.order in visitedPlaces }
            if (visitedList.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    visitedList.forEach { place ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(TteFieldBackground)
                                .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(TteOrange),
                            ) {
                                Text("${place.order}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, style = BadgeNumberTextStyle)
                            }
                            Text(place.placeName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TteDarkGray, maxLines = 1)
                        }
                    }
                }
            }

            // 촬영 예산 진행률 바 (iOS budgetBar) — 촬영한 장소가 있을 때만
            if (visitedList.isNotEmpty()) {
                val budgetSeconds = com.seoktaedev.tteona.core.services.ProManager.vlogBudgetSeconds
                val budgetFull = recordedSeconds >= budgetSeconds
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.impromptu_videoBudget),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TteMediumGray,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (com.seoktaedev.tteona.core.services.ProManager.isPro.value) {
                                "${formatMmss(recordedSeconds)} / ${formatMmss(budgetSeconds)}"
                            } else {
                                LocaleManager.string(
                                    context, R.string.impromptu_videoBudgetValue,
                                    recordedSeconds.toInt(), budgetSeconds.toInt(),
                                )
                            },
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (budgetFull) Color.Red else TteOrange,
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(TteMediumGray.copy(alpha = 0.2f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(((recordedSeconds / budgetSeconds).coerceIn(0.0, 1.0)).toFloat())
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (budgetFull) Color.Red else TteOrange),
                        )
                    }
                }
            }

            if (!allVisited && currentPlace != null) {
                val distance = locationService.distanceTo(currentPlace)
                if (distance != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = TteOrange, modifier = Modifier.size(15.dp))
                        Text(
                            stringResource(R.string.session_distanceRemaining, currentPlace.placeName, formatDistance(distance)),
                            fontSize = 14.sp, color = TteDarkGray,
                        )
                    }
                }
                val isNearby = distance != null && distance <= 200f
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isNearby) TteOrange else Color.Gray.copy(alpha = 0.4f))
                        .clickable {
                            // iOS: 도착 전이면 확인 후 촬영 허용 (GPS 부정확·실내 대비)
                            if (isNearby) showCamera = true
                            else {
                                farCaptureDistance = distance
                                showFarCaptureConfirm = true
                            }
                        },
                ) {
                    Text(
                        if (isNearby) stringResource(R.string.session_arrivedCapture, currentPlace.placeName)
                        else stringResource(R.string.session_movingTo, currentPlace.placeName),
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    )
                }
            }

            if (allVisited) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TteOrange)
                        .clickable {
                            // iOS: postTripEnd + Vlog 생성 화면 (완료 후 세션 화면까지 닫힘)
                            finishTripFeeds()
                            showVlog = true
                        },
                ) {
                    Text("🎬 " + stringResource(R.string.session_makeVlog), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }

    // 도착 전 촬영 확인 (iOS showFarCaptureConfirm)
    if (showFarCaptureConfirm) {
        AlertDialog(
            onDismissRequest = { showFarCaptureConfirm = false },
            title = { Text(stringResource(R.string.session_notArrivedYet)) },
            text = {
                Text(
                    farCaptureDistance?.let { d ->
                        stringResource(R.string.session_farCapture_message, currentPlace?.placeName ?: "", formatDistance(d))
                    } ?: stringResource(R.string.session_farCapture_noLocation)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showFarCaptureConfirm = false
                    showCamera = true
                }) { Text(stringResource(R.string.session_captureAnyway), color = TteOrange, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showFarCaptureConfirm = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    // Vlog 생성 (iOS fullScreenCover showVlog) — 재정렬 반영된 코스로 순서 보장
    if (showVlog) {
        val reorderedCourse = course.copy(places = orderedPlaces)
        com.seoktaedev.tteona.features.vlog.VlogGenerationScreen(
            course = reorderedCourse,
            sessionId = course.courseId,
            onDismissToHome = {
                showVlog = false
                ActiveSessionStore.clear()
                onClose()
            },
            // 포맷/BGM 선택·에러에서 닫기 = 세션 화면 복귀 (기록 보존 — 세션 미정리)
            onBack = { showVlog = false },
        )
    }

    // 장소 촬영 (iOS CameraView fullScreenCover) — 저장 성공 시 방문 처리 + 평점 프롬프트
    if (showCamera) {
        currentPlace?.let { place ->
            com.seoktaedev.tteona.features.camera.CameraScreen(
                place = place,
                sessionId = course.courseId,
                onSaved = {
                    showCamera = false
                    val visitedPlace = currentPlace
                    completeCurrentPlace()
                    ratingPlace = visitedPlace
                },
                onClose = { showCamera = false },
                onBudgetExhausted = { showBudgetAlert = true },
            )
        }
    }

    // 촬영 예산 소진 팝업 (iOS VlogLimitPopupView) — 무료 유저는 PRO 페이월로 연결
    if (showBudgetAlert) {
        com.seoktaedev.tteona.features.pro.VlogLimitPopup(
            isPro = com.seoktaedev.tteona.core.services.ProManager.isPro.value,
            onUpgrade = {
                showBudgetAlert = false
                showPaywall = true
            },
            onDismiss = { showBudgetAlert = false },
        )
    }
    if (showPaywall) {
        com.seoktaedev.tteona.features.pro.ProPaywallScreen(onDismiss = { showPaywall = false })
    }

    // 방문 후 평점 프롬프트 (iOS showRatingPrompt 시트)
    ratingPlace?.let { place ->
        if (uid.isNotEmpty()) {
            PlaceRatingPromptSheet(
                place = place,
                userId = uid,
                nickname = nickname,
                onDismiss = { ratingPlace = null },
            )
        }
    }

    // 이어하기 시트 (iOS resumeSheet)
    if (showResumeSheet) {
        ModalBottomSheet(onDismissRequest = { }) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(TteOrange.copy(alpha = 0.12f)),
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = TteOrange, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.session_courseRemaining_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TteDarkGray)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.session_courseRemaining_progress, course.courseName, visitedPlaces.size, orderedPlaces.size),
                    fontSize = 14.sp, color = TteMediumGray,
                )
                Spacer(Modifier.height(32.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(TteOrange)
                        .clickable {
                            showResumeSheet = false
                            locationService.startTracking(orderedPlaces)
                            if (roomIds.isNotEmpty() && uid.isNotEmpty()) {
                                LocationSocketService.connect(roomIds, uid, nickname)
                            }
                        },
                ) {
                    Text(stringResource(R.string.session_resume), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(TteFieldBackground)
                        .clickable {
                            showResumeSheet = false
                            startNewSession()
                        },
                ) {
                    Text(stringResource(R.string.main_startFresh), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TteDarkGray)
                }
            }
        }
    }

    // 코스 편집 시트 — 건너뛰기/취소 + 드래그 재정렬 (iOS PlaceEditorSheet)
    if (showPlaceEditor) {
        ModalBottomSheet(onDismissRequest = {
            showPlaceEditor = false
            saveSession()
        }) {
            // 드래그 재정렬 상태 — 드래그 중에는 리스트 순서만 바꾸고(order·id 불변 → key 안정),
            // 손을 떼는 순간 commitReorder()로 order 재번호·집합 재매핑을 확정한다 (iOS .onMove 대응)
            var draggingId by remember { mutableStateOf<String?>(null) }
            var dragOffset by remember { mutableFloatStateOf(0f) }
            val rowHeights = remember { mutableStateMapOf<String, Float>() }

            Column(Modifier.padding(bottom = 32.dp)) {
                Text(
                    stringResource(R.string.session_editCourse),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    color = TteDarkGray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                orderedPlaces.forEachIndexed { index, place ->
                    key(place.id) {
                    val isVisited = place.order in visitedPlaces
                    val isSkipped = place.order in skippedPlaces
                    val isCurrent = index == currentPlaceIndex && !isVisited && !isSkipped
                    val isDragging = draggingId == place.id
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                            .onSizeChanged { rowHeights[place.id] = it.height.toFloat() }
                            .background(
                                when {
                                    isDragging -> TteFieldBackground
                                    isCurrent -> TteOrange.copy(alpha = 0.06f)
                                    else -> Color.Transparent
                                }
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isVisited -> Color(0xFF34A853)
                                        isSkipped -> Color.Gray.copy(alpha = 0.3f)
                                        isCurrent -> TteOrange
                                        else -> Color.Gray.copy(alpha = 0.25f)
                                    }
                                ),
                        ) {
                            when {
                                isVisited -> Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                isSkipped -> Icon(Icons.Filled.SkipNext, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                                else -> Text("${index + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isCurrent) Color.White else TteDarkGray, style = BadgeNumberTextStyle)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                place.placeName,
                                fontSize = 15.sp,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isVisited || isSkipped) TteMediumGray else TteDarkGray,
                                textDecoration = if (isVisited || isSkipped) TextDecoration.LineThrough else null,
                            )
                            if (isCurrent) Text(stringResource(R.string.session_currentDestination), fontSize = 11.sp, color = TteOrange)
                            else if (isSkipped) Text(stringResource(R.string.session_skipped), fontSize = 11.sp, color = TteMediumGray)
                        }
                        if (!isVisited) {
                            Text(
                                if (isSkipped) stringResource(R.string.common_cancel) else stringResource(R.string.common_skip),
                                fontSize = 12.sp,
                                color = if (isSkipped) TteOrange else TteMediumGray,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSkipped) Color.Transparent else TteFieldBackground)
                                    .then(
                                        if (isSkipped) Modifier.border(1.dp, TteOrange, CircleShape) else Modifier
                                    )
                                    .clickable {
                                        skippedPlaces =
                                            if (isSkipped) skippedPlaces - place.order
                                            else skippedPlaces + place.order
                                        moveToNextPending()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                        // 드래그 핸들 — 위/아래로 끌어 방문 순서 변경 (iOS 편집 모드 재정렬 핸들)
                        Icon(
                            Icons.Filled.DragIndicator,
                            contentDescription = stringResource(R.string.session_editCourse),
                            tint = TteMediumGray,
                            modifier = Modifier
                                .size(22.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingId = place.id
                                            dragOffset = 0f
                                        },
                                        onDragEnd = {
                                            draggingId = null
                                            dragOffset = 0f
                                            commitReorder()
                                        },
                                        onDragCancel = {
                                            draggingId = null
                                            dragOffset = 0f
                                            commitReorder()
                                        },
                                    ) { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount.y
                                        val id = draggingId ?: return@detectDragGestures
                                        // 이웃 행 높이의 절반을 넘어가면 자리 교환 — 교환한 만큼
                                        // 오프셋을 되돌려 손가락 위치와 행 위치를 계속 맞춘다
                                        while (true) {
                                            val idx = orderedPlaces.indexOfFirst { it.id == id }
                                            if (idx < 0) break
                                            val neighbor = when {
                                                dragOffset > 0 -> orderedPlaces.getOrNull(idx + 1)
                                                dragOffset < 0 -> orderedPlaces.getOrNull(idx - 1)
                                                else -> null
                                            } ?: break
                                            val h = rowHeights[neighbor.id] ?: break
                                            if (kotlin.math.abs(dragOffset) <= h / 2) break
                                            val list = orderedPlaces.toMutableList()
                                            val to = if (dragOffset > 0) idx + 1 else idx - 1
                                            list[idx] = list[to].also { list[to] = list[idx] }
                                            orderedPlaces = list
                                            dragOffset += if (dragOffset > 0) -h else h
                                        }
                                    }
                                },
                        )
                    }
                    }
                }
            }
        }
    }
}

// MARK: - 세션 장소 핀 (iOS SessionPlacePin)
@Composable
private fun SessionPlacePin(order: Int, isVisited: Boolean, isCurrent: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                when {
                    isVisited -> Color(0xFF34A853)
                    isCurrent -> TteOrange
                    else -> Color.Gray.copy(alpha = 0.6f)
                }
            ),
    ) {
        if (isVisited) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        } else {
            Text("$order", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, style = BadgeNumberTextStyle)
        }
    }
}

// MARK: - 동행 멤버 핀 (iOS MemberLocationPin)
@Composable
private fun MemberLocationPin(nickname: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF8E44AD)),
        ) {
            Text(nickname.take(1), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Text(
            nickname,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0xFF8E44AD).copy(alpha = 0.85f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun formatDistance(meters: Float): String =
    if (meters < 1000) "${meters.toInt()}m" else String.format("%.1fkm", meters / 1000)
