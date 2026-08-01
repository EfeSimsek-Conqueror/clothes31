import Foundation

/// Result of a credits pre-check. Ports Android `GateResult`.
enum GateResult {
    case ok
    case insufficientBalance(need: Int, have: Int)
    case trialCapReached(cap: Int)
    case monthlyCapReached(cap: Int)
}

/// Pre-flight check before spending credits on any AI action. Mirrors the
/// Android `CreditsGate` object 1:1 — same ordering, same messages.
///
/// Order:
///   1) Balance must cover `cost`.
///   2) If Pro AND in trial (INTRO period), enforce `Supa.trialDailyCap` per day.
///   3) If Pro AND not in trial, enforce `Supa.subMonthlyCap` (or
///      `subAnnualMonthlyCap` if the active product is annual) per month.
@MainActor
enum CreditsGate {
    static func check(_ cost: Int) async -> GateResult {
        // Server-granted Pro (Supabase profile.is_pro) bypasses balance and caps —
        // ops-controlled unlock for comp accounts / testers.
        if RcBilling.shared.hasServerPro { return .ok }

        let balance = (try? await Repo.shared.credits()) ?? 0
        if balance < cost { return .insufficientBalance(need: cost, have: balance) }

        let rc = RcBilling.shared
        if rc.isTrial {
            let spentToday = (try? await Repo.shared.creditsSpentToday()) ?? 0
            if spentToday + cost > Supa.trialDailyCap {
                return .trialCapReached(cap: Supa.trialDailyCap)
            }
        } else if rc.isPro {
            // Annual subs get a lower monthly allowance (~250/mo across the
            // year); monthly subs get the full 400. Detect via the product id
            // suffix. Fall back to the monthly cap when unknown.
            let productId = rc.activeProductId ?? ""
            let cap: Int = {
                let id = productId.lowercased()
                if id.contains("annual") || id.contains("year") { return Supa.subAnnualMonthlyCap }
                return Supa.subMonthlyCap
            }()
            let spentMonth = (try? await Repo.shared.creditsSpentThisMonth()) ?? 0
            if spentMonth + cost > cap {
                return .monthlyCapReached(cap: cap)
            }
        }
        return .ok
    }

    /// Post a toast for a non-OK result. Returns true if blocked (caller should
    /// stop). Matches Android's `explainAndBlock` semantics.
    @discardableResult
    static func explainAndBlock(_ result: GateResult) -> Bool {
        switch result {
        case .ok:
            return false
        case .insufficientBalance(let need, _):
            ToastBus.shared.post("Not enough credits — \(need) needed.")
            return true
        case .trialCapReached:
            ToastBus.shared.post("Trial daily cap reached — resets tomorrow, or upgrade for full access.")
            return true
        case .monthlyCapReached:
            ToastBus.shared.post("Monthly cap reached — upgrade or wait for reset.")
            return true
        }
    }
}
