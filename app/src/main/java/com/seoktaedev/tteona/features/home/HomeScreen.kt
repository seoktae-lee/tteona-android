package com.seoktaedev.tteona.features.home

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 홈 탭 — iOS MainView에 해당.
 * Google Maps 지도 + 코스 핀 표시 예정 (MAPS_API_KEY 설정 후 GoogleMap 컴포저블로 교체).
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "홈 (지도)",
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
