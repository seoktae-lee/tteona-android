package com.seoktaedev.tteona.core.services

import android.content.Context
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 나루 무드 필터 — iOS NaruFilter(CIFilter 체인)의 안드로이드 대응.
 * iOS는 촬영 중 Metal로 라이브 적용하지만, 안드로이드는 CameraX 라이브 필터 리스크를 피해
 * 촬영 직후 Media3 Transformer로 클립 파일에 색보정을 '굽는다'. 결과물(필터 적용된 클립)은
 * iOS와 동일하므로 서버 Vlog 합성 입력이 일치한다.
 */
enum class NaruFilter {
    NONE, COZY, FILM, FRESH;

    /** 이 무드의 Media3 비디오 이펙트 체인 (iOS NaruFilter.apply와 동일 의도) */
    @OptIn(UnstableApi::class)
    fun videoEffects(): List<Effect> = when (this) {
        NONE -> emptyList()
        // 포근 — 따뜻한 톤 + 약한 채도/밝기 업
        COZY -> listOf(
            RgbAdjustment.Builder().setRedScale(1.08f).setBlueScale(0.92f).build(),
            HslAdjustment.Builder().adjustSaturation(10f).adjustLightness(2f).build(),
        )
        // 필름 — 대비 낮추고 채도 다운, 살짝 페이드
        FILM -> listOf(
            Contrast(-0.08f),
            HslAdjustment.Builder().adjustSaturation(-25f).adjustLightness(3f).build(),
        )
        // 청량 — 차가운 톤 + 채도/대비 업
        FRESH -> listOf(
            RgbAdjustment.Builder().setRedScale(0.94f).setBlueScale(1.08f).build(),
            HslAdjustment.Builder().adjustSaturation(15f).build(),
            Contrast(0.06f),
        )
    }

    companion object {
        private const val PREFS = "tteona_camera"
        private const val KEY = "naru_filter"

        fun saved(context: Context): NaruFilter {
            val i = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 0)
            return entries.getOrElse(i) { NONE }
        }

        fun save(context: Context, filter: NaruFilter) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY, filter.ordinal).apply()
        }
    }
}

object NaruVideoFilter {

    /**
     * 클립 파일에 필터 색보정을 in-place로 굽는다. 성공 시 원본을 필터본으로 교체하고 true,
     * 실패하면 원본을 그대로 두고 false를 반환한다(사용자 영상 손실 없음).
     * Transformer는 Looper가 필요하므로 메인 스레드에서 실행한다.
     */
    @OptIn(UnstableApi::class)
    suspend fun apply(context: Context, file: File, filter: NaruFilter): Boolean {
        if (filter == NaruFilter.NONE || !file.exists()) return false
        val effects = filter.videoEffects()
        if (effects.isEmpty()) return false

        val outFile = File(file.parentFile, "flt_${file.name}")
        outFile.delete()

        val ok = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(file)))
                    .setEffects(Effects(emptyList(), effects))
                    .build()
                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resumeWith(Result.success(true))
                        }
                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            if (cont.isActive) cont.resumeWith(Result.success(false))
                        }
                    })
                    .build()
                cont.invokeOnCancellation { runCatching { transformer.cancel() } }
                runCatching { transformer.start(item, outFile.absolutePath) }
                    .onFailure { if (cont.isActive) cont.resumeWith(Result.success(false)) }
            }
        }

        return if (ok && outFile.exists() && outFile.length() > 0) {
            file.delete()
            outFile.renameTo(file)
        } else {
            outFile.delete()
            false
        }
    }
}
