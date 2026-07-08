package com.seoktaedev.tteona.features.vlog

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.i18n.LocaleManager
import com.seoktaedev.tteona.core.model.Course
import com.seoktaedev.tteona.core.services.CourseThumbnailService
import com.seoktaedev.tteona.core.services.ProManager
import com.seoktaedev.tteona.core.services.VlogClips
import com.seoktaedev.tteona.core.services.VlogServerService
import com.seoktaedev.tteona.core.util.Haptics
import com.seoktaedev.tteona.ui.theme.TteOrange
import com.seoktaedev.tteona.ui.theme.glowCircle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Vlog 생성 — iOS Features/Vlog/VlogGenerationView.swift의 이식본.
 * 포맷 선택 → BGM 선택 → 서버 합성(진행률) → 프리뷰(앨범 저장·공유·썸네일 선택).
 */
@Composable
fun VlogGenerationScreen(
    course: Course,
    sessionId: String,
    thumbnailCourseId: String? = null,
    onDismissToHome: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isPro by ProManager.isPro.collectAsState()
    val creatingText = stringResource(R.string.vlog_creating)

    var phase by remember { mutableStateOf(Phase.CHOOSE_FORMAT) }
    var vlogFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableDoubleStateOf(0.0) }
    var stageText by remember { mutableStateOf(creatingText) }
    var savedFormatsCount by remember { mutableIntStateOf(0) }
    var selectedFormats by remember { mutableStateOf<Set<String>>(emptySet()) }
    var didGenerate by remember { mutableStateOf(false) }
    var shotPortrait by remember { mutableStateOf<Boolean?>(null) }
    var selectedBgm by remember { mutableStateOf("auto") }
    var showProNotice by remember { mutableStateOf(false) }

    val baseFormat = if (shotPortrait ?: true) "reels" else "youtube"

    // Android 9 이하는 MediaStore 저장에 WRITE_EXTERNAL_STORAGE 런타임 권한이 필요하다.
    // 합성이 오래 걸리므로 화면 진입 시 미리 요청해, 완료 시점엔 권한이 확정돼 있게 한다.
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    // 촬영 방향 판별 — 클립 다수 방향 (iOS detectShotOrientation)
    LaunchedEffect(Unit) {
        if (shotPortrait != null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            var portraitVotes = 0
            var landscapeVotes = 0
            course.places.forEach { place ->
                val file = VlogClips.clipFile(context, place, sessionId)
                if (!file.exists()) return@forEach
                // AutoCloseable(close)은 API 29+ — 하위 호환을 위해 release()로 직접 정리
                val r = MediaMetadataRetriever()
                runCatching {
                    r.setDataSource(file.absolutePath)
                    val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    val portrait = if (rot == 90 || rot == 270) w >= h else h >= w
                    if (portrait) portraitVotes++ else landscapeVotes++
                }
                runCatching { r.release() }
            }
            if (portraitVotes + landscapeVotes > 0) {
                val portrait = portraitVotes >= landscapeVotes
                shotPortrait = portrait
                selectedFormats = selectedFormats - (if (portrait) "reels" else "youtube")
            }
        }
    }

    BackHandler { if (phase == Phase.CHOOSE_FORMAT) onDismissToHome() }

    when (phase) {
        Phase.CHOOSE_FORMAT -> ChooseFormatView(
            baseFormat = baseFormat,
            selectedFormats = selectedFormats,
            isPro = isPro,
            onToggle = { key, locked ->
                if (locked) showProNotice = true
                else selectedFormats = if (key in selectedFormats) selectedFormats - key else selectedFormats + key
            },
            onNext = { phase = Phase.CHOOSE_BGM },
            onClose = onDismissToHome,
        )
        Phase.CHOOSE_BGM -> ChooseBgmView(
            courseTagLabel = stringResource(course.tag.labelRes),
            selectedBgm = selectedBgm,
            isPro = isPro,
            onSelect = { id, locked -> if (locked) showProNotice = true else selectedBgm = id },
            onNext = { phase = Phase.GENERATING },
            onBack = { phase = Phase.CHOOSE_FORMAT },
        )
        Phase.GENERATING -> GeneratingView(progress = progress, stageText = stageText, courseName = course.courseName)
        Phase.PREVIEW -> vlogFile?.let { file ->
            VlogPreviewView(
                vlogFile = file,
                thumbnailCourseId = thumbnailCourseId,
                savedFormatsCount = savedFormatsCount,
                onDismiss = onDismissToHome,
            )
        }
        Phase.ERROR -> ErrorView(errorMessage, onDismiss = onDismissToHome)
    }

    // PRO 전용 기능 → 페이월 (iOS showPaywall 시트)
    if (showProNotice) {
        com.seoktaedev.tteona.features.pro.ProPaywallScreen(onDismiss = { showProNotice = false })
    }

    // 생성 실행 (iOS generatingView.task)
    LaunchedEffect(phase) {
        if (phase != Phase.GENERATING || didGenerate) return@LaunchedEffect
        didGenerate = true
        val uid = AuthService.currentUser.value?.uid
        try {
            if (uid == null) throw VlogServerService.ServerVlogException(LocaleManager.string(context, R.string.vlog_loginRequired))
            val result = VlogServerService.generate(
                context = context,
                course = course,
                sessionId = sessionId,
                userId = uid,
                formats = selectedFormats.toList(),
                bgm = selectedBgm,
                watermark = !isPro,
                priority = isPro,
                onProgress = { p, stage ->
                    withContext(Dispatchers.Main) {
                        progress = p
                        stageText = stage
                    }
                },
            )
            // 앨범 저장 — 기본본 + 추가 포맷 (iOS saveToPhotoLibrary).
            // 저장 실패 시 실제 저장 개수(0)를 그대로 반영해 프리뷰에서 거짓 안내를 하지 않는다.
            var saved = 0
            if (saveToGallery(context, result.main)) saved++
            result.extras.forEach { (_, file) -> if (saveToGallery(context, file)) saved++ }
            savedFormatsCount = saved
            vlogFile = result.main
            Haptics.success(view)
            phase = Phase.PREVIEW
        } catch (e: Exception) {
            errorMessage = e.message
            phase = Phase.ERROR
        }
    }
}

