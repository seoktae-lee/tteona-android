package com.seoktaedev.tteona.core.services

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.seoktaedev.tteona.core.model.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * iOS Core/Services/LocationService.swift의 Kotlin 이식본.
 * 세션 중 위치 추적 + 장소 도착 감지 (iOS는 지오펜스, 안드로이드는 업데이트마다 거리 검사).
 */
class LocationService(context: Context) {
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation

    private val _arrivedAtPlace = MutableStateFlow<Place?>(null)
    val arrivedAtPlace: StateFlow<Place?> = _arrivedAtPlace

    private var trackedPlaces: List<Place> = emptyList()
    private val notifiedOrders = mutableSetOf<Int>() // 장소당 도착 알림 1회
    private var callback: LocationCallback? = null

    private val arrivalRadiusM = 50f // iOS arrivalRadius와 동일

    @SuppressLint("MissingPermission")
    fun startTracking(places: List<Place>) {
        trackedPlaces = places
        if (callback != null) return // 이미 추적 중 — 대상 장소만 갱신

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateDistanceMeters(20f)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                _currentLocation.value = location
                checkArrival(location)
            }
        }
        callback = cb
        runCatching { client.requestLocationUpdates(request, cb, android.os.Looper.getMainLooper()) }
        runCatching {
            client.lastLocation.addOnSuccessListener { it?.let { loc -> _currentLocation.value = loc } }
        }
    }

    fun stopTracking() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
        notifiedOrders.clear()
    }

    fun distanceTo(place: Place): Float? {
        val loc = _currentLocation.value ?: return null
        val results = FloatArray(1)
        Location.distanceBetween(loc.latitude, loc.longitude, place.latitude, place.longitude, results)
        return results[0]
    }

    fun clearArrival() {
        _arrivedAtPlace.value = null
    }

    private fun checkArrival(location: Location) {
        for (place in trackedPlaces) {
            if (place.order in notifiedOrders) continue
            val results = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, place.latitude, place.longitude, results)
            if (results[0] <= arrivalRadiusM) {
                notifiedOrders.add(place.order)
                _arrivedAtPlace.value = place
            }
        }
    }
}
