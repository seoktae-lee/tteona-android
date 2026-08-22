package com.seoktaedev.tteona.features.explore

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.i18n.LocaleManager
import com.seoktaedev.tteona.core.model.Course
import com.seoktaedev.tteona.core.model.NearbyFood
import com.seoktaedev.tteona.core.util.Haptics
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.tasks.await
import java.util.Locale
import kotlin.math.roundToInt

/*
 * 코스 상세 화면의 공용 조각. iOS Features/CourseDetail/CourseDetailParts.swift의 이식본.
 *
 * 같은 코스인데 **어디로 들어갔느냐에 따라 다른 정보가 보이는** 문제가 있었다.
 * 탐색 탭과 지도 탭이 서로 다른 화면을 열어, 화면마다 있는 것과 없는 것이 갈렸다.
 * 두 화면을 하나로 합치는 대신 조각을 여기로 모아 양쪽이 같은 것을 쓰게 한다.
 *
 * (특히 출처 표기는 이용약관 11조에 명시한 의무라 어느 경로로 열어도 보여야 한다)
 */

// ── 코스 요약 한 줄 ──────────────────────────────────────────────────────

/**
 * 사진 바로 아래에서 "갈 만한가"를 즉시 판단하게 해주는 한 줄.
 *
 * 코스를 볼 때 사람이 던지는 질문은 셋이다 — 뭐 하는 코스인가, 얼마나 걸리나,
 * 나한테서 얼마나 먼가. 예전에는 이동 정보가 화면 맨 아래에 있었고 **나와의 거리는
 * 아예 없어서** 스크롤을 끝까지 내려야 판단이 됐다.
 *
 * 여기서는 좌표만으로 즉시 구할 수 있는 것만 보여준다 — 요약 한 줄이 네트워크를
 * 기다리면 안 된다.
 */
@Composable
fun CourseSummaryBar(course: Course, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var fromMeKm by remember(course.courseId) { mutableStateOf<Double?>(null) }

    LaunchedEffect(course.courseId) {
        // **마지막으로 알려진 위치만 읽는다.** 상세 화면이 위치 추적을 새로 시작하면
        // 배터리를 쓰고 권한 흐름도 복잡해진다 — 권한이 이미 있을 때만 값이 나온다.
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return@LaunchedEffect
        val main = course.mainPlace ?: return@LaunchedEffect
        val me = runCatching {
            LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
        }.getOrNull() ?: return@LaunchedEffect
        fromMeKm = haversineKm(me.latitude, me.longitude, main.latitude, main.longitude)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        SummaryItem(LocaleManager.string(context, R.string.coursedetail_summaryPlaces, course.displayPlaces.size))
        val totalKm = course.totalDistanceKm
        if (totalKm > 0) {
            SummaryDot()
            SummaryItem(LocaleManager.string(context, R.string.coursedetail_summaryMove, formatKm(totalKm)))
        }
        fromMeKm?.let {
            SummaryDot()
            SummaryItem(LocaleManager.string(context, R.string.coursedetail_summaryFromMe, formatKm(it)))
        }
    }
}

@Composable
private fun SummaryItem(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TteDarkGray.copy(alpha = 0.6f), maxLines = 1)
}

@Composable
private fun SummaryDot() {
    Text("·", fontSize = 12.sp, color = TteDarkGray.copy(alpha = 0.35f))
}

/** 1km 미만은 m로 — "0.4km"보다 "400m"가 걸어갈 거리인지 바로 읽힌다 */
fun formatKm(km: Double): String =
    if (km < 1) "${(km * 1000 / 10).roundToInt() * 10}m"
    else String.format(Locale.US, "%.1fkm", km)

fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
        Math.sin(dLng / 2) * Math.sin(dLng / 2)
    return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

// ── 공공데이터 출처 표기 ─────────────────────────────────────────────────

/**
 * 큐레이션 코스의 정보 제공처. **이용조건상 의무**이므로 지우지 말 것.
 *
 * Firestore에는 한글("한국관광공사")로 저장되므로 그대로 그리면 영어·일본어 사용자에게
 * 한글이 노출된다. 아는 기관은 번역하고, 모르는 값(제휴 크리에이터 등)은 원문을 쓴다.
 */
@Composable
fun CourseSourceLabel(course: Course, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val source = course.curationSource?.takeIf { it.isNotEmpty() } ?: return
    val localized = if (source == "한국관광공사") stringResource(R.string.source_kto) else source

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = TteDarkGray.copy(alpha = 0.6f),
            modifier = Modifier.size(11.dp),
        )
        Text(
            "${LocaleManager.string(context, R.string.coursedetail_sourceLabel)} · $localized",
            fontSize = 11.sp,
            color = TteDarkGray.copy(alpha = 0.6f),
        )
    }
}

// ── 근처 추천 식당 ───────────────────────────────────────────────────────

/**
 * 코스 근처 맛집.
 *
 * **코스의 places에 끼워 넣지 않는다.** 이용약관 11조가 원 데이터의 임의 수정을 금하고,
 * 장소를 추가하는 것은 표기 정리의 범위를 넘는다. 원본 코스는 그대로 두고 곁들임으로만
 * 보여주며, 실제로 넣을지는 사용자가 세션을 시작할 때 고른다.
 */
@Composable
fun CourseNearbyFoodSection(
    course: Course,
    onSelect: (NearbyFood) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    if (course.nearbyFood.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Restaurant, contentDescription = null, tint = TteDarkGray, modifier = Modifier.size(13.dp))
            Text(
                stringResource(R.string.coursedetail_nearbyFood),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TteDarkGray,
            )
        }
        Text(
            stringResource(R.string.coursedetail_nearbyFoodHint),
            fontSize = 11.sp,
            color = TteDarkGray.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = 3.dp, bottom = 10.dp),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            course.nearbyFood.forEach { food ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TteDarkGray.copy(alpha = 0.04f))
                        .clickable {
                            Haptics.light(view)
                            onSelect(food)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                food.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TteDarkGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            // 방송 출처가 붙는 단계에서 뱃지가 여기 표시된다
                            food.source?.let { src ->
                                Text(
                                    src,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = androidx.compose.ui.graphics.Color.White,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(TteOrange)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // 구글에 등재되지 않은 지역 노포는 평점이 없다 —
                            // 그것만으로 나쁜 집은 아니므로 자리를 비워둘 뿐 감점하지 않는다
                            food.rating?.let { rating ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Icon(Icons.Filled.Star, contentDescription = null, tint = TteOrange, modifier = Modifier.size(10.dp))
                                    Text(
                                        String.format(Locale.US, "%.1f", rating),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TteDarkGray.copy(alpha = 0.8f),
                                    )
                                }
                            }
                            Text(
                                LocaleManager.string(
                                    context, R.string.coursedetail_nearbyFoodDistance,
                                    food.nearPlaceName, food.distanceM,
                                ),
                                fontSize = 11.sp,
                                color = TteDarkGray.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = TteDarkGray.copy(alpha = 0.35f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
