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

    /** 한 장소(클립)당 최대 촬영 길이 (초) — 무료 5초, PRO는 제한 없음(총 예산 내) */
    val vlogClipMaxSeconds: Double? get() = if (_isPro.value) null else 5.0

    /**
     * 세션 예산을 클립 단위로 나눈 칸 수 — 무료는 6칸(30÷5) 분절 링,
     * PRO는 클립 제한이 없어 분절이 의미 없으므로 null(연속 링)을 반환한다. (iOS vlogSegmentCount)
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

    fun logIn(userId: String) {
        if (!isConfigured) return
        Purchases.sharedInstance.logIn(
            userId,
            object : LogInCallback {
                override fun onReceived(customerInfo: CustomerInfo, created: Boolean) = apply(customerInfo)
                override fun onError(error: PurchasesError) {}
            },
        )
    }

    fun logOut() {
        if (!isConfigured) return
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
