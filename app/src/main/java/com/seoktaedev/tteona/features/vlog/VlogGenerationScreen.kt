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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.seoktaedev.tteona.core.auth.GuestVlogQuota
import androidx.compose.runtime.Composable
import com.seoktaedev.tteona.core.model.Place
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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
import com.seoktaedev.tteona.features.tutorial.TutorialBubble
import com.seoktaedev.tteona.features.tutorial.TutorialCelebrateOverlay
import com.seoktaedev.tteona.features.tutorial.VlogTutorial
import com.seoktaedev.tteona.features.tutorial.tutorialGlow
import com.seoktaedev.tteona.ui.theme.BadgeNumberTextStyle
import com.seoktaedev.tteona.ui.theme.TteOrange
import com.seoktaedev.tteona.ui.theme.glowCircle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Vlog 생성 — iOS Features/Vlog/VlogGenerationView.swift의 이식본.
 * 포맷 선택 → BGM 선택 → 서버 합성(진행률) → 프리뷰(앨범 저장·공유·썸네일 선택).
 */
@Composable
fun VlogGenerationScreen(
    course: Course,
    sessionId: String,
    thumbnailCourseId: String? = null,
    // 세션 시작 때 위치를 공유한 방들 — 공유 설정이 켜져 있으면 완성본이 이 방들에 자동 공유된다
    shareRoomIds: Set<String> = emptySet(),
    // 완료 후 "홈으로" — 세션을 정리하고 세션 화면까지 닫는다.
    onDismissToHome: () -> Unit,
    // 포맷/BGM 선택·에러 화면에서 닫기 — 세션 화면으로 되돌아가 기록을 보존한다.
    // (iOS VlogGenerationView의 dismiss() 대응. 미전달 시 홈으로 폴백.)
    onBack: () -> Unit = onDismissToHome,
    /** 게스트 한도에 걸렸을 때 가입 화면으로 — 미전달 시 그냥 닫는다 */
    onRequestSignUp: () -> Unit = onBack,
    /**
     * 브이로그를 실제로 손에 넣은 순간. 세션 정리는 여기서 한다 —
     * 생성 전에 미리 지우면 취소·실패 시 그날 기록이 통째로 사라진다.
     */
    onVlogCompleted: () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isPro by ProManager.isPro.collectAsState()
    val creatingText = stringResource(R.string.vlog_creating)

    val isGuest by AuthService.isGuest.collectAsState()
    // 이미 다 썼다면 포맷·BGM을 고르게 한 뒤 마지막에 거절하는 건 잔인하다 —
    // 들어오는 문에서 안내한다.
    var phase by remember {
        mutableStateOf(
            if (isGuest && GuestVlogQuota.isExhausted) Phase.GUEST_LIMIT else Phase.CHOOSE_FORMAT
        )
    }
    var vlogFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableDoubleStateOf(0.0) }
    var stageText by remember { mutableStateOf(creatingText) }
    var savedFormatsCount by remember { mutableIntStateOf(0) }
    var selectedFormats by remember { mutableStateOf<Set<String>>(emptySet()) }
    var shotPortrait by remember { mutableStateOf<Boolean?>(null) }
    var selectedBgm by remember { mutableStateOf("auto") }
    // 장소 자막 설정 — 다음 생성에도 기억된다 (tteona_prefs, iOS @AppStorage와 동일 키).
    // 캡션만은 기억하지 않는다 — 그날 그 장소의 한 줄이라 다음 브이로그로 넘어오면 안 된다.
    val prefs = remember { context.getSharedPreferences("tteona_prefs", android.content.Context.MODE_PRIVATE) }
    var style by remember {
        mutableStateOf(
            VlogSubtitleStyle(
                font = VlogFont.from(prefs.getString("vlog.font", null)),
                scale = VlogFontScale.from(prefs.getString("vlog.fontScale", null)),
                fields = VlogSubtitleFields.from(prefs.getString("vlog.subtitleFields", null)),
                color = VlogSubtitleColor.from(prefs.getString("vlog.subtitleColor", null)),
                holdsSubtitle = prefs.getBoolean("vlog.subtitleHold", false),
            )
        )
    }
    /** 지금 문구를 적고 있는 클립의 파일명 */
    var editingClip by remember { mutableStateOf<String?>(null) }

    /** 실제로 클립 파일이 있는 장소만 — 파일이 없는 장소는 브이로그에 안 들어간다 */
    val clipsForCaption = remember(course.places, sessionId) {
        course.places.filter { VlogClips.clipFile(context, it, sessionId).exists() }
    }
    var showProNotice by remember { mutableStateOf(false) }
    // 서버가 아직 이 잡을 렌더링 중이라 "이어받기"가 가능한 상태인가
    var canResume by remember { mutableStateOf(false) }
    // 재시도 횟수 — 증가할 때마다 생성 LaunchedEffect가 다시 돈다
    var attempt by remember { mutableIntStateOf(0) }

    // 합성은 수 분 걸린다. 화면이 잠기면 앱이 백그라운드로 내려가 업로드·폴링이 끊긴다.
    // (iOS VlogGenerationView의 isIdleTimerDisabled 대응)
    DisposableEffect(phase) {
        view.keepScreenOn = phase == Phase.GENERATING
        onDispose { view.keepScreenOn = false }
    }

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

    // 브이로그 화면을 완성 전에 나가면 튜토리얼을 '오늘 종료' 단계로 되돌린다 (칩은 남아 있음)
    fun exitVlog() {
        VlogTutorial.handleVlogExit()
        onBack()
    }

    // 포맷 선택 단계에서 뒤로가기 = 세션 화면 복귀(기록 보존). 그 외 단계는 뒤로가기를 막는다.
    BackHandler { if (phase == Phase.CHOOSE_FORMAT) exitVlog() }

    when (phase) {
        Phase.CHOOSE_FORMAT -> ChooseFormatView(
            baseFormat = baseFormat,
            selectedFormats = selectedFormats,
            isPro = isPro,
            onToggle = { key, locked ->
                if (locked) showProNotice = true
                else selectedFormats = if (key in selectedFormats) selectedFormats - key else selectedFormats + key
            },
            onNext = {
                VlogTutorial.advance(VlogTutorial.Step.CHOOSE_BGM)
                phase = Phase.CHOOSE_BGM
            },
            onClose = { exitVlog() },
        )
        Phase.CHOOSE_BGM -> ChooseBgmView(
            courseTagLabel = stringResource(course.tag.labelRes),
            selectedBgm = selectedBgm,
            isPro = isPro,
            onSelect = { id, locked -> if (locked) showProNotice = true else selectedBgm = id },
            onNext = { phase = Phase.CHOOSE_TEXT },
            onBack = { phase = Phase.CHOOSE_FORMAT },
        )
        Phase.CHOOSE_TEXT -> ChooseTextView(
            previewPlaceName = course.places.firstOrNull()?.placeName
                ?: stringResource(R.string.vlog_font_sampleName),
            style = style,
            onSelectFont = {
                style = style.copy(font = it)
                prefs.edit().putString("vlog.font", it.key).apply()
                Haptics.light(view)
            },
            onSelectScale = {
                style = style.copy(scale = it)
                prefs.edit().putString("vlog.fontScale", it.key).apply()
                Haptics.light(view)
            },
            onSelectFields = {
                style = style.copy(fields = it)
                prefs.edit().putString("vlog.subtitleFields", it.key).apply()
                Haptics.light(view)
            },
            onSelectColor = {
                style = style.copy(color = it)
                prefs.edit().putString("vlog.subtitleColor", it.key).apply()
                Haptics.light(view)
            },
            onToggleHold = {
                style = style.copy(holdsSubtitle = it)
                prefs.edit().putBoolean("vlog.subtitleHold", it).apply()
                Haptics.light(view)
            },
            onNext = {
                editingClip = clipsForCaption.firstOrNull()?.clipFileName
                phase = Phase.CHOOSE_CAPTION
            },
            onBack = { phase = Phase.CHOOSE_BGM },
        )
        Phase.CHOOSE_CAPTION -> ChooseCaptionView(
            sessionId = sessionId,
            clips = clipsForCaption,
            style = style,
            isReels = selectedFormats.contains("reels") || shotPortrait == true,
            editingClip = editingClip,
            onSelectClip = { editingClip = it },
            onCaptionChange = { key, text ->
                style = style.copy(captions = style.captions + (key to text))
            },
            onNext = { phase = Phase.GENERATING },
            onBack = { phase = Phase.CHOOSE_TEXT },
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
        Phase.GUEST_LIMIT -> GuestLimitView(
            onSignUp = onRequestSignUp,
            onLater = onBack,
        )
        Phase.ERROR -> ErrorView(
            message = errorMessage,
            canResume = canResume,
            onRetry = { attempt++; phase = Phase.GENERATING },
            // 실패 후 돌아가기 = 세션 화면 복귀(클립 보존 → 재시도·재촬영 가능, iOS와 동일)
            onDismiss = { exitVlog() },
        )
    }

    // PRO 전용 기능 → 페이월 (iOS showPaywall 시트)
    if (showProNotice) {
        com.seoktaedev.tteona.features.pro.ProPaywallScreen(onDismiss = { showProNotice = false })
    }

    // 생성 실행 (iOS generatingView.task) — attempt가 바뀌면 재시도/이어받기로 다시 실행된다
    LaunchedEffect(phase, attempt) {
        if (phase != Phase.GENERATING) return@LaunchedEffect
        canResume = false
        val uid = AuthService.currentUser.value?.uid
        try {
            if (uid == null) {
                throw VlogServerService.ServerVlogException(
                    LocaleManager.string(context, R.string.vlog_loginRequired),
                    VlogServerService.ErrorKind.DEFINITIVE,
                )
            }
            // 방 선택 시트의 "완성된 브이로그도 공유" 토글과 같은 프리퍼런스를 본다
            val shareVlogPref = context.getSharedPreferences("tteona_prefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("vlog.shareToRooms", true)
            val result = VlogServerService.generate(
                context = context,
                course = course,
                sessionId = sessionId,
                userId = uid,
                formats = selectedFormats.toList(),
                bgm = selectedBgm,
                watermark = !isPro,
                priority = isPro,
                shareRoomIds = if (shareVlogPref) shareRoomIds.toList() else emptyList(),
                style = style,
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
            // 발자취 적재 — 브이로그가 완성된 여행만 지도에 칠해진다 (실패해도 흐름 방해 없음, iOS와 동일)
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                com.seoktaedev.tteona.core.services.FootprintService.record(
                    context.applicationContext, course, sessionId, uid,
                )
            }
            // 완성된 것만 센다(서버/로컬 경로를 가리지 않는다). 게스트가 아니면 셀 이유가 없다.
            if (isGuest) GuestVlogQuota.recordCompletion()
            // 퍼널 ④ 브이로그 완성 — 코스 → 세션 → 결과물까지 이어진 마지막 단계
            runCatching {
                com.seoktaedev.tteona.core.services.StatsService.postCourseEvent(
                    com.seoktaedev.tteona.core.services.StatsService.CourseFunnelStep.VLOG_COMPLETE, course,
                )
            }
            onVlogCompleted()
            phase = Phase.PREVIEW
            // 튜토리얼: 첫 브이로그 완성 → 축하 카드
            VlogTutorial.advance(VlogTutorial.Step.CELEBRATE)
        } catch (e: CancellationException) {
            throw e   // 화면 이탈 등 정상 취소 — 에러 화면을 띄우지 않는다
        } catch (e: Exception) {
            // 서버가 아직 이 잡을 붙잡고 있으면 이어받기를 안내한다.
            // 지금 포기하면 서버는 헛일을 하고 유저는 영상을 통째로 잃는다.
            val serverErr = e as? VlogServerService.ServerVlogException
            if (serverErr?.isGuestLimit == true) {
                // 기기 쪽 셈을 서버에 맞춘다 — 어긋난 채로 두면 만들려 할 때마다
                // 서버까지 갔다가 거절당하는 길을 매번 되풀이한다.
                GuestVlogQuota.markExhausted()
                phase = Phase.GUEST_LIMIT
                return@LaunchedEffect
            }
            val definitive = serverErr?.isDefinitive ?: false
            val pending = VlogServerService.hasPendingJob(context, sessionId)
            if (!definitive && pending) {
                canResume = true
                errorMessage = LocaleManager.string(context, R.string.vlog_error_serverBusy)
            } else {
                canResume = false
                errorMessage = e.message
            }
            phase = Phase.ERROR
        }
    }
}

private enum class Phase { CHOOSE_FORMAT, CHOOSE_BGM, CHOOSE_TEXT, CHOOSE_CAPTION, GENERATING, PREVIEW, ERROR, GUEST_LIMIT }

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
    val tutStep by VlogTutorial.step.collectAsState()
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

            // 튜토리얼: 기본 포맷 그대로 다음 단계로 유도
            if (tutStep == VlogTutorial.Step.CHOOSE_FORMAT) {
                TutorialBubble(
                    text = stringResource(R.string.tutorial_format_text),
                    mascotRes = R.drawable.tteoni_travel,
                ) { VlogTutorial.finish() }
                Spacer(Modifier.height(8.dp))
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TteOrange)
                    .tutorialGlow(tutStep == VlogTutorial.Step.CHOOSE_FORMAT, cornerRadius = 16)
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
                // 제목은 남는 폭 안에서만 차지하고, 좁으면 말줄임 — 뱃지가 눌려 세로로 뭉치지 않게 한다
                Text(
                    title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    ratio, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TteOrange,
                    maxLines = 1, softWrap = false,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(TteOrange.copy(alpha = 0.18f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
                when {
                    // 라운드 pill + 단일 줄 — 긴 문구(영어)에서도 원형 blob으로 뭉치지 않는다
                    badge != null -> Text(
                        "✨ $badge", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        maxLines = 1, softWrap = false,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
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
    val tutStep by VlogTutorial.step.collectAsState()
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

            // 튜토리얼: 자동 추천 BGM 그대로 생성 유도
            if (tutStep == VlogTutorial.Step.CHOOSE_BGM) {
                TutorialBubble(
                    text = stringResource(R.string.tutorial_bgm_text),
                    mascotRes = R.drawable.tteoni_wink,
                ) { VlogTutorial.finish() }
                Spacer(Modifier.height(8.dp))
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TteOrange)
                    .tutorialGlow(tutStep == VlogTutorial.Step.CHOOSE_BGM, cornerRadius = 16)
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

// ── 글씨 스타일 선택 (iOS chooseTextView) ────────────────────────────────

@Composable
private fun ChooseTextView(
    previewPlaceName: String,
    style: VlogSubtitleStyle,
    onSelectFont: (VlogFont) -> Unit,
    onSelectScale: (VlogFontScale) -> Unit,
    onSelectFields: (VlogSubtitleFields) -> Unit,
    onSelectColor: (VlogSubtitleColor) -> Unit,
    onToggleHold: (Boolean) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val font = style.font
    val scale = style.scale
    val selectedFont = font.key
    val selectedFontScale = scale.key
    val previewDate = remember {
        SimpleDateFormat("yyyy.MM.dd  HH:mm", Locale.KOREA).format(Date())
    }

    Box(Modifier.fillMaxSize()) {
        VlogAuroraBackground()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(48.dp))
            Text(stringResource(R.string.vlog_textSheet_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                stringResource(R.string.vlog_textSheet_subtitle),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 6.dp),
            )

            // 실제 자막이 얹히는 모습 미리보기 (영상 프레임 흉내).
            // 크기·줄임 계산은 캡션 화면과 **같은 규칙**을 쓴다 — 두 화면이 다르게 그리면
            // 어느 쪽을 믿어야 할지 알 수 없다.
            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp)
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2E1A0D), Color(0xFF4D2914)),
                        ),
                    ),
            ) {
                val cardW = maxWidth.value.toDouble()
                val placeSize = videoPlaceSize(scale)
                // 강조색은 **첫 줄에만** 붙는다 — 장소를 끄고 시각만 보는 사람에게도
                // 색 선택이 보여야 하므로, 색을 장소 줄에 고정하지 않는다 (렌더러와 같은 규칙).
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (style.fields.showsPlace) {
                        SubtitlePreviewLine(
                            previewPlaceName, font, placeSize, 1080.0, cardW, style.color.color,
                        )
                    }
                    if (style.fields.showsTime) {
                        SubtitlePreviewLine(
                            previewDate, font, placeSize * 0.62, 1080.0, cardW,
                            if (style.fields.showsPlace) Color.White else style.color.color,
                        )
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 22.dp, bottom = 12.dp),
            ) {
                // 서체
                Text(stringResource(R.string.vlog_textSheet_fontLabel), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    VlogFont.entries.forEach { f ->
                        val isOn = f.key == selectedFont
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = if (isOn) 0.14f else 0.06f))
                                .then(if (isOn) Modifier.border(1.5.dp, TteOrange, RoundedCornerShape(14.dp)) else Modifier)
                                .clickable { onSelectFont(f) }
                                .padding(horizontal = 16.dp),
                        ) {
                            Text(
                                stringResource(f.labelRes),
                                fontFamily = f.family,
                                fontSize = 17.sp,
                                color = if (isOn) Color.White else Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                }

                // 크기
                Text(stringResource(R.string.vlog_textSheet_sizeLabel), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    VlogFontScale.entries.forEach { s ->
                        val isOn = s.key == selectedFontScale
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = if (isOn) 0.14f else 0.06f))
                                .then(if (isOn) Modifier.border(1.5.dp, TteOrange, RoundedCornerShape(14.dp)) else Modifier)
                                .clickable { onSelectScale(s) },
                        ) {
                            Text(
                                stringResource(s.labelRes),
                                fontSize = 15.sp,
                                fontWeight = if (isOn) FontWeight.Bold else FontWeight.Normal,
                                color = if (isOn) Color.White else Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                }

                // 표시 항목 — 장소 / 시각 / 둘 다
                Text(stringResource(R.string.vlog_textSheet_fieldsLabel), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    VlogSubtitleFields.entries.forEach { f ->
                        val isOn = style.fields == f
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = if (isOn) 0.14f else 0.06f))
                                .then(if (isOn) Modifier.border(1.5.dp, TteOrange, RoundedCornerShape(14.dp)) else Modifier)
                                .clickable { onSelectFields(f) },
                        ) {
                            Text(
                                stringResource(f.labelRes),
                                fontSize = 14.sp,
                                fontWeight = if (isOn) FontWeight.Bold else FontWeight.Normal,
                                color = if (isOn) Color.White else Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                            )
                        }
                    }
                }

                // 강조색 — 첫 줄에 적용된다(렌더러와 같은 규칙)
                Text(stringResource(R.string.vlog_textSheet_colorLabel), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    VlogSubtitleColor.entries.forEach { c ->
                        val isOn = style.color == c
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                // 먹색은 어두운 배경에서 안 보인다 — 흰 테두리로 존재를 알린다
                                .background(c.color)
                                .then(
                                    if (isOn) Modifier.border(2.5.dp, Color.White, CircleShape)
                                    else Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                )
                                .clickable { onSelectColor(c) },
                        ) {
                            if (isOn) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = stringResource(c.labelRes),
                                    tint = if (c == VlogSubtitleColor.WHITE || c == VlogSubtitleColor.YELLOW)
                                        Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                // 자막 유지 — 끄면 2.5초만 보이고 사라진다
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.vlog_textSheet_holdLabel),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Text(
                            stringResource(R.string.vlog_textSheet_holdHint),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Switch(
                        checked = style.holdsSubtitle,
                        onCheckedChange = onToggleHold,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TteOrange,
                        ),
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
                    .clickable { onNext() },
            ) {
                Text(stringResource(R.string.common_next), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text(
                stringResource(R.string.vlog_back),
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(top = 14.dp, bottom = 36.dp)
                    .clickable { onBack() },
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

// ── 장소별 한 줄 문구 (iOS chooseCaptionView) ────────────────────────────

/**
 * 장소마다 한 줄씩 적는 화면.
 *
 * 서체·크기·표시항목·색은 브이로그 전체에 공통이고 **이 문구만 장소마다 다르다.**
 * (예전엔 한 줄을 브이로그 하나에 하나만 두어 모든 클립에 같은 말이 반복됐다)
 *
 * 미리보기는 완성될 영상과 **같은 비율**로 보여준다. 가로로 넓은 카드에 자막을 얹어 두면
 * 실제 결과물이 그렇게 나오는 줄 오해한다 — 세로 촬영이면 9:16, 가로면 16:9로 맞춰
 * 잘린 모습까지 그대로 보인다.
 */
@Composable
private fun ChooseCaptionView(
    sessionId: String,
    clips: List<Place>,
    style: VlogSubtitleStyle,
    isReels: Boolean,
    editingClip: String?,
    onSelectClip: (String?) -> Unit,
    onCaptionChange: (String, String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val previewDate = remember { SimpleDateFormat("yyyy.MM.dd  HH:mm", Locale.KOREA).format(Date()) }
    val current = clips.firstOrNull { it.clipFileName == editingClip } ?: clips.firstOrNull()
    val currentKey = current?.clipFileName ?: ""
    val currentCaption = style.captions[currentKey] ?: ""

    // 클립 첫 프레임 — 한 번 뽑은 건 다시 뽑지 않는다
    var frames by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    LaunchedEffect(currentKey) {
        if (currentKey.isEmpty() || frames.containsKey(currentKey)) return@LaunchedEffect
        val place = current ?: return@LaunchedEffect
        val file = VlogClips.clipFile(context, place, sessionId)
        if (!file.exists()) return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) {
            // MediaMetadataRetriever가 AutoCloseable이 된 건 API 29부터다.
            // minSdk 26에서 `use {}`를 쓰면 컴파일은 통과하고 구형 기기에서만 터진다.
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(file.absolutePath)
                // 0.3초 지점 — 첫 프레임은 아직 노출이 안 잡혀 어두운 경우가 많다
                r.getFrameAtTime(300_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Exception) {
                null
            } finally {
                runCatching { r.release() }
            }
        }
        if (bmp != null) frames = frames + (currentKey to bmp.asImageBitmap())
    }

    Box(Modifier.fillMaxSize()) {
        VlogAuroraBackground()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding(),
        ) {
            Spacer(Modifier.height(40.dp))
            Text(stringResource(R.string.vlog_caption_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                stringResource(R.string.vlog_caption_subtitle),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, start = 32.dp, end = 32.dp),
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 20.dp, bottom = 12.dp),
            ) {
                // 클립이 하나뿐이면 고를 것이 없다 — 칩 줄을 감춘다
                if (clips.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                    ) {
                        clips.forEach { place ->
                            val isOn = place.clipFileName == currentKey
                            val written = !(style.captions[place.clipFileName ?: ""] ?: "").isEmpty()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .height(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isOn) TteOrange else Color.White.copy(alpha = 0.10f))
                                    .clickable {
                                        Haptics.light(view)
                                        onSelectClip(place.clipFileName)
                                    }
                                    .padding(horizontal = 12.dp),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isOn) Color.White.copy(alpha = 0.25f)
                                            else TteOrange.copy(alpha = 0.18f)
                                        ),
                                ) {
                                    Text(
                                        "${place.order}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isOn) Color.White else TteOrange,
                                        style = BadgeNumberTextStyle,
                                    )
                                }
                                Text(
                                    place.placeName,
                                    fontSize = 13.sp,
                                    fontWeight = if (isOn) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isOn) Color.White else Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                // 이미 적은 곳은 표시해 둔다 — 여러 곳을 오갈 때 어디를 채웠는지 놓치기 쉽다
                                if (written) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = if (isOn) Color.White else TteOrange,
                                        modifier = Modifier.size(11.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // 미리보기 — 완성될 영상과 같은 비율
                val previewHeight = if (isReels) 300.dp else 190.dp
                val previewWidth = previewHeight * (if (isReels) 9f / 16f else 16f / 9f)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = previewWidth, height = previewHeight)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF2E1A0D), Color(0xFF4D2914)))
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                ) {
                    frames[currentKey]?.let { frame ->
                        Image(
                            bitmap = frame,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,   // 서버의 cover 크롭과 같은 방식
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        val videoWidth = if (isReels) 1080.0 else 1920.0
                        val placeSize = videoPlaceSize(style.scale)
                        val cardW = previewWidth.value.toDouble()
                        if (style.fields.showsPlace) {
                            SubtitlePreviewLine(
                                current?.placeName ?: "", style.font,
                                placeSize, videoWidth, cardW, style.color.color,
                            )
                        }
                        if (style.fields.showsTime) {
                            SubtitlePreviewLine(
                                previewDate, style.font, placeSize * 0.62, videoWidth, cardW,
                                if (style.fields.showsPlace) Color.White else style.color.color,
                            )
                        }
                        SubtitlePreviewLine(
                            VlogSubtitleStyle.sanitize(currentCaption), style.font,
                            placeSize * 0.62, videoWidth, cardW, Color.White,
                        )
                    }
                    Text(
                        if (isReels) "9:16" else "16:9",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }

                // 입력 — 자르기는 한 곳에서만 한다(입력 콜백). 값을 되돌려 쓰는 경로에서
                // 다시 자르면 한 번의 입력에 햅틱이 두 번 울린다.
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) {
                    OutlinedTextField(
                        value = currentCaption,
                        onValueChange = { raw ->
                            if (currentKey.isEmpty()) return@OutlinedTextField
                            val clamped = VlogSubtitleStyle.sanitize(raw)
                            // 자르기와 햅틱을 한곳에서 판단한다.
                            // 값이 그대로면 = 한도에 막힌 것 → 벽에 닿는 감촉으로 알린다.
                            // 아무 반응이 없으면 "왜 안 써지지?"가 되어 고장으로 읽힌다.
                            if (clamped == currentCaption) {
                                if (raw != currentCaption) Haptics.limitReached(view)
                            } else {
                                Haptics.typing(view)
                            }
                            onCaptionChange(currentKey, clamped)
                        },
                        placeholder = {
                            Text(
                                stringResource(R.string.vlog_caption_placeholder),
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 15.sp,
                            )
                        },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.06f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                            focusedBorderColor = TteOrange,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = TteOrange,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${currentCaption.length}/${VlogSubtitleStyle.CAPTION_MAX_LENGTH}",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.4f),
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
                    .clickable { onNext() },
            ) {
                Text(stringResource(R.string.session_makeVlog), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text(
                stringResource(R.string.vlog_back),
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(top = 14.dp, bottom = 30.dp)
                    .clickable { onBack() },
            )
        }
    }
}

/**
 * 자막 미리보기 한 줄.
 *
 * **크기와 줄임은 완성될 영상 기준으로 계산한 뒤 미리보기 크기로 옮긴다.**
 * 미리보기 카드 폭으로 직접 계산하면, 읽히게 하려고 키운 글자 때문에 폭이 모자란 것으로
 * 판정돼 영상에서는 멀쩡한 날짜·이름이 여기서만 "2026.08.22 09:…"처럼 잘린다.
 * 그러면 유저는 "안 되는 건가?" 하게 된다 — 에뮬에서 실제로 그렇게 보였다.
 *
 * 서버 쪽 같은 계산은 server.js의 `placeSize = min(W,H) * 0.042 * fontScale`.
 */
@Composable
private fun SubtitlePreviewLine(
    text: String,
    font: VlogFont,
    /** 영상에서 쓸 글자 크기(px) */
    videoWanted: Double,
    /** 영상 가로 픽셀 — 세로 영상 1080, 가로 영상 1920 */
    videoWidth: Double,
    cardWidthDp: Double,
    color: Color,
) {
    if (text.isEmpty()) return
    val (fittedSize, shownText) = VlogSubtitleFit.fit(text, videoWanted, videoWidth)
    // 그대로 축소하면 9sp라 읽히지 않는다 — 조금 키워 그린다.
    // 다만 확대분이 카드를 넘치면 안 되므로, 넘칠 만큼은 키우지 않는다.
    val cardScale = cardWidthDp / videoWidth
    val baseSp = fittedSize * cardScale
    val roomRatio = if (baseSp > 0) {
        (cardWidthDp * 0.92) / VlogSubtitleFit.estimatedWidth(shownText, baseSp)
    } else 1.0
    val magnify = minOf(SUBTITLE_PREVIEW_MAGNIFY, maxOf(1.0, roomRatio))

    Text(
        shownText,
        fontFamily = font.family,
        fontSize = (baseSp * magnify).sp,
        color = color,
        maxLines = 1,
        textAlign = TextAlign.Center,
    )
}

/** 미리보기는 실제보다 조금 크게 그린다 — 그대로 축소하면 읽히지 않는다 (iOS previewMagnify) */
private const val SUBTITLE_PREVIEW_MAGNIFY = 1.7

/** 영상에서 쓰는 장소명 크기 — 서버와 같은 식(짧은 변의 4.2% × 배율) */
private fun videoPlaceSize(scale: VlogFontScale): Double = 1080.0 * 0.042 * scale.multiplier

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
    val tutStep by VlogTutorial.step.collectAsState()
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
        // 완성 헤더 — 축하하는 나루 + 문구 + 자동 저장 안내
        Spacer(Modifier.height(40.dp))
        Image(
            painter = painterResource(R.drawable.tteoni_jump),
            contentDescription = null,
            modifier = Modifier.height(96.dp),
        )
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

        // 튜토리얼 완주 — 축하 + 무료 한도(6곳×5초) 안내
        if (tutStep == VlogTutorial.Step.CELEBRATE) {
            TutorialCelebrateOverlay { VlogTutorial.finish() }
        }
    }
}

