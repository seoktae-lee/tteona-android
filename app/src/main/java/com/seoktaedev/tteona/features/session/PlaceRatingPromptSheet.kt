package com.seoktaedev.tteona.features.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.model.Place
import com.seoktaedev.tteona.core.services.PlaceDetailService
import com.seoktaedev.tteona.core.services.PlaceReviewService
import com.seoktaedev.tteona.ui.theme.Pretendard
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.launch

/**
 * 방문 후 평점 프롬프트 — iOS Features/ActiveSession/PlaceRatingPromptView.swift의 이식본.
 * 별점 선택 시 한 줄 후기 입력이 나타나고, 등록하면 placeReviews에 저장된다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceRatingPromptSheet(
    place: Place,
    userId: String,
    nickname: String,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedRating by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    fun submit() {
        if (selectedRating <= 0) {
            onDismiss()
            return
        }
        isSubmitting = true
        val key = PlaceDetailService.cacheKey(place.placeName)
        scope.launch {
            PlaceReviewService.saveReview(
                placeKey = key,
                userId = userId,
                nickname = nickname,
                rating = selectedRating,
                comment = comment.ifEmpty { null },
            )
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.rating_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TteDarkGray)
                Text(place.placeName, fontSize = 13.sp, color = TteMediumGray)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                (1..5).forEach { i ->
                    Icon(
                        if (i <= selectedRating) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = stringResource(R.string.rating_stars, i),
                        tint = if (i <= selectedRating) TteOrange else TteMediumGray.copy(alpha = 0.3f),
                        modifier = Modifier
                            .size(34.dp)
                            .clickable { selectedRating = i },
                    )
                }
            }

            AnimatedVisibility(visible = selectedRating > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TteFieldBackground)
                        .padding(12.dp),
                ) {
                    if (comment.isEmpty()) {
                        Text(stringResource(R.string.rating_commentPlaceholder), fontSize = 14.sp, color = TteMediumGray)
                    }
                    BasicTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = TteDarkGray, fontFamily = Pretendard),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TteFieldBackground)
                        .clickable(onClick = onDismiss),
                ) {
                    Text(stringResource(R.string.rating_later), fontSize = 15.sp, color = TteMediumGray)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectedRating > 0) TteOrange else TteMediumGray.copy(alpha = 0.4f))
                        .clickable(enabled = !isSubmitting) { submit() },
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                    } else {
                        Text(
                            if (selectedRating > 0) stringResource(R.string.rating_submit) else stringResource(R.string.common_skip),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}
