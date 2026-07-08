package com.seoktaedev.tteona.features.pro

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

/**
 * tteona PRO 구독 페이월 — iOS Features/Pro/ProPaywallView.swift의 이식본.
 * RevenueCat offerings의 연간/월간 패키지 표시.
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

    LaunchedEffect(Unit) { ProManager.loadOfferings() }
    LaunchedEffect(packages.size) {
        if (selectedPackage == null) selectedPackage = annual ?: packages.firstOrNull()
    }
    LaunchedEffect(isPro) {
        if (isPro) onDismiss()
    }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
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
                    FeatureRow(Icons.Filled.AutoAwesome, stringResource(R.string.paywall_feature_watermark), stringResource(R.string.paywall_feature_watermark_sub))
                    FeatureRow(Icons.Filled.ViewCarousel, stringResource(R.string.paywall_feature_multiformat), stringResource(R.string.paywall_feature_multiformat_sub))
                    FeatureRow(Icons.Filled.LibraryMusic, stringResource(R.string.paywall_feature_bgm), stringResource(R.string.paywall_feature_bgm_sub))
                    FeatureRow(Icons.Filled.Timer, stringResource(R.string.paywall_feature_duration), stringResource(R.string.paywall_feature_duration_sub))
                    FeatureRow(Icons.Filled.Bolt, stringResource(R.string.paywall_feature_priority), stringResource(R.string.paywall_feature_priority_sub))
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
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .padding(top = 24.dp, bottom = 12.dp),
                    ) {
                        annual?.let { PackageCard(it, monthly, selectedPackage) { selectedPackage = it } }
                        monthly?.let { PackageCard(it, monthly, selectedPackage) { selectedPackage = it } }
                    }
                }
            }

            // CTA + 링크
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(TteOrange.copy(alpha = if (selectedPackage != null && !isPurchasing) 1f else 0.5f))
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
                    if (isPurchasing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(stringResource(R.string.paywall_subscribe), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 24.dp),
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
private fun FeatureRow(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, contentDescription = null, tint = TteOrange, modifier = Modifier.width(28.dp).size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
        }
    }
}

@Composable
private fun PackageCard(pkg: Package, monthly: Package?, selected: Package?, onSelect: () -> Unit) {
    val isOn = selected?.identifier == pkg.identifier
    val product = pkg.product
    val isAnnual = pkg.packageType == PackageType.ANNUAL

    // 연간 패키지의 월간 대비 절약률 배지 (iOS savingsBadge)
    val savings: Int? = if (isAnnual && monthly != null) {
        val yearAtMonthly = monthly.product.price.amountMicros * 12
        val pct = ((yearAtMonthly - product.price.amountMicros) * 100 / yearAtMonthly).toInt()
        pct.takeIf { it > 0 }
    } else null

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (isOn) 0.13f else 0.06f))
            .border(1.5.dp, if (isOn) TteOrange else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (isAnnual) stringResource(R.string.paywall_annual) else stringResource(R.string.paywall_monthly), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                savings?.let {
                    Text(
                        stringResource(R.string.paywall_discount, it), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(TteOrange)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Text(
                if (isAnnual) stringResource(R.string.paywall_billedAnnually) else stringResource(R.string.paywall_cancelAnytime),
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(product.price.formatted, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
