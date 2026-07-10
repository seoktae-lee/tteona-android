@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.seoktaedev.tteona.features.profile

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.SubcomposeAsyncImage
import com.google.android.gms.location.LocationServices
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.i18n.LocaleManager
import com.seoktaedev.tteona.core.model.AppUser
import com.seoktaedev.tteona.core.model.Course
import com.seoktaedev.tteona.core.model.FootprintPoint
import com.seoktaedev.tteona.core.model.FootprintRecord
import com.seoktaedev.tteona.core.model.TravelStats
import com.seoktaedev.tteona.core.services.CourseThumbnailService
import com.seoktaedev.tteona.core.services.FootprintAtlas
import com.seoktaedev.tteona.core.services.FootprintService
import com.seoktaedev.tteona.core.services.PlacesPhotoService
import com.seoktaedev.tteona.core.services.ProfileImageService
import com.seoktaedev.tteona.core.services.StatsService
import com.seoktaedev.tteona.core.services.UserService
import com.seoktaedev.tteona.features.explore.CourseDetailScreen
import com.seoktaedev.tteona.features.settings.NicknameEditSheet
import com.seoktaedev.tteona.features.settings.SettingsScreen
import com.seoktaedev.tteona.core.util.pressableCard
import com.seoktaedev.tteona.features.settings.TravelStatsScreen
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.DateFormat
import java.util.Date
import kotlin.coroutines.resume

// iOS Features/Profile/ProfileTabView.swift의 Kotlin 이식본.

private enum class ProfileSubScreen { MAIN, SEARCH, SETTINGS, STATS, USER }

/**
 * 프로필 탭 (나만의 공간) — 인스타그램식 마지막 탭.
 * 내 프로필 + 발자취 지도 + 여행 기록. 설정은 우상단 톱니, 유저 검색은 우상단 돋보기.
 */
@Composable
fun ProfileTabScreen(modifier: Modifier = Modifier, previewFootprintDemo: Boolean = false) {
    var subScreen by rememberSaveable { mutableStateOf(ProfileSubScreen.MAIN) }
    var selectedUser by remember { mutableStateOf<AppUser?>(null) }

    when (subScreen) {
        ProfileSubScreen.MAIN -> ProfileMain(
            modifier = modifier,
            previewFootprintDemo = previewFootprintDemo,
            onOpenSearch = { subScreen = ProfileSubScreen.SEARCH },
            onOpenSettings = { subScreen = ProfileSubScreen.SETTINGS },
            onOpenStats = { subScreen = ProfileSubScreen.STATS },
        )
        ProfileSubScreen.SEARCH -> {
            BackHandler { subScreen = ProfileSubScreen.MAIN }
            UserSearchScreen(
                modifier = modifier,
                onBack = { subScreen = ProfileSubScreen.MAIN },
                onUserClick = { user ->
                    selectedUser = user
                    subScreen = ProfileSubScreen.USER
                },
            )
        }
        ProfileSubScreen.SETTINGS -> {
            BackHandler { subScreen = ProfileSubScreen.MAIN }
            SettingsScreen(modifier, onBack = { subScreen = ProfileSubScreen.MAIN })
        }
        ProfileSubScreen.STATS -> {
            BackHandler { subScreen = ProfileSubScreen.MAIN }
            TravelStatsScreen(modifier = modifier, onBack = { subScreen = ProfileSubScreen.MAIN })
        }
        ProfileSubScreen.USER -> {
            val user = selectedUser
            if (user == null) {
                subScreen = ProfileSubScreen.SEARCH
            } else {
                BackHandler { subScreen = ProfileSubScreen.SEARCH }
                UserProfileScreen(
                    user = user,
                    modifier = modifier,
                    onBack = { subScreen = ProfileSubScreen.SEARCH },
                )
            }
        }
    }
}

