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
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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
    var selectedZoom by remember { mutableStateOf(1.0f) }
    var showTip by remember { mutableStateOf(true) }

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

    // 촬영 중 경과시간 갱신 (예산 링/자동 종료용)
    LaunchedEffect(isRecording) {
        while (isRecording) {
            elapsedSeconds = (System.currentTimeMillis() - recordStartMs) / 1000.0
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
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.FHD))
            .build()
        val capture = VideoCapture.withOutput(recorder)
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

        val file = VlogClips.clipFile(context, place, sessionId)
        file.parentFile?.mkdirs()
        file.delete()

        val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val pending = capture.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
            .apply { if (hasAudio) withAudioEnabled() }

        usedSeconds = effectiveUsed
        currentPlaceClipSeconds = 0.0
        recordStartMs = System.currentTimeMillis()

        activeRecording = pending.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> isRecording = true
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
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        }

        // 상단: 닫기 / 장소명 / 렌즈 전환
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .fillMaxSize(),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = !isSaving, onClick = onClose),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "닫기", tint = Color.White, modifier = Modifier.size(20.dp))
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
                Icon(Icons.Filled.Cameraswitch, contentDescription = "카메라 전환", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // 촬영 팁 칩 (3초 후 사라짐)
        if (showTip) {
            Text(
                "💡 버튼으로 촬영 시작·종료, 가로·세로 자유롭게",
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
            // 남은 예산 (iOS progressRing + countLabel)
            val usedNow = (usedSeconds + if (isRecording) elapsedSeconds else 0.0).coerceAtMost(budgetSeconds)
            val remainingNow = budgetSeconds - usedNow
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(60.dp)) {
                CircularProgressIndicator(
                    progress = { (usedNow / budgetSeconds).toFloat() },
                    color = if (usedNow / budgetSeconds > 0.9) Color.Red else TteOrange,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    strokeWidth = 4.dp,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(formatBudget(remainingNow), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }

            // 녹화 버튼
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clickable(enabled = !isSaving) {
                        if (isRecording) {
                            isRecording = false
                            stopRecording()
                        } else {
                            startRecording()
                        }
                    },
            ) {
                Box(
                    Modifier
                        .size(76.dp)
                        .border(4.dp, Color.White, CircleShape)
                )
                Box(
                    Modifier
                        .size(if (isRecording) 32.dp else 60.dp)
                        .clip(if (isRecording) RoundedCornerShape(8.dp) else CircleShape)
                        .background(Color.Red)
                )
            }

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
                if (isRecording) "촬영 중 · 버튼을 누르면 종료" else "버튼을 눌러 촬영 시작 · 다시 누르면 종료",
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
                        Text("영상 저장 성공!", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    } else {
                        CircularProgressIndicator(color = Color.White)
                        Text("영상 저장 중...", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
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
                Text("카메라 권한이 필요해요", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(
                    "설정에서 카메라 권한을 허용하면 촬영할 수 있어요.",
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
                    Text("설정으로 이동", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                Text(
                    "닫기",
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

private fun formatBudget(seconds: Double): String {
    val v = seconds.toInt().coerceAtLeast(0)
    return if (v >= 60) String.format("%d:%02d", v / 60, v % 60) else "${v}초"
}
