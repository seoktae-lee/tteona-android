package com.seoktaedev.tteona.core.services

import android.content.Context
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.i18n.LocaleManager
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
 *
 * 안드로이드는 로컬 폴백이 없어 일시적 오류가 곧 "영상 없음"이 된다. 서버는 그 사이에도
 * 렌더링을 계속하므로, 헛되이 포기하지 않도록 다음을 지킨다:
 *   1) 잡 진행 상태를 기기에 저장해 재시도 시 처음부터 다시 하지 않고 이어받는다
 *   2) 업로드는 지수 백오프로 재시도한다
 *   3) 상태 폴링은 연속 실패를 일정 횟수까지 견딘다 (셀 전환·짧은 끊김 흡수)
 */
object VlogServerService {
    private const val BASE_URL = "https://tteona.kr/api/vlog"
    private val json = Json { ignoreUnknownKeys = true }

    /** 서버가 "이 영상은 못 만든다"고 확정했는가 — DEFINITIVE면 이어받아도 결과가 같다. */
    enum class ErrorKind { DEFINITIVE, TRANSIENT, JOB_GONE }

    class ServerVlogException(
        message: String,
        val kind: ErrorKind = ErrorKind.TRANSIENT,
    ) : Exception(message) {
        val isDefinitive: Boolean get() = kind == ErrorKind.DEFINITIVE
    }

    // ── 진행 중인 잡 기억하기 ────────────────────────────────────────────
    // 네트워크가 끊기거나 앱이 죽어도 서버는 렌더링을 계속한다.
    // 세션별로 잡 진행 상태를 남겨 두면 재시도 시 업로드를 건너뛰고 결과만 받아올 수 있다.

    @Serializable
    private data class PendingJob(
        val jobId: Int,
        val courseId: String,
        val uploadedOrders: List<Int> = emptyList(),
        val started: Boolean = false,
        val createdAt: Long = 0L,
    )

    private const val PREFS = "tteona_prefs"
    private const val STORE_KEY = "vlog.pendingJobs"
    private const val PENDING_TTL_MS = 24L * 3600 * 1000

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadStore(context: Context): Map<String, PendingJob> {
        val raw = prefs(context).getString(STORE_KEY, null) ?: return emptyMap()
        val decoded = runCatching { json.decodeFromString<Map<String, PendingJob>>(raw) }.getOrNull()
            ?: return emptyMap()
        // 하루 지난 잡은 서버에서도 정리 대상이라 들고 있을 이유가 없다
        val now = System.currentTimeMillis()
        return decoded.filterValues { now - it.createdAt < PENDING_TTL_MS }
    }

    private fun saveStore(context: Context, store: Map<String, PendingJob>) {
        prefs(context).edit().putString(STORE_KEY, json.encodeToString(store)).apply()
    }

    private fun loadPending(context: Context, sessionId: String): PendingJob? =
        loadStore(context)[sessionId]

    private fun savePending(context: Context, sessionId: String, job: PendingJob) {
        saveStore(context, loadStore(context) + (sessionId to job))
    }

    private fun clearPending(context: Context, sessionId: String) {
        saveStore(context, loadStore(context) - sessionId)
    }

    /** 이 세션에 대해 서버가 아직 붙잡고 있는 잡이 있는가 — 오류 화면의 "이어받기" 안내에 사용 */
    fun hasPendingJob(context: Context, sessionId: String): Boolean =
        loadPending(context, sessionId) != null

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
        if (clips.isEmpty()) {
            throw ServerVlogException(LocaleManager.string(R.string.vlogserver_error_noClips), ErrorKind.DEFINITIVE)
        }

        onProgress(0.02, LocaleManager.string(R.string.vlogserver_prep))

        // 0) 이전 시도가 남긴 잡이 있으면 이어받는다
        var pending = loadPending(context, sessionId)
        if (pending != null && pending.courseId != course.courseId) {
            clearPending(context, sessionId)   // 다른 코스의 잔재
            pending = null
        }
        if (pending != null) {
            onProgress(0.05, LocaleManager.string(R.string.vlogserver_resuming))
            val st = runCatching { status(pending.jobId) }.getOrNull()
            when {
                st == null -> {
                    clearPending(context, sessionId)   // 404 등 — 처음부터 다시
                    pending = null
                }
                st.status == "completed" -> {
                    // 폰이 놓친 사이 서버가 이미 완성해 뒀다 — 다운로드만 하면 끝
                    return@withContext downloadOutputs(
                        context, sessionId, st.outputUrl, st.outputs ?: emptyList(), onProgress,
                    )
                }
                st.status == "failed" -> {
                    clearPending(context, sessionId)
                    pending = null
                }
                else -> pending = pending.copy(started = st.status != "uploading")
            }
        }

