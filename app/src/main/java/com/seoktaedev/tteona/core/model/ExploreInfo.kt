package com.seoktaedev.tteona.core.model

import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.i18n.LocaleManager

// iOS ExploreInfoService.swift의 WeatherInfo/RouteInfo 이식본

data class WeatherInfo(val tempC: Double, val code: Int) {
    val emoji: String get() = when (code) {
        0 -> "☀️"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..67 -> "🌧️"
        in 71..77 -> "❄️"
        in 80..82 -> "🌦️"
        85, 86 -> "🌨️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    val description: String get() = when (code) {
        0 -> LocaleManager.string(R.string.weather_clear)
        1, 2 -> LocaleManager.string(R.string.weather_partlyCloudy)
        3 -> LocaleManager.string(R.string.weather_cloudy)
        45, 48 -> LocaleManager.string(R.string.weather_fog)
        in 51..57 -> LocaleManager.string(R.string.weather_drizzle)
        in 61..67 -> LocaleManager.string(R.string.weather_rain)
        in 71..77 -> LocaleManager.string(R.string.weather_snow)
        in 80..82 -> LocaleManager.string(R.string.weather_showers)
        85, 86 -> LocaleManager.string(R.string.weather_snowShowers)
        in 95..99 -> LocaleManager.string(R.string.weather_thunderstorm)
        else -> "-"
    }
}

data class RouteInfo(val distanceMeters: Double, val travelTimeSec: Double) {
    val distanceText: String get() =
        if (distanceMeters >= 1000) "%.1fkm".format(distanceMeters / 1000)
        else "%.0fm".format(distanceMeters)

    val timeText: String get() {
        val minutes = (travelTimeSec / 60).toInt()
        return if (minutes >= 60) LocaleManager.string(R.string.duration_hoursMinutes, minutes / 60, minutes % 60)
        else LocaleManager.string(R.string.duration_minutes, maxOf(minutes, 1))
    }
}
