package com.seoktaedev.tteona.core.services

import android.content.Context
import com.seoktaedev.tteona.core.model.Course
import com.seoktaedev.tteona.core.model.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
        // 달력상 '오늘'로 판정하면 밤 11시에 하던 여행이 자정을 넘기는 순간 사라진다.
        // session.date는 저장할 때마다 갱신되는 '마지막 활동 시각'이므로, 그로부터 18시간
        // 이내면 같은 나들이로 보고 이어할 수 있게 한다(자정 교차 커버, 며칠 전 세션은 제외). — iOS와 동일
        return if (System.currentTimeMillis() - session.date < SESSION_WINDOW_MS) session else null
    }

    fun clear() {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        _hasTodaySession.value = false
    }
}

/** 진행 중 세션을 '이어할 수 있는' 시간 창 — 마지막 활동에서 18시간 (자정 교차 커버) */
private const val SESSION_WINDOW_MS = 18L * 3600 * 1000

/**
 * 즉흥 '나의 오늘' 세션 저장 — iOS SavedImpromptuSession.swift(ImpromptuSessionStore)의 이식본.
 * 저장 시 오후 8시 미종료 리마인더를 예약하고 종료 시 취소한다 (ImpromptuReminder).
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
            ImpromptuReminder.scheduleIfNeeded(appContext, places.size)
        }
    }

    fun load(): SavedImpromptuSession? {
        val raw = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        return runCatching { json.decodeFromString(SavedImpromptuSession.serializer(), raw) }.getOrNull()
    }

    fun loadTodaySession(): SavedImpromptuSession? {
        val session = load() ?: return null
        // 마지막 활동에서 18시간 이내면 이어하기 허용 (자정 교차 커버) — iOS와 동일
        return if (System.currentTimeMillis() - session.date < SESSION_WINDOW_MS) session else null
    }

    fun clear() {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        _hasTodaySession.value = false
        ImpromptuReminder.cancel(appContext)
    }
}
