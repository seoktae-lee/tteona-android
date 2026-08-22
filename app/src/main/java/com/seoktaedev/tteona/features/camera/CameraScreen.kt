package com.seoktaedev.tteona.features.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.i18n.LocaleManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.seoktaedev.tteona.core.model.Place
import com.seoktaedev.tteona.core.model.VlogClipLength
import com.seoktaedev.tteona.core.services.ProManager
import com.seoktaedev.tteona.core.services.VlogClips
import com.seoktaedev.tteona.core.util.Haptics
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.Executor

/**
 * 벽시계 백업 종료에 두는 여유(초).
 *
 * 느린 인코더에서 첫 키프레임까지 걸리는 시간을 덮을 만큼 넉넉해야 하고(에뮬 실측 약 2.2초),
 * 그렇다고 너무 길면 Status가 멎은 기기에서 촬영이 오래 이어진다. 예산은 결과 파일의
 * 실제 길이로 정산되므로 조금 길어져도 회계가 어긋나지는 않는다.
 */
private const val CLIP_WALLCLOCK_SLACK = 4.0

/**
 * 장소 영상 촬영 — iOS Features/Camera/CameraView.swift(CameraViewController)의 CameraX 이식본.
 * 촬영 예산: 무료 세션 총 30초(장소당 5초) / PRO 5분 — ProManager 기준.
 * 클립은 files/Tteona/Sessions/{sessionId}/에 iOS와 동일한 이름 규칙으로 저장된다.
 */
