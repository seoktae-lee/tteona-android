package com.seoktaedev.tteona.core.model

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
        0 -> "맑음"
        1, 2 -> "구름 조금"
        3 -> "흐림"
        45, 48 -> "안개"
        in 51..57 -> "이슬비"
        in 61..67 -> "비"
        in 71..77 -> "눈"
        in 80..82 -> "소나기"
        85, 86 -> "눈 소나기"
        in 95..99 -> "뇌우"
        else -> "-"
    }
}

data class RouteInfo(val distanceMeters: Double, val travelTimeSec: Double) {
    val distanceText: String get() =
        if (distanceMeters >= 1000) "%.1fkm".format(distanceMeters / 1000)
        else "%.0fm".format(distanceMeters)

    val timeText: String get() {
        val minutes = (travelTimeSec / 60).toInt()
        return if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분" else "${maxOf(minutes, 1)}분"
    }
}