        // 1) 잡 생성 (태그 → BGM 무드, shotAt → 클립별 촬영시각 자막)
        val jobId: Int
        val uploadedOrders: MutableSet<Int>
        var alreadyStarted: Boolean

        if (pending != null) {
            jobId = pending.jobId
            uploadedOrders = pending.uploadedOrders.toMutableSet()
            alreadyStarted = pending.started
        } else {
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
            jobId = createJob(userId, course, formats, bgm, watermark, priority, placesPayload)
            uploadedOrders = mutableSetOf()
            alreadyStarted = false
            savePending(context, sessionId, PendingJob(jobId, course.courseId, emptyList(), false, System.currentTimeMillis()))
        }

        // 2) 클립 업로드 (0.05 → 0.45) — 이미 올린 클립은 건너뛴다
        if (!alreadyStarted) {
            clips.forEachIndexed { i, (place, file) ->
                onProgress(0.05 + 0.40 * i / clips.size, LocaleManager.string(R.string.vlogserver_uploading, i + 1, clips.size))
                if (place.order !in uploadedOrders) {
                    uploadClip(jobId, place.order, file)
                    uploadedOrders.add(place.order)
                    savePending(context, sessionId, PendingJob(jobId, course.courseId, uploadedOrders.toList(), false, System.currentTimeMillis()))
                }
            }
        }
        onProgress(0.45, LocaleManager.string(R.string.vlogserver_editing))

        // 3) 합성 시작
        if (!alreadyStarted) {
            startJob(jobId)
            alreadyStarted = true
            savePending(context, sessionId, PendingJob(jobId, course.courseId, uploadedOrders.toList(), true, System.currentTimeMillis()))
        }

        // 4) 진행률 폴링 (0.45 → 0.88) — 최대 30분 (멀티포맷 잡은 10분을 넘길 수 있음).
        //    상태 조회 한 번 삐끗했다고 포기하지 않는다: 연속 실패 15회(≈30초)까지 견딘다.
        var outputUrl: String? = null
        var outputs: List<OutputItem> = emptyList()
        var consecutiveFailures = 0
        val deadline = System.currentTimeMillis() + 30 * 60 * 1000

        while (outputUrl == null && System.currentTimeMillis() < deadline) {
            delay(2000)
            val st = try {
                status(jobId).also { consecutiveFailures = 0 }
            } catch (e: ServerVlogException) {
                if (e.kind == ErrorKind.JOB_GONE) {
                    clearPending(context, sessionId)
                    throw e
                }
                consecutiveFailures++
                if (consecutiveFailures > 15) throw ServerVlogException("status polling", ErrorKind.TRANSIENT)
                continue
            }
            when (st.status) {
                "completed" -> {
                    outputUrl = st.outputUrl
                    outputs = st.outputs ?: emptyList()
                }
                "failed" -> {
                    clearPending(context, sessionId)
                    throw ServerVlogException(
                        LocaleManager.string(R.string.vlogserver_error_processingFailed, st.errorMsg ?: "unknown"),
                        ErrorKind.DEFINITIVE,
                    )
                }
                else -> onProgress(0.45 + 0.43 * st.progress / 100.0, LocaleManager.string(R.string.vlogserver_editing))
            }
        }
        // 시간이 다 됐어도 서버 잡은 살아 있다 — pending을 지우지 않아 다음 시도에 이어받는다
        if (outputUrl == null) {
            throw ServerVlogException(LocaleManager.string(R.string.vlogserver_error_timeout), ErrorKind.TRANSIENT)
        }

