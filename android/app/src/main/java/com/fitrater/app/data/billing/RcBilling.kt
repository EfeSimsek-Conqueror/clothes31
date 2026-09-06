package com.fitrater.app.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PeriodType
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitLogOut
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
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
    // Must match the entitlement lookup_key in RevenueCat ("fitscore_pro") and
    // iOS RcBilling.swift. Reading "pro" here silently never resolved — a paid
    // subscriber would purchase successfully and still not unlock Pro.
    const val ENTITLEMENT_PRO = "fitscore_pro"

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

    /** Outcome of [restore]. Nested so call sites read `RcBilling.RestoreResult` without an extra import. */
    sealed class RestoreResult {
        /** The store answered. No active entitlement here means the account genuinely owns nothing. */
        data class Success(val customerInfo: CustomerInfo) : RestoreResult()
        /** Network/store error, or RC was never configured. [cause] is null in the latter case. */
        data class Failed(val cause: Throwable?) : RestoreResult()
    }

    /**
     * Ask the store to re-deliver past purchases for the current account.
     *
     * Failure and "restored nothing" are separate results on purpose: Billing 8 dropped
     * expired-subscription visibility, so restore is the only path back to Pro on a new
     * device — telling a paying subscriber "no purchases found" after a network blip
     * looks like their subscription vanished.
     */
    suspend fun restore(): RestoreResult {
        if (!configured) return RestoreResult.Failed(null)
        return runCatching {
            Purchases.sharedInstance.awaitRestore().also { publish(it) }
        }.fold(
            onSuccess = { RestoreResult.Success(it) },
            onFailure = {
                Log.w(TAG, "restore failed", it)
                RestoreResult.Failed(it)
            },
        )
    }

    suspend fun refreshCustomerInfo(): CustomerInfo? {
        if (!configured) return null
        return runCatching {
            Purchases.sharedInstance.awaitCustomerInfo().also { publish(it) }
        }.getOrNull()
    }

    fun isPro(info: CustomerInfo? = _customerInfo.value): Boolean =
        _serverPro.value || info?.entitlements?.get(ENTITLEMENT_PRO)?.isActive == true

    /**
     * True if the user is inside the 7-day free-trial window of the annual sub.
     *
     * Play free trials come back as [PeriodType.TRIAL]; [PeriodType.INTRO] is discounted
     * paid intro pricing, which we don't sell — matching INTRO here meant this was always
     * false and the trial daily cap never fired.
     */
    suspend fun isInTrial(): Boolean {
        if (!configured) return false
        return runCatching {
            val info = Purchases.sharedInstance.awaitCustomerInfo()
            val ent = info.entitlements[ENTITLEMENT_PRO]
            ent != null && ent.isActive && ent.periodType == PeriodType.TRIAL
        }.getOrDefault(false)
    }
}

data class PurchaseResult(
    val transaction: StoreTransaction?,
    val customerInfo: CustomerInfo,
)
