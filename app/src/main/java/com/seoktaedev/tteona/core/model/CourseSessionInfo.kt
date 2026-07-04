package com.seoktaedev.tteona.core.model

import java.util.UUID

// iOS Core/Models/CourseSessionInfo.swift의 Kotlin 이식본
data class CourseSessionInfo(
    val course: Course,
    val roomIds: Set<String>,
    val isResuming: Boolean = false,
    val id: String = UUID.randomUUID().toString(),
)
