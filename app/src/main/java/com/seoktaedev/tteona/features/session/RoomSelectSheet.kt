package com.seoktaedev.tteona.features.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.services.RoomService
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange

/**
 * 공유할 그룹 선택 시트 — iOS Features/Main/RoomSelectView.swift의 이식본.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomSelectSheet(
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val myRooms by RoomService.myRooms.collectAsState()
    var selectedRoomIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        AuthService.currentUser.value?.uid?.let { RoomService.startListeningMyRooms(it) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp),
        ) {
            Text(stringResource(R.string.roomselect_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TteDarkGray)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.roomselect_subtitle), fontSize = 14.sp, color = TteMediumGray)
            Spacer(Modifier.height(28.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 20.dp),
            ) {
                myRooms.forEach { room ->
                    val isSelected = room.roomId in selectedRoomIds
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) TteOrange.copy(alpha = 0.08f) else TteFieldBackground)
                            .then(
                                if (isSelected) Modifier.border(1.5.dp, TteOrange.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                else Modifier
                            )
                            .clickable {
                                selectedRoomIds =
                                    if (isSelected) selectedRoomIds - room.roomId
                                    else selectedRoomIds + room.roomId
                            }
                            .padding(horizontal = 16.dp),
                    ) {
                        Icon(
                            if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) TteOrange else TteMediumGray.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(room.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TteDarkGray)
                            Text(stringResource(R.string.roomselect_members, room.memberIds.size), fontSize = 13.sp, color = TteMediumGray)
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TteOrange)
                    .clickable { onConfirm(selectedRoomIds) },
            ) {
                Text(
                    if (selectedRoomIds.isEmpty()) stringResource(R.string.roomselect_startWithoutSharing) else stringResource(R.string.roomselect_start),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.common_cancel),
                fontSize = 15.sp,
                color = TteMediumGray,
                modifier = Modifier.clickable(onClick = onDismiss),
            )
        }
    }
}