private enum class ThumbState { IDLE, UPLOADING, DONE, FAILED }

// PickVisualMedia 콜백 → LaunchedEffect 전달용 (Compose 상태로 담기엔 과한 일회성 값)
private var pendingThumbUri: Uri? = null

// ── 게스트 한도 (iOS guestLimitView) ─────────────────────────────────────

/**
 * 게스트가 무료 체험 브이로그를 이미 만든 경우.
 *
 * 막는 화면이 아니라 **결과물을 손에 쥔 사람에게 다음을 권하는 화면**이다 —
 * 여기까지 온 사람은 이미 브이로그 하나를 만들어 봤고, 그게 가장 좋은 설득이다.
 */
@Composable
private fun GuestLimitView(onSignUp: () -> Unit, onLater: () -> Unit) {
    val view = LocalView.current
    Box(Modifier.fillMaxSize()) {
        VlogAuroraBackground()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 32.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.tteoni_thumbsup),
                contentDescription = null,
                modifier = Modifier.size(140.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.vlog_guestLimit_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.vlog_guestLimit_message),
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(32.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TteOrange)
                    .clickable {
                        Haptics.light(view)
                        onSignUp()
                    },
            ) {
                Text(
                    stringResource(R.string.vlog_guestLimit_signUp),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Text(
                stringResource(R.string.vlog_guestLimit_later),
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 18.dp).clickable { onLater() },
            )
        }
    }
}

// ── 에러 (iOS errorView) ─────────────────────────────────────────────────

@Composable
private fun ErrorView(
    message: String?,
    canResume: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize().background(Color.Black),
    ) {
        Spacer(Modifier.weight(1f))
        Icon(
            if (canResume) Icons.Filled.History else Icons.Filled.Error,
            contentDescription = null,
            tint = if (canResume) TteOrange else Color.Red,
            modifier = Modifier.size(48.dp),
        )
        Text(
            stringResource(if (canResume) R.string.vlog_error_serverBusy_title else R.string.vlog_failed),
            fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
        )
        message?.let {
            Text(
                it, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
        // 다시 시도 — 서버에 잡이 남아 있으면 업로드를 건너뛰고 완성본만 받아온다
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(TteOrange)
                .clickable(onClick = onRetry),
        ) {
            Text(
                stringResource(if (canResume) R.string.vlog_resume else R.string.vlog_retry),
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
            )
        }
        Text(
            stringResource(R.string.vlog_goBack),
            fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp),
        )
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