@Composable
private fun ProfileMain(
    modifier: Modifier,
    previewFootprintDemo: Boolean = false,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authUser by AuthService.currentUser.collectAsState()
    val profileUser by UserService.currentUser.collectAsState()
    val summary by FootprintService.mySummary.collectAsState()

    var footprints by remember { mutableStateOf<List<FootprintRecord>>(emptyList()) }
    var stats by remember { mutableStateOf<TravelStats?>(null) }
    var isLoaded by remember { mutableStateOf(false) }

    // 내 코스 + 썸네일 (프로필에서 직접 썸네일 꾸미기 + 탭하면 상세)
    var myCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var thumbnails by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var editingCourseId by remember { mutableStateOf<String?>(null) }
    var uploadingCourseId by remember { mutableStateOf<String?>(null) }
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var showFullMap by remember { mutableStateOf(false) }

    // 발자취 지도 연출
    var focusCommand by remember { mutableStateOf<FootprintMapFocus?>(null) }
    var highlightCodes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var greetingText by remember { mutableStateOf<String?>(null) }

    var showNicknameEdit by rememberSaveable { mutableStateOf(false) }
    var isUploadingAvatar by remember { mutableStateOf(false) }

    val greetingFormat = stringResource(R.string.footprint_greeting)
    val newRegionText = stringResource(R.string.footprint_newRegion)

    // 홈 국가 — 발자취에서 가장 많이 기록된 나라, 없으면 시스템 지역 (iOS homeCountryCode)
    fun homeCountryCode(records: List<FootprintRecord>): String {
        val counts = mutableMapOf<String, Int>()
        for (record in records) {
            for (code in record.countryCodes) counts[code] = (counts[code] ?: 0) + 1
        }
        counts.maxByOrNull { it.value }?.let { return it.key }
        val alpha2 = java.util.Locale.getDefault().country.ifEmpty { "KR" }
        if (alpha2 == "KR") return "KOR"
        // ISO2 → ISO3 매핑: 해당 국가의 주/도 코드(ISO 3166-2, "US-CA")가 alpha2로 시작
        return FootprintAtlas.worldProvinces.firstOrNull { it.code.startsWith("$alpha2-") }?.country ?: "KOR"
    }

    var initialFocus by remember { mutableStateOf<FootprintMapFocus>(FootprintMapFocus.Korea) }

    /** 새로 칠해진 지역이 있으면: 그 지역으로 날아가 펄스 하이라이트 (브이로그 완성의 보상 연출) */
    fun playNewRegionRevealIfNeeded() {
        val (newCodes, primary) = FootprintService.consumeLastNew()
        if (newCodes.isEmpty()) return
        highlightCodes = newCodes
        greetingText = newRegionText
        // 대표(최다 체류) 신규 지역으로 카메라 이동 — 없으면 시군구 우선 폴백
        val target = primary ?: newCodes.firstOrNull { it.length == 5 } ?: newCodes.firstOrNull()
        when {
            target != null && target.length == 5 ->
                FootprintAtlas.koreaRegion(target)?.let { region ->
                    val (lat, lng) = FootprintAtlas.unproject(region.center)
                    focusCommand = FootprintMapFocus.Point(lat, lng)
                }
            target != null && target.length == 3 ->
                focusCommand = FootprintMapFocus.Country(target)
        }
        scope.launch {
            delay(6000)
            highlightCodes = emptySet()
            greetingText = null
        }
    }

    /** 현재 위치가 홈과 다른 나라·지역이면 "지금 여기 있네요" 안내 후 카메라 이동 (iOS greetIfTravelling) */
    suspend fun greetIfTravelling(home: String) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val location = suspendCancellableCoroutine<android.location.Location?> { cont ->
            LocationServices.getFusedLocationProviderClient(context).lastLocation
                .addOnSuccessListener { loc -> cont.resume(loc) }
                .addOnFailureListener { cont.resume(null) }
        } ?: return
        val resolved = FootprintAtlas.resolve(context, location.latitude, location.longitude)
        val region = resolved.sig ?: resolved.province ?: return
        val travelling = resolved.countryCode != null && resolved.countryCode != home
        val unpainted = resolved.sig?.let { it.code !in summary.sigCodes }
            ?: resolved.province?.let { it.code !in summary.provinceCodes } ?: false
        if (!travelling && !unpainted) return

        delay(1200)
        val isKorean = LocaleManager.current(context).code == "ko"
        val name = if (!isKorean && region.nameEng != null) region.nameEng!! else region.name
        greetingText = String.format(greetingFormat, name)
        focusCommand = FootprintMapFocus.Point(location.latitude, location.longitude)
        delay(5000)
        greetingText = null
    }

    LaunchedEffect(Unit) {
        if (previewFootprintDemo) {
            // 시각 검증용 가짜 데이터 (iOS loadDemoFootprints 대응)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                FootprintAtlas.ensureLoaded(context)
            }
            val demoNames = setOf("종로구", "중구", "용산구", "성동구", "마포구", "강남구",
                "강릉시", "전주시", "경주시", "제주시", "서귀포시", "속초시", "여수시")
            val sigs = FootprintAtlas.koreaRegions.filter { it.name in demoNames }.map { it.code }
            // 해외는 주/도만 색칠 — 오사카부(JP-27) · 방콕(TH-10)
            FootprintService.setDemoSummary(sigs.toSet(), setOf("JP-27", "TH-10"), setOf("KOR", "JPN", "THA"))
            footprints = listOf(
                FootprintRecord(id = "d1", courseName = "성수동 감성 카페 투어", date = System.currentTimeMillis(),
                    regionNames = listOf("서울 성동구"),
                    points = listOf(FootprintPoint(37.5446, 127.0559), FootprintPoint(37.5479, 127.0473))),
                FootprintRecord(id = "d2", courseName = "강릉 바다 브이로그",
                    date = System.currentTimeMillis() - 86400000L * 12,
                    regionNames = listOf("강릉시"),
                    points = listOf(FootprintPoint(37.7710, 128.9473), FootprintPoint(37.8054, 128.8961))),
                FootprintRecord(id = "d3", courseName = "오사카 먹방 여행",
                    date = System.currentTimeMillis() - 86400000L * 40,
                    regionNames = listOf("Ōsaka"),
                    points = listOf(FootprintPoint(34.6687, 135.5010), FootprintPoint(34.6525, 135.5060))),
            )
            myCourses = listOf(
                Course(courseId = "c1", authorId = "me", courseName = "성수동 감성 카페 투어",
                    tag = com.seoktaedev.tteona.core.model.CourseTag.FRIENDS, region = "서울",
                    likeCount = 12, createdAt = System.currentTimeMillis(),
                    places = listOf(com.seoktaedev.tteona.core.model.Place(1, "대림창고", 37.5446, 127.0559))),
                Course(courseId = "c2", authorId = "me", courseName = "강릉 바다 브이로그",
                    tag = com.seoktaedev.tteona.core.model.CourseTag.COUPLE, region = "강릉",
                    likeCount = 34, createdAt = System.currentTimeMillis(),
                    places = listOf(com.seoktaedev.tteona.core.model.Place(1, "안목해변", 37.7710, 128.9473))),
            )
            initialFocus = FootprintMapFocus.Korea
            isLoaded = true
            return@LaunchedEffect
        }
        val uid = authUser?.uid ?: return@LaunchedEffect
        if (profileUser == null) UserService.fetchUser(uid)
        // 1) 요약 로드 → 2) 과거 코스 백필(1회, mySummary 즉시 갱신) → 3) 나머지 조회
        FootprintService.fetchSummary(uid, isMe = true)
        FootprintService.backfillFromMyCourses(context, uid)
        footprints = FootprintService.fetchFootprints(uid)   // 백필 후 재조회 → 과거 코스 포함
        stats = StatsService.fetchMyStats(uid)
        myCourses = FootprintService.fetchCourses(uid)
        thumbnails = runCatching { CourseThumbnailService.fetchAllThumbnails() }.getOrDefault(emptyMap())
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            FootprintAtlas.ensureLoaded(context)
        }
        val home = homeCountryCode(footprints)
        initialFocus = if (home == "KOR") FootprintMapFocus.Korea else FootprintMapFocus.Country(home)
        isLoaded = true
        playNewRegionRevealIfNeeded()
        if (highlightCodes.isEmpty()) greetIfTravelling(home)
    }

    // 코스 썸네일 교체 — 카드의 카메라 버튼이 editingCourseId를 세팅하고 피커를 연다
    val thumbnailPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val courseId = editingCourseId
        editingCourseId = null
        if (uri != null && courseId != null) {
            scope.launch {
                uploadingCourseId = courseId
                val url = CourseThumbnailService.upload(context, courseId, uri)
                if (url != null) {
                    // 서버가 ?v=timestamp 캐시버스트를 붙여 반환 → 그대로 사용하면 탐색탭·DB와 완전히 동일
                    thumbnails = thumbnails + (courseId to url)
                }
                uploadingCourseId = null
            }
        }
    }

    // 브이로그 생성 직후 탭 전환 시 새 지역 연출
    val lastNewCodes by FootprintService.lastNewCodes.collectAsState()
    LaunchedEffect(lastNewCodes) {
        if (isLoaded && lastNewCodes.isNotEmpty()) playNewRegionRevealIfNeeded()
    }

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
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // 상단 툴바 — 우측 돋보기(유저 검색) + 톱니(설정)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = stringResource(R.string.profile_searchUsers),
                        tint = TteDarkGray,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = TteDarkGray,
                    )
                }
            }

            // 헤더 — 아바타 + 닉네임 + 이메일 (iOS header)
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box {
                    ProfileAvatar(
                        nickname = profileUser?.nickname,
                        imageUrl = profileUser?.profileImageUrl,
                        size = 84.dp,
                        fontSize = 32.sp,
                        modifier = Modifier.clickable {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    )
                    if (isUploadingAvatar) {
                        Box(
                            Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp)) }
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .size(26.dp)
                            .background(TteOrange, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.CameraAlt, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(13.dp),
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { showNicknameEdit = true },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            profileUser?.nickname?.takeIf { it.isNotEmpty() }
                                ?: stringResource(R.string.settings_noNickname),
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = TteDarkGray,
                        )
                        if (profileUser?.isVerified == true) {
                            Icon(
                                Icons.Filled.Verified, contentDescription = null,
                                tint = TteOrange, modifier = Modifier.size(16.dp),
                            )
                        }
                        Icon(
                            Icons.Filled.Edit, contentDescription = null,
                            tint = TteMediumGray, modifier = Modifier.size(13.dp),
                        )
                    }
                    profileUser?.creatorLabel?.takeIf { it.isNotEmpty() }?.let { label ->
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TteOrange)
                    }
                    Text(authUser?.email ?: "", fontSize = 12.sp, color = TteMediumGray)
                }
            }

            Spacer(Modifier.height(24.dp))

            // 통계 스트립 (iOS statsStrip) — 탭하면 여행 통계 상세
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(TteFieldBackground)
                    .clickable(onClick = onOpenStats)
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatCell(stats?.coursesCreated?.toString() ?: "–", stringResource(R.string.profile_stats_courses))
                StatDivider()
                StatCell(stats?.likesReceived?.toString() ?: "–", stringResource(R.string.profile_stats_likes))
                StatDivider()
                StatCell("${summary.sigCodes.size}", stringResource(R.string.profile_stats_regions))
                StatDivider()
                StatCell("${summary.countryCodes.size}", stringResource(R.string.profile_stats_countries))
            }

            Spacer(Modifier.height(24.dp))

            // 발자취 지도 섹션 (iOS footprintSection)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    stringResource(R.string.footprint_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TteDarkGray,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.footprint_subtitle, summary.sigCodes.size, summary.countryCodes.size),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TteOrange,
                )
            }
            Spacer(Modifier.height(12.dp))

            Box(Modifier.padding(horizontal = 20.dp)) {
                FootprintMapView(
                    summary = summary,
                    routes = footprints.map { it.points },
                    highlightCodes = highlightCodes,
                    interactive = true,
                    panZoom = false,   // 페이지 스크롤과 충돌 방지 — 탭만
                    initialFocus = initialFocus,
                    focusCommand = focusCommand,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .clip(RoundedCornerShape(24.dp)),
                )
                // 전체화면 확대 버튼 (우상단) — 여기서만 팬/핀치로 세계지도를 자유 탐색
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { showFullMap = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.OpenInFull,
                        contentDescription = stringResource(R.string.footprint_title),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
                // "지금 여기 있네요" / "새 지역!" 배너
                greetingText?.let { text ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-14).dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(R.drawable.tteoni_wink),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TteDarkGray)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 한국 시군구 채움률 게이지 (iOS koreaProgressBar)
            val total = maxOf(FootprintAtlas.koreaRegions.size, 250)
            val filled = summary.sigCodes.size
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEFEAE1)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction = (filled.toFloat() / total).coerceIn(0f, 1f))
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(listOf(Color(0xFFFF8B5E), TteOrange))
                            ),
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.footprint_koreaProgress, filled, total),
                        fontSize = 11.sp, color = TteMediumGray, modifier = Modifier.weight(1f),
                    )
                    Text(
                        String.format("%.1f%%", filled.toFloat() / total * 100),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TteOrange,
                    )
                }
            }

            // 빈 상태 힌트 (iOS emptyFootprintHint)
            if (summary.isEmpty) {
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(TteOrange.copy(alpha = 0.07f))
                        .padding(14.dp),
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.tteoni_wink),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                    Text(
                        stringResource(R.string.footprint_empty),
                        fontSize = 13.sp, color = TteMediumGray,
                    )
                }
            }

            // 내 코스 (썸네일 꾸미기) — iOS coursesSection
            if (myCourses.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) {
                    Text(
                        stringResource(R.string.profile_myCourses),
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TteDarkGray,
                    )
                    Icon(
                        Icons.Filled.AddAPhoto, contentDescription = null,
                        tint = TteMediumGray, modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.profile_myCourses_hint),
                    fontSize = 12.sp, color = TteMediumGray,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) {
                    myCourses.chunked(2).forEach { rowCourses ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowCourses.forEach { course ->
                                Box(Modifier.weight(1f)) {
                                    EditableCourseCard(
                                        course = course,
                                        thumbnailUrl = thumbnails[course.courseId],
                                        isUploading = uploadingCourseId == course.courseId,
                                        onCardClick = { selectedCourse = course },
                                        onEditThumbnail = {
                                            editingCourseId = course.courseId
                                            thumbnailPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                    )
                                }
                            }
                            if (rowCourses.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // 여행 기록 타임라인 (iOS timelineSection)
            if (footprints.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.footprint_timeline),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TteDarkGray,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(12.dp))
                val dateFormatter = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    val shown = footprints.take(20)
                    shown.forEachIndexed { index, record ->
                        TimelineRow(record, isLast = index == shown.lastIndex, dateFormatter)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }

        if (showNicknameEdit) {
            NicknameEditSheet(onDismiss = { showNicknameEdit = false })
        }

        // 코스 상세 풀스크린 (탐색탭과 동일 경험)
        selectedCourse?.let { course ->
            CourseDetailScreen(
                course = course,
                thumbnailUrl = thumbnails[course.courseId],
                onClose = { selectedCourse = null },
                onStartCourse = { selectedCourse = null },
            )
        }

        // 발자취 지도 전체화면 — 여기서만 팬/핀치 활성
        if (showFullMap) {
            BackHandler { showFullMap = false }
            FootprintFullMap(
                summary = summary,
                routes = footprints.map { it.points },
                initialFocus = initialFocus,
                subtitle = stringResource(
                    R.string.footprint_subtitle, summary.sigCodes.size, summary.countryCodes.size
                ),
                onClose = { showFullMap = false },
            )
        }
    }
}

// MARK: - 발자취 지도 전체화면
/// 임베드 지도는 페이지 스크롤과 충돌해 팬/핀치를 껐다.
/// 이 전체화면에서만 팬·핀치·탭이 모두 살아나 세계지도를 자유롭게 탐색한다.
@Composable
private fun FootprintFullMap(
    summary: com.seoktaedev.tteona.core.model.FootprintSummary,
    routes: List<List<FootprintPoint>>,
    initialFocus: FootprintMapFocus,
    subtitle: String,
    onClose: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFBF8F3)),
    ) {
        FootprintMapView(
            summary = summary,
            routes = routes,
            interactive = true,
            panZoom = true,   // 전체화면에서는 팬/핀치 활성 — 이 뷰의 존재 이유
            initialFocus = initialFocus,
            modifier = Modifier.fillMaxSize(),
        )
        // 상단 바 — 제목 + 진행 텍스트 + 닫기
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.footprint_title),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TteDarkGray,
                )
                Text(subtitle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TteOrange)
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = TteDarkGray, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// MARK: - 공용 소품

