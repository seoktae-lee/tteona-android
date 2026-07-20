package com.seoktaedev.tteona.features.tutorial

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 첫 브이로그 튜토리얼 — iOS Features/Tutorial/VlogTutorial.swift의 이식본.
 * 회원가입·온보딩·내비 가이드가 끝난 유저가 앱의 하이라이트인 브이로그 제작을
 * '나의 오늘' → 촬영(5초) → 오늘 종료 → 브이로그 생성까지 실제 버튼을 직접 누르며
 * 한 번 완주하도록 안내한다.
 *
 * 원칙 (iOS와 동일):
 * - 오버레이는 시각 안내(말풍선·글로우)만 담당하고 터치를 가로채지 않는다.
 *   진행은 실제 사용자 행동(세션 시작, 클립 저장, 시트 열림 등)으로 감지한다.
 * - 어느 단계에서든 말풍선의 X로 영구 종료할 수 있다.
 * - 중도 이탈(세션 나가기 등) 시 해당 지점에 맞는 단계로 되돌린다.
 * - 노출 플래그는 시작 즉시 저장 — 무시한 유저에게 매 실행마다 다시 뜨지 않게
 *   "누구나 정확히 한 번"을 보장한다. (같은 세션 안에선 완주/X까지 계속 안내)
 */
object VlogTutorial {

    enum class Step {
        TAP_MY_TODAY,      // 메인: '나의 오늘' 누르기
        CAPTURE_HERE,      // 세션: '여기서 촬영' (5초 클립)
        END_TODAY,         // 칩 1개 확인 → '오늘 종료'
        CHOOSE_VLOG_ONLY,  // 종료 시트: '브이로그만 생성하기'
        CHOOSE_FORMAT,     // 포맷 선택
        CHOOSE_BGM,        // BGM 선택
        CELEBRATE,         // 완성 축하 + 무료 6곳 안내
    }

    private val _step = MutableStateFlow<Step?>(null)
    val step: StateFlow<Step?> = _step

    /** 내비 가이드 종료 후 호출 — 계정별 1회만 시작한다. */
    fun beginIfNeeded(context: Context, uid: String) {
        val prefs = context.applicationContext.getSharedPreferences("tteona", Context.MODE_PRIVATE)
        val doneKey = "vlogTutorialDone_$uid"
        if (prefs.getBoolean(doneKey, false) || _step.value != null) return
        prefs.edit().putBoolean(doneKey, true).apply()
        _step.value = Step.TAP_MY_TODAY
    }

    /** 앞 단계로만 진행 — 뒤로 가는 신호(중복 onAppear 등)는 무시한다. */
    fun advance(next: Step) {
        val current = _step.value ?: return
        if (next.ordinal > current.ordinal) _step.value = next
    }

    /** 흐름 중간에서 빠져나갈 때 해당 지점의 단계로 되돌린다. */
    fun regress(target: Step) {
        val current = _step.value ?: return
        if (current.ordinal > target.ordinal) _step.value = target
    }

    /** 세션 화면을 브이로그 완성 전에 나감 → 처음부터 다시 안내 */
    fun handleSessionExit() = regress(Step.TAP_MY_TODAY)

    /** 브이로그 생성 화면을 닫음 → 칩은 남아 있으므로 '오늘 종료' 단계로 */
    fun handleVlogExit() = regress(Step.END_TODAY)

    /** 완주 또는 '그만 보기' — 다시 표시하지 않는다. */
    fun finish() {
        _step.value = null
    }
}
