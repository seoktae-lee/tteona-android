package com.seoktaedev.tteona.features.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.i18n.LocaleManager
import com.seoktaedev.tteona.core.model.Place
import com.seoktaedev.tteona.core.services.GooglePlaceReview
import com.seoktaedev.tteona.core.services.PlaceDetail
import com.seoktaedev.tteona.core.services.PlaceDetailService
import com.seoktaedev.tteona.core.services.PlaceReviewService
import com.seoktaedev.tteona.core.services.ReportService
import com.seoktaedev.tteona.core.services.TteonaPlaceReview
import com.seoktaedev.tteona.core.services.UserService
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * 장소 상세 시트 — iOS Features/CourseDetail/PlaceDetailSheet.swift의 이식본.
 * 사진 갤러리 + 구글 리뷰 / 떠나 후기 탭, 후기 신고·작성자 차단 지원.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailSheet(
    place: Place,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uid = AuthService.currentUser.value?.uid

    var detail by remember { mutableStateOf<PlaceDetail?>(null) }
    var tteonaReviews by remember { mutableStateOf<List<TteonaPlaceReview>>(emptyList()) }
    var visitCount by remember { mutableIntStateOf(0) }
    var isLoadingDetail by remember { mutableStateOf(true) }
    var isLoadingReviews by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=구글, 1=떠나

    val context = LocalContext.current
    var reviewForAction by remember { mutableStateOf<TteonaPlaceReview?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<Pair<String, String>?>(null) } // title to message

    LaunchedEffect(place.placeName) {
        launch {
            detail = PlaceDetailService.fetchDetail(place.placeName, place.latitude, place.longitude)
            isLoadingDetail = false
        }
        launch {
            val key = PlaceDetailService.cacheKey(place.placeName)
            val blocked = UserService.currentUser.value?.blockedUserIds ?: emptyList()
            val result = PlaceReviewService.fetchReviews(key, blocked)
            tteonaReviews = result.reviews
            visitCount = result.visitCount
            isLoadingReviews = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxHeight(0.92f)
                .verticalScroll(rememberScrollState())
        ) {
            // 헤더
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(place.placeName, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = TteDarkGray)
                    detail?.rating?.let { rating ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            StarRow(rating = rating, color = Color(0xFFF5C518), size = 12)
                            Text(String.format("%.1f", rating), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TteDarkGray)
                            Text(stringResource(R.string.placedetail_reviewCount, detail?.reviewCount ?: 0), fontSize = 12.sp, color = TteMediumGray)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (visitCount > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = TteOrange, modifier = Modifier.size(22.dp))
                        Text(stringResource(R.string.placedetail_visitorCount, visitCount), fontSize = 10.sp, fontWeight = FontWeight.Medium, color = TteMediumGray)
                    }
                }
            }

            // 사진 갤러리
            when {
                isLoadingDetail -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 14.dp)
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TteOrange.copy(alpha = 0.06f)),
                ) { CircularProgressIndicator(color = TteOrange) }

                !detail?.photos.isNullOrEmpty() -> Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 14.dp),
                ) {
                    detail?.photos.orEmpty().forEach { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(200.dp)
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(TteOrange.copy(alpha = 0.06f)),
                        )
                    }
                }
            }

            // 탭 바
            Row(Modifier.padding(horizontal = 20.dp)) {
                TabButton(stringResource(R.string.placedetail_googleReviews), selectedTab == 0, Modifier.weight(1f)) { selectedTab = 0 }
                TabButton(stringResource(R.string.placedetail_tteonaReviews), selectedTab == 1, Modifier.weight(1f)) { selectedTab = 1 }
            }
            HorizontalDivider()

            // 리뷰 목록
            if (selectedTab == 0) {
                when {
                    isLoadingDetail -> LoadingRow()
                    !detail?.reviews.isNullOrEmpty() -> detail?.reviews.orEmpty().forEach { review ->
                        GoogleReviewRow(review)
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                    }
                    else -> EmptyReviewState(stringResource(R.string.placedetail_noGoogleReviews))
                }
            } else {
                when {
                    isLoadingReviews -> LoadingRow()
                    tteonaReviews.isNotEmpty() -> tteonaReviews.forEach { review ->
                        TteonaReviewRow(
                            review = review,
                            currentUserId = uid,
                            onReport = { reviewForAction = review; showReportDialog = true },
                            onBlock = { reviewForAction = review; showBlockDialog = true },
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                    }
                    else -> EmptyReviewState(stringResource(R.string.placedetail_noVisits))
                }
            }
        }
    }

    // 신고 사유 선택 (iOS confirmationDialog)
    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text(stringResource(R.string.report_selectReason)) },
            text = {
                Column {
                    ReportService.REASONS.forEach { reason ->
                        Text(
                            reason,
                            fontSize = 15.sp,
                            color = TteDarkGray,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showReportDialog = false
                                    val review = reviewForAction
                                    if (uid != null && review != null) {
                                        val key = PlaceDetailService.cacheKey(place.placeName)
                                        scope.launch {
                                            runCatching {
                                                ReportService.reportContent(
                                                    reporterId = uid,
                                                    targetType = "review",
                                                    targetId = "$key/${review.userId}",
                                                    targetAuthorId = review.userId,
                                                    reason = reason,
                                                )
                                            }.onSuccess {
                                                resultMessage = LocaleManager.string(context, R.string.report_done_title) to LocaleManager.string(context, R.string.report_done_message)
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text(stringResource(R.string.common_cancel), color = TteMediumGray) }
            },
        )
    }

    // 작성자 차단 확인 (iOS alert)
    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text(stringResource(R.string.placedetail_blockReviewer_title)) },
            text = { Text(stringResource(R.string.placedetail_blockReviewer_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showBlockDialog = false
                    val review = reviewForAction
                    if (uid != null && review != null) {
                        scope.launch {
                            runCatching { UserService.blockUser(uid, review.userId) }
                                .onSuccess {
                                    tteonaReviews = tteonaReviews.filter { it.userId != review.userId }
                                    // 차단 즉시 이 작성자의 코스도 홈/탐색 목록에서 숨긴다
                                    com.seoktaedev.tteona.core.services.CourseService.hideAuthorCourses(review.userId)
                                    resultMessage = LocaleManager.string(context, R.string.block_done_title) to LocaleManager.string(context, R.string.placedetail_blockDone_message)
                                }
                        }
                    }
                }) { Text(stringResource(R.string.block_action), color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) { Text(stringResource(R.string.common_cancel), color = TteMediumGray) }
            },
        )
    }

    // 완료 알림
    resultMessage?.let { (title, message) ->
        AlertDialog(
            onDismissRequest = { resultMessage = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { resultMessage = null }) { Text(stringResource(R.string.common_ok), color = TteOrange) }
            },
        )
    }
}