private enum class Phase { CHOOSE_FORMAT, CHOOSE_BGM, GENERATING, PREVIEW, ERROR }

// ── 포맷 선택 (iOS chooseFormatView) ────────────────────────────────────

@Composable
private fun ChooseFormatView(
    baseFormat: String,
    selectedFormats: Set<String>,
    isPro: Boolean,
    onToggle: (key: String, locked: Boolean) -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        VlogAuroraBackground()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        ) {
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.vlog_formatSheet_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                stringResource(R.string.vlog_formatSheet_subtitle),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 6.dp),
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 24.dp).padding(top = 28.dp),
            ) {
                FormatRow(
                    icon = Icons.Filled.Smartphone, title = stringResource(R.string.vlog_format_reels), ratio = "9:16",
                    subtitle = if (baseFormat == "reels") stringResource(R.string.vlog_format_included) else stringResource(R.string.vlog_format_blurConvert),
                    badge = if (baseFormat == "reels") stringResource(R.string.vlog_format_portraitBadge) else null,
                    fixed = baseFormat == "reels",
                    locked = baseFormat != "reels" && !isPro,
                    isOn = baseFormat == "reels" || "reels" in selectedFormats,
                ) { locked -> onToggle("reels", locked) }
                FormatRow(
                    icon = Icons.Filled.SmartDisplay, title = stringResource(R.string.vlog_format_youtube), ratio = "16:9",
                    subtitle = if (baseFormat == "youtube") stringResource(R.string.vlog_format_included) else stringResource(R.string.vlog_format_blurConvert),
                    badge = if (baseFormat == "youtube") stringResource(R.string.vlog_format_landscapeBadge) else null,
                    fixed = baseFormat == "youtube",
                    locked = baseFormat != "youtube" && !isPro,
                    isOn = baseFormat == "youtube" || "youtube" in selectedFormats,
                ) { locked -> onToggle("youtube", locked) }
                FormatRow(
                    icon = Icons.Filled.CropSquare, title = stringResource(R.string.vlog_format_insta), ratio = "1:1",
                    subtitle = stringResource(R.string.vlog_format_squareCrop),
                    badge = null, fixed = false, locked = !isPro,
                    isOn = "insta" in selectedFormats,
                ) { locked -> onToggle("insta", locked) }
            }

            Spacer(Modifier.weight(1f))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TteOrange)
                    .clickable(onClick = onNext),
            ) {
                Text(
                    if (selectedFormats.isEmpty()) stringResource(R.string.session_makeVlog) else stringResource(R.string.vlog_makeVersions, selectedFormats.size + 1),
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White,
                )
            }
            Text(
                stringResource(R.string.common_close),
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(top = 14.dp, bottom = 36.dp)
                    .clickable(onClick = onClose),
            )
        }
    }
}

