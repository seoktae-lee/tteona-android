package com.seoktaedev.tteona.features.pro

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.i18n.LocaleManager
import com.seoktaedev.tteona.core.services.ProManager
import com.seoktaedev.tteona.core.util.Haptics
import com.seoktaedev.tteona.features.common.InfoAlert
import com.seoktaedev.tteona.ui.theme.TteOrange
import com.seoktaedev.tteona.ui.theme.glowCircle
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency

/**
 * tteona PRO 구독 페이월 — iOS Features/Pro/ProPaywallView.swift의 이식본.
 * 연간 플랜을 기본 선택 + 할인율·월환산가·정가 비교로 강조하고,
 * 등장 스태거·선택 스프링·CTA 슈머 등 가벼운 인터랙션으로 결제 전환을 돕는다.
 */
@Composable
fun ProPaywallScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val isPro by ProManager.isPro.collectAsState()
    val offerings by ProManager.offerings.collectAsState()

    val packages = offerings?.current?.availablePackages ?: emptyList()
    val annual = packages.firstOrNull { it.packageType == PackageType.ANNUAL }
    val monthly = packages.firstOrNull { it.packageType == PackageType.MONTHLY }

    var selectedPackage by remember { mutableStateOf<Package?>(null) }
    var isPurchasing by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf<String?>(null) }

    // 등장 스태거 트리거 (iOS appeared)
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        ProManager.loadOfferings()
        appeared = true
    }
    LaunchedEffect(packages.size) {
        if (selectedPackage == null) selectedPackage = annual ?: packages.firstOrNull()
    }
    LaunchedEffect(isPro) {
        if (isPro) onDismiss()
    }

    // CTA 슈머 스윕 + 할인 배지 맥동 (iOS shimmer/badgePulse)
    val infinite = rememberInfiniteTransition(label = "paywall-fx")
    val shimmerT by infinite.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "paywall-shimmer",
    )
    val badgePulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "paywall-badge-pulse",
    )

    val hasFreeTrial = selectedPackage?.product?.defaultOption?.freePhase != null

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    fun select(pkg: Package) {
        if (selectedPackage?.identifier == pkg.identifier) return
        Haptics.light(view)
        selectedPackage = pkg
    }

    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
        PaywallAuroraBackground()
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            // 닫기
            Row(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp).fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(onClick = onDismiss),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close), tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                // 히어로 — 로고+태그라인 (등장 스태거 0)
                val heroT by animateFloatAsState(
                    targetValue = if (appeared) 1f else 0f,
                    animationSpec = tween(500),
                    label = "hero",
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer {
                        alpha = heroT
                        translationY = (1f - heroT) * 12.dp.toPx()
                    },
                ) {
                    Image(
                        painterResource(R.drawable.tteona_pro_logo),
                        contentDescription = "tteona PRO",
                        modifier = Modifier.padding(top = 12.dp).height(34.dp),
                    )
                    Text(
                        stringResource(R.string.paywall_tagline),
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }

                // 기능 리스트 — 행별 스태거 등장 (iOS delay 0.12 + i*0.07)
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(top = 24.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .padding(20.dp),
                ) {
                    val features = listOf(
                        Triple(Icons.Filled.AutoAwesome, R.string.paywall_feature_watermark, R.string.paywall_feature_watermark_sub),
                        Triple(Icons.Filled.ViewCarousel, R.string.paywall_feature_multiformat, R.string.paywall_feature_multiformat_sub),
                        Triple(Icons.Filled.LibraryMusic, R.string.paywall_feature_bgm, R.string.paywall_feature_bgm_sub),
                        Triple(Icons.Filled.Timer, R.string.paywall_feature_duration, R.string.paywall_feature_duration_sub),
                        Triple(Icons.Filled.Bolt, R.string.paywall_feature_priority, R.string.paywall_feature_priority_sub),
                    )
                    features.forEachIndexed { index, (icon, title, sub) ->
                        val t by animateFloatAsState(
                            targetValue = if (appeared) 1f else 0f,
                            animationSpec = tween(450, delayMillis = 120 + index * 70),
                            label = "feature-$index",
                        )
                        FeatureRow(
                            icon, stringResource(title), stringResource(sub),
                            modifier = Modifier.graphicsLayer {
                                alpha = t
                                translationX = (1f - t) * 18.dp.toPx()
                            },
                        )
                    }
                }

                if (packages.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 24.dp),
                    ) {
                        Text(stringResource(R.string.paywall_loadFailed), fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                        Text(
                            stringResource(R.string.paywall_retry),
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TteOrange,
                            modifier = Modifier.clickable { ProManager.loadOfferings() },
                        )
                    }
                } else {
                    // 플랜 카드 (등장 스태거 마지막)
                    val cardsT by animateFloatAsState(
                        targetValue = if (appeared) 1f else 0f,
                        animationSpec = tween(550, delayMillis = 400),
                        label = "cards",
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .padding(top = 24.dp, bottom = 12.dp)
                            .graphicsLayer {
                                alpha = cardsT
                                translationY = (1f - cardsT) * 16.dp.toPx()
                            },
                    ) {
                        annual?.let { AnnualCard(it, monthly, selectedPackage, badgePulse, onSelect = { select(it) }) }
                        monthly?.let { MonthlyCard(it, selectedPackage, onSelect = { select(it) }) }
                    }
                }
            }

            // CTA + 심리 안전판 + 링크
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    TteOrange.copy(alpha = if (selectedPackage != null && !isPurchasing) 1f else 0.5f),
                                    TteOrange.copy(alpha = if (selectedPackage != null && !isPurchasing) 0.85f else 0.4f),
                                )
                            )
                        )
                        .clickable(enabled = selectedPackage != null && !isPurchasing) {
                            val activity = context as? Activity ?: return@clickable
                            val pkg = selectedPackage ?: return@clickable
                            isPurchasing = true
                            scope.launch {
                                try {
                                    if (ProManager.purchase(activity, pkg)) Haptics.success(view)
                                } catch (e: Exception) {
                                    alertMessage = LocaleManager.string(context, R.string.paywall_purchaseFailed)
                                } finally {
                                    isPurchasing = false
                                }
                            }
                        },
                ) {
                    // 좌→우로 흐르는 밝은 띠 — 버튼에 시선을 잡아둔다 (iOS shimmer)
                    Box(Modifier.matchParentSize()) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.45f)
                                .matchParentSize()
                                .graphicsLayer { translationX = size.width / 0.45f * shimmerT }
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, Color.White.copy(alpha = 0.30f), Color.Transparent)
                                    )
                                ),
                        )
                    }
                    if (isPurchasing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            if (hasFreeTrial) stringResource(R.string.paywall_startFreeTrial)
                            else stringResource(R.string.paywall_subscribe),
                            fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        )
                    }
                }

                // 결제 심리 안전판 — 체험이면 '지금 결제 안 됨'을 명시해 진입 장벽을 낮춘다 (iOS와 동일)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(13.dp))
                    Text(
                        if (hasFreeTrial) "${stringResource(R.string.paywall_noPaymentNow)} · ${stringResource(R.string.paywall_cancelAnytime)}"
                        else stringResource(R.string.paywall_cancelAnytime),
                        fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.75f),
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp, bottom = 24.dp),
                ) {
                    Text(
                        stringResource(R.string.paywall_restore), fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.clickable {
                            scope.launch {
                                alertMessage = try {
                                    if (ProManager.restore()) LocaleManager.string(context, R.string.paywall_restored) else LocaleManager.string(context, R.string.paywall_nothingToRestore)
                                } catch (e: Exception) {
                                    LocaleManager.string(context, R.string.paywall_restoreFailed)
                                }
                            }
                        },
                    )
                    Text(
                        stringResource(R.string.settings_terms), fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { openUrl("https://tteona.kr/terms.html") },
                    )
                    Text(
                        stringResource(R.string.paywall_privacyPolicy), fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { openUrl("https://tteona.kr/privacy.html") },
                    )
                }
            }
        }
    }

    alertMessage?.let { InfoAlert(stringResource(R.string.common_notice), it) { alertMessage = null } }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = modifier) {
        Icon(icon, contentDescription = null, tint = TteOrange, modifier = Modifier.width(28.dp).size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
        }
    }
}