@Composable
fun CameraScreen(
    sessionId: String,
    onSaved: () -> Unit,
    onClose: () -> Unit,
    /** 코스·재촬영 경로 — 파일명과 상단 장소 칩에 쓴다 */
    place: Place? = null,
    /**
     * 촬영 탭 경로 — 저장 경로를 직접 받는다.
     * 촬영 시작 시점에 장소가 아직 없기 때문이다('나의 오늘'은 먼저 찍고 장소를 나중에 붙인다).
     */
    clipFile: File? = null,
    /** 촬영 탭에 임베드된 상태 — 닫기·장소 칩을 숨기고 길이 칩·노출·격자를 보여준다 */
    embedded: Boolean = false,
    /** 값이 바뀌면 예산을 다시 센다 — 클립 삭제·세션 비우기가 바깥에서 일어난다 */
    budgetRefreshToken: Int = 0,
    onRecordingChanged: (Boolean) -> Unit = {},
    onUsedSecondsChanged: (Double) -> Unit = {},
    onBudgetExhausted: (() -> Unit)? = null,
    onRequestPaywall: (() -> Unit)? = null,
) {
    require(place != null || clipFile != null) { "place 또는 clipFile 중 하나는 있어야 한다" }
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor: Executor = remember { ContextCompat.getMainExecutor(context) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = it[Manifest.permission.CAMERA] == true
        permissionDenied = !hasPermission
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var saveDone by remember { mutableStateOf(false) }
    var recordStartMs by remember { mutableStateOf(0L) }
    var elapsedSeconds by remember { mutableDoubleStateOf(0.0) }
    // 이번 클립의 자동 종료 한도(초) — startRecording에서 갱신. 유저가 고른 길이를 남은 예산으로 클램프한 값.
    var clipLimitSeconds by remember { mutableDoubleStateOf(5.0) }
    /**
     * **실제로 파일에 기록된** 길이(초). CameraX가 Status 이벤트로 알려준다.
     * 벽시계 경과와 다르다 — 느린 인코더에서는 녹화 시작 신호 뒤에도 첫 키프레임이
     * 나오기까지 몇 초가 걸리고, 그동안은 이 값이 0에 머문다.
     */
    var recordedSeconds by remember { mutableDoubleStateOf(0.0) }
    var selectedZoom by remember { mutableStateOf(1.0f) }
    var showTip by remember { mutableStateOf(true) }
    // 탭 초점 인디케이터 위치 (px) — 잠시 표시 후 사라짐
    var focusIndicator by remember { mutableStateOf<Offset?>(null) }
    // 구도 보조 격자 — 촬영 중에도 유지한다
    var showGrid by rememberSaveable { mutableStateOf(false) }
    // 노출 보정(EV) — 역광에서 얼굴이 어둡게 나오는 걸 유저가 직접 잡는다.
    // 기기가 보고한 범위로 클램프해야 한다: 고정 범위를 쓰면 미지원 기기에서 조용히 무시된다.
    var exposureFraction by remember { mutableStateOf(0.5f) }
    val clipLength by ProManager.clipLength.collectAsState()
    val isPro by ProManager.isPro.collectAsState()

    // 촬영 예산 (iOS refreshUsedSeconds) — 세션 폴더 클립 합계, 재촬영이면 이 장소 클립만큼 돌려받음
    var usedSeconds by remember { mutableDoubleStateOf(0.0) }
    var currentPlaceClipSeconds by remember { mutableDoubleStateOf(0.0) }
    val budgetSeconds = ProManager.vlogBudgetSeconds

    // 촬영 탭은 촬영할 때마다 새 파일명을 쓰므로 '재촬영으로 예산 환급'이 없다.
    // 코스·재촬영 경로에서만 이 장소의 기존 클립 길이를 돌려받는다.
    val targetFile = clipFile ?: VlogClips.clipFile(context, place!!, sessionId)

    fun refreshUsedSeconds() {
        usedSeconds = VlogClips.totalSeconds(context, sessionId)
        currentPlaceClipSeconds =
            if (clipFile != null) 0.0 else VlogClips.clipSeconds(targetFile)
        onUsedSecondsChanged(usedSeconds)
    }
    LaunchedEffect(Unit) { refreshUsedSeconds() }
    // 바깥에서 클립을 지우거나 세션을 비우면 예산을 다시 센다 —
    // 그러지 않으면 예산이 찬 것으로 알고 셔터가 잠긴 채 남는다.
    LaunchedEffect(budgetRefreshToken) { if (budgetRefreshToken > 0) refreshUsedSeconds() }
    LaunchedEffect(isRecording) { onRecordingChanged(isRecording) }
    LaunchedEffect(Unit) {
        delay(3000)
        showTip = false
    }

    // 촬영 중 경과시간 갱신 (예산 링/자동 종료용).
    // 자동 종료는 벽시계 경과시간 기준 — 에뮬레이터/일부 기기는 VideoRecordEvent.Status의
    // recordedDurationNanos가 실제 촬영시간보다 뒤처져(느린 소프트 인코더) 클립 한도에 도달하지
    // 못해 자동 종료가 안 걸리므로, 벽시계로 확실히 끊는다.
    LaunchedEffect(isRecording) {
        while (isRecording) {
            elapsedSeconds = (System.currentTimeMillis() - recordStartMs) / 1000.0
            // **실제 기록 길이**가 한도에 닿으면 끝낸다 — 그게 결과물의 길이다.
            //
            // 벽시계만 보고 끊으면 느린 인코더에서 사고가 난다: Start 신호가 온 뒤에도
            // 첫 비디오 키프레임이 나오기까지 2~3초가 걸리는 기기가 있고(에뮬에서 실측),
            // 그 사이에 3초 한도로 끊으면 muxer가 한 프레임도 못 받아
            // ERROR_NO_VALID_DATA로 끝나 파일이 통째로 사라진다.
            // 5초 고정이던 시절엔 겨우 넘어갔지만 2·3초 옵션이 생기며 드러났다.
            //
            // 그렇다고 벽시계를 버릴 수도 없다 — 일부 기기는 Status가 뒤처지거나 멎어
            // 기록 길이만 보면 영영 안 끝난다. 여유(SLACK)를 둔 백업으로 남긴다.
            if (recordedSeconds >= clipLimitSeconds ||
                elapsedSeconds >= clipLimitSeconds + CLIP_WALLCLOCK_SLACK
            ) {
                // stopRecording()과 동일 — 종료는 Finalize 이벤트에서 마무리된다.
                isSaving = true
                activeRecording?.stop()
                break
            }
            delay(50)
        }
        elapsedSeconds = 0.0
        recordedSeconds = 0.0
    }

    // 카메라 바인딩 (렌즈 전환 시 재바인딩)
    val previewView = remember { PreviewView(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    LaunchedEffect(hasPermission, lensFacing) {
        if (!hasPermission) return@LaunchedEffect
        val provider = ProcessCameraProvider.awaitInstance(context)
        cameraProvider = provider
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        // iOS는 .hd1920x1080(FHD) 고정 — 아이폰은 전부 1080p 지원.
        // 안드로이드는 기기별 지원 화질이 달라(에뮬·일부 기기는 720p가 최대) FHD를 폴백 없이
        // 요구하면 selectedQualities가 비어 Recorder가 PENDING_RECORDING에서 멈추거나 크래시한다.
        // FHD 선호 + 미지원 시 HD→SD로 자동 강등하도록 정렬 리스트+폴백 지정.
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.fromOrderedList(
                    listOf(Quality.FHD, Quality.HD, Quality.SD),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.HD),
                )
            )
            .build()
        // 손떨림 방지 — 미지원 기기에서는 CameraX가 조용히 무시한다 (iOS .auto 대응)
        val capture = VideoCapture.Builder(recorder)
            .setVideoStabilizationEnabled(true)
            .build()
        provider.unbindAll()
        camera = runCatching {
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                preview,
                capture,
            )
        }.getOrNull()
        videoCapture = capture
        selectedZoom = 1.0f
    }

    LaunchedEffect(camera, exposureFraction) {
        val cam = camera ?: return@LaunchedEffect
        val range = cam.cameraInfo.exposureState.exposureCompensationRange
        if (!cam.cameraInfo.exposureState.isExposureCompensationSupported) return@LaunchedEffect
        val index = (range.lower + (range.upper - range.lower) * exposureFraction).toInt()
        runCatching { cam.cameraControl.setExposureCompensationIndex(index) }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            cameraProvider?.unbindAll()
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        isSaving = true
        activeRecording?.stop()
    }

    fun startRecording() {
        val capture = videoCapture ?: return
        // 재촬영이면 기존 클립이 덮어써지므로 그 길이만큼 예산을 돌려받는다 (iOS와 동일)
        val effectiveUsed = (usedSeconds - currentPlaceClipSeconds).coerceAtLeast(0.0)
        val remaining = budgetSeconds - effectiveUsed
        if (remaining < 1) {
            onBudgetExhausted?.invoke()
            return
        }
        // 고른 길이보다 남은 예산이 짧으면 그만큼만 찍힌다 — 안내 문구도 이 값을 말한다
        val clipLimit = (ProManager.vlogClipMaxSeconds ?: remaining).coerceAtMost(remaining)
        clipLimitSeconds = clipLimit

        val file = targetFile
        file.parentFile?.mkdirs()
        file.delete()

        val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val pending = capture.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
            .apply { if (hasAudio) withAudioEnabled() }

        usedSeconds = effectiveUsed
        currentPlaceClipSeconds = 0.0

        activeRecording = pending.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    // 벽시계 기준시각을 '실제 녹화 시작' 시점으로 잡는다 — prepareRecording~실제 시작
                    // 사이 지연 동안 타이머가 앞서 달려 첫 촬영이 짧게 잘리는 문제 방지.
                    recordStartMs = System.currentTimeMillis()
                    isRecording = true
                }
                is VideoRecordEvent.Status -> {
                    // 종료 판정은 위 루프가 단독으로 한다 — 두 곳에서 stop()을 부르면
                    // 어느 쪽이 이겼는지에 따라 결과가 달라져 재현이 어려워진다.
                    recordedSeconds = event.recordingStats.recordedDurationNanos / 1e9
                }
                is VideoRecordEvent.Finalize -> {
                    isRecording = false
                    activeRecording = null
                    refreshUsedSeconds()
                    // 인터럽션(전화 수신 = SOURCE_INACTIVE)·한도 도달로 끝난 클립은 에러 코드가 붙어도
                    // 파일이 재생 가능하다. 실제 인코딩 실패(ENCODING_FAILED·NO_VALID_DATA 등)와 구분해,
                    // 유효 길이(≥0.5초)를 가진 파일만 저장 성공으로 처리한다 (부분 클립 무단 삭제 방지).
                    val code = event.error
                    val recoverable = code == VideoRecordEvent.Finalize.ERROR_NONE ||
                        code == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED ||
                        code == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED ||
                        code == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE
                    val validClip = recoverable && file.exists() && VlogClips.clipSeconds(file) >= 0.5
                    if (validClip) {
                        Haptics.success(view)
                        saveDone = true
                    } else {
                        file.delete()
                        isSaving = false
                    }
                }
            }
        }
    }

    // 저장 성공 → 1.2초 후 다음 단계로 (iOS recordingDone)
    LaunchedEffect(saveDone) {
        if (!saveDone) return@LaunchedEffect
        delay(1200)
        onSaved()
        // 임베드 모드에서는 이 화면이 그대로 남는다 — 오버레이를 직접 걷어내지 않으면
        // '저장됐어요'가 화면을 덮은 채로 굳고, 그 뒤의 터치 차단막이 셔터까지 막는다.
        // (코스 경로는 onSaved가 화면을 닫으므로 원래 문제가 없었다)
        if (embedded) {
            saveDone = false
            isSaving = false
        }
    }

    BackHandler {
        if (!isSaving) onClose()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    // 핀치 줌 — 렌즈 교체 없이 현재 기기 배율만 연속 조정 (iOS handlePinch)
                    .pointerInput(camera) {
                        detectTransformGestures { _, _, zoom, _ ->
                            val cam = camera ?: return@detectTransformGestures
                            if (zoom == 1f) return@detectTransformGestures
                            val state = cam.cameraInfo.zoomState.value ?: return@detectTransformGestures
                            val newRatio = (state.zoomRatio * zoom)
                                .coerceIn(state.minZoomRatio, minOf(state.maxZoomRatio, 15f))
                            cam.cameraControl.setZoomRatio(newRatio)
                            selectedZoom = 0f   // 커스텀 줌 중에는 프리셋 강조 해제
                        }
                    }
                    // 탭 초점/노출 (iOS handleTap)
                    .pointerInput(camera) {
                        detectTapGestures { offset ->
                            val cam = camera ?: return@detectTapGestures
                            val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                            cam.cameraControl.startFocusAndMetering(
                                FocusMeteringAction.Builder(
                                    point,
                                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
                                ).build()
                            )
                            focusIndicator = offset
                        }
                    },
            )
        }

        // 구도 보조 격자 — 촬영 중에도 유지한다(구도를 잡는 게 목적이다)
        if (showGrid) {
            Canvas(Modifier.fillMaxSize()) {
                val line = Color.White.copy(alpha = 0.35f)
                for (i in 1..2) {
                    val x = size.width * i / 3f
                    val y = size.height * i / 3f
                    drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
            }
        }

        // 탭 초점 인디케이터 (주황 박스, 잠시 후 사라짐)
        focusIndicator?.let { p ->
            LaunchedEffect(p) {
                delay(900)
                focusIndicator = null
            }
            Box(
                Modifier
                    .offset { IntOffset((p.x - 36.dp.toPx()).roundToInt(), (p.y - 36.dp.toPx()).roundToInt()) }
                    .size(72.dp)
                    .border(1.5.dp, TteOrange, RoundedCornerShape(6.dp))
            )
        }

        // 상단: 닫기 / 장소명 / 렌즈 전환.
        // 임베드일 때는 닫기와 장소 칩이 없다 — 탭 자체가 화면이라 닫을 대상이 없고,
        // 장소는 촬영이 끝난 뒤에 붙으므로 아직 이름이 없다. 세션 칩은 촬영 탭이 그린다.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .fillMaxWidth(),
        ) {
            if (!embedded) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = !isSaving, onClick = onClose),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close), tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (!embedded && place != null) {
                    Text(
                        place.placeName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = !isRecording && !isSaving) {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    },
            ) {
                Icon(Icons.Filled.Cameraswitch, contentDescription = stringResource(R.string.camera_switchCamera), tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // 촬영 팁 칩 (3초 후 사라짐). 임베드는 세션 칩이 예산을 상시 보여줘 필요 없다.
        if (showTip && !embedded) {
            // 촬영 예산 안내 토스트 — 3초 뒤 사라짐. PRO는 분 단위, 무료는 장소당/총 초
            Text(
                if (ProManager.isPro.value) {
                    LocaleManager.string(context, R.string.camera_budgetToastPro, (ProManager.vlogBudgetSeconds / 60).toInt())
                } else {
                    LocaleManager.string(
                        context, R.string.camera_budgetToastFree,
                        (ProManager.vlogClipMaxSeconds ?: 5.0).toInt(), ProManager.vlogBudgetSeconds.toInt(),
                    )
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // 하단: 예산 표시 + 녹화 버튼 + 줌 + 힌트
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            val isProUser = isPro
            // 총 촬영 예산 UI(분절 링·캡션)는 카메라에서 제거 — 지도 장소칩에 이미 있고,
            // 예산 안내는 상단 토스트(showTip)로 잠깐만 노출한다. (사용자 피드백 반영)

            // 클립 길이 — 총 예산을 어떻게 쪼갤지의 선택. 10초 이상은 PRO.
            // 무료 유저에게 잠긴 길이를 셋씩 늘어놓을 이유가 없다 — 어느 걸 눌러도 같은
            // 페이월로 가므로 '더 길게' 하나로 묶는다. 칩이 줄어 작은 기기에서도 안전하다.
            if (embedded && !isRecording) {
                val remainingSeconds = (budgetSeconds - usedSeconds).coerceAtLeast(0.0)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VlogClipLength.entries
                        .filter { isPro || !it.requiresPro }
                        .forEach { length ->
                            val selected = clipLength == length ||
                                (clipLength.requiresPro && !isPro && length == VlogClipLength.FREE_DEFAULT)
                            // 남은 예산보다 긴 길이는 골라도 잘려서 찍힌다 — 미리 흐리게 알린다
                            val overBudget = length.seconds > remainingSeconds
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .height(32.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) Color.White else Color.Black.copy(alpha = 0.45f))
                                    .clickable {
                                        Haptics.light(view)
                                        ProManager.setClipLength(length)
                                    }
                                    .padding(horizontal = 13.dp),
                            ) {
                                Text(
                                    LocaleManager.string(context, R.string.camera_clipLength_seconds, length.seconds.toInt()),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color.Black
                                            else Color.White.copy(alpha = if (overBudget) 0.45f else 1f),
                                )
                            }
                        }

                    if (!isPro) {
                        // 잠긴 길이를 만지는 순간이 가장 자연스러운 업셀 지점이다
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .clickable {
                                    Haptics.light(view)
                                    onRequestPaywall?.invoke()
                                }
                                .padding(horizontal = 12.dp),
                        ) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(11.dp),
                            )
                            Text(
                                stringResource(R.string.camera_clipLength_more),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }

            // 녹화 버튼 — 바깥 링이 이번 클립(장소당 한도) 게이지 (iOS clipProgress)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clickable(enabled = !isSaving) {
                        // 모든 길이가 자동 종료다 — 중간 종료는 두지 않는다.
                        // 수동 종료를 열어두면 한 장소에서 예산을 다 태우는 사고가 나고,
                        // "짧게 툭 찍으면 알아서 브이로그가 된다"는 컨셉과도 어긋난다.
                        // 실수한 컷은 목록에서 지우면 예산을 돌려받는다.
                        if (!isRecording) startRecording()
                    },
            ) {
                // 기록이 실제로 시작되면 그 길이를 따른다 — 링이 다 찼는데 결과물이
                // 짧으면 거짓말이 된다. 아직 0이면(키프레임 대기) 벽시계로 채워 둔다.
                val progressSeconds = if (recordedSeconds > 0) recordedSeconds else elapsedSeconds
                val clipFrac = if (isRecording) {
                    (progressSeconds / clipLimitSeconds.coerceAtLeast(0.1)).coerceIn(0.0, 1.0)
                } else 0.0
                Canvas(Modifier.size(76.dp)) {
                    val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    val inset = stroke.width / 2
                    val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                    val topLeft = Offset(inset, inset)
                    drawArc(Color.White.copy(alpha = 0.35f), 0f, 360f, false, topLeft, arcSize, style = stroke)
                    if (clipFrac > 0) {
                        drawArc(Color.White, -90f, (360.0 * clipFrac).toFloat(), false, topLeft, arcSize, style = stroke)
                    }
                }
                Box(
                    Modifier
                        .size(if (isRecording) 32.dp else 60.dp)
                        .clip(if (isRecording) RoundedCornerShape(8.dp) else CircleShape)
                        .background(Color.Red)
                )
            }

            // 버튼 아래 힌트 — 대기: 이번 장소 한도 / 녹화 중: 경과 실시간 (iOS clipHint)
            Text(
                when {
                    isRecording -> LocaleManager.string(
                        context, R.string.camera_clipElapsed,
                        String.format(
                            "%.1f",
                            (if (recordedSeconds > 0) recordedSeconds else elapsedSeconds)
                                .coerceAtMost(clipLimitSeconds),
                        ),
                        clipLimitSeconds.roundToInt(),
                    )
                    embedded -> ""
                    isProUser -> stringResource(R.string.camera_clipHintPro)
                    else -> LocaleManager.string(
                        context, R.string.camera_clipHintFree,
                        ProManager.effectiveClipLength.seconds.roundToInt(),
                    )
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.9f),
            )

            // 줌 바 (후면일 때만, iOS zoomBar). 임베드는 좌측 세로 슬라이더로 대신한다.
            if (lensFacing == CameraSelector.LENS_FACING_BACK && !embedded) {
                val zoomState = camera?.cameraInfo?.zoomState?.value
                val factors = buildList {
                    if ((zoomState?.minZoomRatio ?: 1f) <= 0.6f) add(0.5f)
                    add(1.0f)
                    if ((zoomState?.maxZoomRatio ?: 1f) >= 3f) add(3.0f)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    factors.forEach { factor ->
                        val selected = selectedZoom == factor
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (selected) Color.White else Color.Black.copy(alpha = 0.45f))
                                .clickable {
                                    selectedZoom = factor
                                    camera?.cameraControl?.setZoomRatio(factor)
                                },
                        ) {
                            Text(
                                "${if (factor == factor.toInt().toFloat()) factor.toInt() else factor}x",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (selected) Color.Black else Color.White,
                            )
                        }
                    }
                }
            }

            // 셔터 아래 안내 — **실제로 찍히게 될 시간**을 말한다.
            // 고른 길이를 그대로 읽으면 남은 예산이 그보다 짧을 때 거짓말이 된다.
            val remainingForHint = (budgetSeconds - usedSeconds).coerceAtLeast(0.0)
            val effectiveSeconds = ProManager.effectiveClipLength.seconds.coerceAtMost(remainingForHint)
            Text(
                when {
                    isRecording -> stringResource(R.string.camera_recordingHintAuto)
                    remainingForHint < 1 -> stringResource(R.string.impromptu_budgetFull)
                    else -> LocaleManager.string(context, R.string.camera_hintAuto, effectiveSeconds.roundToInt())
                },
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.8f),
            )
        }

        // 좌: 줌 · 우: 밝기 — 세로 슬라이더. 촬영 중에도 조작할 수 있어야 한다.
        //
        // 검은 판을 깔면 화면 한쪽이 어두워져 뷰파인더를 가린다. 트랙만 남기고
        // 밝은 장면에서 묻히지 않도록 아이콘 배경만 둔다.
        if (embedded && hasPermission) {
            val zoomState = camera?.cameraInfo?.zoomState?.value
            VerticalCameraSlider(
                value = zoomState?.linearZoom ?: 0f,
                onValueChange = { camera?.cameraControl?.setLinearZoom(it) },
                icon = Icons.Filled.ZoomIn,
                label = stringResource(R.string.camera_switchCamera),
                isDefault = (zoomState?.linearZoom ?: 0f) < 0.01f,
                onReset = { camera?.cameraControl?.setLinearZoom(0f) },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 14.dp, bottom = 96.dp),
            )
            VerticalCameraSlider(
                value = exposureFraction,
                onValueChange = { exposureFraction = it },
                icon = Icons.Filled.WbSunny,
                label = stringResource(R.string.camera_exposure),
                isDefault = kotlin.math.abs(exposureFraction - 0.5f) < 0.01f,
                onReset = { exposureFraction = 0.5f },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp, bottom = 96.dp),
            )

            // 격자 — 셔터와 같은 높이 좌측 (전환 버튼과 좌우 대칭)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 24.dp, bottom = 74.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable {
                        Haptics.light(view)
                        showGrid = !showGrid
                    },
            ) {
                Icon(
                    Icons.Filled.Grid3x3,
                    contentDescription = stringResource(R.string.camera_grid),
                    tint = if (showGrid) TteOrange else Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // 저장 중/성공 오버레이 (iOS savingOverlay)
        if (isSaving) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .size(200.dp, 160.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(20.dp),
                ) {
                    if (saveDone) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(48.dp))
                        Text(stringResource(R.string.camera_saveSuccess), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    } else {
                        CircularProgressIndicator(color = Color.White)
                        Text(stringResource(R.string.camera_saving), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    }
                }
            }
        }

        // 권한 거부 오버레이 (iOS permissionOverlay)
        if (permissionDenied) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 24.dp),
            ) {
                Box(Modifier.weight(1f))
                Text(stringResource(R.string.camera_permission_title), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(
                    stringResource(R.string.camera_permission_subtitle),
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TteOrange)
                        .clickable {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            )
                        }
                        .padding(horizontal = 32.dp),
                ) {
                    Text(stringResource(R.string.camera_openSettings), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                Text(
                    stringResource(R.string.common_close),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.clickable(onClick = onClose),
                )
                Box(Modifier.weight(1f))
            }
        }
    }
}

