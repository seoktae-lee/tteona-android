package com.seoktaedev.tteona.core.auth

import android.content.Context
import android.util.Log
import com.seoktaedev.tteona.core.services.ImpromptuSessionStore
import java.io.File

/**
 * 게스트가 만든 브이로그 수. **기기 단위**로 센다.
 *
 * 서버도 uid 기준으로 막지만(GUEST_VLOG_LIMIT), 통신이 끊기면 로컬 합성으로 떨어지므로
 * 앱에서도 세야 한다 — 안 그러면 비행기 모드가 무제한 우회로가 된다.
 * 경로(서버/로컬)를 가리지 않고 **완성된 것만** 센다.
 */
object GuestVlogQuota {
    private const val PREFS = "tteona"
    private const val KEY = "guestVlogMadeCount"
    const val LIMIT = 1

    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val made: Int get() = prefs().getInt(KEY, 0)
    val isExhausted: Boolean get() = made >= LIMIT

    fun recordCompletion() {
        prefs().edit().putInt(KEY, made + 1).apply()
    }

    /**
     * 서버가 "이미 다 썼다"고 알려줬을 때 기기 쪽 셈을 거기에 맞춘다.
     *
     * 기기 카운트와 서버 카운트는 서로 다른 곳에 있어서 어긋날 수 있다
     * (기기 백업 복원처럼 SharedPreferences만 초기화되고 uid는 살아남는 경우).
     * 어긋난 채로 두면 브이로그를 만들려 할 때마다 서버까지 갔다가 거절당하는 길을
     * 매번 되풀이한다 — 한 번 알았으면 다음부터는 들어오는 문에서 안내한다.
     */
    fun markExhausted() {
        prefs().edit().putInt(KEY, maxOf(made, LIMIT)).apply()
    }

    /**
     * 회원가입하면 제한이 사라지므로 기록도 지운다 —
     * 나중에 로그아웃해 다시 게스트가 됐을 때 옛 기록으로 막히면 안 된다.
     */
    fun reset() {
        prefs().edit().remove(KEY).apply()
    }
}

/**
 * 게스트 약관 동의 기록. 기기 단위로 남긴다 —
 * 익명 uid는 재설치·카카오 로그인 등으로 바뀔 수 있어서 그걸 키로 쓰면 다시 물어보게 된다.
 */
object GuestTermsConsent {
    private const val PREFS = "tteona"
    private const val KEY = "guestTermsAgreedAt"

    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val isAgreed: Boolean get() = prefs().contains(KEY)

    fun record() {
        prefs().edit().putLong(KEY, System.currentTimeMillis()).apply()
    }
}

/**
 * 촬영 클립이 담긴 세션 폴더의 뒷정리.
 *
 * 세션은 두 조각으로 나뉘어 산다 — **목록**은 SharedPreferences에(신원과 무관하게 한 벌),
 * **영상 파일**은 `Tteona/Sessions/free_{uid}/`에(신원별로). 이 비대칭이 새는 지점이다.
 * uid가 바뀌는 길 중 가입·승계는 `migrateGuestSession`이 파일을 옮겨 주지만,
 * **로그아웃과 회원 탈퇴**는 새 익명 신원을 발급받으면서 옛 폴더를 그대로 두고 간다.
 * 목록은 지워지니 데이터가 깨지진 않지만, 아무도 다시 열지 않을 영상이 계속 쌓인다
 * (한 번에 무료 30초분, PRO는 5분분).
 */
object SessionFileHousekeeping {

    private fun sessionsRoot(context: Context) = File(context.filesDir, "Tteona/Sessions")

    /**
     * 지금 신원의 것이 아닌 게스트 세션 폴더를 지운다.
     *
     * **신원이 확정된 뒤에만** 부를 것 — uid가 비어 있을 때 돌리면 살아 있는 오늘치까지
     * 남의 것으로 판정해 지운다. 그래서 빈 uid는 스스로 거른다.
     *
     * 코스 따라가기 세션 폴더는 이름이 courseId(UUID)라 여기서 건드리지 않는다 —
     * 진행 중인 코스의 클립이 거기 있고, 그 정리는 코스 흐름이 따로 책임진다.
     */
    fun purgeOrphanedGuestSessions(context: Context, currentUid: String) {
        if (currentUid.isEmpty()) return
        val root = sessionsRoot(context)
        val entries = root.listFiles() ?: return

        val keep = "free_$currentUid"
        // 지금 목록이 가리키고 있는 클립은 어느 폴더에 있든 남긴다.
        // 목록만 살아남고 파일이 다른 폴더에 있는 어긋난 상태에서, 지워버리면
        // 복구할 길까지 끊는다 — 새는 것보다 나쁜 결과다.
        // 만료된 세션(18시간 초과)의 목록도 함께 본다 — 보존 쪽으로 기울이는 게 안전하다
        val referenced = (ImpromptuSessionStore.load()?.places ?: emptyList())
            .mapNotNull { it.clipFileName }
            .toSet()

        for (dir in entries) {
            if (!dir.isDirectory) continue
            if (!dir.name.startsWith("free_") || dir.name == keep) continue
            if (referenced.isNotEmpty() &&
                dir.listFiles()?.any { it.name in referenced } == true
            ) {
                Log.d("SessionFiles", "${dir.name} 는 현재 목록이 참조 중 — 보존")
                continue
            }
            dir.deleteRecursively()
            Log.d("SessionFiles", "고아 세션 폴더 정리: ${dir.name}")
        }
    }

    /**
     * 게스트로 찍어둔 클립을 새 계정 폴더로 옮긴다.
     *
     * 카카오(커스텀 토큰)와 '이미 있는 계정으로 로그인'은 link가 불가능해 uid가 바뀐다.
     * 그러면 `Sessions/free_{옛uid}`에 남은 영상을 앱이 영영 못 찾는다 — 장소 목록은
     * SharedPreferences라 살아남는데 파일만 사라져, "3곳인데 영상 없음" 상태가 된다.
     *
     * 통째로 옮기지 않고 **파일 단위로** 옮기는 이유: 대상 계정에 이미 오늘 기록이 있으면
     * 폴더째 덮어써서 그쪽 영상을 지우게 된다. 같은 이름은 손대지 않고 건너뛴다.
     */
    fun migrateGuestSession(context: Context, oldUid: String, newUid: String) {
        if (oldUid.isEmpty() || newUid.isEmpty() || oldUid == newUid) return
        val root = sessionsRoot(context)
        val src = File(root, "free_$oldUid")
        val dst = File(root, "free_$newUid")
        if (!src.exists()) return

        if (!dst.exists()) dst.mkdirs()
        var movedAll = true
        for (file in src.listFiles() ?: emptyArray()) {
            val to = File(dst, file.name)
            if (to.exists()) continue
            if (!file.renameTo(to)) {
                // 같은 파일시스템이 아니거나 잠긴 경우 — 복사로 물러난다
                val copied = runCatching { file.copyTo(to, overwrite = false) }.isSuccess
                if (copied) file.delete() else movedAll = false
            }
        }
        // 전부 옮긴 뒤에만 원본을 지운다 — 옮기다 실패했는데 원본까지 지우면 영상이 사라진다
        if (movedAll) src.deleteRecursively()
        Log.d("Guest", "세션 이관 $oldUid → $newUid (원본정리=$movedAll)")
    }
}
