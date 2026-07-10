package com.seoktaedev.tteona.core.i18n

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** 앱 지원 언어 — 국기와 원어 표기로 설정 화면에 노출된다. */
enum class AppLanguage(val code: String, val flag: String, val nativeName: String) {
    KOREAN("ko", "🇰🇷", "한국어"),
    ENGLISH("en", "🇺🇸", "English"),
    JAPANESE("ja", "🇯🇵", "日本語");

    companion object {
        fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }
    }
}

/**
 * 선택 언어를 SharedPreferences에 저장하고, 액티비티의 baseContext에 로케일을 씌운다.
 * 언어 변경 시 액티비티를 recreate하면 Compose가 새 로케일의 리소스를 다시 읽어 앱 전역에 즉시 반영된다.
 * (appcompat 의존 없이 minSdk 26에서 동작 — attachBaseContext 오버라이드 방식)
 */
object LocaleManager {
    private const val PREFS = "tteona_prefs"
    private const val KEY_LANGUAGE = "app_language"

    // 서비스·모델 등 Context를 넘기기 어려운 곳에서 쓰는 앱 컨텍스트 — Application.onCreate에서 주입
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 앱 컨텍스트 기반 문자열 조회 (컴포저블 밖·서비스 레이어용). */
    fun string(resId: Int, vararg args: Any): String = string(appContext, resId, *args)

    /** 앱 컨텍스트 기반 현재 언어 조회 (ViewModel 등 Context가 없는 곳에서 사용). */
    fun current(): AppLanguage = current(appContext)

    fun current(context: Context): AppLanguage {
        val saved = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
        return AppLanguage.fromCode(saved) ?: systemDefault()
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.code)
            .apply()
    }

    /** baseContext에 현재 선택 언어의 Configuration을 적용한 컨텍스트를 반환한다. */
    fun wrap(context: Context): Context {
        val locale = Locale(current(context).code)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    /**
     * 서비스·알림 등 컴포저블 밖에서 현재 언어로 문자열을 얻을 때 사용.
     * 저장된 선택을 매 호출마다 읽으므로 항상 최신 언어를 반영한다.
     */
    fun string(context: Context, resId: Int, vararg args: Any): String =
        wrap(context).resources.getString(resId, *args)

    /** 저장된 선택이 없으면 시스템 언어를 따른다 (ko/ja 외에는 영어). */
    private fun systemDefault(): AppLanguage {
        val lang = Locale.getDefault().language
        return when {
            lang.startsWith("ko") -> AppLanguage.KOREAN
            lang.startsWith("ja") -> AppLanguage.JAPANESE
            else -> AppLanguage.ENGLISH
        }
    }
}
