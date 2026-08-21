package com.seoktaedev.tteona.features.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.i18n.AppLanguage
import com.seoktaedev.tteona.core.i18n.LocaleManager
import com.seoktaedev.tteona.core.util.Haptics
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import com.seoktaedev.tteona.ui.theme.TteonaSplashBackground
import kotlinx.coroutines.delay

/**
 * 게스트가 앱을 처음 열었을 때 받는 약관 동의.
 * iOS Features/Auth/GuestTermsGate.swift의 이식본.
 *
 * 계정 온보딩(닉네임·스타일·권한·약관)은 가입한 사람만 거치는데, 게스트도 촬영하고
 * 브이로그를 만들고 앨범에 저장까지 한다 — 서비스를 온전히 쓰면서 동의만 없는 상태였다.
 *
 * **내비 가이드의 한 단계로 넣지 않은 이유**: 그 가이드에는 '건너뛰기'가 있다.
 * 건너뛸 수 있으면 동의가 아니고, 투어의 한 장면으로 보이면 동의를 받았다는 근거도 약하다.
 * 그래서 가이드보다 앞에, 넘어갈 수 없는 화면으로 세운다.
 */
@Composable
fun GuestTermsGate(onAgree: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current

    var agreedTerms by rememberSaveable { mutableStateOf(false) }
    var agreedPrivacy by rememberSaveable { mutableStateOf(false) }
    val allAgreed = agreedTerms && agreedPrivacy

    /** 환영 문구가 다 찍혔는가 — 축하 파티클과 부제 등장을 여기에 맞춘다 */
    var welcomeTyped by remember { mutableStateOf(false) }

    val appeared = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(50)
        appeared.animateTo(1f, tween(550))
    }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Box(Modifier.fillMaxSize()) {
        TteonaSplashBackground()

        Column(Modifier.fillMaxSize()) {
            // 언어를 여기서도 바꿀 수 있어야 한다 — 약관은 읽고 동의하는 화면이라
            // 읽을 수 없는 언어로 떠 있으면 동의를 받는 의미가 없다.
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp),
            ) {
                LanguagePicker()
            }

            Spacer(Modifier.weight(0.6f))

            Image(
                painter = painterResource(R.drawable.tteoni_guide),
                contentDescription = null,
                modifier = Modifier
                    .size(132.dp)
                    .align(Alignment.CenterHorizontally)
                    .alpha(appeared.value),
            )

            // 받을 것(동의)보다 줄 것(브이로그)을 먼저 말한다.
            // 인사 없이 동의부터 요구하면 첫 화면이 요구로 시작해 방어적으로 읽힌다.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .padding(top = 22.dp),
            ) {
                // 이 화면에서 가장 먼저 읽혀야 할 한 줄 — 동의를 요구하기 전에
                // 무엇을 받는지부터 보여준다
                Text(
                    stringResource(R.string.guestTerms_badge),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .alpha(appeared.value)
                        .clip(CircleShape)
                        .background(TteOrange)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )

                // 한 글자씩 찍히고, 다 찍히면 양옆으로 팡
                Box(contentAlignment = Alignment.Center) {
                    if (welcomeTyped) {
                        ConfettiBurst(
                            modifier = Modifier.fillMaxWidth(),
                            pieceCount = 16,
                        )
                    }
                    TypewriterText(
                        text = stringResource(R.string.guestTerms_title),
                        fontSize = 27.sp,
                        color = TteDarkGray,
                        speedMs = 110,
                        onFinished = {
                            Haptics.success(view)
                            welcomeTyped = true
                        },
                    )
                }

                Text(
                    stringResource(R.string.guestTerms_subtitle),
                    fontSize = 16.sp,
                    color = TteDarkGray.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                    // 문구가 다 찍힌 뒤에 이어서 나타난다
                    modifier = Modifier.alpha(if (welcomeTyped) 1f else 0f),
                )
            }

            Spacer(Modifier.weight(1f))

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                Text(
                    stringResource(R.string.guestTerms_consentNote),
                    fontSize = 12.5.sp,
                    color = TteMediumGray,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )

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
                    Text(
                        stringResource(R.string.onboarding_terms_agreeAll),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TteDarkGray,
                    )
                }

                HorizontalDivider(Modifier.padding(horizontal = 8.dp))

                GuestTermsRow(
                    title = stringResource(R.string.onboarding_terms_service),
                    isChecked = agreedTerms,
                    onToggle = { agreedTerms = !agreedTerms },
                    onOpen = { openUrl("https://tteona.kr/terms.html") },
                )
                GuestTermsRow(
                    title = stringResource(R.string.onboarding_terms_privacy),
                    isChecked = agreedPrivacy,
                    onToggle = { agreedPrivacy = !agreedPrivacy },
                    onOpen = { openUrl("https://tteona.kr/privacy.html") },
                )
            }

            Spacer(Modifier.weight(0.7f))

            Button(
                onClick = {
                    Haptics.light(view)
                    onAgree()
                },
                enabled = allAgreed,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TteOrange,
                    contentColor = Color.White,
                    disabledContainerColor = TteOrange.copy(alpha = 0.4f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(54.dp),
            ) {
                Text(
                    stringResource(R.string.guestTerms_start),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ── 약관 한 줄 (온보딩 TermsRow와 같은 모양) ─────────────────────────────

@Composable
private fun GuestTermsRow(
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
            Text(
                stringResource(R.string.onboarding_terms_required),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TteOrange,
            )
        }
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = stringResource(R.string.onboarding_viewTerms),
            tint = TteMediumGray.copy(alpha = 0.6f),
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onOpen),
        )
    }
}

