package com.seoktaedev.tteona.core.services

import android.util.Log
import com.seoktaedev.tteona.core.i18n.AppLanguage
import com.seoktaedev.tteona.core.network.ApiClient
import com.seoktaedev.tteona.core.network.TranslateRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 코스 제목 같은 UGC를 앱 언어로 번역한다 (iOS Core/Services/TranslationService.swift의 이식본).
 * 서버(`/api/translate`)가 Google Cloud Translation을 호출하고 결과를 영구 캐시하므로,
 * 클라이언트는 화면당 한 번만 배치로 물어보면 된다.
 *
 * 번역이 불가능한 상황(서버에 번역 키 미설정, 네트워크 오류)에서는
 * 언제나 원문을 그대로 돌려준다 — 번역은 부가 기능이지 표시 조건이 아니다.
 */
object TranslationService {
    private const val CHUNK_SIZE = 50 // 서버가 요청당 50개까지만 받는다

    // 원문+대상언어 → 번역문. 앱 실행 동안만 유지 (영구 캐시는 서버가 담당)
    private val cache = mutableMapOf<String, String>()
    private val mutex = Mutex()

    private fun cacheKey(text: String, target: AppLanguage) = "${target.code}|$text"

    /**
     * 원문이 이미 대상 언어로 쓰였는지 문자 종류로 판별한다.
     * 한국어 유저가 한국어 코스를 보는 대다수 경우에 왕복·과금을 아끼되,
     * 일본어·영어로 쓰인 코스는 한국어 유저에게도 번역되도록 남겨둔다.
     */
    private fun isAlreadyWritten(text: String, target: AppLanguage): Boolean {
        var hasHangul = false; var hasKana = false; var hasHan = false; var hasLatin = false
        for (ch in text) {
            val c = ch.code
            when {
                c in 0xAC00..0xD7A3 || c in 0x1100..0x11FF || c in 0x3130..0x318F -> hasHangul = true
                c in 0x3040..0x309F || c in 0x30A0..0x30FF -> hasKana = true
                c in 0x3400..0x4DBF || c in 0x4E00..0x9FFF -> hasHan = true
                c in 0x41..0x5A || c in 0x61..0x7A -> hasLatin = true
            }
        }
        return when (target) {
            AppLanguage.KOREAN -> hasHangul
            // 가나가 없어도 한자만 있는 제목("東京旅行")은 일본어로 본다 — 한글이 섞이면 한국어.
            AppLanguage.JAPANESE -> hasKana || (hasHan && !hasHangul)
            AppLanguage.ENGLISH -> hasLatin && !hasHangul && !hasKana && !hasHan
        }
    }

    /** 여러 원문을 한 번에 번역해 원문→번역문 맵을 돌려준다. 맵에 없는 원문은 원문 그대로 표시하면 된다. */
    suspend fun translate(texts: List<String>, target: AppLanguage): Map<String, String> {
        val wanted = texts.filter { it.isNotBlank() && !isAlreadyWritten(it, target) }.toSet()
        if (wanted.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, String>()
        val missing = mutableListOf<String>()
        mutex.withLock {
            for (text in wanted) {
                cache[cacheKey(text, target)]?.let { result[text] = it } ?: missing.add(text)
            }
        }
        if (missing.isEmpty()) return result

        for (chunk in missing.chunked(CHUNK_SIZE)) {
            val fetched = fetchTranslations(chunk, target) ?: continue
            mutex.withLock {
                for ((source, translated) in fetched) {
                    cache[cacheKey(source, target)] = translated
                    result[source] = translated
                }
            }
        }
        return result
    }

    /** 단건 편의 메서드 — 실패 시 원문을 그대로 돌려준다. */
    suspend fun translate(text: String, target: AppLanguage): String =
        translate(listOf(text), target)[text] ?: text

    private suspend fun fetchTranslations(texts: List<String>, target: AppLanguage): Map<String, String>? =
        runCatching {
            val response = ApiClient.api.translate(TranslateRequest(texts, target.code))
            // 서버에 번역 키가 없으면 원문이 그대로 온다 — 캐시에 담아 원문을 고착시키지 않는다.
            if (!response.translated || response.translations.size != texts.size) return null
            texts.zip(response.translations).toMap()
        }.onFailure { Log.w("TranslationService", "translate 실패", it) }.getOrNull()
}
