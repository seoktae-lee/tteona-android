package com.seoktaedev.tteona.features.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.SubcomposeAsyncImage
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.services.ProfileImageService
import com.seoktaedev.tteona.core.services.UserService
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.launch

/**
 * 설정 탭 — iOS Features/Settings/SettingsView.swift의 이식본.
 * 하위 화면(여행 통계·차단 관리)은 iOS NavigationStack push처럼 탭 콘텐츠 영역 안에서 전환한다.
 */
private enum class SettingsSubScreen { MAIN, STATS, BLOCKED }

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var subScreen by rememberSaveable { mutableStateOf(SettingsSubScreen.MAIN) }

    when (subScreen) {
        SettingsSubScreen.MAIN -> SettingsMain(
            modifier = modifier,
            onOpenStats = { subScreen = SettingsSubScreen.STATS },
            onOpenBlocked = { subScreen = SettingsSubScreen.BLOCKED },
        )
        SettingsSubScreen.STATS -> {
            BackHandler { subScreen = SettingsSubScreen.MAIN }
            TravelStatsScreen(modifier = modifier, onBack = { subScreen = SettingsSubScreen.MAIN })
        }
        SettingsSubScreen.BLOCKED -> {
            BackHandler { subScreen = SettingsSubScreen.MAIN }
            BlockedUsersScreen(modifier = modifier, onBack = { subScreen = SettingsSubScreen.MAIN })
        }
    }
}

@Composable
private fun SettingsMain(
    modifier: Modifier,
    onOpenStats: () -> Unit,
    onOpenBlocked: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authUser by AuthService.currentUser.collectAsState()
    val profileUser by UserService.currentUser.collectAsState()

    var showSignOutAlert by rememberSaveable { mutableStateOf(false) }
    var showPaywall by rememberSaveable { mutableStateOf(false) }
    var showDeleteAlert by rememberSaveable { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteFailed by remember { mutableStateOf(false) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var notificationGranted by remember { mutableStateOf<Boolean?>(null) }

    // Firestore 프로필 로드
    LaunchedEffect(authUser?.uid) {
        authUser?.uid?.let { UserService.fetchUser(it) }
    }

    // 알림 권한 상태 — 설정 앱에 다녀온 뒤에도 갱신되도록 ON_RESUME마다 확인 (iOS didBecomeActive 대응)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 갤러리 사진 선택 → WAS 업로드
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val uid = authUser?.uid
        if (uri != null && uid != null) {
            scope.launch {
                isUploadingAvatar = true
                ProfileImageService.upload(context, uid, uri)?.let { UserService.setProfileImageUrl(it) }
                isUploadingAvatar = false
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                "설정",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 24.dp, bottom = 16.dp),
            )

            // 프로필 섹션
            SectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(14.dp),
                ) {
                    AvatarWithPicker(
                        nickname = profileUser?.nickname,
                        imageUrl = profileUser?.profileImageUrl,
                        isUploading = isUploadingAvatar,
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    )
                    Column {
                        Text(
                            profileUser?.nickname?.takeIf { it.isNotEmpty() } ?: "닉네임 없음",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TteDarkGray,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(authUser?.email ?: "", fontSize = 13.sp, color = TteMediumGray)
                    }
                }
                SettingsDivider()
                SettingsRow(Icons.Filled.BarChart, "내 여행 통계", onClick = onOpenStats) { Chevron() }
            }

            // tteona PRO (iOS proSection)
            val isPro by com.seoktaedev.tteona.core.services.ProManager.isPro.collectAsState()
            SectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isPro) { showPaywall = true }
                        .padding(14.dp),
                ) {
                    Icon(
                        Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = TteOrange,
                        modifier = Modifier.size(24.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                        Text(
                            if (isPro) "tteona PRO 이용 중" else "tteona PRO",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TteDarkGray,
                        )
                        Text(
                            if (isPro) "모든 프리미엄 기능이 켜져 있어요"
                            else "워터마크 제거 · 멀티포맷 · 5분 영상 · 우선 렌더링",
                            fontSize = 12.sp,
                            color = TteMediumGray,
                        )
                    }
                    if (isPro) {
                        Icon(Icons.Filled.Verified, contentDescription = null, tint = TteOrange, modifier = Modifier.size(20.dp))
                    } else {
                        Chevron()
                    }
                }
            }

            SectionHeader("앱 정보")
            SectionCard {
                val versionName = remember {
                    runCatching {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    }.getOrNull() ?: "-"
                }
                SettingsRow(Icons.Filled.Info, "버전") {
                    Text(versionName, fontSize = 14.sp, color = TteMediumGray)
                }
                SettingsDivider()
                SettingsRow(Icons.Filled.Notifications, "푸시알림", onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }) {
                    notificationGranted?.let { granted ->
                        Text(
                            if (granted) "켜짐" else "꺼짐",
                            fontSize = 14.sp,
                            color = if (granted) TteMediumGray else Color.Red,
                        )
                    }
                    Chevron()
                }
                SettingsDivider()
                SettingsRow(Icons.Filled.Language, "언어", onClick = {
                    // 앱별 언어 설정 (Android 13+), 미지원 기기는 앱 정보 화면으로
                    val intent = Intent(
                        if (android.os.Build.VERSION.SDK_INT >= 33) Settings.ACTION_APP_LOCALE_SETTINGS
                        else Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    ).setData(Uri.parse("package:${context.packageName}"))
                    runCatching { context.startActivity(intent) }
                }) {
                    Text("한국어", fontSize = 14.sp, color = TteMediumGray)
                    Chevron()
                }
                SettingsDivider()
                SettingsRow(Icons.Filled.Shield, "개인정보 처리방침", onClick = {
                    context.openUrl("https://tteona.kr/privacy.html")
                }) { Chevron() }
                SettingsDivider()
                SettingsRow(Icons.Filled.Description, "이용약관", onClick = {
                    context.openUrl("https://tteona.kr/terms.html")
                }) { Chevron() }
                SettingsDivider()
                SettingsRow(Icons.Filled.VerifiedUser, "아동 안전 기준 정책", onClick = {
                    context.openUrl("https://tteona.kr/child-safety.html")
                }) { Chevron() }
                SettingsDivider()
                SettingsRow(Icons.Filled.Mail, "문의하기", onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:just.tteona@gmail.com"))
                        )
                    }
                }) { Chevron() }
            }

            SectionHeader("계정")
            SectionCard {
                SettingsRow(Icons.Filled.PersonOff, "차단된 사용자 관리", onClick = onOpenBlocked) { Chevron() }
                SettingsDivider()
                SettingsRow(
                    Icons.AutoMirrored.Filled.Logout, "로그아웃",
                    tint = Color.Red,
                    onClick = { showSignOutAlert = true },
                )
                SettingsDivider()
                SettingsRow(
                    Icons.Filled.PersonRemove, "회원 탈퇴",
                    tint = Color.Red,
                    onClick = { showDeleteAlert = true },
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        // PRO 페이월 (iOS sheet showPaywall)
        if (showPaywall) {
            com.seoktaedev.tteona.features.pro.ProPaywallScreen(onDismiss = { showPaywall = false })
        }

        // 탈퇴 진행 오버레이 (iOS isDeletingAccount overlay 대응)
        if (isDeleting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(28.dp),
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text("탈퇴 처리 중...", fontSize = 14.sp, color = Color.White)
                }
            }
        }
    }

    if (showSignOutAlert) {
        AlertDialog(
            onDismissRequest = { showSignOutAlert = false },
            title = { Text("로그아웃") },
            text = { Text("정말 로그아웃 하시겠어요?") },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutAlert = false
                    AuthService.signOut()
                }) { Text("로그아웃", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showSignOutAlert = false }) { Text("취소") } },
        )
    }

    if (showDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showDeleteAlert = false },
            title = { Text("회원 탈퇴") },
            text = { Text("탈퇴 시 코스, 그룹, 계정 정보가 모두 삭제되며 복구할 수 없어요.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAlert = false
                    scope.launch {
                        isDeleting = true
                        val ok = AuthService.deleteAccount(context)
                        isDeleting = false
                        if (!ok) deleteFailed = true
                    }
                }) { Text("탈퇴", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showDeleteAlert = false }) { Text("취소") } },
        )
    }

    if (deleteFailed) {
        AlertDialog(
            onDismissRequest = { deleteFailed = false },
            title = { Text("탈퇴 실패") },
            text = { Text("회원 탈퇴에 실패했어요. 잠시 후 다시 시도해주세요.") },
            confirmButton = {
                TextButton(onClick = { deleteFailed = false }) { Text("확인", color = TteOrange) }
            },
        )
    }
}

