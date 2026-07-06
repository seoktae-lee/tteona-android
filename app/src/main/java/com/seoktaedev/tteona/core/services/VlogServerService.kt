package com.seoktaedev.tteona.core.services

import android.content.Context
import com.seoktaedev.tteona.core.model.Course
import com.seoktaedev.tteona.core.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 서버 사이드 Vlog 생성 클라이언트 — iOS Core/Services/VlogServerService.swift의 이식본.
 * 흐름: 잡 생성 → 클립 업로드 → start → 진행률 폴링 → 완성본 다운로드.
 * (iOS는 실패 시 로컬 AVFoundation 합성으로 폴백하지만 안드로이드는 서버 전용 — 실패 시 에러 표시)
 */
object VlogServerService {
    private const val BASE_URL = "https://tteona.kr/api/vlog"
    private val json = Json { ignoreUnknownKeys = true }

    class ServerVlogException(message: String) : Exception(message)

    @Serializable
    data class OutputItem(val format: String = "", val url: String = "")

    @Serializable
    private data class JobStatus(
        val status: String = "",
        val progress: Int = 0,
        val outputUrl: String? = null,
        val outputs: List<OutputItem>? = null,
        val errorMsg: String? = null,
    )

    data class GeneratedVlog(
        val main: File,
        val extras: List<Pair<String, File>>, // format to file
    )

    @Serializable
    data class BgmTrack(
        val id: String = "",   // "mood/파일명" — 잡 생성 시 bgm 필드로 전달
        val name: String = "",
        val mood: String = "",
        val url: String = "",  // 미리듣기 스트리밍 URL
    )

    @Serializable
    private data class BgmWrapper(val tracks: List<BgmTrack> = emptyList())

