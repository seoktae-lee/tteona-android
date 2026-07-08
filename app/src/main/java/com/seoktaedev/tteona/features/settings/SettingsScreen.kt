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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.SubcomposeAsyncImage
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.i18n.LocaleManager
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
private enum class SettingsSubScreen { MAIN, STATS, BLOCKED, LANGUAGE }

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var subScreen by rememberSaveable { mutableStateOf(SettingsSubScreen.MAIN) }

    when (subScreen) {
        SettingsSubScreen.MAIN -> SettingsMain(
            modifier = modifier,
            onOpenStats = { subScreen = SettingsSubScreen.STATS },
            onOpenBlocked = { subScreen = SettingsSubScreen.BLOCKED },
            onOpenLanguage = { subScreen = SettingsSubScreen.LANGUAGE },
        )
        SettingsSubScreen.STATS -> {
            BackHandler { subScreen = SettingsSubScreen.MAIN }
            TravelStatsScreen(modifier = modifier, onBack = { subScreen = SettingsSubScreen.MAIN })
        }
        SettingsSubScreen.BLOCKED -> {
            BackHandler { subScreen = SettingsSubScreen.MAIN }
            BlockedUsersScreen(modifier = modifier, onBack = { subScreen = SettingsSubScreen.MAIN })
        }
        SettingsSubScreen.LANGUAGE -> {
            BackHandler { subScreen = SettingsSubScreen.MAIN }
            LanguageSettingsScreen(modifier = modifier, onBack = { subScreen = SettingsSubScreen.MAIN })
        }
    }
}

