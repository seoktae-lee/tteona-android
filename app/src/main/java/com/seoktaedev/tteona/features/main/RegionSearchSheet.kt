package com.seoktaedev.tteona.features.main

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.services.PlaceSearchService
import com.seoktaedev.tteona.ui.theme.Pretendard
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.delay

/**
 * 지역 검색 시트 — iOS Features/Main/RegionSearchView.swift의 이식본.
 * 400ms 디바운스로 카카오 로컬 키워드 검색 후 결과 목록을 보여준다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionSearchSheet(
    onSelect: (name: String, latitude: Double, longitude: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceSearchService.SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // iOS searchDebounced(400ms) 대응
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(400)
        isSearching = true
        results = PlaceSearchService.search(context, q)
        isSearching = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxHeight(0.9f)) {
            Text(
                stringResource(R.string.region_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = TteDarkGray,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp),
            )

            // 검색 바
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TteFieldBackground)
                    .padding(12.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = TteMediumGray, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.region_placeholder), fontSize = 15.sp, color = TteMediumGray)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = TteDarkGray, fontFamily = Pretendard),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { /* 디바운스 검색이 이미 처리 */ }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Cancel,
                        contentDescription = stringResource(R.string.main_clearSearch),
                        tint = TteMediumGray,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { query = "" },
                    )
                }
            }

            when {
                isSearching -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TteOrange)
                }
                results.isEmpty() && query.isNotEmpty() -> EmptyState(
                    icon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TteMediumGray.copy(alpha = 0.4f), modifier = Modifier.size(36.dp)) },
                    message = stringResource(R.string.region_noResults),
                )
                results.isEmpty() -> EmptyState(
                    icon = { Icon(Icons.Filled.Map, contentDescription = null, tint = TteOrange.copy(alpha = 0.4f), modifier = Modifier.size(36.dp)) },
                    message = stringResource(R.string.region_hint),
                )
                else -> LazyColumn {
                    items(results) { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(item.name, item.latitude, item.longitude)
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Icon(
                                Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = TteOrange,
                                modifier = Modifier.width(32.dp).size(22.dp),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TteDarkGray)
                                if (item.address.isNotEmpty()) {
                                    Text(item.address, fontSize = 13.sp, color = TteMediumGray)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun EmptyState(icon: @Composable () -> Unit, message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
    ) {
        icon()
        Text(message, fontSize = 15.sp, color = TteMediumGray)
    }
}