// ── 언어 선택 ────────────────────────────────────────────────────────────

@Composable
private fun LanguagePicker() {
    val context = LocalContext.current
    val view = LocalView.current
    var expanded by remember { mutableStateOf(false) }
    val current = LocaleManager.current(context)

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(CircleShape)
                .background(TteFieldBackground)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Filled.Language, contentDescription = null, tint = TteMediumGray, modifier = Modifier.size(15.dp))
            Text(current.nativeName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TteMediumGray)
            Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = TteMediumGray, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text("${language.flag}  ${language.nativeName}") },
                    onClick = {
                        expanded = false
                        if (language == current) return@DropdownMenuItem
                        Haptics.light(view)
                        LocaleManager.setLanguage(context, language)
                        // baseContext에 새 로케일을 다시 씌우기 위해 액티비티 재생성
                        (context as? Activity)?.recreate()
                    },
                )
            }
        }
    }
}

// ── 한 글자씩 찍히는 문구 ────────────────────────────────────────────────

/**
 * 한 글자씩 나타나는 텍스트. 다 찍히면 [onFinished]를 한 번 부른다.
 *
 * 글자 수만큼 상태를 갱신하므로 **짧은 문구에만** 쓴다. 표시 중에도 전체 문구가 차지할
 * 자리를 미리 잡아 둬야 하는데(안 그러면 글자가 늘 때마다 아래 요소가 밀린다),
 * 투명한 전체 문구를 뒤에 깔아 높이를 확보한다.
 */
@Composable
private fun TypewriterText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    speedMs: Long,
    onFinished: () -> Unit,
) {
    var shown by remember(text) { mutableStateOf(0) }

    LaunchedEffect(text) {
        shown = 0
        for (i in 1..text.length) {
            shown = i
            delay(speedMs)
        }
        onFinished()
    }

    Box(contentAlignment = Alignment.Center) {
        // 최종 크기를 미리 차지해 둔다 — 글자가 늘어날 때 레이아웃이 흔들리지 않게
        Text(
            text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            lineHeight = fontSize * 1.28f,
            textAlign = TextAlign.Center,
            color = Color.Transparent,
        )
        Text(
            text.take(shown),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            lineHeight = fontSize * 1.28f,
            textAlign = TextAlign.Center,
            color = color,
        )
    }
}

// ── 축하 파티클 ──────────────────────────────────────────────────────────

/**
 * 문구 양옆에서 바깥으로 퍼지는 축하 조각. iOS ConfettiBurst의 이식본.
 * 한 번만 터지고 사라진다 — 반복하면 축하가 아니라 배경 장식이 된다.
 */
@Composable
private fun ConfettiBurst(
    modifier: Modifier = Modifier,
    pieceCount: Int = 16,
    distanceDp: Float = 130f,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(900, easing = LinearEasing))
    }

    val colors = listOf(
        TteOrange,
        Color(0xFFFFD400),
        Color(0xFF3DDC97),
        Color(0xFF4FC3F7),
        Color(0xFFFF7AB6),
    )
    // 조각의 방향·색·크기는 매 프레임 다시 뽑으면 안 된다(춤을 춘다) — 한 번만 정한다
    val pieces = remember {
        List(pieceCount) { i ->
            val toRight = i % 2 == 0
            // 좌우 각각 -45°..+45° 부채꼴
            val spread = (-45f + 90f * (i / 2) / (pieceCount / 2f - 1f).coerceAtLeast(1f))
            Triple(
                if (toRight) spread else 180f - spread,
                colors[i % colors.size],
                4f + (i % 3),
            )
        }
    }

    androidx.compose.foundation.Canvas(modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val travel = distanceDp * density * t
        // 끝으로 갈수록 옅어지며 아래로 조금 처진다
        val fade = (1f - t).coerceIn(0f, 1f)
        val gravity = 40f * density * t * t
        pieces.forEach { (angleDeg, color, radius) ->
            val rad = Math.toRadians(angleDeg.toDouble())
            val x = center.x + (Math.cos(rad) * travel).toFloat()
            val y = center.y - (Math.sin(rad) * travel).toFloat() + gravity
            drawCircle(
                color = color.copy(alpha = fade),
                radius = radius * density,
                center = androidx.compose.ui.geometry.Offset(x, y),
            )
        }
    }
}
