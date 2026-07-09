package com.seoktaedev.tteona.core.services

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

// iOS Core/Services/FootprintAtlas.swift의 Kotlin 이식본.

/**
 * 번들 GeoJSON에서 로드한 하나의 지역(한국 시군구 또는 세계 국가).
 * 링 좌표는 웹 메르카토르 단위공간(0~1)으로 투영해 보관 — 렌더링·판정 모두 이 공간에서 수행.
 */
class GeoRegion(
    val code: String,        // 시군구 행정코드("11010") 또는 ISO 3166-2 주/도코드("JP-27")
    val name: String,        // 한글/원어 이름
    val nameEng: String?,    // 영문 이름 (주/도는 name이 이미 영문)
    val country: String?,    // 소속 국가 ISO3 (주/도만; 한국 시군구는 null = 암묵적 KOR)
    val rings: List<FloatArray>, // [x0,y0,x1,y1,...] 외곽 + 구멍 링 (even-odd 채움)
    val bbox: Rect,          // 단위공간 바운딩박스 (빠른 후보 선별용)
    val path: Path,          // 단위공간에 미리 구운 패스 (Canvas 렌더링 캐시)
) {
    val center: Offset get() = bbox.center
}

/**
 * 한국 시군구 250개 + 세계 국가 242개 경계를 assets에서 로드하고,
 * 좌표 → 지역 판정(point-in-polygon)을 오프라인으로 수행한다.
 */
object FootprintAtlas {

    var koreaRegions: List<GeoRegion> = emptyList()      // 시군구 (250)
        private set
    var worldProvinces: List<GeoRegion> = emptyList()    // 세계 주/도 (admin-1)
        private set
    /** 국가 ISO3 → 소속 주/도들의 합집합 바운딩박스 (카메라 포커스용) */
    var countryBBox: Map<String, Rect> = emptyMap()
        private set

    private val lock = Any()
    @Volatile
    private var loaded = false

    // 한국 대략 범위(도 단위) — 시군구 판정을 시도할지 결정
    private val koreaLatRange = 32.0..39.5
    private val koreaLngRange = 124.0..132.5

    /** 최초 1회 GeoJSON 파싱 (약 0.3~0.6초, 백그라운드 스레드에서 호출할 것) */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            koreaRegions = load(context, "korea-sig.geojson", "code", "name", "name_eng", null)
            worldProvinces = load(context, "world-admin1.geojson", "code", "nm", null, "country")

            // 국가별 바운딩박스 = 소속 주/도 bbox 합집합
            val boxes = mutableMapOf<String, Rect>()
            for (p in worldProvinces) {
                val iso3 = p.country ?: continue
                boxes[iso3] = boxes[iso3]?.let { union(it, p.bbox) } ?: p.bbox
            }
            countryBBox = boxes

