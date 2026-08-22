package com.seoktaedev.tteona.core.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.i18n.LocaleManager
import java.net.URLEncoder

/**
 * 길찾기·장소 보기를 **설치된 지도 앱에 넘긴다.**
 * iOS Core/Services/MapAppLauncher.swift의 이식본.
 *
 * 앱 안에 턴바이턴 안내를 만들지 않는다. 도로 단위 경로, 이탈 시 재탐색, 음성 안내는
 * 지도 회사가 수년에 걸쳐 만드는 것이고, 무엇보다 사용자는 이미 쓰던 지도 앱이 있다.
 * 그쪽이 더 정확하고 익숙하다.
 *
 * iOS는 애플 지도가 항상 있어 최후 수단이 보장되지만, 안드로이드는 그렇지 않다.
 * 대신 `geo:` 인텐트가 있어 **시스템이 알아서 선택창을 띄운다** — 그게 우리의 바닥이다.
 */
object MapAppLauncher {

    /** 네이버지도 / 카카오맵 패키지 — AndroidManifest의 <queries>에도 같이 있어야 조회된다 */
    private const val PKG_NAVER = "com.nhn.android.nmap"
    private const val PKG_KAKAO = "net.daum.android.map"

    /**
     * 이동 수단. 거리로 자동 판단한다 — 20km 떨어진 곳을 도보로 안내하면 곤란하고,
     * 500m를 대중교통으로 안내해도 마찬가지다.
     */
    enum class Mode(val kakao: String, val naverPath: String, val googleMode: String) {
        WALK("FOOT", "route/walk", "w"),
        TRANSIT("PUBLICTRANSIT", "route/public", "r"),
        CAR("CAR", "route/car", "d");

        companion object {
            fun suggested(meters: Double?): Mode = when {
                meters == null -> TRANSIT
                meters <= 1_500 -> WALK
                meters <= 40_000 -> TRANSIT
                else -> CAR
            }
        }
    }

    /**
     * 현재 위치에서 목적지까지 길안내를 연다.
     *
     * 출발지를 넘기지 않는다 — 지도 앱이 자기 위치 정보로 현재 위치를 잡는 편이
     * 우리가 마지막으로 알던 좌표를 넘기는 것보다 정확하다.
     */
    fun openDirections(
        context: Context,
        latitude: Double,
        longitude: Double,
        name: String,
        distanceMeters: Double? = null,
    ) {
        val mode = Mode.suggested(distanceMeters)
        val encoded = encode(name)
        val appId = context.packageName

        // 네이버지도 → 카카오맵 순. 국내 이용자 다수가 네이버를 쓴다.
        val candidates = listOf(
            PKG_NAVER to "nmap://${mode.naverPath}?dlat=$latitude&dlng=$longitude&dname=$encoded&appname=$appId",
            PKG_KAKAO to "kakaomap://route?ep=$latitude,$longitude&by=${mode.kakao}",
        )
        if (launchFirstInstalled(context, candidates)) return

        // 바닥: geo 인텐트 — 시스템이 설치된 지도 앱들로 선택창을 띄운다.
        // (구글 지도가 있으면 거기로, 없으면 다른 앱으로)
        openGeoFallback(context, latitude, longitude, name, mode)
    }

    /** 목적지를 지도에서 보여주기만 한다(길안내 없이). */
    fun openPlace(context: Context, latitude: Double, longitude: Double, name: String) {
        val encoded = encode(name)
        val appId = context.packageName
        val candidates = listOf(
            PKG_NAVER to "nmap://place?lat=$latitude&lng=$longitude&name=$encoded&appname=$appId",
            PKG_KAKAO to "kakaomap://look?p=$latitude,$longitude",
        )
        if (launchFirstInstalled(context, candidates)) return
        openGeoFallback(context, latitude, longitude, name, mode = null)
    }

    /**
     * 설치된 첫 앱으로 연다.
     *
     * **패키지 조회가 매니페스트 `<queries>`에 걸려 있어야 한다.** Android 11+에서는
     * 선언하지 않으면 resolveActivity가 항상 null을 돌려줘 이 분기가 통째로 죽고
     * 매번 폴백으로 떨어진다 — 아무 오류 없이 조용히.
     */
    private fun launchFirstInstalled(context: Context, candidates: List<Pair<String, String>>): Boolean {
        for ((pkg, uri) in candidates) {
            if (!isInstalled(context, pkg)) continue
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launched = runCatching { context.startActivity(intent); true }.getOrElse {
                Log.w("MapAppLauncher", "$pkg 실행 실패", it)
                false
            }
            if (launched) return true
        }
        return false
    }

    private fun isInstalled(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    private fun openGeoFallback(
        context: Context,
        latitude: Double,
        longitude: Double,
        name: String,
        mode: Mode?,
    ) {
        val encoded = encode(name)
        val uri = if (mode == null) {
            // 좌표를 앞에 두고 q에 이름을 함께 넘긴다 — 이름만 넘기면 동명 장소로 갈 수 있다
            "geo:$latitude,$longitude?q=$latitude,$longitude($encoded)"
        } else {
            "google.navigation:q=$latitude,$longitude&mode=${mode.googleMode}"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        val ok = runCatching { context.startActivity(intent); true }.getOrElse { false }
        if (ok) return

        // google.navigation을 받을 앱이 없을 수도 있다 — 순수 geo로 한 번 더
        val plain = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$latitude,$longitude?q=$encoded"))
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        val ok2 = runCatching { context.startActivity(plain); true }.getOrElse { false }
        if (!ok2) {
            // 지도 앱이 하나도 없는 기기 — 조용히 실패하면 버튼이 고장 난 것처럼 보인다
            Toast.makeText(
                context,
                LocaleManager.string(context, R.string.common_directionsNoApp),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun encode(text: String): String =
        runCatching { URLEncoder.encode(text, "UTF-8") }.getOrDefault("")
}
