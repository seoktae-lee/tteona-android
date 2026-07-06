package com.seoktaedev.tteona.core.services

import android.content.Context
import com.seoktaedev.tteona.core.model.Course
import com.seoktaedev.tteona.core.model.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Calendar

/**
 * iOS Core/Services/SavedActiveSession.swift(ActiveSessionStore)의 Kotlin 이식본.
 * 진행 중 코스 세션을 로컬에 저장해 당일 이어하기를 지원한다.
 */
@Serializable
data class SavedActiveSession(
    val date: Long,
    val course: Course,
    val orderedPlaces: List<Place>,
    val visitedPlaceOrders: List<Int>,
    val skippedPlaceOrders: List<Int>,
    val currentPlaceIndex: Int,
    val roomIds: List<String>,
)

object ActiveSessionStore {
    private const val PREFS = "tteona_session"
    private const val KEY = "savedActiveSession"
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var appContext: Context

    private val _hasTodaySession = MutableStateFlow(false)
    val hasTodaySession: StateFlow<Boolean> = _hasTodaySession

    fun initialize(context: Context) {
        appContext = context.applicationContext
        _hasTodaySession.value = loadTodaySession() != null
    }

    fun save(session: SavedActiveSession) {
        runCatching {
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, json.encodeToString(SavedActiveSession.serializer(), session)).apply()
            _hasTodaySession.value = true
        }.onFailure { clear() }
    }

    fun load(): SavedActiveSession? {
        val raw = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        return runCatching { json.decodeFromString(SavedActiveSession.serializer(), raw) }.getOrNull()
    }

    fun loadTodaySession(): SavedActiveSession? {
        val session = load() ?: return null
        val saved = Calendar.getInstance().apply { timeInMillis = session.date }
        val now = Calendar.getInstance()
        val isToday = saved.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            saved.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        return if (isToday) session else null
    }

    fun clear() {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        _hasTodaySession.value = false
    }
}

/**
 * 즉흥 '나의 오늘' 세션 저장 — iOS SavedImpromptuSession.swift(ImpromptuSessionStore)의 이식본.
 * TODO: iOS의 오후 8시 미종료 리마인더 알림은 AlarmManager 이식 시 추가.
 */
@Serializable
data class SavedImpromptuSession(
    val date: Long,
    val places: List<Place>,
    val roomIds: List<String>,
)

object ImpromptuSessionStore {
    private const val PREFS = "tteona_session"
    private const val KEY = "savedImpromptuSession"
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var appContext: Context

    private val _hasTodaySession = MutableStateFlow(false)
    val hasTodaySession: StateFlow<Boolean> = _hasTodaySession

    fun initialize(context: Context) {
        appContext = context.applicationContext
        _hasTodaySession.value = loadTodaySession() != null
    }

    fun save(places: List<Place>, roomIds: List<String> = emptyList()) {
        if (places.isEmpty()) return
        val ids = roomIds.ifEmpty { load()?.roomIds ?: emptyList() }
        val session = SavedImpromptuSession(System.currentTimeMillis(), places, ids)
        runCatching {
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, json.encodeToString(SavedImpromptuSession.serializer(), session)).apply()
            _hasTodaySession.value = true
        }
    }

    fun load(): SavedImpromptuSession? {
        val raw = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        return runCatching { json.decodeFromString(SavedImpromptuSession.serializer(), raw) }.getOrNull()
    }

    fun loadTodaySession(): SavedImpromptuSession? {
        val session = load() ?: return null
        val saved = Calendar.getInstance().apply { timeInMillis = session.date }
        val now = Calendar.getInstance()
        val isToday = saved.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            saved.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        return if (isToday) session else null
    }

    fun clear() {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        _hasTodaySession.value = false
    }
}
