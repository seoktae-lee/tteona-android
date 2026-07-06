package com.seoktaedev.tteona.features.session

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.model.Course
import com.seoktaedev.tteona.core.model.CourseTag
import com.seoktaedev.tteona.core.model.FeedType
import com.seoktaedev.tteona.core.model.Place
import com.seoktaedev.tteona.core.services.CourseService
import com.seoktaedev.tteona.core.services.ImpromptuSessionStore
import com.seoktaedev.tteona.core.services.PushService
import com.seoktaedev.tteona.core.services.RoomService
import com.seoktaedev.tteona.core.services.SessionForegroundService
import com.seoktaedev.tteona.core.services.UserService
import com.seoktaedev.tteona.core.services.VlogClips
import com.seoktaedev.tteona.core.util.Haptics
import com.seoktaedev.tteona.ui.theme.Pretendard
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 즉흥 '나의 오늘' 세션 — iOS Features/ActiveSession/ImpromptuSessionView.swift의 이식본.
 * 코스 없이 자유롭게 이동하며 장소를 골라 촬영 → 종료 시 Vlog 생성/코스 저장.
 */
@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImpromptuSessionScreen(
    selectedRoomIds: Set<String>,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val uid = AuthService.currentUser.value?.uid ?: ""
    val nickname = UserService.currentUser.value?.nickname?.takeIf { it.isNotEmpty() } ?: "멤버"
    val sessionId = "free_$uid"

    var capturedPlaces by remember { mutableStateOf<List<Place>>(emptyList()) }
    var activeRoomIds by remember { mutableStateOf(selectedRoomIds) }
    var pendingPlace by remember { mutableStateOf<Place?>(null) }
    var resolvedLatLng by remember { mutableStateOf<LatLng?>(null) }
    var isResolvingLocation by remember { mutableStateOf(false) }
    var showPlacePicker by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var showEndSheet by remember { mutableStateOf(false) }
    var showSaveCourse by remember { mutableStateOf(false) }
    var showVlog by remember { mutableStateOf(false) }
    var generatedCourse by remember { mutableStateOf<Course?>(null) }
    var courseSavedToFirestore by remember { mutableStateOf(false) }
    var showResumeSheet by remember { mutableStateOf(false) }
    var showIntegrityAlert by remember { mutableStateOf(false) }
    var showBudgetAlert by remember { mutableStateOf(false) }
    var showPaywall by remember { mutableStateOf(false) }
    var didStart by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(36.5, 127.8), 6f)
    }

    fun reorderPlaces() {
        capturedPlaces = capturedPlaces.mapIndexed { i, p -> p.copy(order = i + 1) }
    }

    fun postEndFeed(roomIds: List<String>, count: Int) {
        if (roomIds.isEmpty() || uid.isEmpty()) return
        PushService.requestGroupNotification(
            PushService.GroupNotificationType.FREE_TRIP_END,
            uid, nickname, roomIds, courseName = "${count}곳 방문",
        )
        roomIds.forEach { rid ->
            RoomService.postFeed(rid, FeedType.FREE_TRIP_END, uid, nickname, "free", "${count}곳 방문")
        }
    }

    fun startNewSession() {
        // 세션 폴더(free_{uid})는 유저당 고정 — 새 세션은 깨끗한 폴더로 시작 (촬영 예산 보호)
        VlogClips.deleteAll(context, sessionId)
        capturedPlaces = emptyList()
        activeRoomIds = selectedRoomIds
        if (uid.isEmpty()) return
        activeRoomIds.forEach { rid ->
            RoomService.postFeed(rid, FeedType.FREE_TRIP_START, uid, nickname, "free", "나의 오늘")
        }
    }

    fun resumeSession(saved: com.seoktaedev.tteona.core.services.SavedImpromptuSession) {
        // 클립 파일이 실제로 남아있는 장소만 복원 (iOS resumeSession 무결성 검증)
        val validated = saved.places.filter { VlogClips.clipFile(context, it, sessionId).exists() }
        if (validated.size < saved.places.size) showIntegrityAlert = true
        capturedPlaces = validated
        reorderPlaces()
        if (saved.roomIds.isNotEmpty()) activeRoomIds = saved.roomIds.toSet()
    }

    fun buildCourseAndEnd(saveToFirestore: Boolean, courseName: String, tag: CourseTag) {
        val name = courseName.trim().ifEmpty {
            "나의 오늘 " + SimpleDateFormat("M월 d일", Locale.KOREA).format(Date())
        }
        val region = capturedPlaces.firstOrNull()?.let { String.format(Locale.US, "%.1f°N", it.latitude) } ?: "기타"
        val course = Course(
            courseId = UUID.randomUUID().toString(),
            authorId = uid,
            courseName = name,
            tag = tag,
            region = region,
            likeCount = 0,
            createdAt = System.currentTimeMillis(),
            places = capturedPlaces,
        )
        generatedCourse = course
        courseSavedToFirestore = saveToFirestore
        ImpromptuSessionStore.clear()
        if (saveToFirestore) scope.launch { runCatching { CourseService.saveCourse(course) } }
        postEndFeed(activeRoomIds.toList(), capturedPlaces.size)
    }

    // 세션 시작 — 저장된 당일 세션이 있으면 이어하기 분기 (iOS task)
    LaunchedEffect(Unit) {
        if (didStart) return@LaunchedEffect
        didStart = true
        val saved = ImpromptuSessionStore.loadTodaySession()
        if (saved != null && saved.places.isNotEmpty()) {
            showResumeSheet = true
        } else {
            ImpromptuSessionStore.clear()
            startNewSession()
        }
        // 내 위치로 카메라 이동
        runCatching {
            LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
        }.getOrNull()?.let { loc ->
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15f)
            )
        }
    }

    // 상시 알림 (iOS Live Activity — TodaySessionActivityManager)
    LaunchedEffect(capturedPlaces) {
        SessionForegroundService.start(
            context, "나의 오늘 기록 중",
            if (capturedPlaces.isEmpty()) "첫 장소에서 촬영을 시작해보세요"
            else "${capturedPlaces.size}곳 기록 · 마지막: ${capturedPlaces.last().placeName}",
        )
    }
    DisposableEffect(Unit) {
        onDispose { SessionForegroundService.stop(context) }
    }

    fun startCapture() {
        if (isResolvingLocation) return
        isResolvingLocation = true
        scope.launch {
            try {
                val loc = runCatching {
                    LocationServices.getFusedLocationProviderClient(context)
                        .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                }.getOrNull() ?: runCatching {
                    LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
                }.getOrNull()
                if (loc != null) {
                    resolvedLatLng = LatLng(loc.latitude, loc.longitude)
                    showPlacePicker = true
                }
            } finally {
                isResolvingLocation = false
            }
        }
    }

    fun handleCameraSaved() {
        val place = pendingPlace ?: return
        Haptics.success(view)
        capturedPlaces = capturedPlaces + place
        reorderPlaces()
        ImpromptuSessionStore.save(capturedPlaces, activeRoomIds.toList())
        if (uid.isNotEmpty()) {
            activeRoomIds.forEach { rid ->
                RoomService.postFeed(
                    rid, FeedType.FREE_CAPTURE, uid, nickname, "free", "나의 오늘",
                    placeName = place.placeName, latitude = place.latitude, longitude = place.longitude,
                )
            }
            if (activeRoomIds.isNotEmpty()) {
                PushService.requestGroupNotification(
                    PushService.GroupNotificationType.VIDEO_RECORDED,
                    uid, nickname, activeRoomIds.toList(), placeName = place.placeName,
                )
            }
        }
        pendingPlace = null
    }

    fun removePlace(place: Place) {
        Haptics.light(view)
        pendingPlace = null
        VlogClips.deleteClip(context, place, sessionId)
        capturedPlaces = capturedPlaces.filter { it.order != place.order }
        reorderPlaces()
        if (capturedPlaces.isEmpty()) ImpromptuSessionStore.clear()
        else ImpromptuSessionStore.save(capturedPlaces)
    }

    BackHandler { onClose() }

    Box(Modifier.fillMaxSize()) {
        // 지도
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = true),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false),
        ) {
            capturedPlaces.forEach { place ->
                MarkerComposable(
                    keys = arrayOf("free_${place.order}_${place.placeName}"),
                    state = rememberUpdatedMarkerState(position = LatLng(place.latitude, place.longitude)),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(TteOrange),
                    ) {
                        Text("${place.order}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            if (capturedPlaces.size >= 2) {
                Polyline(
                    points = capturedPlaces.map { LatLng(it.latitude, it.longitude) },
                    color = TteOrange,
                    width = 6f,
                )
            }
        }

        // 상단 바 (iOS topBar)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .fillMaxWidth(),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(onClick = onClose),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "닫기", tint = Color.White, modifier = Modifier.size(18.dp))
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
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color.Red))
                    Text("나의 오늘 기록 중", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                if (activeRoomIds.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(TteOrange.copy(alpha = 0.85f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                        Text("그룹 위치 공유 중", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(TteOrange),
            ) {
                Text("${capturedPlaces.size}곳", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        // 하단 패널 (iOS bottomPanel)
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding()
                .padding(20.dp),
        ) {
            if (capturedPlaces.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    capturedPlaces.forEach { place ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(TteFieldBackground)
                                .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(20.dp).clip(CircleShape).background(TteOrange),
                            ) {
                                Text("${place.order}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Text(place.placeName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TteDarkGray, maxLines = 1)
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "삭제",
                                tint = TteMediumGray,
                                modifier = Modifier.size(12.dp).clickable { removePlace(place) },
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TteOrange)
                        .clickable(enabled = !isResolvingLocation) { startCapture() },
                ) {
                    Spacer(Modifier.weight(1f))
                    if (isResolvingLocation) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Text("여기서 촬영", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Spacer(Modifier.weight(1f))
                }
                if (capturedPlaces.isNotEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.5.dp, TteOrange, RoundedCornerShape(14.dp))
                            .clickable { showEndSheet = true }
                            .padding(horizontal = 20.dp),
                    ) {
                        Text("오늘 종료", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TteOrange)
                    }
                }
            }
        }
    }

    // 1단계: 장소 선택 (iOS PlacePickerView 시트)
    if (showPlacePicker) {
        resolvedLatLng?.let { loc ->
            PlacePickerSheet(
                latitude = loc.latitude,
                longitude = loc.longitude,
                onSelect = { name ->
                    pendingPlace = Place(
                        order = capturedPlaces.size + 1,
                        placeName = name,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        clipFileName = "${UUID.randomUUID()}.mp4",
                    )
                    showPlacePicker = false
                    showCamera = true
                },
                onDismiss = { showPlacePicker = false },
            )
        }
    }

    // 2단계: 카메라 (iOS fullScreenCover)
    if (showCamera) {
        pendingPlace?.let { place ->
            com.seoktaedev.tteona.features.camera.CameraScreen(
                place = place,
                sessionId = sessionId,
                onSaved = {
                    showCamera = false
                    handleCameraSaved()
                },
                onClose = {
                    showCamera = false
                    pendingPlace = null
                },
                onBudgetExhausted = { showBudgetAlert = true },
            )
        }
    }

    // 촬영 예산 소진 팝업 (iOS VlogLimitPopupView)
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

    // Vlog 생성 (iOS fullScreenCover showVlog)
    if (showVlog) {
        generatedCourse?.let { course ->
            com.seoktaedev.tteona.features.vlog.VlogGenerationScreen(
                course = course,
                sessionId = sessionId,
                thumbnailCourseId = if (courseSavedToFirestore) course.courseId else null,
                onDismissToHome = onClose,
            )
        }
    }

    // 이어하기 / 새로 시작 시트 (iOS resumeSheet)
    if (showResumeSheet) {
        val saved = remember { ImpromptuSessionStore.loadTodaySession() }
        ModalBottomSheet(
            onDismissRequest = { },
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
                Text("오늘 기록이 남아있어요", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TteDarkGray)
                saved?.let {
                    Text(
                        "장소 ${it.places.size}곳 · ${SimpleDateFormat("a h:mm", Locale.KOREA).format(Date(it.date))}",
                        fontSize = 14.sp, color = TteMediumGray,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(32.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(TteOrange)
                        .clickable {
                            saved?.let { resumeSession(it) }
                            showResumeSheet = false
                        },
                ) {
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("이어서 기록하기", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(TteFieldBackground)
                        .clickable {
                            VlogClips.deleteAll(context, sessionId)
                            ImpromptuSessionStore.clear()
                            showResumeSheet = false
                            startNewSession()
                        },
                ) {
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = TteDarkGray, modifier = Modifier.size(18.dp))
                    Text("새로 시작하기", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TteDarkGray)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    // 오늘 종료 시트 (iOS endSheet)
    if (showEndSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEndSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp),
            ) {
                Text("오늘을 마칠까요?", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TteDarkGray)
                Text(
                    "방문한 장소 ${capturedPlaces.size}곳이 기록됐어요",
                    fontSize = 14.sp, color = TteMediumGray,
                    modifier = Modifier.padding(top = 6.dp, bottom = 28.dp),
                )

                // Vlog 만들기 — 메인 액션
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(TteOrange)
                        .clickable {
                            buildCourseAndEnd(saveToFirestore = false, courseName = "", tag = CourseTag.FRIENDS)
                            showEndSheet = false
                            showVlog = true
                        }
                        .padding(horizontal = 20.dp),
                ) {
                    Icon(Icons.Filled.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Vlog 만들기", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("영상을 이어 붙여 추억을 만들어요", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                }
                Spacer(Modifier.height(12.dp))

                // 코스로 저장 후 Vlog
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(TteFieldBackground)
                        .clickable {
                            showEndSheet = false
                            showSaveCourse = true
                        }
                        .padding(horizontal = 20.dp),
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = TteDarkGray, modifier = Modifier.size(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("코스로 저장하고 Vlog 만들기", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TteDarkGray)
                        Text("이 경로를 나중에도 사용할 수 있어요", fontSize = 12.sp, color = TteMediumGray)
                    }
                }
                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(Modifier.weight(1f))
                    Text("또는", fontSize = 12.sp, color = TteMediumGray)
                    HorizontalDivider(Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, TteOrange.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .clickable { showEndSheet = false },
                ) {
                    Text("계속 기록할게요", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TteOrange)
                }
            }
        }
    }

    // 코스 저장 시트 (iOS saveCourseSheet)
    if (showSaveCourse) {
        var courseName by remember { mutableStateOf("") }
        var selectedTag by remember { mutableStateOf(CourseTag.FRIENDS) }
        ModalBottomSheet(
            onDismissRequest = { showSaveCourse = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp),
            ) {
                Text(
                    "코스로 저장",
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TteDarkGray,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("코스 이름", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TteMediumGray)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(TteFieldBackground)
                            .padding(14.dp),
                    ) {
                        if (courseName.isEmpty()) {
                            Text("이번 여행의 이름을 지어주세요", fontSize = 17.sp, color = TteMediumGray)
                        }
                        BasicTextField(
                            value = courseName,
                            onValueChange = { courseName = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 17.sp, color = TteDarkGray, fontFamily = Pretendard),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("태그", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TteMediumGray)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CourseTag.entries.forEach { tag ->
                            val selected = selectedTag == tag
                            Text(
                                tag.label,
                                fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                color = if (selected) Color.White else TteDarkGray,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) TteOrange else TteFieldBackground)
                                    .clickable { selectedTag = tag }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                val nameValid = courseName.trim().isNotEmpty()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (nameValid) TteOrange else Color.Gray.copy(alpha = 0.4f))
                        .clickable(enabled = nameValid) {
                            buildCourseAndEnd(saveToFirestore = true, courseName = courseName, tag = selectedTag)
                            showSaveCourse = false
                            showVlog = true
                        },
                ) {
                    Text("저장하고 Vlog 만들기", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }

    // 클립 무결성 안내 (iOS showIntegrityAlert)
    if (showIntegrityAlert) {
        AlertDialog(
            onDismissRequest = { showIntegrityAlert = false },
            title = { Text("일부 영상 확인 불가") },
            text = { Text("일부 장소의 영상 파일이 확인되지 않아 촬영 리스트가 자동으로 정리되었습니다. 해당 장소는 다시 촬영하실 수 있습니다.") },
            confirmButton = {
                TextButton(onClick = { showIntegrityAlert = false }) { Text("확인", color = TteOrange) }
            },
        )
    }
}