@Composable
private fun FormatRow(
    icon: ImageVector,
    title: String,
    ratio: String,
    subtitle: String,
    badge: String?,
    fixed: Boolean,
    locked: Boolean,
    isOn: Boolean,
    onClick: (locked: Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (isOn) 0.12f else 0.06f))
            .border(1.2.dp, if (isOn) TteOrange.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(enabled = !fixed) { onClick(locked) }
            .padding(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (isOn) TteOrange else Color.White.copy(alpha = 0.5f), modifier = Modifier.width(30.dp).size(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(
                    ratio, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TteOrange,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(TteOrange.copy(alpha = 0.18f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
                when {
                    badge != null -> Text(
                        "✨ $badge", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(TteOrange)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    locked -> ProBadge()
                }
            }
            Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
        }
        Icon(
            when {
                locked -> Icons.Filled.Lock
                isOn -> Icons.Filled.CheckCircle
                else -> Icons.Filled.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = when {
                locked -> Color.White.copy(alpha = 0.4f)
                isOn -> TteOrange
                else -> Color.White.copy(alpha = 0.3f)
            },
            modifier = Modifier.size(if (locked) 18.dp else 22.dp),
        )
    }
}

@Composable
private fun ProBadge() {
    Text(
        "👑 PRO", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(Color(0xFFFFB34D), TteOrange)))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

// ── BGM 선택 (iOS chooseBgmView) ────────────────────────────────────────

@Composable
private fun ChooseBgmView(
    courseTagLabel: String,
    selectedBgm: String,
    isPro: Boolean,
    onSelect: (id: String, locked: Boolean) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    var tracks by remember { mutableStateOf<List<VlogServerService.BgmTrack>>(emptyList()) }
    var playingTrackId by remember { mutableStateOf<String?>(null) }
    val player = remember { MediaPlayer() }

    LaunchedEffect(Unit) {
        runCatching { tracks = VlogServerService.fetchBgmTracks() }
        // 목록을 못 받아도 자동 추천/음악 없음 두 옵션으로 진행 가능
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { player.release() } }
    }

    fun stopPreview() {
        runCatching { player.reset() }
        playingTrackId = null
    }

    fun togglePreview(id: String, url: String) {
        if (playingTrackId == id) {
            stopPreview()
            return
        }
        runCatching {
            player.reset()
            player.setDataSource(url)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { playingTrackId = null }
            player.prepareAsync()
            playingTrackId = id
        }
    }

    Box(Modifier.fillMaxSize()) {
        VlogAuroraBackground()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(60.dp))
            Text(stringResource(R.string.vlog_bgmSheet_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                stringResource(R.string.vlog_bgmSheet_subtitle),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 6.dp),
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 28.dp, bottom = 12.dp),
            ) {
                BgmRow(
                    icon = Icons.Filled.AutoAwesome, name = stringResource(R.string.vlog_bgm_auto), mood = courseTagLabel,
                    subtitle = stringResource(R.string.vlog_bgm_auto_subtitle),
                    isOn = selectedBgm == "auto", locked = false, playing = false, hasPreview = false,
                    onClick = { onSelect("auto", false) }, onPreview = {},
                )
                BgmRow(
                    icon = Icons.Filled.MusicOff, name = stringResource(R.string.vlog_bgm_none), mood = null,
                    subtitle = stringResource(R.string.vlog_bgm_none_subtitle),
                    isOn = selectedBgm == "none", locked = false, playing = false, hasPreview = false,
                    onClick = { onSelect("none", false) }, onPreview = {},
                )
                tracks.forEach { track ->
                    val locked = !isPro
                    BgmRow(
                        icon = Icons.Filled.MusicNote, name = track.name, mood = track.mood,
                        subtitle = null,
                        isOn = selectedBgm == track.id, locked = locked,
                        playing = playingTrackId == track.id, hasPreview = !locked,
                        onClick = { onSelect(track.id, locked) },
                        onPreview = { togglePreview(track.id, track.url) },
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TteOrange)
                    .clickable {
                        stopPreview()
                        onNext()
                    },
            ) {
                Text(stringResource(R.string.session_makeVlog), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text(
                stringResource(R.string.vlog_back),
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(top = 14.dp, bottom = 36.dp)
                    .clickable {
                        stopPreview()
                        onBack()
                    },
            )
        }
    }
}

@Composable
private fun BgmRow(
    icon: ImageVector,
    name: String,
    mood: String?,
    subtitle: String?,
    isOn: Boolean,
    locked: Boolean,
    playing: Boolean,
    hasPreview: Boolean,
    onClick: () -> Unit,
    onPreview: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (isOn) 0.12f else 0.06f))
            .border(1.2.dp, if (isOn) TteOrange.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (isOn) TteOrange else Color.White.copy(alpha = 0.5f), modifier = Modifier.width(28.dp).size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            // 제목이 길면(예: "Acoustic Road Trip Music") mood·PRO 뱃지가 밀려 한 글자씩
            // 세로로 줄바꿈되는 문제 방지 — 제목만 weight로 줄이고, 뱃지들은 항상 한 줄 고정폭 유지.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                mood?.let {
                    Text(
                        it, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TteOrange,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(TteOrange.copy(alpha = 0.18f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
                if (locked) ProBadge()
            }
            subtitle?.let { Text(it, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f)) }
        }
        if (locked) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
        } else {
            if (hasPreview) {
                Icon(
                    if (playing) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                    contentDescription = stringResource(R.string.vlog_preview),
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(26.dp).clickable(onClick = onPreview),
                )
            }
            Icon(
                if (isOn) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isOn) TteOrange else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ── 생성 중 (iOS generatingView) ─────────────────────────────────────────

@Composable
private fun GeneratingView(progress: Double, stageText: String, courseName: String) {
    val animated by animateFloatAsState(progress.toFloat(), tween(300), label = "vlog-progress")
    Box(Modifier.fillMaxSize()) {
        VlogAuroraBackground()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                CircularProgressIndicator(
                    progress = { animated },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.15f),
                    strokeWidth = 6.dp,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.fillMaxSize(),
                )
                Text("${(animated * 100).toInt()}%", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stageText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(courseName, fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

// ── 프리뷰 (iOS VlogPreviewView) ─────────────────────────────────────────

@Composable
private fun VlogPreviewView(
    vlogFile: File,
    thumbnailCourseId: String?,
    savedFormatsCount: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var thumbState by remember { mutableStateOf(ThumbState.IDLE) }

    val thumbPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && thumbnailCourseId != null) {
            thumbState = ThumbState.UPLOADING
        }
        pendingThumbUri = uri
    }
    // 선택된 이미지 업로드
    LaunchedEffect(thumbState) {
        if (thumbState != ThumbState.UPLOADING) return@LaunchedEffect
        val uri = pendingThumbUri
        val courseId = thumbnailCourseId
        if (uri == null || courseId == null) {
            thumbState = ThumbState.FAILED
            return@LaunchedEffect
        }
        val bytes = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        }
        thumbState = if (bytes != null && CourseThumbnailService.upload(courseId, bytes) != null) {
            ThumbState.DONE
        } else ThumbState.FAILED
    }

    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
        VlogAuroraBackground()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
        // 완성 헤더 — 축하하는 나루 + 폭죽 + 문구 + 자동 저장 안내
        Spacer(Modifier.height(40.dp))
        Box(contentAlignment = Alignment.TopEnd) {
            Image(
                painter = painterResource(R.drawable.tteoni_jump),
                contentDescription = null,
                modifier = Modifier.height(96.dp),
            )
            Text("🎉", fontSize = 26.sp, modifier = Modifier.offset(x = 10.dp, y = (-2).dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.vlog_done_title), fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Icon(
                if (savedFormatsCount > 0) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (savedFormatsCount > 0) Color(0xFF34C759) else Color(0xFFFFCC00),
                modifier = Modifier.size(14.dp),
            )
            Text(
                when {
                    savedFormatsCount > 1 -> stringResource(R.string.vlog_done_savedMulti, savedFormatsCount)
                    savedFormatsCount == 1 -> stringResource(R.string.vlog_done_savedSingle)
                    else -> stringResource(R.string.vlog_done_saveFailed)
                },
                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.9f),
            )
        }

        // 비디오 플레이어 — 라운드 카드
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp)
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(22.dp)),
        ) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoPath(vlogFile.absolutePath)
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            start()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .navigationBarsPadding()
                .padding(top = 22.dp),
        ) {

            // 탐색탭 썸네일 선택 (이번 세션에서 저장한 코스일 때만)
            if (thumbnailCourseId != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .clickable(enabled = thumbState != ThumbState.UPLOADING) {
                            thumbPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        .padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.weight(1f))
                    when (thumbState) {
                        ThumbState.IDLE -> {
                            Icon(Icons.Filled.Photo, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.vlog_thumbnail_pick), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                        ThumbState.UPLOADING -> {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Text(stringResource(R.string.vlog_thumbnail_uploading), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                        ThumbState.DONE -> {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.vlog_thumbnail_done), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                        ThumbState.FAILED -> {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFFCC00), modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.vlog_thumbnail_failed), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TteOrange)
                    .clickable { shareVideo(context, vlogFile) },
            ) {
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.vlog_share), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.weight(1f))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 34.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable(onClick = onDismiss),
            ) {
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.Home, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.vlog_goHome), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.weight(1f))
            }
        }
        }
    }
}

