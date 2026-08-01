package com.fitrater.app.util

import com.fitrater.app.data.Supa
import com.fitrater.app.data.billing.RcBilling
import com.fitrater.app.data.repo.Repo

/** Result of a credits pre-check. */
sealed class GateResult {
    object Ok : GateResult()
    /** User is below balance for this action. Caller should show the paywall. */
    data class InsufficientBalance(val need: Int, val have: Int) : GateResult()
    /** User is on the annual trial and today's cap would be exceeded. */
    data class TrialCap(val cap: Int) : GateResult()
    /** User is a full-paid Pro subscriber and this-month cap would be exceeded. */
    data class MonthlyCap(val cap: Int) : GateResult()
}

object CreditsGate {
    /**
     * Check whether the current user is allowed to spend [cost] credits. Order:
     *   1) Balance must cover [cost].
     *   2) If pro && intro (annual free trial), enforce [Supa.TRIAL_DAILY_CAP] per day.
     *   3) If pro && not intro, enforce [Supa.SUB_MONTHLY_CAP] per calendar month.
     */
    suspend fun check(cost: Int): GateResult {
        // Server-granted Pro (Supabase profile.is_pro) bypasses balance and caps —
        // ops-controlled unlock for testers/comps without a purchase.
        if (RcBilling.hasServerPro()) return GateResult.Ok

        val balance = runCatching { Repo.credits() }.getOrDefault(0)
        if (balance < cost) return GateResult.InsufficientBalance(need = cost, have = balance)

        val inTrial = runCatching { RcBilling.isInTrial() }.getOrDefault(false)
        val isPro = RcBilling.isPro()
        if (inTrial) {
            val spentToday = runCatching { Repo.creditsSpentToday() }.getOrDefault(0)
            if (spentToday + cost > Supa.TRIAL_DAILY_CAP) {
                return GateResult.TrialCap(cap = Supa.TRIAL_DAILY_CAP)
            }
        } else if (isPro) {
            val spentMonth = runCatching { Repo.creditsSpentThisMonth() }.getOrDefault(0)
            if (spentMonth + cost > Supa.SUB_MONTHLY_CAP) {
                return GateResult.MonthlyCap(cap = Supa.SUB_MONTHLY_CAP)
            }
        }
        return GateResult.Ok
    }

    /** Convenience: post a user-facing toast for a non-OK gate result. Returns true if blocked. */
    fun explainAndBlock(result: GateResult): Boolean = when (result) {
        GateResult.Ok -> false
        is GateResult.InsufficientBalance -> {
            ToastBus.post("Not enough credits — ${result.need} needed.")
            true
        }
        is GateResult.TrialCap -> {
            ToastBus.post("Trial daily cap reached — cap resets tomorrow, or upgrade to a paid plan for full access.")
            true
        }
        is GateResult.MonthlyCap -> {
            ToastBus.post("Monthly cap reached — upgrade or wait for reset.")
            true
        }
    }
}
