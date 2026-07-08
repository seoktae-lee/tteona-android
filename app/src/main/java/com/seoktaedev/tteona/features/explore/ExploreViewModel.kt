package com.seoktaedev.tteona.features.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.model.Course
import com.seoktaedev.tteona.core.model.CreatorRank
import com.seoktaedev.tteona.core.services.CourseService
import com.seoktaedev.tteona.core.services.CourseThumbnailService
import com.seoktaedev.tteona.core.services.RecommendationService
import com.seoktaedev.tteona.core.services.StatsService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * iOS ExploreGridView의 상태 로직 이식본.
 */
class ExploreViewModel : ViewModel() {

    enum class SortMode(val labelRes: Int) {
        RECOMMENDED(com.seoktaedev.tteona.R.string.explore_sort_recommended),
        LATEST(com.seoktaedev.tteona.R.string.explore_sort_latest),
        POPULAR(com.seoktaedev.tteona.R.string.explore_sort_popular),
    }

    data class UiState(
        val thumbnails: Map<String, String> = emptyMap(),
        val recommendedIds: List<String> = emptyList(),
        val creatorRanking: List<CreatorRank> = emptyList(),
        val sortMode: SortMode = SortMode.RECOMMENDED,
        val isLoading: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    val courses: StateFlow<List<Course>> = CourseService.courses

    private var loaded = false

    fun loadIfNeeded() {
        if (loaded) return
        loaded = true
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            coroutineScope {
                // iOS loadAll과 동일하게 4개 요청 병렬 수행
                val coursesJob = async {
                    CourseService.fetchCourses(
                        com.seoktaedev.tteona.core.services.UserService.currentUser.value?.blockedUserIds
                            ?: emptyList()
                    )
                }
                val thumbsJob = async { CourseThumbnailService.fetchAllThumbnails() }
                val recJob = async {
                    RecommendationService.fetchRecommended(userId = AuthService.currentUser.value?.uid)
                }
                val rankJob = async { StatsService.fetchCreatorRanking() }
                val likedJob = async {
                    AuthService.currentUser.value?.uid?.let { CourseService.fetchLikedCourseIds(it) }
                }

                coursesJob.await()
                likedJob.await()
                _uiState.update {
                    it.copy(
                        thumbnails = thumbsJob.await(),
                        recommendedIds = recJob.await(),
                        creatorRanking = rankJob.await(),
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun setSortMode(mode: SortMode) {
        _uiState.update { it.copy(sortMode = mode) }
    }

    // 위치를 처음 확보하면 위치 기반으로 추천 1회 재조회 (iOS didRefetchWithLocation)
    private var didRefetchWithLocation = false

    fun refetchRecommendationsWithLocation(lat: Double, lng: Double) {
        if (didRefetchWithLocation) return
        didRefetchWithLocation = true
        viewModelScope.launch {
            val ids = RecommendationService.fetchRecommended(
                userId = AuthService.currentUser.value?.uid, lat = lat, lng = lng,
            )
            if (ids.isNotEmpty()) _uiState.update { it.copy(recommendedIds = ids) }
        }
    }

    // iOS sortedCourses 계산 프로퍼티와 동일한 정렬 규칙
    fun sortedCourses(courses: List<Course>, state: UiState): List<Course> = when (state.sortMode) {
        SortMode.LATEST -> courses.sortedByDescending { it.createdAt }
        SortMode.POPULAR -> courses.sortedByDescending { it.likeCount }
        SortMode.RECOMMENDED -> {
            if (state.recommendedIds.isEmpty()) {
                courses.sortedByDescending { it.likeCount }
            } else {
                val map = courses.associateBy { it.courseId }
                val ranked = state.recommendedIds.mapNotNull { map[it] }
                val rest = courses.filter { it.courseId !in state.recommendedIds }
                ranked + rest
            }
        }
    }
}
