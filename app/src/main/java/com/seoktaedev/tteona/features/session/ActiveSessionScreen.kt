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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.seoktaedev.tteona.core.services.StatsService
import com.seoktaedev.tteona.core.services.UserService
import com.seoktaedev.tteona.core.model.StatsEvent
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
 * 위치 추적/도착 감지/그룹 실시간 위치 공유/피드 기록/당일 이어하기.
 * TODO: 장소 영상 촬영(CameraX)·Vlog 생성(Media3)은 별도 단계 — 현재는 방문 체크로 기록.
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
    val scope = rememberCoroutineScope()
    val uid = AuthService.currentUser.value?.uid ?: ""
    val nickname = UserService.currentUser.value?.nickname?.takeIf { it.isNotEmpty() } ?: "멤버"

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
    var showVisitConfirm by remember { mutableStateOf(false) }
    var didStart by remember { mutableStateOf(false) }

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

    fun startNewSession() {
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
            scope.launch { PushService.notifyCourseFollowed(course.authorId, nickname, course.courseName) }
        }
        if (roomIds.isNotEmpty()) {
            roomIds.firstOrNull()?.let { LocationSocketService.connect(it, uid, nickname) }
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
                    roomIds.firstOrNull()?.let { LocationSocketService.connect(it, uid, nickname) }
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
        }
    }

    // 그룹 위치 공유 — 위치 변경 시 소켓 전송 (iOS onChange(currentLocation))
    LaunchedEffect(currentLocation) {
        val loc = currentLocation ?: return@LaunchedEffect
        if (roomIds.isNotEmpty()) LocationSocketService.sendLocation(loc.latitude, loc.longitude)
    }

    // 도착 감지 배너 + 통계 이벤트
    LaunchedEffect(arrivedPlace) {
        val place = arrivedPlace ?: return@LaunchedEffect
        showArrivalBanner = true
        if (uid.isNotEmpty()) StatsService.postEvent(StatsEvent.PLACE_VISITED, uid)
        delay(4000)
        showArrivalBanner = false
        locationService.clearArrival()
    }

    fun completeCurrentPlace() {
        val place = currentPlace ?: return
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

    fun finishTrip() {
        if (roomIds.isNotEmpty() && uid.isNotEmpty()) {
            roomIds.forEach { rid ->
                RoomService.postFeed(rid, FeedType.TRIP_END, uid, nickname, course.courseId, course.courseName)
            }
        }
        ActiveSessionStore.clear()
        onClose()
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
            properties = MapProperties(isMyLocationEnabled = true),
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "나가기", tint = Color.White, modifier = Modifier.size(20.dp))
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
                    Text("코스 진행 중", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
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
                        Text("그룹 위치 공유 중", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .size(width = 52.dp, height = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(TteOrange)
                    .clickable { showPlaceEditor = true }
                    .padding(top = 5.dp),
            ) {
                Text("${visitedPlaces.size}/${orderedPlaces.size}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("편집", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
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
                        .clickable { showVisitConfirm = true }
                        .padding(16.dp),
                ) {
                    Text("📍", fontSize = 20.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${place.placeName}에 도착했어요!", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("탭하여 방문 기록", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
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
                                Text("${place.order}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Text(place.placeName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TteDarkGray, maxLines = 1)
                        }
                    }
                }
            }

            if (!allVisited && currentPlace != null) {
                val distance = locationService.distanceTo(currentPlace)
                if (distance != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = TteOrange, modifier = Modifier.size(15.dp))
                        Text(
                            "${currentPlace.placeName}까지 ${formatDistance(distance)}",
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
                        .clickable(enabled = isNearby) { showVisitConfirm = true },
                ) {
                    Text(
                        if (isNearby) "📍 ${currentPlace.placeName} 도착! 방문 기록하기"
                        else "📍 ${currentPlace.placeName}으로 이동 중...",
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
                        .clickable { finishTrip() },
                ) {
                    Text("🎉 여행 완료", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }

    // 방문 기록 확인 (iOS 카메라 촬영 자리 — CameraX 단계 전까지 방문 체크로 대체)
    if (showVisitConfirm) {
        AlertDialog(
            onDismissRequest = { showVisitConfirm = false },
            title = { Text(currentPlace?.placeName ?: "") },
            text = { Text("이 장소 방문을 기록할까요?\n그룹에 소식이 공유돼요.") },
            confirmButton = {
                TextButton(onClick = {
                    showVisitConfirm = false
                    completeCurrentPlace()
                }) { Text("방문 기록", color = TteOrange, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showVisitConfirm = false }) { Text("취소") } },
        )
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
                Text("오늘 코스가 남아있어요", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TteDarkGray)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${course.courseName} · ${visitedPlaces.size}/${orderedPlaces.size}곳 완료",
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
                                roomIds.firstOrNull()?.let { LocationSocketService.connect(it, uid, nickname) }
                            }
                        },
                ) {
                    Text("이어서 하기", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
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
                    Text("새로 시작하기", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TteDarkGray)
                }
            }
        }
    }

    // 코스 편집 시트 (건너뛰기/취소 — iOS PlaceEditorSheet의 스킵 기능. 드래그 재정렬은 추후)
    if (showPlaceEditor) {
        ModalBottomSheet(onDismissRequest = {
            showPlaceEditor = false
            saveSession()
        }) {
            Column(Modifier.padding(bottom = 32.dp)) {
                Text(
                    "코스 편집",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    color = TteDarkGray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                orderedPlaces.forEachIndexed { index, place ->
                    val isVisited = place.order in visitedPlaces
                    val isSkipped = place.order in skippedPlaces
                    val isCurrent = index == currentPlaceIndex && !isVisited && !isSkipped
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isCurrent) TteOrange.copy(alpha = 0.06f) else Color.Transparent)
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
                                else -> Text("${index + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isCurrent) Color.White else TteDarkGray)
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
                            if (isCurrent) Text("현재 목적지", fontSize = 11.sp, color = TteOrange)
                            else if (isSkipped) Text("건너뜀", fontSize = 11.sp, color = TteMediumGray)
                        }
                        if (!isVisited) {
                            Text(
                                if (isSkipped) "취소" else "건너뛰기",
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
            Text("$order", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
