package com.seoktaedev.tteona.core.model

import kotlinx.serialization.Serializable

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