// MARK: - 프로필 아바타 (카메라 뱃지 + 업로드 상태)
@Composable
private fun AvatarWithPicker(
    nickname: String?,
    imageUrl: String?,
    isUploading: Boolean,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.clickable(enabled = !isUploading, onClick = onClick)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(TteOrange.copy(alpha = 0.15f)),
        ) {
            if (imageUrl != null) {
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = "프로필 사진",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = { AvatarInitial(nickname) },
                )
            } else {
                AvatarInitial(nickname)
            }
            if (isUploading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
        // 카메라 뱃지
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 2.dp, y = 2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(TteOrange),
        ) {
            Icon(
                Icons.Filled.CameraAlt,
                contentDescription = "사진 변경",
                tint = Color.White,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

@Composable
private fun AvatarInitial(nickname: String?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            (nickname?.takeIf { it.isNotEmpty() } ?: "?").take(1),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = TteOrange,
        )
    }
}

// MARK: - 공용 컴포넌트 (iOS List Section 대응)

@Composable
internal fun SectionHeader(title: String) {
    Text(
        title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = TteMediumGray,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
internal fun SectionCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TteFieldBackground),
    ) {
        content()
    }
}

@Composable
internal fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 48.dp)
            .height(0.5.dp)
            .background(TteMediumGray.copy(alpha = 0.2f))
    )
}

@Composable
internal fun SettingsRow(
    icon: ImageVector,
    label: String,
    tint: Color = TteDarkGray,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (tint == Color.Red) Color.Red else TteOrange,
            modifier = Modifier.size(20.dp),
        )
        Text(label, fontSize = 15.sp, color = tint, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
internal fun Chevron() {
    Icon(
        Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = TteMediumGray.copy(alpha = 0.5f),
        modifier = Modifier.size(18.dp),
    )
}

private fun android.content.Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
