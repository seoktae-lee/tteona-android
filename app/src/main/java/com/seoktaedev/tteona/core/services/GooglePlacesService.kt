package com.seoktaedev.tteona.core.services

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.seoktaedev.tteona.BuildConfig
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.i18n.LocaleManager
import com.seoktaedev.tteona.core.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Google Places API (New) 직접 호출 공용 클라이언트 — iOS의 PlacesPhotoService/PlaceDetailService가
 * 각자 들고 있는 Google 호출부를 한 곳에 모은 것. 키는 local.properties의 GOOGLE_PLACES_API_KEY
 * (미지정 시 MAPS_API_KEY 재사용). Android 앱 제한 키 검증용으로 패키지명·서명 SHA-1 헤더를 함께 보낸다.
 */
object GooglePlacesService {
    private const val TAG = "GooglePlaces"
    private const val BASE = "https://places.googleapis.com/v1"

    private val apiKey: String get() = BuildConfig.GOOGLE_PLACES_API_KEY
    val isConfigured: Boolean get() = apiKey.isNotEmpty()

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // 앱 제한 키의 X-Android-Cert 헤더 값 — 서명 인증서 SHA-1 (콜론 없는 대문자 hex)
    private val certSha1: String by lazy {
        runCatching {
            val pm = appContext.packageManager
            val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(appContext.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo!!.apkContentsSigners[0]
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(appContext.packageName, PackageManager.GET_SIGNATURES).signatures!![0]
            }
            MessageDigest.getInstance("SHA-1").digest(signature.toByteArray())
                .joinToString("") { "%02X".format(it) }
        }.getOrDefault("")
    }

    /**
     * places:searchText로 첫 번째 장소 JSON을 반환. 좌표가 있으면 5km 반경 우선 검색
     * (iOS locationBias와 동일 — 동명 장소 오매칭 방지). 실패/미설정 시 null.
     */
    suspend fun searchTextFirstPlace(
        query: String,
        fieldMask: String,
        latitude: Double? = null,
        longitude: Double? = null,
    ): JSONObject? {
        if (!isConfigured) return null
        val body = JSONObject().apply {
            put("textQuery", query)
            if (latitude != null && longitude != null) {
                put("locationBias", JSONObject().put("circle", JSONObject().apply {
                    put("center", JSONObject().put("latitude", latitude).put("longitude", longitude))
                    put("radius", 5000.0)
                }))
            }
        }
        val request = Request.Builder()
            .url("$BASE/places:searchText")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("X-Goog-Api-Key", apiKey)
            .addHeader("X-Android-Package", appContext.packageName)
            .addHeader("X-Android-Cert", certSha1)
            .addHeader("X-Goog-FieldMask", fieldMask)
            .build()
        val json = execute(request) ?: return null
        return json.optJSONArray("places")?.optJSONObject(0)
    }

    /** 사진 리소스명("places/…/photos/…")을 실제 이미지 URL(photoUri)로 변환. */
    suspend fun photoUri(photoName: String): String? {
        if (!isConfigured) return null
        val request = Request.Builder()
            .url("$BASE/$photoName/media?maxHeightPx=800&skipHttpRedirect=true")
            .addHeader("X-Goog-Api-Key", apiKey)
            .addHeader("X-Android-Package", appContext.packageName)
            .addHeader("X-Android-Cert", certSha1)
            .build()
        return execute(request)?.optString("photoUri")?.takeIf { it.isNotEmpty() }
    }

    private suspend fun execute(request: Request): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            ApiClient.httpClient.newCall(request).execute().use { res ->
                if (!res.isSuccessful) {
                    Log.w(TAG, "요청 실패 ${res.code}: ${request.url.encodedPath}")
                    return@use null
                }
                res.body?.string()?.let(::JSONObject)
            }
        }.onFailure { Log.w(TAG, "요청 오류: ${request.url.encodedPath}", it) }.getOrNull()
    }

    /** Google 장소 types → 표시용 카테고리 문구 (iOS categoryText와 동일한 우선순위). */
    fun categoryText(types: List<String>): String? {
        val priority = listOf(
            "beach" to R.string.place_category_beach,
            "mountain" to R.string.place_category_mountain,
            "national_park" to R.string.place_category_nationalPark,
            "university" to R.string.place_category_university,
            "school" to R.string.place_category_school,
            "library" to R.string.place_category_library,
            "museum" to R.string.place_category_museum,
            "art_gallery" to R.string.place_category_artGallery,
            "amusement_park" to R.string.place_category_amusementPark,
            "zoo" to R.string.place_category_zoo,
            "aquarium" to R.string.place_category_aquarium,
            "stadium" to R.string.place_category_stadium,
            "park" to R.string.place_category_park,
            "cafe" to R.string.place_category_cafe,
            "bakery" to R.string.place_category_bakery,
            "restaurant" to R.string.place_category_restaurant,
            "bar" to R.string.place_category_bar,
            "night_club" to R.string.place_category_nightClub,
            "movie_theater" to R.string.place_category_movieTheater,
            "shopping_mall" to R.string.place_category_shoppingMall,
            "store" to R.string.place_category_store,
            "lodging" to R.string.place_category_lodging,
            "spa" to R.string.place_category_spa,
            "gym" to R.string.place_category_gym,
            "hospital" to R.string.place_category_hospital,
            "pharmacy" to R.string.place_category_pharmacy,
            "subway_station" to R.string.place_category_subwayStation,
            "train_station" to R.string.place_category_trainStation,
            "tourist_attraction" to R.string.place_category_touristAttraction,
            "point_of_interest" to R.string.place_category_pointOfInterest,
        )
        for ((type, resId) in priority) {
            if (type in types) return LocaleManager.string(resId)
        }
        return null
    }
}
