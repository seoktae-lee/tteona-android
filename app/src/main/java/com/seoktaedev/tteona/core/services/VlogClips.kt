package com.seoktaedev.tteona.core.services

import android.content.Context
import android.media.MediaMetadataRetriever
import com.seoktaedev.tteona.core.model.Place
import java.io.File

/**
 * 세션 촬영 클립 파일 레이아웃 — iOS VlogService.clipURL / CameraService.videoOutputURL 대응.
 * files/Tteona/Sessions/{sessionId}/{clipFileName | order_장소명.mp4} 구조를 iOS와 동일하게 유지해
 * 서버 Vlog 파이프라인 업로드 순서·이름 규칙을 공유한다.
 */
object VlogClips {

    fun sessionDir(context: Context, sessionId: String): File =
        File(context.filesDir, "Tteona/Sessions/$sessionId")

    fun clipFile(context: Context, place: Place, sessionId: String): File =
        File(sessionDir(context, sessionId), clipName(place))

    /** clipFileName이 있으면 사용, 없으면 order+장소명 fallback (iOS와 동일 규칙) */
    fun clipName(place: Place): String =
        place.clipFileName ?: "${place.order}_${
            place.placeName.replace(" ", "_").replace("/", "_").replace(":", "_")
        }.mp4"

    /** 세션 폴더의 mp4 클립 길이 합계(초) — 촬영 예산 계산용 */
    fun totalSeconds(context: Context, sessionId: String): Double =
        sessionDir(context, sessionId).listFiles()
            ?.filter { it.extension.lowercase() == "mp4" }
            ?.sumOf { clipSeconds(it) } ?: 0.0

    fun clipSeconds(file: File): Double {
        if (!file.exists()) return 0.0
        // AutoCloseable(close)은 API 29+ — 하위 호환을 위해 release()로 직접 정리
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000.0
        } catch (e: Exception) {
            0.0
        } finally {
            runCatching { r.release() }
        }
    }

    fun deleteClip(context: Context, place: Place, sessionId: String) {
        clipFile(context, place, sessionId).delete()
    }

    fun deleteAll(context: Context, sessionId: String) {
        sessionDir(context, sessionId).deleteRecursively()
    }
}