// ── 구성 요소 ────────────────────────────────────────────────────────────

@Composable
private fun TabButton(title: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.clickable(onClick = onClick).padding(top = 4.dp),
    ) {
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) TteDarkGray else TteMediumGray,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) TteOrange else Color.Transparent)
        )
    }
}

@Composable
private fun LoadingRow() {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = TteOrange)
    }
}

@Composable
private fun EmptyReviewState(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 20.dp),
    ) {
        Icon(Icons.Filled.StarBorder, contentDescription = null, tint = TteMediumGray.copy(alpha = 0.35f), modifier = Modifier.size(36.dp))
        Text(message, fontSize = 14.sp, color = TteMediumGray, textAlign = TextAlign.Center)
    }
}

@Composable
fun StarRow(rating: Double, color: Color, size: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..5).forEach { i ->
            val icon = when {
                i <= rating -> Icons.Filled.Star
                i - 0.5 <= rating -> Icons.Filled.StarHalf
                else -> Icons.Filled.StarBorder
            }
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size.dp))
        }
    }
}

@Composable
private fun GoogleReviewRow(review: GooglePlaceReview) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(TteMediumGray.copy(alpha = 0.15f)),
            ) {
                Text(review.authorName.take(1), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TteDarkGray)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(review.authorName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TteDarkGray)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    StarRow(rating = review.rating.toDouble(), color = Color(0xFFF5C518), size = 10)
                    if (review.publishTime.isNotEmpty()) {
                        Text("· ${review.publishTime}", fontSize = 11.sp, color = TteMediumGray)
                    }
                }
            }
        }
        if (review.text.isNotEmpty()) {
            Text(review.text, fontSize = 13.sp, color = TteDarkGray, maxLines = 4)
        }
    }
}

@Composable
private fun TteonaReviewRow(
    review: TteonaPlaceReview,
    currentUserId: String?,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(TteOrange.copy(alpha = 0.1f)),
            ) {
                Text(review.nickname.take(1), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TteOrange)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(review.nickname, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TteDarkGray)
                StarRow(rating = review.rating.toDouble(), color = TteOrange, size = 10)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(relativeTime(LocalContext.current, review.createdAt.time), fontSize = 11.sp, color = TteMediumGray)
                if (currentUserId != null && review.userId != currentUserId) {
                    Box {
                        Icon(
                            Icons.Filled.MoreHoriz,
                            contentDescription = stringResource(R.string.common_more),
                            tint = TteMediumGray,
                            modifier = Modifier.size(18.dp).clickable { showMenu = true },
                        )
                        androidx.compose.material3.DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.report_action), color = Color.Red) },
                                onClick = { showMenu = false; onReport() },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.detail_blockAuthor)) },
                                onClick = { showMenu = false; onBlock() },
                            )
                        }
                    }
                }
            }
        }
        review.comment?.let { Text(it, fontSize = 13.sp, color = TteDarkGray) }
    }
}

/** iOS Text(date, style: .relative) 대응 간이 상대시간 */
fun relativeTime(context: android.content.Context, epochMs: Long): String {
    val diff = System.currentTimeMillis() - epochMs
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    return when {
        minutes < 1 -> LocaleManager.string(context, R.string.time_justNow)
        minutes < 60 -> LocaleManager.string(context, R.string.time_minutesAgo, minutes.toInt())
        minutes < 60 * 24 -> LocaleManager.string(context, R.string.time_hoursAgo, TimeUnit.MILLISECONDS.toHours(diff).toInt())
        else -> LocaleManager.string(context, R.string.time_daysAgo, TimeUnit.MILLISECONDS.toDays(diff).toInt())
    }
}
