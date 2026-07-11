package com.seoktaedev.tteona.features.settings

import android.app.Activity
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.i18n.AppLanguage
import com.seoktaedev.tteona.core.i18n.LocaleManager
import com.seoktaedev.tteona.ui.theme.TteDarkGray
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.seoktaedev.tteona.core.services.TteonaMessagingService
import com.seoktaedev.tteona.ui.theme.TteFieldBackground
import com.seoktaedev.tteona.ui.theme.TteMediumGray
import com.seoktaedev.tteona.ui.theme.TteOrange

/**
 * 설정 > 언어 — 국기와 원어 표기로 언어를 고르면 액티비티를 recreate해 앱 전체에 즉시 반영된다.
 */
@Composable
fun LanguageSettingsScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val current = LocaleManager.current(context)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_close),
                tint = TteDarkGray,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onBack),
            )
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(R.string.settings_language),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TteDarkGray,
            )
        }

        SectionCard {
            AppLanguage.entries.forEachIndexed { index, language ->
                if (index > 0) SettingsDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = language != current) {
                            LocaleManager.setLanguage(context, language)
                            // 서버에 등록된 푸시 언어도 갱신 — 다음 알림부터 새 언어로 온다.
                            Firebase.auth.currentUser?.uid?.let {
                                TteonaMessagingService.registerCurrentToken(it)
                            }
                            // baseContext에 새 로케일을 다시 씌우기 위해 액티비티 재생성
                            (context as? Activity)?.recreate()
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Text(language.flag, fontSize = 26.sp)
                    Text(
                        language.nativeName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = TteDarkGray,
                        modifier = Modifier.weight(1f),
                    )
                    if (language == current) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = TteOrange,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.settings_language_footer),
            fontSize = 12.sp,
            color = TteMediumGray,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}
