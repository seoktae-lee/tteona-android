package com.seoktaedev.tteona.features.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seoktaedev.tteona.core.auth.AuthService
import com.seoktaedev.tteona.core.model.AppUser
import com.seoktaedev.tteona.core.model.Course
import com.seoktaedev.tteona.core.model.RouteInfo
import com.seoktaedev.tteona.core.model.WeatherInfo
import com.seoktaedev.tteona.core.i18n.LocaleManager
import com.seoktaedev.tteona.core.services.CourseService
import com.seoktaedev.tteona.core.services.ExploreInfoService
import com.seoktaedev.tteona.core.services.TranslationService
import com.seoktaedev.tteona.core.services.UserService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** iOS ExploreDetailView의 상태 로직 이식본. */
class CourseDetailViewModel : ViewModel() {

    data class UiState(
        val author: AppUser? = null,
        val weather: WeatherInfo? = null,
        val carRoute: RouteInfo? = null,
        val walkRoute: RouteInfo? = null,
        val transitRoute: RouteInfo? = null,
        val isLoadingRoute: Boolean = true,
        val isLoadingTransit: Boolean = true,
        // 코스 제목(UGC) 번역문 — 도착 전·실패 시에는 원문을 보여준다.
        val translatedTitle: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    val likedCourseIds: StateFlow<Set<String>> = CourseService.likedCourseIds

    fun load(course: Course) {
        viewModelScope.launch {
            coroutineScope {
                val authorJob = async { UserService.fetchAuthor(course.authorId) }
                val weatherJob = async {
                    course.mainPlace?.let { ExploreInfoService.fetchWeather(it.latitude, it.longitude) }
                }
                launch {
                    // 자동차: 서버 실측 우선, 실패 시 직선거리 추정 폴백 (iOS와 동일)
                    val car = ExploreInfoService.computeRoute(course.places, "car")
                        ?: ExploreInfoService.fallbackRoute(course.places, walking = false)
                    val walk = ExploreInfoService.computeRoute(course.places, "walk")
                        ?: ExploreInfoService.fallbackRoute(course.places, walking = true)
                    _uiState.update { it.copy(carRoute = car, walkRoute = walk, isLoadingRoute = false) }
                }
                launch {
                    val transit = ExploreInfoService.computeTransitRoute(course.places)
                    _uiState.update { it.copy(transitRoute = transit, isLoadingTransit = false) }
                }
                _uiState.update { it.copy(author = authorJob.await(), weather = weatherJob.await()) }

                // 날씨·경로 조회 뒤로 밀리지 않도록 별도 코루틴 — 도착 전까지는 원문이 보인다.
                launch {
                    val translated = TranslationService.translate(course.courseName, LocaleManager.current())
                    _uiState.update { it.copy(translatedTitle = translated) }
                }
            }
        }
    }

    fun toggleLike(courseId: String) {
        val userId = AuthService.currentUser.value?.uid ?: return
        val nickname = com.seoktaedev.tteona.core.services.UserService.currentUser.value?.nickname ?: ""
        viewModelScope.launch {
            runCatching { CourseService.toggleLike(courseId, userId, nickname) }
        }
    }
}