            loaded = true
            android.util.Log.d("FootprintAtlas", "loaded korea=${koreaRegions.size} provinces=${worldProvinces.size} countries=${countryBBox.size}")
        }
    }

    val isLoaded: Boolean get() = loaded

    private fun union(a: Rect, b: Rect): Rect = Rect(
        minOf(a.left, b.left), minOf(a.top, b.top),
        maxOf(a.right, b.right), maxOf(a.bottom, b.bottom),
    )

    private fun load(
        context: Context,
        assetName: String,
        codeKey: String,
        nameKey: String,
        nameEngKey: String?,
        countryKey: String?,
    ): List<GeoRegion> {
        val json = runCatching {
            context.assets.open(assetName).bufferedReader().use { it.readText() }
        }.getOrNull() ?: run {
            android.util.Log.e("FootprintAtlas", "failed to load $assetName")
            return emptyList()
        }

        val regions = mutableListOf<GeoRegion>()
        val features = JSONObject(json).getJSONArray("features")
        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val props = feature.optJSONObject("properties") ?: continue
            val code = props.optString(codeKey).takeIf { it.isNotEmpty() } ?: continue
            val name = props.optString(nameKey).takeIf { it.isNotEmpty() } ?: continue
            val geometry = feature.optJSONObject("geometry") ?: continue
            val coords = geometry.optJSONArray("coordinates") ?: continue

            val rings = mutableListOf<FloatArray>()
            when (geometry.optString("type")) {
                "Polygon" -> {
                    for (r in 0 until coords.length()) {
                        projectRing(coords.getJSONArray(r))?.let { rings.add(it) }
                    }
                }
                "MultiPolygon" -> {
                    for (p in 0 until coords.length()) {
                        val polygon = coords.getJSONArray(p)
                        for (r in 0 until polygon.length()) {
                            projectRing(polygon.getJSONArray(r))?.let { rings.add(it) }
                        }
                    }
                }
            }
            if (rings.isEmpty()) continue

            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            val path = Path()
            for (ring in rings) {
                path.moveTo(ring[0], ring[1])
                var j = 2
                while (j < ring.size) {
                    path.lineTo(ring[j], ring[j + 1])
                    j += 2
                }
                path.close()
                j = 0
                while (j < ring.size) {
                    val x = ring[j]; val y = ring[j + 1]
                    if (x < minX) minX = x; if (x > maxX) maxX = x
                    if (y < minY) minY = y; if (y > maxY) maxY = y
                    j += 2
                }
            }
            path.fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd

            regions.add(
                GeoRegion(
                    code = code,
                    name = name,
                    nameEng = nameEngKey?.let { props.optString(it).takeIf { s -> s.isNotEmpty() } },
                    country = countryKey?.let { props.optString(it).takeIf { s -> s.isNotEmpty() } },
                    rings = rings,
                    bbox = Rect(minX, minY, maxX, maxY),
                    path = path,
                )
            )
        }
        return regions
    }

    private fun projectRing(ring: org.json.JSONArray): FloatArray? {
        if (ring.length() < 3) return null
        val out = FloatArray(ring.length() * 2)
        for (i in 0 until ring.length()) {
            val pt = ring.getJSONArray(i)
            val projected = project(pt.getDouble(1), pt.getDouble(0))
            out[i * 2] = projected.x
            out[i * 2 + 1] = projected.y
        }
        return out
    }

    /** 위경도 → 웹 메르카토르 단위공간(0~1). 지도 렌더링·판정 공용. */
    fun project(lat: Double, lng: Double): Offset {
        val x = (lng + 180) / 360
        val clamped = lat.coerceIn(-85.0, 85.0) * PI / 180
        val y = (1 - ln(tan(PI / 4 + clamped / 2)) / PI) / 2
        return Offset(x.toFloat(), y.toFloat())
    }

    /** 단위공간 → 위경도 역변환 */
    fun unproject(unit: Offset): Pair<Double, Double> {
        val lng = unit.x.toDouble() * 360 - 180
        val lat = atan(sinh(PI * (1 - 2 * unit.y.toDouble()))) * 180 / PI
        return lat to lng
    }

    data class ResolvedRegion(
        val sig: GeoRegion?,        // 한국 시군구 (한국 밖이면 null)
        val province: GeoRegion?,   // 세계 주/도
        val countryCode: String?,   // 소속 국가 ISO3
    )

    /** 좌표가 속한 시군구·주도·국가 판정. 단순화된 경계라 해안가 등에서 빗나가면 근접 폴리곤으로 보정. */
    fun resolve(context: Context, lat: Double, lng: Double): ResolvedRegion {
        ensureLoaded(context)
        val pt = project(lat, lng)

        var sig: GeoRegion? = null
        if (lat in koreaLatRange && lng in koreaLngRange) {
            sig = hit(pt, koreaRegions)
                ?: nearest(pt, koreaRegions, 0.0006f) // ≈ 20km
        }

        val province = hit(pt, worldProvinces)
            ?: nearest(pt, worldProvinces, 0.009f)    // ≈ 3°
        // 시군구가 잡혔으면 국가는 무조건 한국 (경계 오차로 주/도가 빗나가도 보정)
        val countryCode = if (sig != null) "KOR" else province?.country
        return ResolvedRegion(sig, province, countryCode)
    }

    private fun hit(point: Offset, regions: List<GeoRegion>): GeoRegion? {
        for (region in regions) {
            val b = region.bbox
            if (point.x < b.left - 0.0001f || point.x > b.right + 0.0001f ||
                point.y < b.top - 0.0001f || point.y > b.bottom + 0.0001f
            ) continue
            if (contains(point, region.rings)) return region
        }
        return null
    }

    /** even-odd 규칙 point-in-polygon (구멍 링 포함 처리) */
    private fun contains(point: Offset, rings: List<FloatArray>): Boolean {
        var inside = false
        for (ring in rings) {
            val count = ring.size / 2
            var j = count - 1
            for (i in 0 until count) {
                val ax = ring[i * 2]; val ay = ring[i * 2 + 1]
                val bx = ring[j * 2]; val by = ring[j * 2 + 1]
                if ((ay > point.y) != (by > point.y) &&
                    point.x < (bx - ax) * (point.y - ay) / (by - ay) + ax
                ) {
                    inside = !inside
                }
                j = i
            }
        }
        return inside
    }

    /** 어느 폴리곤에도 안 들어갈 때(단순화 오차·해안) 가장 가까운 경계 정점 기준 근접 지역 선택 */
    private fun nearest(point: Offset, regions: List<GeoRegion>, maxUnitDistance: Float): GeoRegion? {
        var best: GeoRegion? = null
        var bestDist = maxUnitDistance
        for (region in regions) {
            val b = region.bbox
            if (point.x < b.left - maxUnitDistance || point.x > b.right + maxUnitDistance ||
                point.y < b.top - maxUnitDistance || point.y > b.bottom + maxUnitDistance
            ) continue
            for (ring in region.rings) {
                var j = 0
                while (j < ring.size) {
                    val d = hypot(ring[j] - point.x, ring[j + 1] - point.y)
                    if (d < bestDist) {
                        bestDist = d
                        best = region
                    }
                    j += 2
                }
            }
        }
        return best
    }

    fun koreaRegion(code: String): GeoRegion? = koreaRegions.firstOrNull { it.code == code }
    fun province(code: String): GeoRegion? = worldProvinces.firstOrNull { it.code == code }
}
