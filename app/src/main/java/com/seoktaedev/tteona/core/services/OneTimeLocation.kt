package com.seoktaedev.tteona.core.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 1회성 위치 조회. iOS Core/Services/LocationService.requestOneTimeLocation의 이식본.
 *
 * "지금 어디서 찍었나"를 묻는 자리에서만 쓴다. 연속 추적(LocationService)과 달리
 * 한 번 받고 끝나며, **응답이 오지 않는 경우를 반드시 스스로 끊는다** —
 * 측위가 안 되는 실내에서 콜백을 기다리면 스피너가 영영 멎는다.
 */
object OneTimeLocation {

    sealed interface Result {
        data class Success(val location: Location) : Result
        /** 권한이 거부·제한됨. 재시도해도 소용없으니 설정으로 안내해야 한다. */
        data object Denied : Result
        /** 응답이 오지 않아 스스로 끊었다 */
        data object Failed : Result
    }

    /** 이보다 최근에 받은 위치가 있으면 새로 측위하지 않고 그대로 쓴다 */
    private const val FRESH_MS = 60_000L

    /**
     * 시한이 다 됐을 때 쓸 수 있는 '조금 오래된' 위치의 한계.
     * 방금 찍은 클립의 장소를 고르는 자리라 사용자는 실제로 그 근처에 있다.
     * 다만 너무 묵은 좌표는 엉뚱한 도시를 기록해 버리므로 30분으로 끊는다.
     */
    private const val STALE_LIMIT_MS = 30 * 60 * 1000L

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * @param timeoutMs 실제 측위에 줄 시간. 권한 팝업 대기 시간은 여기 포함되지 않는다 —
     *   호출 전에 권한이 정해져 있어야 한다. (팝업이 떠 있는 동안 시계를 켜면 사용자가
     *   읽는 사이 시간이 다 흘러 '허용'을 누르자마자 실패로 끝난다)
     */
    suspend fun request(context: Context, timeoutMs: Long = 6_000): Result {
        // 권한이 없으면 getCurrentLocation은 콜백 없이 조용히 잠긴다.
        // 시한까지 기다릴 이유가 없으니 바로 알리고 설정으로 안내한다.
        if (!hasPermission(context)) return Result.Denied

        val client = LocationServices.getFusedLocationProviderClient(context)
        val cached = runCatching { client.lastLocation.await() }.getOrNull()
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.time < FRESH_MS) return Result.Success(cached)

        val fresh = withTimeoutOrNull(timeoutMs) {
            runCatching {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            }.getOrNull()
        }
        if (fresh != null) return Result.Success(fresh)

        // 시한이 다 됐다 — 조금 오래된 위치라도 있으면 그걸 쓴다
        if (cached != null && now - cached.time < STALE_LIMIT_MS) return Result.Success(cached)
        return Result.Failed
    }
}
