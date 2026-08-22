package com.seoktaedev.tteona.features.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.i18n.LocaleManager
import com.seoktaedev.tteona.core.model.Place
import com.seoktaedev.tteona.core.services.VlogClips
import com.seoktaedev.tteona.ui.theme.BadgeNumberTextStyle
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import java.io.File
import kotlin.math.roundToInt

/**
 * '오늘 찍은 곳' 목록. iOS Features/Capture/CaptureListSheet.swift의 이식본.
 *
 * 세션 칩을 누르면 올라온다. 칩은 '확인·정리', 우상단 ✓는 '오늘 마치기' —
 * 예전엔 둘 다 종료로 가서 하는 일이 겹쳤다.
 *
 * **오늘 기록 통째로 버리기를 여기 함께 둔다.** 예산이 찬 채로 아무것도 못 하게 갇혔을 때의
 * 탈출구다 — 하나씩 지우는 것만으로는 빠져나오기 번거롭다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureListSheet(
    places: List<Place>,
    sessionId: String,
    usedSeconds: Double,
    budgetSeconds: Double,
    onDelete: (Place) -> Unit,
    onDiscardAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingDelete by remember { mutableStateOf<Place?>(null) }
    var confirmDiscardAll by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                stringResource(R.string.capture_list_title),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = TteDarkGray,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    LocaleManager.string(context, R.string.capture_list_count, places.size),
                    fontSize = 13.sp,
                    color = TteMediumGray,
                )
                Text("·", fontSize = 13.sp, color = TteMediumGray.copy(alpha = 0.6f))
                Text(
                    LocaleManager.string(
                        context, R.string.capture_list_budget,
                        usedSeconds.roundToInt(), budgetSeconds.roundToInt(),
                    ),
                    fontSize = 13.sp,
                    color = TteMediumGray,
                )
            }

            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = {
                    if (budgetSeconds > 0) (usedSeconds / budgetSeconds).coerceIn(0.0, 1.0).toFloat() else 0f
                },
                color = TteOrange,
                trackColor = TteFieldBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.height(16.dp))

            if (places.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.capture_list_empty),
                        fontSize = 14.sp,
                        color = TteMediumGray,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.heightIn(max = 340.dp),
                ) {
                    items(places, key = { it.order }) { place ->
                        CaptureRow(
                            place = place,
                            clipFile = place.clipFileName?.let {
                                File(VlogClips.sessionDir(context, sessionId), it)
                            },
                            onDelete = { pendingDelete = place },
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TteOrange)
                        .clickable(onClick = onDismiss),
                ) {
                    Text(
                        stringResource(R.string.capture_list_keepShooting),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
                if (places.isNotEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(TteFieldBackground)
                            .clickable { confirmDiscardAll = true }
                            .padding(horizontal = 18.dp),
                    ) {
                        Text(
                            stringResource(R.string.impromptu_discardToday),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TteMediumGray,
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { place ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.capture_list_deleteTitle)) },
            text = {
                Text(LocaleManager.string(context, R.string.capture_list_deleteMessage, place.placeName))
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(place)
                    pendingDelete = null
                }) { Text(stringResource(R.string.common_delete), color = TteOrange) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel), color = TteMediumGray)
                }
            },
        )
    }

    if (confirmDiscardAll) {
        AlertDialog(
            onDismissRequest = { confirmDiscardAll = false },
            title = { Text(stringResource(R.string.impromptu_discardToday)) },
            text = { Text(stringResource(R.string.capture_list_discardAllMessage)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscardAll = false
                    onDiscardAll()
                    onDismiss()
                }) { Text(stringResource(R.string.common_delete), color = TteOrange) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscardAll = false }) {
                    Text(stringResource(R.string.common_cancel), color = TteMediumGray)
                }
            },
        )
    }
}

@Composable
private fun CaptureRow(place: Place, clipFile: File?, onDelete: () -> Unit) {
    val seconds = remember(clipFile?.path, clipFile?.length()) {
        clipFile?.let { VlogClips.clipSeconds(it) } ?: 0.0
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(TteOrange.copy(alpha = 0.12f)),
        ) {
            Text(
                "${place.order}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TteOrange,
                style = BadgeNumberTextStyle,
            )
        }
        Text(
            place.placeName,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TteDarkGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            LocaleManager.string(context = LocalContext.current, R.string.capture_list_seconds,
                String.format("%.1f", seconds)),
            fontSize = 13.sp,
            color = TteMediumGray,
            maxLines = 1,
        )
        Icon(
            Icons.Filled.DeleteOutline,
            contentDescription = stringResource(R.string.common_delete),
            tint = TteMediumGray.copy(alpha = 0.7f),
            modifier = Modifier
                .size(22.dp)
                .clickable(onClick = onDelete),
        )
    }
}