private enum class ThumbState { IDLE, UPLOADING, DONE, FAILED }

// PickVisualMedia 콜백 → LaunchedEffect 전달용 (Compose 상태로 담기엔 과한 일회성 값)
private var pendingThumbUri: Uri? = null

// ── 에러 (iOS errorView) ─────────────────────────────────────────────────

@Composable
private fun ErrorView(message: String?, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize().background(Color.Black),
    ) {
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
        Text(stringResource(R.string.vlog_failed), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        message?.let {
            Text(
                it, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(TteOrange)
                .clickable(onClick = onDismiss),
        ) {
            Text(stringResource(R.string.vlog_goBack), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
        Spacer(Modifier.weight(1f))
    }
}

// ── 주황 그라데이션 일렁임 배경 (iOS VlogAuroraBackground) ────────────────

@Composable
private fun VlogAuroraBackground() {
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "vlog-aurora")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            tween(5000), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "vlog-aurora-t",
    )
    Box(Modifier.fillMaxSize().background(Color(0xFF190A03))) {
        Box(
            Modifier
                .size(430.dp)
                .offset(x = (110 - 240 * t).dp, y = (-70 - 160 * t).dp)
                .glowCircle(TteOrange, 0.55f)
        )
        Box(
            Modifier
                .size(360.dp)
                .offset(x = (-100 + 240 * t).dp, y = (330 - 140 * t).dp)
                .glowCircle(Color(0xFFFFA159), 0.45f)
        )
        Box(
            Modifier
                .size(320.dp)
                .offset(x = (70 - 120 * t).dp, y = (60 + 280 * t).dp)
                .glowCircle(Color(0xFFFF6673), 0.30f)
        )
    }
}

// ── 앨범 저장 / 공유 헬퍼 ─────────────────────────────────────────────────

/** 완성본을 갤러리(Movies/tteona)에 저장 — iOS saveToPhotoLibrary 대응 */
private suspend fun saveToGallery(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/tteona")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@runCatching false
        context.contentResolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { it.copyTo(output) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        true
    }.getOrDefault(false)
}

private fun shareVideo(context: Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, LocaleManager.string(context, R.string.vlog_shareChooser)))
    }
}
