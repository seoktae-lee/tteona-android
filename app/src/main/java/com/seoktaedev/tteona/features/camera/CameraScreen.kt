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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import com.seoktaedev.tteona.core.services.ProManager
import com.seoktaedev.tteona.core.services.VlogClips
import com.seoktaedev.tteona.core.util.Haptics
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.delay
import java.util.concurrent.Executor

/**
 * 장소 영상 촬영 — iOS Features/Camera/CameraView.swift(CameraViewController)의 CameraX 이식본.
 * 촬영 예산: 무료 세션 총 30초(장소당 5초) / PRO 5분 — ProManager 기준.
 * 클립은 files/Tteona/Sessions/{sessionId}/에 iOS와 동일한 이름 규칙으로 저장된다.
 */
@Composable
fun CameraScreen(
    place: Place,
    sessionId: String,
    onSaved: () -> Unit,
    onClose: () -> Unit,
    onBudgetExhausted: (() -> Unit)? = null,
) {
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
    // 이번 클립의 자동 종료 한도(초) — startRecording에서 갱신. 무료=5초, PRO=남은 예산.
    var clipLimitSeconds by remember { mutableDoubleStateOf(5.0) }
    var selectedZoom by remember { mutableStateOf(1.0f) }
    var showTip by remember { mutableStateOf(true) }
    // 탭 초점 인디케이터 위치 (px) — 잠시 표시 후 사라짐
    var focusIndicator by remember { mutableStateOf<Offset?>(null) }

    // 촬영 예산 (iOS refreshUsedSeconds) — 세션 폴더 클립 합계, 재촬영이면 이 장소 클립만큼 돌려받음
    var usedSeconds by remember { mutableDoubleStateOf(0.0) }
    var currentPlaceClipSeconds by remember { mutableDoubleStateOf(0.0) }
    val budgetSeconds = ProManager.vlogBudgetSeconds

    fun refreshUsedSeconds() {
        usedSeconds = VlogClips.totalSeconds(context, sessionId)
        currentPlaceClipSeconds = VlogClips.clipSeconds(VlogClips.clipFile(context, place, sessionId))
    }
    LaunchedEffect(Unit) { refreshUsedSeconds() }
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
            if (elapsedSeconds >= clipLimitSeconds) {
                // stopRecording()과 동일 — 종료는 Finalize 이벤트에서 마무리된다.
                isSaving = true
                activeRecording?.stop()
                break
            }
            delay(50)
        }
        elapsedSeconds = 0.0
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
        val clipLimit = ProManager.vlogClipMaxSeconds?.coerceAtMost(remaining) ?: remaining
        clipLimitSeconds = clipLimit

        val file = VlogClips.clipFile(context, place, sessionId)
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
                    // 클립 한도 도달 시 자동 종료 (iOS maxDuration)
                    if (event.recordingStats.recordedDurationNanos / 1e9 >= clipLimit && isRecording) {
                        isRecording = false
                        isSaving = true
                        activeRecording?.stop()
                    }
                }
                is VideoRecordEvent.Finalize -> {
                    isRecording = false
                    activeRecording = null
                    refreshUsedSeconds()
                    if (!event.hasError() && file.exists()) {
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

    // 저장 성공 → 1.2초 후 자동 닫기 (iOS recordingDone)
    LaunchedEffect(saveDone) {
        if (saveDone) {
            delay(1200)
            onSaved()
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

        // 상단: 닫기 / 장소명 / 렌즈 전환
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .fillMaxWidth(),
        ) {
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
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
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

        // 촬영 팁 칩 (3초 후 사라짐)
        if (showTip) {
            // 촬영 예산 안내 토스트 — 3초 뒤 사라짐. PRO는 분 단위, 무료는 장소당/총 초
            Text(
                if (ProManager.vlogClipMaxSeconds == null) {
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
            val isProUser = ProManager.vlogClipMaxSeconds == null
            // 총 촬영 예산 UI(분절 링·캡션)는 카메라에서 제거 — 지도 장소칩에 이미 있고,
            // 예산 안내는 상단 토스트(showTip)로 잠깐만 노출한다. (사용자 피드백 반영)

            // 녹화 버튼 — 바깥 링이 이번 클립(장소당 한도) 게이지 (iOS clipProgress)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clickable(enabled = !isSaving) {
                        if (isRecording) {
                            // 무료 유저는 5초 고정 촬영 — 중간 종료 불가 ("한 번 탭 = 한 칸" 멘탈모델).
                            // 실수한 컷은 같은 장소에서 재촬영하면 예산을 돌려받는다. (iOS와 동일)
                            if (isProUser) stopRecording()
                        } else {
                            startRecording()
                        }
                    },
            ) {
                val clipFrac = if (isRecording) {
                    (elapsedSeconds / clipLimitSeconds.coerceAtLeast(0.1)).coerceIn(0.0, 1.0)
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
                        String.format("%.1f", elapsedSeconds.coerceAtMost(clipLimitSeconds)),
                        clipLimitSeconds.roundToInt(),
                    )
                    isProUser -> stringResource(R.string.camera_clipHintPro)
                    else -> stringResource(R.string.camera_clipHintFree)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.9f),
            )

            // 줌 바 (후면일 때만, iOS zoomBar)
            if (lensFacing == CameraSelector.LENS_FACING_BACK) {
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

            Text(
                when {
                    isRecording && isProUser -> stringResource(R.string.camera_recordingHint)
                    isRecording -> stringResource(R.string.camera_recordingHintAuto)
                    isProUser -> stringResource(R.string.camera_hint)
                    else -> stringResource(R.string.camera_hintAuto)
                },
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.8f),
            )
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