// 통화 형식 문자열 (amountMicros → "₩44,000") — 취소선 정가·월환산가 계산용
private fun formatPrice(amountMicros: Long, currencyCode: String): String? =
    runCatching {
        val nf = NumberFormat.getCurrencyInstance()
        nf.currency = Currency.getInstance(currencyCode)
        nf.format(amountMicros / 1_000_000.0)
    }.getOrNull()

// MARK: - 연간 카드 (기본 선택 · 할인 강조 · '가장 인기' 리본) — iOS annualCard
@Composable
private fun AnnualCard(
    pkg: Package,
    monthly: Package?,
    selected: Package?,
    badgePulse: Float,
    onSelect: () -> Unit,
) {
    val isOn = selected?.identifier == pkg.identifier
    val product = pkg.product
    val hasTrial = product.defaultOption?.freePhase != null

    // 월간 대비 절약률 + 월간×12 정가 (취소선 비교용)
    val yearAtMonthlyMicros = monthly?.product?.price?.amountMicros?.times(12)
    val savings: Int? = yearAtMonthlyMicros?.let { yam ->
        if (yam <= 0) null
        else Math.round((1.0 - product.price.amountMicros.toDouble() / yam) * 100).toInt().takeIf { it > 0 }
    }
    val compareAt: String? = yearAtMonthlyMicros
        ?.takeIf { it > product.price.amountMicros }
        ?.let { formatPrice(it, product.price.currencyCode) }
    val perMonth: String? = formatPrice(product.price.amountMicros / 12, product.price.currencyCode)

    val scaleT by animateFloatAsState(
        targetValue = if (isOn) 1f else 0.98f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "annual-scale",
    )

    Box(Modifier.padding(top = 10.dp)) { // 리본이 위 요소와 겹치지 않게
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .scale(scaleT)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = if (isOn) 0.14f else 0.07f))
                .border(
                    if (isOn) 2.dp else 1.dp,
                    if (isOn) TteOrange else Color.White.copy(alpha = 0.15f),
                    RoundedCornerShape(18.dp),
                )
                .clickable(onClick = onSelect)
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionCircle(isOn)
                Text(stringResource(R.string.paywall_annual), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (hasTrial) {
                    Text(
                        stringResource(R.string.paywall_freeTrial7),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TteOrange,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(TteOrange.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                savings?.let {
                    Text(
                        stringResource(R.string.paywall_discount, it),
                        fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                        modifier = Modifier
                            .scale(badgePulse)
                            .clip(CircleShape)
                            .background(TteOrange)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 월간×12 정가 취소선 — 절약분을 한눈에
                compareAt?.let {
                    Text(
                        it, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.LineThrough,
                        color = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                Text(product.price.formatted, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }

            Text(
                perMonth?.let { stringResource(R.string.paywall_perMonthAnnual, it) }
                    ?: stringResource(R.string.paywall_billedAnnually),
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f),
            )
        }

        // '가장 인기' 리본 — 카드 상단 테두리에 걸쳐 띄운다
        Text(
            stringResource(R.string.paywall_bestValue),
            fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-10).dp)
                .clip(CircleShape)
                .background(TteOrange)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

// MARK: - 월간 카드 (컴팩트) — iOS monthlyCard
@Composable
private fun MonthlyCard(pkg: Package, selected: Package?, onSelect: () -> Unit) {
    val isOn = selected?.identifier == pkg.identifier
    val product = pkg.product
    val hasTrial = product.defaultOption?.freePhase != null

    val scaleT by animateFloatAsState(
        targetValue = if (isOn) 1f else 0.98f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "monthly-scale",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scaleT)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (isOn) 0.13f else 0.06f))
            .border(
                if (isOn) 2.dp else 1.dp,
                if (isOn) TteOrange else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onSelect)
            .padding(16.dp),
    ) {
        SelectionCircle(isOn)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.paywall_monthly), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (hasTrial) {
                    Text(
                        stringResource(R.string.paywall_freeTrial7),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TteOrange,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(TteOrange.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Text(stringResource(R.string.paywall_cancelAnytime), fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
        }
        Spacer(Modifier.weight(1f))
        Text(product.price.formatted, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun SelectionCircle(isOn: Boolean) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(22.dp)) {
        if (isOn) {
            Box(Modifier.matchParentSize().background(TteOrange, CircleShape))
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        } else {
            Box(Modifier.matchParentSize().border(1.8.dp, Color.White.copy(alpha = 0.35f), CircleShape))
        }
    }
}

// iOS VlogAuroraBackground와 동일한 무드의 페이월 배경
@Composable
private fun PaywallAuroraBackground() {
    val infinite = rememberInfiniteTransition(label = "paywall-aurora")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5000), repeatMode = RepeatMode.Reverse),
        label = "paywall-aurora-t",
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
    }
}
