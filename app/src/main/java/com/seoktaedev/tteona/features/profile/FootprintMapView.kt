package com.seoktaedev.tteona.features.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.i18n.LocaleManager
import com.seoktaedev.tteona.core.model.FootprintPoint
import com.seoktaedev.tteona.core.model.FootprintSummary
import com.seoktaedev.tteona.core.services.FootprintAtlas
import com.seoktaedev.tteona.core.services.GeoRegion
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

// iOS Features/Profile/FootprintMapView.swift의 Kotlin 이식본.

/** 발자취 지도 초기 포커스 */
sealed class FootprintMapFocus {
    data object World : FootprintMapFocus()
    data object Korea : FootprintMapFocus()
    data class Country(val iso3: String) : FootprintMapFocus()
    data class Point(val lat: Double, val lng: Double) : FootprintMapFocus()
}

// 종이 지도 팔레트 (라이트 고정 — iOS와 동일)
private val PaperColor = Color(0xFFFBF8F3)
private val LandColor = Color(0xFFEFEAE1)
private val BorderColor = Color(0xFFD9D2C4)
private val VisitedSoft = Color(0xFFFF8B5E)

/** 포커스 → 카메라 (중심·줌) 계산. zoom은 "세계 너비가 뷰 너비의 몇 배인가" */
private fun cameraFor(focus: FootprintMapFocus): Pair<Offset, Float> = when (focus) {
    FootprintMapFocus.World -> Offset(0.5f, 0.42f) to 1.35f
    // 시군구 데이터 실측 bbox: x 0.8461~0.8637, y 0.3836~0.4023 (center 0.8549, 0.3929)
    FootprintMapFocus.Korea -> Offset(0.8549f, 0.3929f) to 40f
    is FootprintMapFocus.Country -> {
        if (focus.iso3 == "KOR") cameraFor(FootprintMapFocus.Korea)
        else FootprintAtlas.worldRegion(focus.iso3)?.let { region ->
            val span = maxOf(region.bbox.width, region.bbox.height * 1.4f)
            region.center to (0.75f / maxOf(span, 0.001f)).coerceIn(1.2f, 60f)
        } ?: cameraFor(FootprintMapFocus.World)
    }
    is FootprintMapFocus.Point -> FootprintAtlas.project(focus.lat, focus.lng) to 90f
}

/**
 * 빈 도화지 스타일의 세계지도 위에 방문 지역(한국 시군구·세계 국가)을 색칠하고
 * 여행 경로를 점선으로 얹는 커스텀 렌더러. 팬·핀치·탭 지원.
 */
