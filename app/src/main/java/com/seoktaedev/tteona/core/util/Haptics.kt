package com.seoktaedev.tteona.core.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * 앱 공통 햅틱 — 보상·확정 순간에만 절제해서 사용한다. (iOS Core/Utils/Haptics.swift 대응)
 * (버튼 탭마다 울리면 피로하므로 목록: 도착, 촬영 완료, 좋아요, 코스 시작, Vlog 완성, 결제 성공)
 *
 * Compose에서는 `val view = LocalView.current` 후 `Haptics.light(view)` 형태로 호출한다.
 */
object Haptics {
    /** 가벼운 톡 — 좋아요, 선택 확정 같은 작은 상호작용 */
    fun light(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** 중간 임팩트 — 코스 시작 등 화면이 크게 전환되는 확정 액션 */
    fun medium(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** 성공 노티 — 도착, 촬영 완료, Vlog 완성, 결제 성공 */
    fun success(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /** 경고 노티 — 되돌릴 수 없는 삭제/차단 직전 */
    /**
     * 타이핑 틱 — 글자가 하나 늘고 줄 때의 아주 작은 반응.
     * CLOCK_TICK은 안드로이드에서 가장 가벼운 촉각 상수라 연타 상황에 맞는다.
     */
    fun typing(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /**
     * 한도에 닿아 더 들어가지 않을 때 — 벽에 닿는 단단한 감촉.
     * **입력이 막혔는데 아무 반응이 없으면 고장으로 읽힌다.**
     * REJECT는 API 30부터라 그 아래에서는 LONG_PRESS로 물러난다.
     */
    fun limitReached(view: View) {
        val constant = if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
                       else HapticFeedbackConstants.LONG_PRESS
        view.performHapticFeedback(constant)
    }

    fun warning(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
}