@Composable
private fun SettingsMain(
    modifier: Modifier,
    onOpenStats: () -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenLanguage: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authUser by AuthService.currentUser.collectAsState()
    val profileUser by UserService.currentUser.collectAsState()

    var showSignOutAlert by rememberSaveable { mutableStateOf(false) }
    var showPaywall by rememberSaveable { mutableStateOf(false) }
    var showDeleteAlert by rememberSaveable { mutableStateOf(false) }
    var showNicknameEdit by rememberSaveable { mutableStateOf(false) }
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
                stringResource(R.string.settings_title),
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
                    // 닉네임 영역 탭 → 변경 시트 (updateNickname 서비스는 있었지만 진입점이 없던 문제 해결)
                    Column(Modifier.clickable { showNicknameEdit = true }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                profileUser?.nickname?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.settings_noNickname),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TteDarkGray,
                            )
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.settings_editNickname),
                                tint = TteMediumGray,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(authUser?.email ?: "", fontSize = 13.sp, color = TteMediumGray)
                    }
                }
                SettingsDivider()
                SettingsRow(Icons.Filled.BarChart, stringResource(R.string.settings_travelStats), onClick = onOpenStats) { Chevron() }
                SettingsDivider()
                TravelStyleRow(
                    currentTag = profileUser?.preferredTag,
                    onSelect = { tag ->
                        authUser?.uid?.let { uid ->
                            scope.launch {
                                runCatching { UserService.updatePreferredTag(uid, tag) }
                            }
                        }
                    },
                )
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
                            if (isPro) stringResource(R.string.settings_pro_active) else "tteona PRO",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TteDarkGray,
                        )
                        Text(
                            if (isPro) stringResource(R.string.settings_pro_activeDesc)
                            else stringResource(R.string.settings_pro_features),
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

            SectionHeader(stringResource(R.string.settings_appInfo))
            SectionCard {
                val versionName = remember {
                    runCatching {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    }.getOrNull() ?: "-"
                }
                SettingsRow(Icons.Filled.Info, stringResource(R.string.settings_version)) {
                    Text(versionName, fontSize = 14.sp, color = TteMediumGray)
                }
                SettingsDivider()
                SettingsRow(Icons.Filled.Notifications, stringResource(R.string.settings_push), onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }) {
                    notificationGranted?.let { granted ->
                        Text(
                            if (granted) stringResource(R.string.common_on) else stringResource(R.string.common_off),
                            fontSize = 14.sp,
                            color = if (granted) TteMediumGray else Color.Red,
                        )
                    }
                    Chevron()
                }
                SettingsDivider()
                val currentLang = remember { LocaleManager.current(context) }
                SettingsRow(Icons.Filled.Language, stringResource(R.string.settings_language), onClick = onOpenLanguage) {
                    Text("${currentLang.flag} ${currentLang.nativeName}", fontSize = 14.sp, color = TteMediumGray)
                    Chevron()
                }
                SettingsDivider()
                SettingsRow(Icons.Filled.Shield, stringResource(R.string.settings_privacy), onClick = {
                    context.openUrl("https://tteona.kr/privacy.html")
                }) { Chevron() }
                SettingsDivider()
                SettingsRow(Icons.Filled.Description, stringResource(R.string.settings_terms), onClick = {
                    context.openUrl("https://tteona.kr/terms.html")
                }) { Chevron() }
                SettingsDivider()
                SettingsRow(Icons.Filled.VerifiedUser, stringResource(R.string.settings_childSafety), onClick = {
                    context.openUrl("https://tteona.kr/child-safety.html")
                }) { Chevron() }
                SettingsDivider()
                SettingsRow(Icons.Filled.Mail, stringResource(R.string.settings_contact), onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:just.tteona@gmail.com"))
                        )
                    }
                }) { Chevron() }
            }

            SectionHeader(stringResource(R.string.settings_account))
            SectionCard {
                SettingsRow(Icons.Filled.PersonOff, stringResource(R.string.settings_blockedUsers), onClick = onOpenBlocked) { Chevron() }
                SettingsDivider()
                SettingsRow(
                    Icons.AutoMirrored.Filled.Logout, stringResource(R.string.settings_signOut),
                    tint = Color.Red,
                    onClick = { showSignOutAlert = true },
                )
                SettingsDivider()
                SettingsRow(
                    Icons.Filled.PersonRemove, stringResource(R.string.settings_deleteAccount),
                    tint = Color.Red,
                    onClick = { showDeleteAlert = true },
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        // 닉네임 변경 시트 (iOS sheet showNicknameEdit)
        if (showNicknameEdit) {
            NicknameEditSheet(onDismiss = { showNicknameEdit = false })
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
                    Text(stringResource(R.string.settings_deleting), fontSize = 14.sp, color = Color.White)
                }
            }
        }
    }

    if (showSignOutAlert) {
        AlertDialog(
            onDismissRequest = { showSignOutAlert = false },
            title = { Text(stringResource(R.string.settings_signOut)) },
            text = { Text(stringResource(R.string.settings_signOut_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutAlert = false
                    AuthService.signOut()
                }) { Text(stringResource(R.string.settings_signOut), color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showSignOutAlert = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    if (showDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showDeleteAlert = false },
            title = { Text(stringResource(R.string.settings_deleteAccount)) },
            text = { Text(stringResource(R.string.settings_deleteAccount_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAlert = false
                    scope.launch {
                        isDeleting = true
                        val ok = AuthService.deleteAccount(context)
                        isDeleting = false
                        if (!ok) deleteFailed = true
                    }
                }) { Text(stringResource(R.string.settings_deleteAccount_confirmButton), color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showDeleteAlert = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    if (deleteFailed) {
        AlertDialog(
            onDismissRequest = { deleteFailed = false },
            title = { Text(stringResource(R.string.settings_deleteFailed_title)) },
            text = { Text(stringResource(R.string.settings_deleteFailed_message)) },
            confirmButton = {
                TextButton(onClick = { deleteFailed = false }) { Text(stringResource(R.string.common_ok), color = TteOrange) }
            },
        )
    }
}

// MARK: - 여행 취향 행 (iOS travelStyleRow) — 온보딩을 건너뛴 유저·기존 유저의 설정 경로
@Composable
private fun TravelStyleRow(
    currentTag: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val tags = com.seoktaedev.tteona.core.model.CourseTag.entries
    val current = tags.firstOrNull { it.label == currentTag }

    Box {
        SettingsRow(
            Icons.Filled.FavoriteBorder,
            stringResource(R.string.settings_travelStyle),
            onClick = { expanded = true },
        ) {
            Text(
                if (current != null) "${current.emoji} ${stringResource(current.labelRes)}"
                else stringResource(R.string.settings_travelStyle_none),
                fontSize = 14.sp,
                color = TteMediumGray,
            )
            Chevron()
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            tags.forEach { tag ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("${tag.emoji} ${stringResource(tag.labelRes)}") },
                    trailingIcon = {
                        if (currentTag == tag.label) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = TteOrange, modifier = Modifier.size(16.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(tag.label)
                    },
                )
            }
            androidx.compose.material3.HorizontalDivider()
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_travelStyle_none)) },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
        }
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
                    contentDescription = stringResource(R.string.settings_profilePhoto),
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
                contentDescription = stringResource(R.string.settings_changePhoto),
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
