package com.seoktaedev.tteona.features.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.services.RoomService
import com.seoktaedev.tteona.core.services.UserService
import com.seoktaedev.tteona.core.util.Haptics
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import com.seoktaedev.tteona.ui.theme.glowCircle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * 온보딩 — iOS Features/Auth/OnboardingView.swift + OnboardingHero.swift의 이식본.
 * 5단계: 스플래시 히어로 → 기능 소개 슬라이드 → 닉네임 → 권한 → 약관 동의.
 */
@Composable
fun OnboardingScreen() {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var nickname by rememberSaveable { mutableStateOf("") }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 기능 소개(step 1)는 자체 페이지 도트가 있으므로 닉네임 단계부터 표시 (iOS progressBar)
            if (step > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 16.dp)
                        .padding(horizontal = 24.dp),
                ) {
                    (1 until 5).forEach { i ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(if (i < step) TteOrange else TteMediumGray.copy(alpha = 0.2f))
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (slideInHorizontally { it / 3 } + fadeIn())
                        .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
                },
                label = "onboarding-step",
                modifier = Modifier.weight(1f),
            ) { current ->
                when (current) {
                    0 -> SplashStep(onNext = { step = 1 })
                    1 -> FeatureShowcaseStep(onFinish = { step = 2 })
                    2 -> NicknameStep(
                        nickname = nickname,
                        onNicknameChange = { nickname = it },
                        onNext = { step = 3 },
                    )
                    3 -> PermissionStep(onNext = { step = 4 })
                    else -> TermsStep(nickname = nickname)
                }
            }
        }
    }
}

// ── 공통: 플로팅 모디파이어 (iOS FloatingEffect) ─────────────────────────

private fun Modifier.floating(amplitude: Float = 7f, speed: Float = 1.3f, phase: Float = 0f): Modifier =
    composed {
        val infinite = rememberInfiniteTransition(label = "float")
        val t by infinite.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween((4000 / speed).toInt(), easing = LinearEasing)),
            label = "float-t",
        )
        graphicsLayer {
            translationY = sin((t + phase).toDouble()).toFloat() * amplitude * density
            rotationZ = sin((t * 0.7f + phase).toDouble()).toFloat() * 1.2f
        }
    }

// ── 공통: 아우라 배경 (iOS AuroraBackground) ─────────────────────────────

@Composable
private fun AuroraBackground(tint: Color, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "aurora")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "aurora-t",
    )
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .size(320.dp)
                .offset(x = (40 + sin(t.toDouble()) * 40).dp, y = (60 + cos(t.toDouble() * 1.3) * 30).dp)
                .glowCircle(tint, 0.16f)
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .size(280.dp)
                .offset(x = (cos(t.toDouble()) * 35).dp, y = (sin(t.toDouble() * 1.4) * 40).dp)
                .glowCircle(tint, 0.10f)
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .size(250.dp)
                .offset(x = (60 + sin(t.toDouble() + 2) * 45).dp, y = (cos(t.toDouble() * 1.2) * 30).dp)
                .glowCircle(tint, 0.12f)
        )
    }
}

// ── 공통: 다음 버튼 ─────────────────────────────────────────────────────

