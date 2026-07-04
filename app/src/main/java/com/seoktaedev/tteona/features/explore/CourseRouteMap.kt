package com.seoktaedev.tteona.features.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.seoktaedev.tteona.core.model.Course
import com.seoktaedev.tteona.ui.theme.TteOrange

/**
 * 코스 동선 미니 지도 — iOS ExploreDetailView.routeMap의 이식본.
 * 번호 마커 + 점선 폴리라인, 탭하면 전체화면 인터랙티브 지도.
 */
@Composable
fun CourseRouteMap(course: Course) {
    var showFullMap by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        RouteMapContent(course, interactive = false)

        // 주변 보기 배지
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 9.dp, vertical = 5.dp),
        ) {
            Icon(Icons.Filled.OpenInFull, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
            Text("주변 보기", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }

        // 제스처 충돌 방지용 투명 오버레이 — 탭하면 전체화면 (iOS와 동일)
        Box(
            Modifier
                .fillMaxSize()
                .clickable { showFullMap = true }
        )
    }

    if (showFullMap) {
        Dialog(
            onDismissRequest = { showFullMap = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize()) {
                RouteMapContent(course, interactive = true)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 48.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { showFullMap = false },
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "닫기", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun RouteMapContent(course: Course, interactive: Boolean) {
    val points = remember(course.courseId) {
        course.places.sortedBy { it.order }.map { LatLng(it.latitude, it.longitude) }
    }
    if (points.isEmpty()) return

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(points.first(), 13f)
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
            scrollGesturesEnabled = interactive,
            zoomGesturesEnabled = interactive,
            tiltGesturesEnabled = interactive,
            rotationGesturesEnabled = interactive,
        ),
        onMapLoaded = {
            // 모든 장소가 들어오도록 카메라 핏 (iOS fittingCamera 대응)
            if (points.size >= 2) {
                val bounds = LatLngBounds.builder().apply { points.forEach { include(it) } }.build()
                cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 80))
            } else {
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(points.first(), 14f))
            }
        },
    ) {
        if (points.size >= 2) {
            Polyline(
                points = points,
                color = TteOrange,
                width = 8f,
                pattern = listOf(Dash(24f), Gap(14f)),
                geodesic = true,
            )
        }
        points.forEachIndexed { idx, point ->
            MarkerComposable(
                keys = arrayOf(course.courseId, idx),
                state = rememberUpdatedMarkerState(position = point),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(TteOrange),
                ) {
                    Text("${idx + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
