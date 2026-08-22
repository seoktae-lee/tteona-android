package com.seoktaedev.tteona.features.capture

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.i18n.LocaleManager
import com.seoktaedev.tteona.core.model.Place
import com.seoktaedev.tteona.core.services.ImpromptuSessionStore
import com.seoktaedev.tteona.core.services.OneTimeLocation
import com.seoktaedev.tteona.core.services.ProManager
import com.seoktaedev.tteona.core.services.VlogClips
import com.seoktaedev.tteona.core.util.Haptics
import com.seoktaedev.tteona.features.camera.CameraScreen
import com.seoktaedev.tteona.features.session.PlacePickerSheet
import com.seoktaedev.tteona.features.tutorial.VlogTutorial
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/**
 * 앱을 열면 가장 먼저 보이는 촬영 탭. iOS Features/Capture/CaptureTabView.swift의 이식본.
 *
 * 뷰파인더가 상주하고 셔터를 누르면 바로 찍힌다 — 장소·GPS·그룹 선택은 전부 촬영 뒤로 밀었다.
 * 셔터·촬영 예산·권한 안내는 CameraScreen이 이미 갖고 있으므로 그대로 임베드하고,
 * 이 화면은 그 위에 세션 상태 칩과 촬영 후 장소 선택만 얹는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureTabScreen(
    onRecordingChanged: (Boolean) -> Unit,
    onFinishToday: (Set<String>) -> Unit,
    onRequestPaywall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // 저장 경로는 게이팅 신원(currentUser)이 아니라 저장 신원을 쓴다 —
    // 인증 대기 같은 과도기에 경로가 바뀌면 찍어둔 클립을 잃는다.
    val identityUid by AuthService.identityUid.collectAsState()
    val sessionId = "free_$identityUid"

    var places by remember { mutableStateOf<List<Place>>(emptyList()) }
    var usedSeconds by remember { mutableDoubleStateOf(0.0) }
    // 목록에서 클립을 지우면 값을 올려 카메라가 예산을 다시 세게 한다
    var budgetRefreshToken by remember { mutableIntStateOf(0) }
    var isRecording by remember { mutableStateOf(false) }

    // 촬영 시작 전에 정해두는 클립 파일명 — 장소는 촬영이 끝난 뒤에 붙는다
    var clipFileName by remember { mutableStateOf("${UUID.randomUUID()}.mp4") }
    var showPlacePicker by remember { mutableStateOf(false) }
    var showCaptureList by remember { mutableStateOf(false) }
    var resolvedLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var isResolvingLocation by remember { mutableStateOf(false) }
    // 권한 거부는 재시도해도 소용없다 — 실패와 구분해 설정으로 안내한다
    var locationDenied by remember { mutableStateOf(false) }
    var savedToast by remember { mutableStateOf<String?>(null) }

    val budgetSeconds = ProManager.vlogBudgetSeconds
    val tutorialStep by VlogTutorial.step.collectAsState()

    val clipFile = remember(sessionId, clipFileName) {
        File(VlogClips.sessionDir(context, sessionId), clipFileName)
    }

    fun clipDir() = VlogClips.sessionDir(context, sessionId)

    /**
     * 세션 상태를 저장소와 다시 맞춘다.
     * 세션이 비었다면 남은 클립 파일과 촬영 예산까지 함께 되돌린다 — 기록은 없는데 파일만
     * 남으면 예산이 찬 채로 촬영도 삭제도 못 하는 상태에 갇힌다.
     */
    fun syncSessionState() {
        places = ImpromptuSessionStore.loadTodaySession()?.places ?: emptyList()
        // 카메라는 촬영 예산을 스스로 들고 있어서, 세션이 **바깥에서** 비워진 걸 모른다.
        // 그대로 두면 예산이 가득 찬 것으로 알고 셔터가 잠긴 채 남는다 — 다시 세게 한다.
        budgetRefreshToken++
        if (places.isEmpty()) {
            clipDir().deleteRecursively()
            usedSeconds = 0.0
        }
    }

    LaunchedEffect(sessionId) { syncSessionState() }

    var locationJob by remember { mutableStateOf<Job?>(null) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted.values.any { it }
        locationDenied = !ok
        if (ok) {
            locationJob?.cancel()
            locationJob = scope.launch {
                isResolvingLocation = true
                when (val r = OneTimeLocation.request(context)) {
                    is OneTimeLocation.Result.Success ->
                        resolvedLocation = r.location.latitude to r.location.longitude
                    OneTimeLocation.Result.Denied -> { resolvedLocation = null; locationDenied = true }
                    OneTimeLocation.Result.Failed -> resolvedLocation = null
                }
                isResolvingLocation = false
            }
        }
    }

    fun requestLocation() {
        if (isResolvingLocation) return
        // 권한이 아직 없으면 시스템에 묻는다. 시한은 권한이 정해진 뒤에야 켠다 —
        // 팝업이 떠 있는 동안까지 세면 '허용'을 누르자마자 실패로 끝난다.
        if (!OneTimeLocation.hasPermission(context)) {
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
            return
        }
        locationJob?.cancel()
        locationJob = scope.launch {
            isResolvingLocation = true
            locationDenied = false
            when (val r = OneTimeLocation.request(context)) {
                is OneTimeLocation.Result.Success ->
                    resolvedLocation = r.location.latitude to r.location.longitude
                OneTimeLocation.Result.Denied -> { resolvedLocation = null; locationDenied = true }
                OneTimeLocation.Result.Failed -> resolvedLocation = null
            }
            isResolvingLocation = false
        }
    }

    fun appendPlace(name: String, lat: Double, lng: Double) {
        val place = Place(
            order = places.size + 1,
            placeName = name,
            latitude = lat,
            longitude = lng,
            clipFileName = clipFileName,
        )
        places = places + place
        ImpromptuSessionStore.save(places)
        VlogTutorial.advance(VlogTutorial.Step.END_TODAY)   // 첫 장소가 담겼다 → ✓ 를 누르도록
        Haptics.success(view)
        savedToast = LocaleManager.string(context, R.string.capture_saved, name, places.size)
        // 다음 촬영을 위해 새 파일명을 예약한다
        clipFileName = "${UUID.randomUUID()}.mp4"
    }

    /** 목록에서 클립 하나를 지운다 — 예산도 그만큼 돌아온다. */
    fun deleteCapture(place: Place) {
        // 파일을 먼저 지우고 카메라에 재계산을 요청하는 순서라야 남은 예산이 정확하다
        place.clipFileName?.let { File(clipDir(), it).delete() }
        // 번호는 1부터 다시 매긴다 — 중간이 비면 브이로그 순서가 어긋난다
        val remaining = places.filter { it.order != place.order }
            .mapIndexed { i, p -> p.copy(order = i + 1) }
        places = remaining
        if (remaining.isEmpty()) ImpromptuSessionStore.clear() else ImpromptuSessionStore.save(remaining)
        budgetRefreshToken++
        Haptics.light(view)
    }

    /**
     * 오늘 기록을 통째로 버린다. 예산이 찬 채로 아무것도 못 하게 갇혔을 때의 탈출구다 —
     * 하나씩 지우는 것만으로는 빠져나오기 번거로워 이 길을 함께 열어 둔다.
     */
    fun discardAllCaptures() {
        clipDir().deleteRecursively()
        ImpromptuSessionStore.clear()
        places = emptyList()
        usedSeconds = 0.0
        budgetRefreshToken++
        Haptics.warning(view)
    }

    // 기록 완료 토스트 — 2.2초 뒤 스스로 사라진다
    LaunchedEffect(savedToast) {
        if (savedToast != null) {
            delay(2200)
            savedToast = null
        }
    }

    DisposableEffect(Unit) {
        onDispose { onRecordingChanged(false) }
    }

    Box(modifier.fillMaxSize()) {
        CameraScreen(
            sessionId = sessionId,
            clipFile = clipFile,
            embedded = true,
            budgetRefreshToken = budgetRefreshToken,
            onRecordingChanged = {
                isRecording = it
                onRecordingChanged(it)
                // 찍는 동안 위치를 확보해 둔다 — 촬영이 끝났을 땐 이미 준비돼 있게.
                // 다만 **아직 물어본 적이 없다면 여기서 요청하지 않는다** — 첫 촬영 도중에
                // 시스템 팝업이 화면을 덮어버린다. 앱을 처음 쓰는 사람의 가장 중요한 순간이
                // 바로 그 첫 촬영인데, 거기를 대화상자로 끊는 셈이다.
                if (it && OneTimeLocation.hasPermission(context)) requestLocation()
            },
            onUsedSecondsChanged = { usedSeconds = it },
            onRequestPaywall = onRequestPaywall,
            onSaved = {
                showPlacePicker = true
                // 위치가 실제로 필요한 시점 — 처음이라면 여기서 권한을 묻는다.
                // "어디서 찍으셨나요?"가 떠 있는 상태라 왜 필요한지가 보인다.
                requestLocation()
                VlogTutorial.advance(VlogTutorial.Step.PICK_PLACE)
            },
            onClose = {},
        )

        // 세션 상태 칩 — 누르면 '오늘 찍은 곳' 목록. 우상단 ✓는 '오늘 마치기'.
        AnimatedVisibility(
            visible = places.isNotEmpty() && !isRecording,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .width(210.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable {
                        Haptics.light(view)
                        showCaptureList = true
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                val budgetFull = budgetSeconds > 0 && usedSeconds >= budgetSeconds
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (budgetFull) Color.White.copy(alpha = 0.5f) else Color.Red)
                    )
                    Text(
                        LocaleManager.string(
                            context, R.string.capture_progress,
                            places.size, usedSeconds.roundToInt(), budgetSeconds.roundToInt(),
                        ),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp),
                    )
                }
                // 남은 촬영 예산 — 장소 수는 상한이 없어 바로 표현할 수 없지만 예산은 가능하다
                LinearProgressIndicator(
                    progress = {
                        if (budgetSeconds > 0) (usedSeconds / budgetSeconds).coerceIn(0.0, 1.0).toFloat() else 0f
                    },
                    color = if (budgetFull) Color.White else TteOrange,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                )
            }
        }

        // 오늘 마치기 — 우측 상단. 셔터에서 멀리 떨어뜨려 오조작을 막는다
        AnimatedVisibility(
            visible = places.isNotEmpty() && !isRecording,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 16.dp, top = 8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(TteOrange)
                    .clickable {
                        Haptics.light(view)
                        onFinishToday(
                            ImpromptuSessionStore.loadTodaySession()?.roomIds?.toSet() ?: emptySet()
                        )
                    },
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.capture_finish),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // 기록 완료 토스트
        savedToast?.let { toast ->
            if (!isRecording) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 66.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = TteOrange,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        toast,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // 첫 브이로그 안내 — 셔터 위 / ✓ 아래
        if (!isRecording) {
            when (tutorialStep) {
                // 말풍선은 길이 칩보다 **위**에 둔다. 셔터 바로 위 200dp 자리에 놓았더니
                // 칩을 통째로 덮어 "몇 초로 찍을지"를 고를 수 없었다 (에뮬 실측).
                VlogTutorial.Step.CAPTURE_HERE -> TutorialHint(
                    text = stringResource(R.string.tutorial_capture_text),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 290.dp),
                )
                VlogTutorial.Step.END_TODAY -> TutorialHint(
                    text = stringResource(R.string.tutorial_endToday_text),
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 62.dp),
                )
                else -> Unit
            }
        }
    }

    if (showCaptureList) {
        CaptureListSheet(
            places = places,
            sessionId = sessionId,
            usedSeconds = usedSeconds,
            budgetSeconds = budgetSeconds,
            onDelete = ::deleteCapture,
            onDiscardAll = ::discardAllCaptures,
            onDismiss = { showCaptureList = false },
        )
    }

    if (showPlacePicker) {
        val loc = resolvedLocation
        if (loc != null) {
            PlacePickerSheet(
                latitude = loc.first,
                longitude = loc.second,
                onSelect = { name ->
                    appendPlace(name, loc.first, loc.second)
                    showPlacePicker = false
                },
                onDismiss = {
                    // 장소를 정하지 않고 닫으면 고아 클립이 남는다 — 파일째 지운다
                    clipFile.delete()
                    clipFileName = "${UUID.randomUUID()}.mp4"
                    budgetRefreshToken++
                    showPlacePicker = false
                },
            )
        } else {
            ModalBottomSheet(
                onDismissRequest = {
                    clipFile.delete()
                    clipFileName = "${UUID.randomUUID()}.mp4"
                    budgetRefreshToken++
                    showPlacePicker = false
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(32.dp).padding(bottom = 40.dp),
                ) {
                    if (isResolvingLocation) {
                        CircularProgressIndicator(color = TteOrange)
                        Text(
                            stringResource(R.string.impromptu_locating),
                            fontSize = 14.sp,
                            color = TteMediumGray,
                        )
                    } else {
                        Icon(
                            Icons.Filled.LocationOff,
                            contentDescription = null,
                            tint = TteMediumGray,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            stringResource(
                                if (locationDenied) R.string.impromptu_locationDenied
                                else R.string.impromptu_locationFailed
                            ),
                            fontSize = 14.sp,
                            color = TteMediumGray,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(
                                if (locationDenied) R.string.camera_openSettings else R.string.common_retry
                            ),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TteOrange,
                            modifier = Modifier.clickable {
                                if (locationDenied) {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${context.packageName}"),
                                        )
                                    )
                                } else {
                                    requestLocation()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 촬영 화면 위에 얹는 간단한 안내 말풍선 (어두운 배경 위라 밝은 칩으로 그린다) */
@Composable
private fun TutorialHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White,
        textAlign = TextAlign.Center,
        lineHeight = 19.sp,
        modifier = modifier
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(TteOrange.copy(alpha = 0.94f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