@Composable
private fun NextButton(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(TteOrange.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

// ── Step 0: 스플래시 히어로 (iOS SplashHeroView) ─────────────────────────

@Composable
private fun SplashStep(onNext: () -> Unit) {
    val view = LocalView.current
    Box(Modifier.fillMaxSize()) {
        AuroraBackground(tint = TteOrange)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            Spacer(Modifier.weight(1f))

            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(260.dp)
                        .glowCircle(TteOrange, 0.22f)
                )
                Image(
                    painterResource(R.drawable.tteoni_front),
                    contentDescription = null,
                    modifier = Modifier
                        .size(200.dp)
                        .floating(amplitude = 8f, speed = 1.1f),
                )
            }

            Spacer(Modifier.height(18.dp))
            Image(
                painterResource(R.drawable.tteona_logo),
                contentDescription = "떠나",
                modifier = Modifier.height(42.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text("특별한 순간을 영상으로 기록하세요", fontSize = 16.sp, color = TteMediumGray)

            Spacer(Modifier.weight(1f))

            Box(Modifier.padding(horizontal = 24.dp)) {
                NextButton("시작하기") {
                    Haptics.light(view)
                    onNext()
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

// ── Step 1: 기능 소개 쇼케이스 (iOS OnboardingFeatureShowcase) ───────────

private data class HeroSlide(
    val mascotRes: Int,
    val title: String,
    val subtitle: String,
    val tint: Color,
    val chips: List<Pair<ImageVector, Double>>, // icon to angle(도)
)

private val heroSlides = listOf(
    HeroSlide(
        R.drawable.tteoni_travel,
        "지도에서 코스 발견",
        "전 세계 여행 코스를 지도에서 한눈에.\n마음에 드는 코스로 바로 떠나보세요",
        Color(0xFFFF6B35),
        listOf(Icons.Filled.Map to -140.0, Icons.Filled.PinDrop to -40.0, Icons.Filled.FlightTakeoff to 95.0),
    ),
    HeroSlide(
        R.drawable.tteoni_wink,
        "도착하면, 딱 5초 촬영",
        "장소에 도착하면 나루가 알려드려요.\n5초씩만 담아도 하루가 기록돼요",
        Color(0xFF2EA8C4),
        listOf(Icons.Filled.NotificationsActive to -135.0, Icons.Filled.CameraAlt to -45.0, Icons.Filled.Timer to 100.0),
    ),
    HeroSlide(
        R.drawable.tteoni_thumbsup,
        "Vlog는 자동 완성",
        "여행이 끝나면 촬영한 클립을 모아\n감성 Vlog 영상을 만들어드려요",
        Color(0xFF8B5CF6),
        listOf(Icons.Filled.Movie to -140.0, Icons.Filled.AutoAwesome to -35.0, Icons.Filled.MusicNote to 90.0),
    ),
    HeroSlide(
        R.drawable.tteoni_jump,
        "함께라서 더 좋아",
        "친구·가족과 그룹을 만들어\n코스와 '나의 오늘'을 공유해보세요",
        Color(0xFFFF4F79),
        listOf(Icons.Filled.Group to -140.0, Icons.AutoMirrored.Filled.Chat to -40.0, Icons.Filled.Favorite to 95.0),
    ),
)

@Composable
private fun FeatureShowcaseStep(onFinish: () -> Unit) {
    val view = LocalView.current
    var slideIndex by rememberSaveable { mutableIntStateOf(0) }
    val slide = heroSlides[slideIndex]
    val isLast = slideIndex == heroSlides.size - 1

    fun advance(forward: Boolean) {
        Haptics.light(view)
        slideIndex = (slideIndex + if (forward) 1 else -1).coerceIn(0, heroSlides.size - 1)
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(slideIndex) {
                var dragX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragX = 0f },
                    onHorizontalDrag = { _, delta -> dragX += delta },
                    onDragEnd = {
                        if (dragX < -60 && !isLast) advance(true)
                        else if (dragX > 60 && slideIndex > 0) advance(false)
                    },
                )
            },
    ) {
        AuroraBackground(tint = slide.tint)

        Column(Modifier.fillMaxSize()) {
            // 상단 로고 + 건너뛰기
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .fillMaxWidth(),
            ) {
                Image(painterResource(R.drawable.tteona_logo), contentDescription = null, modifier = Modifier.height(22.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    "건너뛰기",
                    fontSize = 14.sp,
                    color = TteMediumGray,
                    modifier = Modifier.clickable(onClick = onFinish),
                )
            }

            Spacer(Modifier.weight(1f))

            // 히어로 카드
            AnimatedContent(
                targetState = slideIndex,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally { if (forward) it else -it } + fadeIn())
                        .togetherWith(slideOutHorizontally { if (forward) -it else it } + fadeOut())
                },
                label = "hero-card",
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { index ->
                HeroCard(heroSlides[index])
            }

            Spacer(Modifier.weight(1f))

            // 텍스트
            AnimatedContent(
                targetState = slideIndex,
                transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                label = "hero-text",
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { index ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Text(heroSlides[index].title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TteDarkGray)
                    Text(
                        heroSlides[index].subtitle,
                        fontSize = 16.sp,
                        color = TteMediumGray,
                        textAlign = TextAlign.Center,
                        lineHeight = 23.sp,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // 페이지 인디케이터
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 28.dp),
            ) {
                heroSlides.indices.forEach { i ->
                    Box(
                        Modifier
                            .width(if (i == slideIndex) 24.dp else 8.dp)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(if (i == slideIndex) slide.tint else TteMediumGray.copy(alpha = 0.2f))
                    )
                }
            }

            // 다음/시작 버튼
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(slide.tint)
                    .clickable {
                        if (isLast) {
                            Haptics.medium(view)
                            onFinish()
                        } else {
                            advance(true)
                        }
                    },
            ) {
                Text(
                    if (isLast) "시작하기" else "다음",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun HeroCard(slide: HeroSlide) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(340.dp)) {
        // 유리 카드
        Box(
            Modifier
                .size(290.dp, 300.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
                .background(slide.tint.copy(alpha = 0.08f))
                .border(1.2.dp, slide.tint.copy(alpha = 0.25f), RoundedCornerShape(32.dp))
        )

        // 마스코트
        Image(
            painterResource(slide.mascotRes),
            contentDescription = null,
            modifier = Modifier
                .size(185.dp)
                .floating(amplitude = 7f, speed = 1.25f),
        )

        // 궤도 아이콘 칩
        slide.chips.forEachIndexed { i, (icon, angle) ->
            val rad = Math.toRadians(angle)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(x = (cos(rad) * 132).dp, y = (sin(rad) * 132).dp)
                    .floating(amplitude = 5f, speed = 1.5f, phase = i * 1.7f)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            ) {
                Icon(icon, contentDescription = null, tint = slide.tint, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ── Step 2: 닉네임 (iOS nicknameStep) ────────────────────────────────────

private enum class NicknameState { IDLE, CHECKING, AVAILABLE, TAKEN, INAPPROPRIATE }

@Composable
private fun NicknameStep(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    var state by remember { mutableStateOf(NicknameState.IDLE) }

    // 600ms 디바운스 닉네임 검사 (iOS scheduleNicknameCheck)
    LaunchedEffect(nickname) {
        val trimmed = nickname.trim()
        if (trimmed.length < 2 || trimmed.length > 10) {
            state = NicknameState.IDLE
            return@LaunchedEffect
        }
        state = NicknameState.CHECKING
        delay(600)
        if (!RoomService.isTextAllowed(trimmed)) {
            state = NicknameState.INAPPROPRIATE
            return@LaunchedEffect
        }
        state = if (UserService.isNicknameTaken(trimmed)) NicknameState.TAKEN else NicknameState.AVAILABLE
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Text("닉네임을\n설정해주세요", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TteDarkGray, lineHeight = 38.sp)
            Text("코스를 만들 때 닉네임이 표시됩니다.", fontSize = 15.sp, color = TteMediumGray)
        }

        Spacer(Modifier.weight(1f))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = nickname,
                onValueChange = onNicknameChange,
                placeholder = { Text("닉네임 입력 (2~10자)", color = TteMediumGray) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = TteFieldBackground,
                    focusedContainerColor = TteFieldBackground,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = TteOrange,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .height(20.dp),
            ) {
                when (state) {
                    NicknameState.CHECKING -> {
                        CircularProgressIndicator(color = TteMediumGray, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                        Text("확인 중...", fontSize = 12.sp, color = TteMediumGray)
                    }
                    NicknameState.AVAILABLE -> {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(13.dp))
                        Text("사용 가능한 별명이에요", fontSize = 12.sp, color = Color(0xFF34C759))
                    }
                    NicknameState.TAKEN -> {
                        Icon(Icons.Filled.Cancel, contentDescription = null, tint = Color.Red, modifier = Modifier.size(13.dp))
                        Text("이미 사용 중인 별명이에요", fontSize = 12.sp, color = Color.Red)
                    }
                    NicknameState.INAPPROPRIATE -> {
                        Icon(Icons.Filled.Cancel, contentDescription = null, tint = Color.Red, modifier = Modifier.size(13.dp))
                        Text("사용할 수 없는 표현이 포함돼 있어요", fontSize = 12.sp, color = Color.Red)
                    }
                    NicknameState.IDLE -> Unit
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${nickname.length}/10",
                    fontSize = 12.sp,
                    color = if (nickname.length > 10) Color.Red else TteMediumGray,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Box(Modifier.padding(horizontal = 24.dp)) {
            NextButton("다음", enabled = state == NicknameState.AVAILABLE, onClick = onNext)
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ── Step 3: 권한 (iOS permissionStep) ───────────────────────────────────

@Composable
private fun PermissionStep(onNext: () -> Unit) {
    val context = LocalContext.current

    fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var locationGranted by remember { mutableStateOf(granted(Manifest.permission.ACCESS_FINE_LOCATION)) }
    var notificationGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS))
    }
    var cameraGranted by remember {
        mutableStateOf(granted(Manifest.permission.CAMERA) && granted(Manifest.permission.RECORD_AUDIO))
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locationGranted = it.values.any { g -> g }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationGranted = it
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        cameraGranted = it[Manifest.permission.CAMERA] == true
    }

    fun requestLocation() = locationLauncher.launch(
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    )

    fun requestNotification() {
        if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun requestCamera() = cameraLauncher.launch(
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    )

    Column(Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Text("앱 사용을 위해\n권한이 필요해요", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TteDarkGray, lineHeight = 38.sp)
            Text("아래 권한들은 핵심 기능에 사용됩니다.", fontSize = 15.sp, color = TteMediumGray)
        }

        Spacer(Modifier.weight(1f))

        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            PermissionRow(
                icon = Icons.Filled.LocationOn,
                title = "위치 권한",
                description = "장소 도착을 감지하고 지도에 현재 위치를 표시해요",
                isGranted = locationGranted,
                onTap = ::requestLocation,
            )
            PermissionRow(
                icon = Icons.Filled.Notifications,
                title = "알림 권한",
                description = "장소에 도착하면 촬영 알림을 보내드려요",
                isGranted = notificationGranted,
                onTap = ::requestNotification,
            )
            PermissionRow(
                icon = Icons.Filled.Videocam,
                title = "카메라 권한",
                description = "각 장소에서 5초 영상을 촬영해요",
                isGranted = cameraGranted,
                onTap = ::requestCamera,
            )
        }

        Spacer(Modifier.weight(1f))

        Box(Modifier.padding(horizontal = 24.dp)) {
            // iOS requestAllPermissionsThenContinue — 미허용 권한을 순서대로 요청하고 넘어간다
            NextButton("다음") {
                when {
                    !locationGranted -> requestLocation()
                    !notificationGranted -> requestNotification()
                    !cameraGranted -> requestCamera()
                }
                onNext()
            }
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onTap: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TteFieldBackground)
            .padding(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(TteOrange.copy(alpha = 0.12f)),
        ) {
            Icon(icon, contentDescription = null, tint = TteOrange, modifier = Modifier.size(24.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TteDarkGray)
            Text(description, fontSize = 12.sp, color = TteMediumGray)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(CircleShape)
                .background(if (isGranted) Color(0xFF34C759).copy(alpha = 0.12f) else TteOrange)
                .clickable(enabled = !isGranted, onClick = onTap)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                if (isGranted) "허용됨" else "계속",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isGranted) Color(0xFF34C759) else Color.White,
            )
        }
    }
}

// ── Step 4: 약관 (iOS termsStep) ────────────────────────────────────────

@Composable
private fun TermsStep(nickname: String) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val isLoading by AuthService.isLoading.collectAsState()
    val errorMessage by AuthService.errorMessage.collectAsState()

    var agreedTerms by rememberSaveable { mutableStateOf(false) }
    var agreedPrivacy by rememberSaveable { mutableStateOf(false) }
    val allAgreed = agreedTerms && agreedPrivacy

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "서비스 이용을\n동의해주세요",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = TteDarkGray,
            lineHeight = 38.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
        )

        Spacer(Modifier.weight(1f))

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            // 전체 동의
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (allAgreed) TteOrange.copy(alpha = 0.06f) else TteFieldBackground)
                    .border(
                        1.5.dp,
                        if (allAgreed) TteOrange.copy(alpha = 0.3f) else Color.Transparent,
                        RoundedCornerShape(14.dp),
                    )
                    .clickable {
                        val newValue = !allAgreed
                        agreedTerms = newValue
                        agreedPrivacy = newValue
                    }
                    .padding(18.dp),
            ) {
                Icon(
                    if (allAgreed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (allAgreed) TteOrange else TteMediumGray.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp),
                )
                Text("전체 동의", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TteDarkGray)
            }

            TermsRow(
                title = "서비스 이용약관 동의",
                isChecked = agreedTerms,
                onToggle = { agreedTerms = !agreedTerms },
                onOpen = { openUrl("https://tteona.kr/terms.html") },
            )
            TermsRow(
                title = "개인정보 처리방침 동의",
                isChecked = agreedPrivacy,
                onToggle = { agreedPrivacy = !agreedPrivacy },
                onOpen = { openUrl("https://tteona.kr/privacy.html") },
            )

            errorMessage?.let {
                Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.weight(1f))

        Box(Modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(color = TteOrange)
            } else {
                NextButton("떠나기 시작", enabled = allAgreed) {
                    Haptics.medium(view)
                    scope.launch { AuthService.completeOnboarding(nickname.trim()) }
                }
            }
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun TermsRow(
    title: String,
    isChecked: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        Icon(
            if (isChecked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isChecked) TteOrange else TteMediumGray.copy(alpha = 0.5f),
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = onToggle),
        )
        Text(title, fontSize = 14.sp, color = TteDarkGray)
        Box(
            Modifier
                .clip(CircleShape)
                .background(TteOrange.copy(alpha = 0.1f))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Text("필수", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TteOrange)
        }
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = "약관 보기",
            tint = TteMediumGray.copy(alpha = 0.6f),
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onOpen),
        )
    }
}