        // 5) 완성본 다운로드 (0.88 → 0.98) — 기본본 + 추가 포맷들
        downloadOutputs(context, sessionId, outputUrl, outputs, onProgress)
    }

    /** 완성된 잡의 결과물을 내려받는다 — 성공 시 이 세션의 pending 기록을 지운다. */
    private suspend fun downloadOutputs(
        context: Context,
        sessionId: String,
        outputUrl: String?,
        outputs: List<OutputItem>,
        onProgress: suspend (Double, String) -> Unit,
    ): GeneratedVlog {
        val mainUrl = outputUrl
            ?: throw ServerVlogException(LocaleManager.string(R.string.vlogserver_error_downloadFailed), ErrorKind.TRANSIENT)

        onProgress(0.90, LocaleManager.string(R.string.vlogserver_receiving))
        val mainLocal = download(context, mainUrl)
        val extras = mutableListOf<Pair<String, File>>()
        val extraItems = outputs.filter { it.url != mainUrl }
        extraItems.forEachIndexed { i, item ->
            onProgress(0.92 + 0.05 * i / maxOf(extraItems.size, 1), LocaleManager.string(R.string.vlogserver_receivingFormats))
            runCatching { download(context, item.url) }.getOrNull()?.let { extras.add(item.format to it) }
        }
        onProgress(0.98, LocaleManager.string(R.string.vlogserver_almostDone))
        clearPending(context, sessionId)
        return GeneratedVlog(mainLocal, extras)
    }

    // ── 재시도 헬퍼 ─────────────────────────────────────────────────────

    /** 지수 백오프 재시도. 서버가 확정 실패를 알린 경우(isDefinitive)엔 즉시 포기한다. */
    private suspend fun <T> retrying(attempts: Int, operation: () -> T): T {
        var lastError: Exception = ServerVlogException("unknown", ErrorKind.TRANSIENT)
        for (attempt in 0 until attempts) {
            if (attempt > 0) delay(2000L shl (attempt - 1))   // 2s, 4s, 8s...
            try {
                return operation()
            } catch (e: ServerVlogException) {
                if (e.isDefinitive) throw e
                lastError = e
            } catch (e: java.io.IOException) {
                lastError = e
            }
        }
        throw lastError
    }

    // ── API 단계별 호출 ─────────────────────────────────────────────────

    private suspend fun createJob(
        userId: String,
        course: Course,
        formats: List<String>,
        bgm: String,
        watermark: Boolean,
        priority: Boolean,
        placesPayload: JsonArray,
    ): Int = retrying(3) {
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
            if (!res.isSuccessful) throw ServerVlogException(LocaleManager.string(R.string.vlogserver_error_jobCreateFailed, text.take(120)))
            json.parseToJsonElement(text).jsonObject["jobId"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: throw ServerVlogException(LocaleManager.string(R.string.vlogserver_error_jobCreateResponse))
        }
    }

    /** 클립 1개 업로드 실패로 전체가 무너지지 않도록 지수 백오프 3회 재시도 */
    private suspend fun uploadClip(jobId: Int, order: Int, file: File) = retrying(3) {
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
                throw ServerVlogException(LocaleManager.string(R.string.vlogserver_error_clipUploadFailed, order, msg.take(120)))
            }
        }
    }

    private suspend fun startJob(jobId: Int) = retrying(3) {
        val req = Request.Builder()
            .url("$BASE_URL/jobs/$jobId/start")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        val ok = ApiClient.httpClient.newCall(req).execute().use { res -> res.isSuccessful }
        if (!ok) {
            // 앞선 시도의 요청이 실제로는 서버에 닿았을 수 있다 (응답만 유실).
            // 그 경우 잡은 이미 uploading을 벗어나 404가 오므로, 상태로 확인해 통과시킨다.
            val st = runCatching { status(jobId) }.getOrNull()
            if (st == null || st.status !in listOf("pending", "processing", "completed")) {
                throw ServerVlogException(LocaleManager.string(R.string.vlogserver_error_compositeStartFailed))
            }
        }
    }

    private fun status(jobId: Int): JobStatus {
        val req = Request.Builder().url("$BASE_URL/jobs/$jobId").build()
        ApiClient.httpClient.newCall(req).execute().use { res ->
            // 404는 잡이 만료·삭제된 것 — 이어받기가 불가능하니 폴링을 즉시 끝낸다.
            if (res.code == 404) {
                throw ServerVlogException(LocaleManager.string(R.string.vlogserver_error_statusFailed), ErrorKind.JOB_GONE)
            }
            if (!res.isSuccessful) throw ServerVlogException(LocaleManager.string(R.string.vlogserver_error_statusFailed))
            return json.decodeFromString(res.body?.string() ?: "{}")
        }
    }

    private suspend fun download(context: Context, urlString: String): File = retrying(3) {
        val req = Request.Builder().url(urlString).build()
        ApiClient.httpClient.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw ServerVlogException(LocaleManager.string(R.string.vlogserver_error_downloadFailed))
            val dest = File(context.cacheDir, "vlog_${UUID.randomUUID()}.mp4")
            res.body?.byteStream()?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: throw ServerVlogException(LocaleManager.string(R.string.vlogserver_error_downloadFailed))
            dest
        }
    }
}
