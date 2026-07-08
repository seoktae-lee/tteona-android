package com.seoktaedev.tteona.features.session

import android.content.Context
import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.i18n.LocaleManager
import com.seoktaedev.tteona.core.network.ApiClient
import com.seoktaedev.tteona.ui.theme.Pretendard
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

/**
 * "어디서 찍으셨나요?" 주변 장소 선택 시트 — iOS Features/ActiveSession/PlacePickerView.swift의 이식본.
 * 역지오코딩 현재 위치 + 카카오 카테고리(음식점·카페·관광명소 등) 반경 200m 검색.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacePickerSheet(
    latitude: Double,
    longitude: Double,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var nearbyPlaces by remember { mutableStateOf<List<NearbyPlace>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var customName by remember { mutableStateOf("") }
    var showCustomInput by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        nearbyPlaces = fetchNearbyPlaces(context, latitude, longitude)
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxHeight(0.85f)) {
            Text(
                stringResource(R.string.placepicker_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = TteDarkGray,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp),
            )
            HorizontalDivider()

            // 직접 입력 (상단 고정 — iOS bc3f901)
            if (showCustomInput) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = TteOrange, modifier = Modifier.width(28.dp).size(18.dp))
                    Box(Modifier.weight(1f)) {
                        if (customName.isEmpty()) {
                            Text(stringResource(R.string.placepicker_customName), fontSize = 15.sp, color = TteMediumGray)
                        }
                        BasicTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 15.sp, color = TteDarkGray, fontFamily = Pretendard),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                customName.trim().takeIf { it.isNotEmpty() }?.let(onSelect)
                            }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (customName.isNotEmpty()) {
                        Icon(
                            Icons.Filled.ArrowCircleRight,
                            contentDescription = stringResource(R.string.common_ok),
                            tint = TteOrange,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { customName.trim().takeIf { it.isNotEmpty() }?.let(onSelect) },
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCustomInput = true }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = TteOrange, modifier = Modifier.width(28.dp).size(18.dp))
                    Text(stringResource(R.string.placepicker_enterManually), fontSize = 15.sp, color = TteOrange)
                }
            }
            HorizontalDivider()

            if (isLoading) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TteOrange)
                }
            } else {
                LazyColumn {
                    items(nearbyPlaces, key = { it.placeName }) { place ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(place.placeName) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                        ) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = TteOrange, modifier = Modifier.width(28.dp).size(24.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                                Text(place.placeName, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TteDarkGray, maxLines = 1)
                                place.category?.let { Text(it, fontSize = 12.sp, color = TteMediumGray) }
                            }
                            place.distanceMeters?.let { d ->
                                Text(
                                    if (d < 1000) "${d.toInt()}m" else String.format("%.1fkm", d / 1000),
                                    fontSize = 12.sp, color = TteMediumGray,
                                )
                            }
                        }
                        HorizontalDivider(Modifier.padding(start = 56.dp))
                    }
                }
            }
            Spacer(Modifier.weight(1f, fill = false))
        }
    }
}

data class NearbyPlace(val placeName: String, val category: String?, val distanceMeters: Double?)

private const val KAKAO_API_KEY = "b31c03c128d37a877e6cb407f59b8911"
private val pickerJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class KakaoCategoryResponse(val documents: List<KakaoCategoryPlace> = emptyList())

@Serializable
private data class KakaoCategoryPlace(
    @SerialName("place_name") val placeName: String = "",
    @SerialName("category_name") val categoryName: String = "",
    val distance: String = "",
)

/** 역지오코딩 현재 위치 + 카카오 카테고리 병렬 검색 (iOS fetchNearbyPlaces) */
private suspend fun fetchNearbyPlaces(context: Context, lat: Double, lng: Double): List<NearbyPlace> =
    withContext(Dispatchers.IO) {
        val seen = mutableSetOf<String>()
        val collected = mutableListOf<NearbyPlace>()

        // 역지오코딩으로 건물/도로명 먼저 추가 (가장 위)
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, java.util.Locale.KOREA).getFromLocation(lat, lng, 1)?.firstOrNull()
        }.getOrNull()?.let { addr ->
            val name = addr.featureName ?: addr.thoroughfare
            if (!name.isNullOrBlank() && seen.add(name)) {
                collected.add(NearbyPlace(name, LocaleManager.string(context, R.string.placepicker_currentLocation), 0.0))
            }
        }

        // 카카오 카테고리: 음식점·카페·관광명소·숙박·대형마트·편의점·지하철
        val categories = listOf("FD6", "CE7", "AT4", "AD5", "MT1", "CS2", "SW8")
        val results = coroutineScope {
            categories.map { code ->
                async { fetchKakaoCategory(code, lat, lng, radius = 200) }
            }.awaitAll()
        }
        results.flatten().forEach { place ->
            if (seen.add(place.placeName)) collected.add(place)
        }
        collected.sortedBy { it.distanceMeters ?: 9999.0 }
    }

private fun fetchKakaoCategory(code: String, lat: Double, lng: Double, radius: Int): List<NearbyPlace> =
    runCatching {
        val url = "https://dapi.kakao.com/v2/local/search/category.json" +
            "?category_group_code=$code&x=$lng&y=$lat&radius=$radius&size=5&sort=distance"
        val req = Request.Builder().url(url).header("Authorization", "KakaoAK $KAKAO_API_KEY").build()
        ApiClient.httpClient.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@runCatching emptyList()
            pickerJson.decodeFromString<KakaoCategoryResponse>(res.body?.string() ?: "{}").documents.map {
                NearbyPlace(
                    placeName = it.placeName,
                    category = it.categoryName.split(" > ").lastOrNull(),
                    distanceMeters = it.distance.toDoubleOrNull(),
                )
            }
        }
    }.getOrDefault(emptyList())
