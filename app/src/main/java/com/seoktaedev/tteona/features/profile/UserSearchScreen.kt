package com.seoktaedev.tteona.features.profile

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.model.AppUser
import com.seoktaedev.tteona.core.services.FootprintService
import com.seoktaedev.tteona.core.services.UserService
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange
import kotlinx.coroutines.delay

// iOS Features/Profile/UserSearchView.swift의 Kotlin 이식본.

/** 유저 검색 — 닉네임으로 다른 유저를 찾아 프로필(발자취·코스)로 이동한다. */
@Composable
fun UserSearchScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onUserClick: (AppUser) -> Unit,
) {
    val authUser by AuthService.currentUser.collectAsState()
    val profileUser by UserService.currentUser.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var didSearch by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // 타이핑 멈춘 뒤 0.35초 디바운스 검색 (iOS scheduleSearch)
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            results = emptyList()
            didSearch = false
            isSearching = false
            return@LaunchedEffect
        }
        delay(350)
        isSearching = true
        val blocked = profileUser?.blockedUserIds?.toSet() ?: emptySet()
        val myUid = authUser?.uid
        results = FootprintService.searchUsers(trimmed)
            .filter { it.uid != myUid && it.uid !in blocked }
        didSearch = true
        isSearching = false
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(modifier.fillMaxSize()) {
        // 타이틀 바
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = TteDarkGray)
            }
            Text(
                stringResource(R.string.profile_searchUsers),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = TteDarkGray,
            )
        }

        // 검색바
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.profile_search_placeholder), fontSize = 15.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TteMediumGray) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Cancel, contentDescription = null, tint = TteMediumGray)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = TteFieldBackground,
                unfocusedContainerColor = TteFieldBackground,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .focusRequester(focusRequester),
        )

        when {
            isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TteOrange, modifier = Modifier.size(28.dp))
            }
            results.isEmpty() && didSearch -> EmptyState(
                icon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0x33000000), modifier = Modifier.size(36.dp)) },
                text = stringResource(R.string.profile_search_empty),
            )
            results.isEmpty() -> EmptyState(
                icon = { Icon(Icons.Filled.Group, contentDescription = null, tint = Color(0x33000000), modifier = Modifier.size(36.dp)) },
                text = stringResource(R.string.profile_search_hint),
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.uid }) { user ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUserClick(user) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        ProfileAvatar(
                            nickname = user.nickname,
                            imageUrl = user.profileImageUrl,
                            size = 46.dp,
                            fontSize = 18.sp,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(
                                    user.nickname,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TteDarkGray,
                                )
                                if (user.isVerified) {
                                    Icon(
                                        Icons.Filled.Verified, contentDescription = null,
                                        tint = TteOrange, modifier = Modifier.size(13.dp),
                                    )
                                }
                            }
                            user.creatorLabel?.takeIf { it.isNotEmpty() }?.let { label ->
                                Text(label, fontSize = 12.sp, color = TteOrange)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(icon: @Composable () -> Unit, text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp)
            .padding(bottom = 80.dp),
    ) {
        Spacer(Modifier.height(120.dp))
        icon()
        Text(text, fontSize = 14.sp, color = TteMediumGray, textAlign = TextAlign.Center)
    }
}