/**
 * 세로 슬라이더 — **직접 그린다.**
 *
 * 처음엔 Material Slider를 `rotationZ = -90f`로 돌려 썼는데, 회전은 그리기에만 적용되고
 * 레이아웃 크기는 회전 전 기준이라 트랙이 옆 컨트롤 위로 삐져나오고 터치 영역도 어긋났다
 * (에뮬 실측: 트랙이 가로로 누운 채 뷰파인더를 가로질렀다).
 *
 * 트랙 하나와 손잡이 하나가 전부라 직접 그리는 편이 예측 가능하다.
 * 검은 판을 깔지 않는다 — 화면 한쪽이 어두워지면 뷰파인더를 가린다. 대신 그림자로 띄운다.
 */
@Composable
private fun VerticalCameraSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isDefault: Boolean,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackHeight = 140.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onReset),
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isDefault) Color.White.copy(alpha = 0.85f) else TteOrange,
                modifier = Modifier.size(15.dp),
            )
        }

        // 위가 1, 아래가 0 — 밝기·줌 모두 "위로 올리면 커진다"가 직관적이다
        Canvas(
            Modifier
                .size(width = 28.dp, height = trackHeight)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        onValueChange((1f - change.position.y / size.height).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onValueChange((1f - offset.y / size.height).coerceIn(0f, 1f))
                    }
                },
        ) {
            val cx = size.width / 2f
            val trackW = 3.dp.toPx()
            val y = size.height * (1f - value)
            // 트랙(전체) → 활성 구간(손잡이 아래) → 손잡이
            drawLine(
                Color.Black.copy(alpha = 0.35f),
                Offset(cx, 0f), Offset(cx, size.height),
                strokeWidth = trackW + 2.dp.toPx(), cap = StrokeCap.Round,
            )
            drawLine(
                Color.White.copy(alpha = 0.45f),
                Offset(cx, 0f), Offset(cx, size.height),
                strokeWidth = trackW, cap = StrokeCap.Round,
            )
            drawLine(
                TteOrange,
                Offset(cx, y), Offset(cx, size.height),
                strokeWidth = trackW, cap = StrokeCap.Round,
            )
            drawCircle(Color.Black.copy(alpha = 0.35f), radius = 8.dp.toPx(), center = Offset(cx, y))
            drawCircle(Color.White, radius = 6.5f.dp.toPx(), center = Offset(cx, y))
        }
    }
}
