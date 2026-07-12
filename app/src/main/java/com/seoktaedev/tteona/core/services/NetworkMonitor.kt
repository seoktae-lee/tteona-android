package com.seoktaedev.tteona.core.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 앱 전역 네트워크 연결 상태 모니터 — iOS Core/Services/NetworkMonitor.swift의 이식본.
 * 오프라인이면 상단 배너를 띄워 "왜 아무것도 안 되는지"를 사용자가 알 수 있게 한다
 * (그동안은 조용히 실패했음). Application.onCreate에서 start(context) 1회 호출.
 */
object NetworkMonitor {
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline

    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return

        // 초기 상태 반영 (콜백은 변화 시점에만 온다)
        _isOnline.value = cm.activeNetwork?.let { net ->
            cm.getNetworkCapabilities(net)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } ?: false

        val callback = object : ConnectivityManager.NetworkCallback() {
            // 유효 인터넷을 가진 네트워크 집합 — 하나라도 있으면 온라인
            private val available = mutableSetOf<Network>()
            override fun onAvailable(network: Network) {
                available.add(network); _isOnline.value = available.isNotEmpty()
            }
            override fun onLost(network: Network) {
                available.remove(network); _isOnline.value = available.isNotEmpty()
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) available.add(network)
                else available.remove(network)
                _isOnline.value = available.isNotEmpty()
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
    }
}
