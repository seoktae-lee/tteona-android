package com.seoktaedev.tteona.core.model

import kotlinx.serialization.Serializable

// iOS StatsService.swift의 TravelStats 이식본
@Serializable
data class TravelStats(
    val coursesCreated: Int = 0,
    val placesInCourses: Int = 0,
    val likesReceived: Int = 0,
    val groups: Int = 0,
    val placesVisited: Int = 0,
    val activeDays: Int = 0,
)

// iOS StatsService.swift의 StatsEvent 이식본
enum class StatsEvent(val type: String) {
    COURSE_CREATED("course_created"),
    PLACE_VISITED("place_visited"),
    COURSE_LIKED("course_liked"),
    COURSE_SHARED("course_shared"),
}

// iOS StatsService.swift의 CreatorRank 이식본
@Serializable
data class CreatorRank(
    val rank: Int,
    val userId: String,
    val nickname: String,
    val isVerified: Boolean = false,
    val profileImageUrl: String? = null,
    val likes: Int = 0,
    val courses: Int = 0,
)
