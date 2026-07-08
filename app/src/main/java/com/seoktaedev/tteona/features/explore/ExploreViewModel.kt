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

    /** 유저 선호 태그 (온보딩/설정에서 선택, CourseTag rawValue) — 추천 API와 폴백 정렬에 반영 */
    private val preferredTag: String?
        get() = com.seoktaedev.tteona.core.services.UserService.currentUser.value?.preferredTag

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
                    RecommendationService.fetchRecommended(
                        userId = AuthService.currentUser.value?.uid,
                        tag = preferredTag,
                    )
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
                tag = preferredTag,
            )
            if (ids.isNotEmpty()) _uiState.update { it.copy(recommendedIds = ids) }
        }
    }

    // 설정에서 여행 취향 변경 시 추천만 재조회 (iOS onChange(preferredTag) 대응)
    fun refetchRecommendationsForPreference() {
        viewModelScope.launch {
            val ids = RecommendationService.fetchRecommended(
                userId = AuthService.currentUser.value?.uid,
                tag = preferredTag,
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
                // 서버 추천 도착 전 폴백: 선호 태그 코스 우선 → 인기순 (iOS와 동일)
                val pref = preferredTag
                if (pref == null) {
                    courses.sortedByDescending { it.likeCount }
                } else {
                    courses.sortedWith(
                        compareByDescending<Course> { it.tag.label == pref }
                            .thenByDescending { it.likeCount }
                    )
                }
            } else {
                val map = courses.associateBy { it.courseId }
                val ranked = state.recommendedIds.mapNotNull { map[it] }
                val rest = courses.filter { it.courseId !in state.recommendedIds }
                ranked + rest
            }
        }
    }
}
