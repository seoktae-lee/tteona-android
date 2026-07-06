package com.seoktaedev.tteona.features.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.core.services.ReportService
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange

/** 신고 사유 선택 다이얼로그 — iOS confirmationDialog("신고 사유를 선택해주세요") 대응 */
@Composable
fun ReportReasonDialog(
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("신고 사유를 선택해주세요") },
        text = {
            Column {
                ReportService.REASONS.forEach { reason ->
                    Text(
                        reason,
                        fontSize = 15.sp,
                        color = TteDarkGray,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDismiss(); onSelect(reason) }
                            .padding(vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", color = TteMediumGray) }
        },
    )
}

/** 확인 버튼 하나짜리 안내 알림 — iOS .alert(title, message) 대응 */
@Composable
fun InfoAlert(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("확인", color = TteOrange) }
        },
    )
}

/** 파괴적 액션 확인 다이얼로그 — iOS .alert + destructive 버튼 대응 */
@Composable
fun DestructiveConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirmLabel, color = Color.Red) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", color = TteMediumGray) }
        },
    )
}