    /** BGM 선택 화면용 트랙 목록 */
    suspend fun fetchBgmTracks(): List<BgmTrack> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$BASE_URL/bgm").build()
        ApiClient.httpClient.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw ServerVlogException("bgm list failed")
            json.decodeFromString<BgmWrapper>(res.body?.string() ?: "{}").tracks
        }
    }

    /**
     * 서버에서 Vlog 합성 후 로컬 파일 반환.
     * formats: 추가 생성할 포맷 — 촬영 방향 기본본은 항상 생성.
     * bgm: "auto" | "none" | "mood/파일명"
     * onProgress: (0.0~1.0, 단계 설명 텍스트)
     */
    suspend fun generate(
        context: Context,
        course: Course,
        sessionId: String,
        userId: String,
        formats: List<String>,
        bgm: String = "auto",
        watermark: Boolean = true,
        priority: Boolean = false,
        onProgress: suspend (Double, String) -> Unit,
    ): GeneratedVlog = withContext(Dispatchers.IO) {
        // 로컬에 실제 존재하는 클립만 수집
        val clips = course.places.mapNotNull { place ->
            val file = VlogClips.clipFile(context, place, sessionId)
            if (file.exists()) place to file else null
        }
        if (clips.isEmpty()) throw ServerVlogException("업로드할 클립이 없어요.")

        onProgress(0.02, "서버에 편집을 준비하고 있어요")

        // 1) 잡 생성 (태그 → BGM 무드, shotAt → 클립별 촬영시각 자막)
        val df = SimpleDateFormat("yyyy.MM.dd  HH:mm", Locale.KOREA)
        val placesPayload = JsonArray(
            clips.map { (place, file) ->
                JsonObject(
                    mapOf(
                        "order" to JsonPrimitive(place.order),
                        "placeName" to JsonPrimitive(place.placeName),
                        "shotAt" to JsonPrimitive(df.format(Date(file.lastModified()))),
                    )
                )
            }
        )
        val jobId = createJob(userId, course, formats, bgm, watermark, priority, placesPayload)

        // 2) 클립 업로드 (0.05 → 0.45)
        clips.forEachIndexed { i, (place, file) ->
            onProgress(0.05 + 0.40 * i / clips.size, "클립 업로드 중 (${i + 1}/${clips.size})")
            uploadClip(jobId, place.order, file)
        }
        onProgress(0.45, "서버에서 편집 중이에요")

        // 3) 합성 시작
        startJob(jobId)

        // 4) 진행률 폴링 (0.45 → 0.88) — 최대 30분 (멀티포맷 잡은 10분을 넘길 수 있음).
        // 완료를 감지하면 즉시 루프를 빠져나가야 한다 (repeat의 return@repeat는 continue이므로 while 사용).
        var outputUrl: String? = null
        var outputs: List<OutputItem> = emptyList()
        var polls = 0
        while (outputUrl == null && polls < 900) {
            polls++
            delay(2000)
            val st = status(jobId)
            when (st.status) {
                "completed" -> {
                    outputUrl = st.outputUrl
                    outputs = st.outputs ?: emptyList()
                }
                "failed" -> throw ServerVlogException("서버 편집 실패: ${st.errorMsg ?: "unknown"}")
                else -> onProgress(0.45 + 0.43 * st.progress / 100.0, "서버에서 편집 중이에요")
            }
        }
        val mainUrl = outputUrl ?: throw ServerVlogException("서버 편집 시간 초과")

        // 5) 완성본 다운로드 (0.88 → 0.98) — 기본본 + 추가 포맷들
        onProgress(0.90, "완성본을 받아오고 있어요")
        val mainLocal = download(context, mainUrl)
        val extras = mutableListOf<Pair<String, File>>()
        val extraItems = outputs.filter { it.url != mainUrl }
        extraItems.forEachIndexed { i, item ->
            onProgress(0.92 + 0.05 * i / maxOf(extraItems.size, 1), "선택한 포맷 버전을 받아오고 있어요")
            runCatching { download(context, item.url) }.getOrNull()?.let { extras.add(item.format to it) }
        }
        onProgress(0.98, "거의 다 됐어요")
        GeneratedVlog(mainLocal, extras)
    }

    // ── API 단계별 호출 ─────────────────────────────────────────────────

    private fun createJob(
        userId: String,
        course: Course,
        formats: List<String>,
        bgm: String,
        watermark: Boolean,
        priority: Boolean,
        placesPayload: JsonArray,
    ): Int {
        val body = JsonObject(
            mapOf(
                "userId" to JsonPrimitive(userId),
                "courseId" to JsonPrimitive(course.courseId),
                "courseName" to JsonPrimitive(course.courseName),
                "tag" to JsonPrimitive(course.tag.label),
                "formats" to JsonArray(formats.map { JsonPrimitive(it) }),
                "bgm" to JsonPrimitive(bgm),
                "watermark" to JsonPrimitive(watermark),
                "priority" to JsonPrimitive(priority),
                "places" to placesPayload,
            )
        ).toString().toRequestBody("application/json".toMediaType())

        val req = Request.Builder().url("$BASE_URL/jobs").post(body).build()
        ApiClient.httpClient.newCall(req).execute().use { res ->
            val text = res.body?.string() ?: ""
            if (!res.isSuccessful) throw ServerVlogException("잡 생성 실패: ${text.take(120)}")
            return json.parseToJsonElement(text).jsonObject["jobId"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: throw ServerVlogException("잡 생성 응답 오류")
        }
    }

    private fun uploadClip(jobId: Int, order: Int, file: File) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("clip", "$order.mp4", file.asRequestBody("video/mp4".toMediaType()))
            .build()
        val req = Request.Builder().url("$BASE_URL/jobs/$jobId/clips?order=$order").post(body).build()
        // 영상 업로드는 오래 걸릴 수 있어 타임아웃 연장 (iOS 300초)
        val client = ApiClient.httpClient.newBuilder()
            .writeTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) {
                val msg = res.body?.string() ?: "upload failed"
                throw ServerVlogException("클립 $order 업로드 실패: ${msg.take(120)}")
            }
        }
    }

    private fun startJob(jobId: Int) {
        val req = Request.Builder()
            .url("$BASE_URL/jobs/$jobId/start")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        ApiClient.httpClient.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw ServerVlogException("합성 시작 실패")
        }
    }

    private fun status(jobId: Int): JobStatus {
        val req = Request.Builder().url("$BASE_URL/jobs/$jobId").build()
        ApiClient.httpClient.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw ServerVlogException("상태 조회 실패")
            return json.decodeFromString(res.body?.string() ?: "{}")
        }
    }

    private fun download(context: Context, urlString: String): File {
        val req = Request.Builder().url(urlString).build()
        ApiClient.httpClient.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw ServerVlogException("완성본 다운로드 실패")
            val dest = File(context.cacheDir, "vlog_${UUID.randomUUID()}.mp4")
            res.body?.byteStream()?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: throw ServerVlogException("완성본 다운로드 실패")
            return dest
        }
    }
}