@Composable
fun FootprintMapView(
    summary: FootprintSummary,
    modifier: Modifier = Modifier,
    routes: List<List<FootprintPoint>> = emptyList(),
    highlightCodes: Set<String> = emptySet(),   // 새로 칠해진 지역 — 펄스 연출
    interactive: Boolean = true,                 // 탭으로 지역 정보 표시
    // 임베드(스크롤 안) 지도는 팬/핀치를 끈다 — 한 손가락 드래그가 페이지 스크롤로 넘어가도록.
    panZoom: Boolean = true,
    initialFocus: FootprintMapFocus = FootprintMapFocus.Korea,
    /** 값이 바뀌면 해당 위치로 카메라가 날아간다 ("지금 여기 있네요" 연출 등) */
    focusCommand: FootprintMapFocus? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var atlasReady by remember { mutableStateOf(FootprintAtlas.isLoaded) }
    // 카메라 — centerX/Y는 단위공간(0~1)
    val centerX = remember { Animatable(0.8549f) }
    val centerY = remember { Animatable(0.3929f) }
    val zoomLog = remember { Animatable(ln(40f)) }  // 줌은 로그 공간에서 보간해야 자연스럽다

    var tappedRegion by remember { mutableStateOf<GeoRegion?>(null) }
    var tappedVisited by remember { mutableStateOf(false) }

    // 펄스 위상 — 새로 칠해진 지역이 은은하게 숨쉬는 연출
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulsePhase by pulseTransition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1960, easing = LinearEasing), RepeatMode.Restart),
        label = "pulsePhase",
    )

    LaunchedEffect(Unit) {
        if (!atlasReady) {
            withContext(Dispatchers.Default) { FootprintAtlas.ensureLoaded(context) }
            atlasReady = true
        }
        val (target, targetZoom) = cameraFor(initialFocus)
        centerX.snapTo(target.x)
        centerY.snapTo(target.y)
        zoomLog.snapTo(ln(targetZoom))
    }

    // 부모의 카메라 명령 → 플라이 애니메이션
    LaunchedEffect(focusCommand) {
        val command = focusCommand ?: return@LaunchedEffect
        if (!atlasReady) return@LaunchedEffect
        val (target, targetZoom) = cameraFor(command)
        val spec = tween<Float>(900, easing = FastOutSlowInEasing)
        launch { centerX.animateTo(target.x, spec) }
        launch { centerY.animateTo(target.y, spec) }
        launch { zoomLog.animateTo(ln(targetZoom), spec) }
    }

    Box(modifier.background(PaperColor).clipToBounds()) {
        if (!atlasReady) {
            CircularProgressIndicator(
                color = TteOrange,
                modifier = Modifier.align(Alignment.Center).size(28.dp),
            )
        } else {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .let { base -> if (!panZoom) base else base
                        .pointerInput(Unit) {
                                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                                    scope.launch {
                                        val zoom = exp(zoomLog.value)
                                        val scale = size.width * zoom
                                        // 핀치 중심이 고정되도록 센터 보정
                                        val newZoom = (zoom * gestureZoom).coerceIn(0.8f, 900f)
                                        val newScale = size.width * newZoom
                                        val cx = centerX.value; val cy = centerY.value
                                        val anchorUx = cx + (centroid.x - size.width / 2f) / scale
                                        val anchorUy = cy + (centroid.y - size.height / 2f) / scale
                                        var nx = anchorUx - (centroid.x - size.width / 2f) / newScale - pan.x / newScale
                                        var ny = anchorUy - (centroid.y - size.height / 2f) / newScale - pan.y / newScale
                                        nx = nx.coerceIn(0f, 1f)
                                        ny = ny.coerceIn(0.04f, 0.96f)
                                        centerX.snapTo(nx)
                                        centerY.snapTo(ny)
                                        zoomLog.snapTo(ln(newZoom))
                                    }
                                }
                            }
                    }
                    .let { base -> if (!interactive) base else base
                            .pointerInput(summary) {
                                detectTapGestures { tapOffset ->
                                    val zoom = exp(zoomLog.value)
                                    val scale = size.width * zoom
                                    val unit = Offset(
                                        centerX.value + (tapOffset.x - size.width / 2f) / scale,
                                        centerY.value + (tapOffset.y - size.height / 2f) / scale,
                                    )
                                    val (lat, lng) = FootprintAtlas.unproject(unit)
                                    val resolved = FootprintAtlas.resolve(context, lat, lng)
                                    val region = resolved.sig ?: resolved.country
                                    if (region != null) {
                                        tappedVisited = region.code in summary.sigCodes ||
                                            region.code in summary.countryCodes
                                        tappedRegion = region
                                        scope.launch {
                                            delay(3000)
                                            if (tappedRegion?.code == region.code) tappedRegion = null
                                        }
                                    } else {
                                        tappedRegion = null
                                    }
                                }
                            }
                    }
            ) {
                val zoom = exp(zoomLog.value)
                val scale = size.width * zoom
                val cx = centerX.value
                val cy = centerY.value
                val pulse = 0.55f + 0.45f * (0.5f + 0.5f * sin(pulsePhase))

                // 화면에 보이는 단위공간 범위 (컬링)
                val visible = Rect(
                    cx - size.width / 2f / scale - 0.002f,
                    cy - size.height / 2f / scale - 0.002f,
                    cx + size.width / 2f / scale + 0.002f,
                    cy + size.height / 2f / scale + 0.002f,
                )
                val hairline = maxOf(0.6f / scale, 0.0000015f) * scale // px 단위 stroke

                translate(
                    left = size.width / 2f - cx * scale,
                    top = size.height / 2f - cy * scale,
                ) {
                    scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero) {
                        val strokeWidth = hairline / scale

                        // 1) 세계 국가 (한국 본토는 시군구 레이어가 덮는다)
                        for (region in FootprintAtlas.worldRegions) {
                            if (!region.bbox.overlaps(visible)) continue
                            val visited = region.code in summary.countryCodes
                            val highlighted = region.code in highlightCodes
                            val fill = when {
                                region.code == "KOR" -> LandColor
                                highlighted -> VisitedSoft.copy(alpha = pulse)
                                visited -> TteOrange.copy(alpha = 0.88f)
                                else -> LandColor
                            }
                            val border = if (region.code != "KOR" && (visited || highlighted)) TteOrange else BorderColor
                            drawPath(region.path, fill)
                            drawPath(region.path, border, style = Stroke(width = strokeWidth))
                        }

                        // 2) 한국 시군구
                        val koreaBBox = Rect(0.843f, 0.373f, 0.869f, 0.399f + 0.006f)
                        if (koreaBBox.overlaps(visible)) {
                            for (region in FootprintAtlas.koreaRegions) {
                                if (!region.bbox.overlaps(visible)) continue
                                val visited = region.code in summary.sigCodes
                                val highlighted = region.code in highlightCodes
                                val fill = when {
                                    highlighted -> VisitedSoft.copy(alpha = pulse)
                                    visited -> TteOrange.copy(alpha = 0.88f)
                                    else -> PaperColor
                                }
                                drawPath(region.path, fill)
                                drawPath(
                                    region.path,
                                    if (visited) Color.White.copy(alpha = 0.7f) else BorderColor,
                                    style = Stroke(width = strokeWidth),
                                )
                            }
                        }

                        // 3) 여행 경로 (점선 + 장소 점)
                        if (routes.isNotEmpty() && zoom > 8f) {
                            val routeWidth = maxOf(1.6f / scale, strokeWidth)
                            for (route in routes) {
                                if (route.isEmpty()) continue
                                val pts = route.map { FootprintAtlas.project(it.lat, it.lng) }
                                if (pts.size >= 2) {
                                    val linePath = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(pts[0].x, pts[0].y)
                                        for (pt in pts.drop(1)) lineTo(pt.x, pt.y)
                                    }
                                    drawPath(
                                        linePath,
                                        TteOrange.copy(alpha = 0.55f),
                                        style = Stroke(
                                            width = routeWidth,
                                            pathEffect = PathEffect.dashPathEffect(
                                                floatArrayOf(routeWidth * 2.2f, routeWidth * 2.2f)
                                            ),
                                        ),
                                    )
                                }
                                val r = routeWidth * 1.4f
                                for (pt in pts) {
                                    drawCircle(Color.White, radius = r, center = pt)
                                    drawCircle(
                                        TteOrange, radius = r, center = pt,
                                        style = Stroke(width = routeWidth * 0.7f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 탭한 지역 이름 칩
        tappedRegion?.let { region ->
            val isKorean = remember { LocaleManager.current(context).code == "ko" }
            val name = if (!isKorean && region.nameEng != null) region.nameEng!! else region.name
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .shadow(6.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(if (tappedVisited) TteOrange else Color(0xFFC9C2B4), CircleShape)
                    )
                    Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TteDarkGray)
                    Text(
                        androidx.compose.ui.res.stringResource(
                            if (tappedVisited) R.string.footprint_visited else R.string.footprint_notYet
                        ),
                        fontSize = 12.sp,
                        color = if (tappedVisited) TteOrange else TteMediumGray,
                    )
                }
            }
        }
    }
}
