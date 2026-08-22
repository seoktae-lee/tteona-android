package com.seoktaedev.tteona.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "지금 가입이 필요합니다"를 앱 어디서든 올릴 수 있는 한 줄짜리 신호.
 *
 * 가입을 요구해야 하는 자리는 여기저기 흩어져 있다 — 게스트 탭 게이트, 브이로그 한도,
 * 결제 직전. 그때마다 상위로 콜백을 넘기면 화면 서너 단을 통과시켜야 하고,
 * 중간 한 곳만 빠뜨려도 **버튼이 아무 일도 안 하는** 막다른 길이 된다.
 * (실제로 페이월은 다섯 곳에서 열린다)
 *
 * 신호는 한 곳(MainTabScreen)만 듣고 로그인 화면을 띄운다.
 */
object AuthPrompt {
    private val _requested = MutableStateFlow(false)
    val requested: StateFlow<Boolean> = _requested

    /** 가입 화면을 띄워 달라고 요청한다 */
    fun request() {
        _requested.value = true
    }

    /** 화면이 떴거나 닫혔다 — 신호를 내린다 */
    fun clear() {
        _requested.value = false
    }
}
