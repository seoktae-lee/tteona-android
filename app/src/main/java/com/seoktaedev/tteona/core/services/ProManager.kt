package com.seoktaedev.tteona.core.services

import android.app.Activity
import android.content.Context
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.LogInCallback
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.models.StoreTransaction
import com.seoktaedev.tteona.BuildConfig
import com.seoktaedev.tteona.core.model.VlogClipLength
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * tteona PRO 구독 상태 관리 — iOS Core/Services/ProManager.swift의 이식본.
 * RevenueCat "pro" 엔타이틀먼트 기준, iOS와 동일 프로젝트를 공유한다.
 * REVENUECAT_API_KEY(local.properties, goog_...) 미설정이면 무료 모드로 동작하고 결제 UI는 잠긴다.
 * TODO(수동): Google Play Console 구독 상품 등록 + RevenueCat에 Google 앱 추가.
 */
object ProManager {
    const val ENTITLEMENT_ID = "pro"

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro

    private val _offerings = MutableStateFlow<Offerings?>(null)
    val offerings: StateFlow<Offerings?> = _offerings

    /** 브이로그 촬영 총 길이 예산 (초) — 무료 30초, PRO 5분 */
    val vlogBudgetSeconds: Double get() = if (_isPro.value) 300.0 else 30.0

    private const val CLIP_LENGTH_KEY = "vlog.clipLength"
    private lateinit var prefs: android.content.SharedPreferences

    /** 유저가 고른 한 장소당 촬영 길이. PRO 전용을 고른 채 구독이 끝나면 무료 기본값으로 되돌린다. */
    private val _clipLength = MutableStateFlow(VlogClipLength.FREE_DEFAULT)
    val clipLength: StateFlow<VlogClipLength> = _clipLength

    fun initClipLength(context: Context) {
        prefs = context.getSharedPreferences("tteona_prefs", Context.MODE_PRIVATE)
        _clipLength.value = VlogClipLength.from(prefs.getString(CLIP_LENGTH_KEY, null))
    }

    fun setClipLength(length: VlogClipLength) {
        _clipLength.value = length
        if (::prefs.isInitialized) prefs.edit().putString(CLIP_LENGTH_KEY, length.key).apply()
    }

    /** 실제로 적용되는 길이 — 권한 없는 선택은 무료 기본값으로 강등한다 */
    val effectiveClipLength: VlogClipLength
        get() = _clipLength.value.let {
            if (it.requiresPro && !_isPro.value) VlogClipLength.FREE_DEFAULT else it
        }

    /** 이 길이를 지금 쓸 수 있는가 (칩 잠금 표시용) */
    fun canUse(length: VlogClipLength): Boolean = _isPro.value || !length.requiresPro

    /**
     * 한 장소(클립)당 최대 촬영 길이 (초).
     *
     * 예전에는 무료 5초 고정 / PRO는 null(무제한)이었다. 이제는 유저가 고른 길이가 곧 한도이며
     * **PRO도 자동 종료된다** — 수동 종료를 두면 한 장소에서 예산을 다 태우는 사고가 난다.
     * (null을 "PRO 무제한"의 신호로 읽던 곳들이 있으므로 isPro를 직접 보도록 바꿀 것)
     */
    val vlogClipMaxSeconds: Double? get() = effectiveClipLength.seconds

    /**
     * 세션 예산을 클립 단위로 나눈 칸 수 (iOS vlogSegmentCount).
     * 이제는 PRO도 클립 한도가 있으므로 항상 값이 나온다 — 예: 3초 선택 시 무료는 10칸.
     */
    val vlogSegmentCount: Int?
        get() = vlogClipMaxSeconds?.takeIf { it > 0 }?.let {
            Math.round(vlogBudgetSeconds / it).toInt()
        }

    private val isConfigured get() = Purchases.isConfigured

    fun configure(context: Context, userId: String?) {
        val key = BuildConfig.REVENUECAT_API_KEY
        if (key.isEmpty() || !key.startsWith("goog_")) {
            Log.i("Pro", "RevenueCat API 키 미설정 — 무료 모드로 동작 (local.properties REVENUECAT_API_KEY 확인)")
            return
        }
        Purchases.logLevel = LogLevel.WARN
        Purchases.configure(
            PurchasesConfiguration.Builder(context, key)
                .appUserID(userId)
                .build()
        )
        refresh()
        loadOfferings()
    }

    /**
     * RevenueCat에 결제 신원을 붙인 적이 있는가.
     *
     * 게스트에게는 결제 신원을 붙이지 않는다 — 익명 상태로 결제가 이뤄지면 기기를 바꿨을 때
     * 복원되지 않기 때문이다. 그런데 그 "안 붙인다"를 **로그아웃 호출**로 표현하면,
     * 붙인 적도 없는 걸 떼라고 부르는 셈이 되어 앱을 켤 때마다 SDK가 거부하며 에러를 남긴다.
     * ("LogOut was called but the current user is anonymous")
     * 뗄 것이 있을 때만 떼도록, 붙였다는 사실을 여기서 기억한다.
     */
    private var didAttachIdentity = false

    fun logIn(userId: String) {
        if (!isConfigured) return
        Purchases.sharedInstance.logIn(
            userId,
            object : LogInCallback {
                override fun onReceived(customerInfo: CustomerInfo, created: Boolean) {
                    didAttachIdentity = true
                    apply(customerInfo)
                }
                override fun onError(error: PurchasesError) {}
            },
        )
    }

    fun logOut() {
        if (!isConfigured || !didAttachIdentity) return
        didAttachIdentity = false
        Purchases.sharedInstance.logOut(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) = apply(customerInfo)
            override fun onError(error: PurchasesError) {}
        })
    }

    fun refresh() {
        if (!isConfigured) return
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) = apply(customerInfo)
            override fun onError(error: PurchasesError) {}
        })
    }

    fun loadOfferings() {
        if (!isConfigured) return
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: Offerings) {
                _offerings.value = offerings
            }
            override fun onError(error: PurchasesError) {}
        })
    }

    /** 반환값: 구매 후 PRO 활성 여부 (유저가 결제 시트를 닫으면 false) */
    suspend fun purchase(activity: Activity, pkg: Package): Boolean {
        if (!isConfigured) return false
        return suspendCoroutine { cont ->
            Purchases.sharedInstance.purchase(
                PurchaseParams.Builder(activity, pkg).build(),
                object : PurchaseCallback {
                    override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                        apply(customerInfo)
                        cont.resume(_isPro.value)
                    }
                    override fun onError(error: PurchasesError, userCancelled: Boolean) {
                        if (userCancelled) cont.resume(false)
                        else cont.resumeWithException(Exception(error.message))
                    }
                },
            )
        }
    }

    suspend fun restore(): Boolean {
        if (!isConfigured) return false
        return suspendCoroutine { cont ->
            Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    apply(customerInfo)
                    cont.resume(_isPro.value)
                }
                override fun onError(error: PurchasesError) {
                    cont.resumeWithException(Exception(error.message))
                }
            })
        }
    }

    private fun apply(info: CustomerInfo?) {
        _isPro.value = info?.entitlements?.get(ENTITLEMENT_ID)?.isActive == true
    }
}