/** 아바타 (이미지 or 이니셜) — 프로필/검색/타인 프로필 공용 */
@Composable
fun ProfileAvatar(
    nickname: String?,
    imageUrl: String?,
    size: androidx.compose.ui.unit.Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(TteOrange.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        val initial = @Composable {
            Text(
                nickname?.take(1) ?: "?",
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                color = TteOrange,
            )
        }
        if (imageUrl != null) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = { initial() },
                error = { initial() },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            initial()
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatCell(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.weight(1f),
    ) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TteDarkGray)
        Text(label, fontSize = 11.sp, color = TteMediumGray)
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(26.dp)
            .background(Color(0x66C6C6C8)),
    )
}

@Composable
private fun TimelineRow(record: FootprintRecord, isLast: Boolean, dateFormatter: DateFormat) {
    Row(Modifier.height(intrinsicSize = androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        // 타임라인 축
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(10.dp).fillMaxHeight(),
        ) {
            Spacer(Modifier.height(5.dp))
            Box(Modifier.size(10.dp).background(TteOrange, CircleShape))
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(TteOrange.copy(alpha = 0.25f)),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                record.courseName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TteDarkGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(dateFormatter.format(Date(record.date)), fontSize = 12.sp, color = TteMediumGray)
            if (record.regionNames.isNotEmpty()) {
                // FlowRow 사용 — LazyRow는 IntrinsicSize.Min(타임라인 축) 안에서 측정 불가
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    record.regionNames.forEach { name ->
                        Text(
                            name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TteOrange,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(TteOrange.copy(alpha = 0.1f))
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

// MARK: - 편집 가능한 코스 카드 (내 프로필 전용 — 썸네일 직접 교체)
@Composable
private fun EditableCourseCard(
    course: Course,
    thumbnailUrl: String?,
    isUploading: Boolean,
    onCardClick: () -> Unit,
    onEditThumbnail: () -> Unit,
) {
    val context = LocalContext.current
    var placePhotoUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(course.courseId) {
        if (thumbnailUrl == null && placePhotoUrl == null) {
            course.mainPlace?.let { main ->
                placePhotoUrl = PlacesPhotoService.photoUrl(main.placeName, main.latitude, main.longitude)
            }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(14.dp))
            .background(TteFieldBackground)
            // 카드 본문 탭 → 코스 상세 (카메라 Box는 자체 clickable이라 자식 우선)
            .pressableCard(onCardClick),
    ) {
        val url = thumbnailUrl ?: placePhotoUrl
        if (url != null) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = course.courseName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // 하단 그라디언트 + 코스 정보
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.8f))
                    )
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                course.courseName,
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${course.region} · ${stringResource(course.tag.labelRes)}",
                    fontSize = 10.sp, color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.Favorite, contentDescription = null,
                    tint = Color.White.copy(alpha = 0.95f), modifier = Modifier.size(9.dp),
                )
                Spacer(Modifier.size(2.dp))
                Text("${course.likeCount}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
        // 썸네일 교체 버튼 (우상단 카메라)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(enabled = !isUploading, onClick = onEditThumbnail),
            contentAlignment = Alignment.Center,
        ) {
            if (isUploading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Filled.CameraAlt, contentDescription = stringResource(R.string.profile_myCourses),
                    tint = Color.White, modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}
