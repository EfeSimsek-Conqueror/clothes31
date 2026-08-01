package com.fitrater.app.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitLogOut
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.StoreTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thin wrapper over the RevenueCat Purchases SDK.
 *
 * Configure once from [com.fitrater.app.FitraterApplication.onCreate]. Then, after
 * Supabase auth succeeds, call [identify] with the Supabase user id so RC ties
 * subscription entitlements + credit history to that user across devices.
 *
 * All purchase flows are exposed as `suspend` — call from a coroutine tied to
 * a UI scope. The SDK handles the Play Billing dance internally.
 */
object RcBilling {
    private const val TAG = "RcBilling"
    const val ENTITLEMENT_PRO = "pro"

    private var configured = false

    private val _customerInfo = MutableStateFlow<CustomerInfo?>(null)
    val customerInfo: StateFlow<CustomerInfo?> = _customerInfo.asStateFlow()

    /** Server-granted Pro flag from Supabase profile.is_pro. Lets ops grant Pro without a purchase. */
    private val _serverPro = MutableStateFlow(false)

    private val _isPro = MutableStateFlow(false)
    /** Reactive Pro state. Compose can `collectAsState` this and pass it into feature-gate params. */
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private fun recompute() {
        val rc = _customerInfo.value?.entitlements?.get(ENTITLEMENT_PRO)?.isActive == true
        _isPro.value = rc || _serverPro.value
    }

    private fun publish(info: CustomerInfo?) {
        _customerInfo.value = info
        recompute()
    }

    /** Called after Supabase profile loads. `true` unlocks all Pro gates. */
    fun setServerPro(value: Boolean) {
        _serverPro.value = value
        recompute()
    }

    /** True if Pro was granted server-side (Supabase profile.is_pro). */
    fun hasServerPro(): Boolean = _serverPro.value

    fun init(context: Context, apiKey: String) {
        if (configured) return
        if (apiKey.isBlank() || apiKey.startsWith("goog_REPLACE")) {
            Log.w(TAG, "RevenueCat API key is not set. Billing will be disabled.")
            return
        }
        Purchases.logLevel = LogLevel.INFO
        Purchases.configure(
            PurchasesConfiguration.Builder(context.applicationContext, apiKey).build(),
        )
        Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { info ->
            publish(info)
        }
        configured = true
    }

    fun isReady(): Boolean = configured

    /** Tie the current RC anonymous ID to the Supabase user id. Safe to call repeatedly. */
    suspend fun identify(userId: String) {
        if (!configured) return
        runCatching {
            val result = Purchases.sharedInstance.awaitLogIn(userId)
            publish(result.customerInfo)
        }.onFailure { Log.w(TAG, "identify($userId) failed", it) }
    }

    suspend fun signOut() {
        if (!configured) return
        runCatching {
            publish(Purchases.sharedInstance.awaitLogOut())
        }.onFailure { Log.w(TAG, "signOut failed", it) }
    }

    suspend fun currentOffering(): Offering? {
        if (!configured) return null
        return runCatching {
            Purchases.sharedInstance.awaitOfferings().current
        }.getOrNull()
    }

    /**
     * Kick off a Play Billing purchase for the given [pkg] on the given [activity].
     * Throws on cancel/failure — caller should wrap in runCatching.
     */
    suspend fun purchase(activity: Activity, pkg: Package): PurchaseResult {
        check(configured) { "RevenueCat is not configured" }
        val params = PurchaseParams.Builder(activity, pkg).build()
        val result = Purchases.sharedInstance.awaitPurchase(params)
        publish(result.customerInfo)
        return PurchaseResult(result.storeTransaction, result.customerInfo)
    }

    suspend fun refreshCustomerInfo(): CustomerInfo? {
        if (!configured) return null
        return runCatching {
            Purchases.sharedInstance.awaitCustomerInfo().also { publish(it) }
        }.getOrNull()
    }

    fun isPro(info: CustomerInfo? = _customerInfo.value): Boolean =
        _serverPro.value || info?.entitlements?.get(ENTITLEMENT_PRO)?.isActive == true

    /** True if the user is inside the 7-day intro (free trial) window of the annual sub. */
    suspend fun isInTrial(): Boolean {
        if (!configured) return false
        return runCatching {
            val info = Purchases.sharedInstance.awaitCustomerInfo()
            val ent = info.entitlements[ENTITLEMENT_PRO]
            ent?.isActive == true && ent.periodType?.name == "INTRO"
        }.getOrDefault(false)
    }
}

data class PurchaseResult(
    val transaction: StoreTransaction?,
    val customerInfo: CustomerInfo,
)
