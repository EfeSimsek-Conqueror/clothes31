import Foundation
import RevenueCat

/// Thin wrapper over the RevenueCat Purchases SDK. Ports Android
/// `data/billing/RcBilling.kt` — same entitlement id ("pro"), same trial
/// detection (INTRO period type), same publish semantics.
///
/// Configure once from `SessionStore.handleAuthChange`, passing the current
/// Supabase user id when a session exists (or nil for anonymous). Reconfigure
/// on sign-in / sign-out via `logIn` / `logOut`.
///
/// All UI (CreditsSheet, FeatureGates, YouSheet PRO badge) observes the
/// `@Published` fields on the shared singleton.
@MainActor
final class RcBilling: ObservableObject {
    static let shared = RcBilling()

    static let entitlementPro = "fitscore_pro"

    @Published var offerings: Offerings?
    @Published var customerInfo: CustomerInfo?
    @Published var isPro: Bool = false
    @Published var isTrial: Bool = false
    @Published var trialEndDate: Date?
    @Published var activeProductId: String?

    /// Server-granted Pro flag from Supabase `profile.is_pro`. Lets ops grant
    /// Pro without a purchase (comp accounts, testers). ORs into `isPro`.
    private var serverPro: Bool = false
    var hasServerPro: Bool { serverPro }

    private var configured = false
    private var streamTask: Task<Void, Never>?
    private var currentUserId: String?

    private init() {}

    /// Idempotent configure. Safe to call repeatedly — on user-id change we
    /// call `logIn` / `logOut` to keep RC's app-user-id in sync with Supabase.
    func configure(userId: String?) {
        if !configured {
            Purchases.logLevel = .info
            if let userId, !userId.isEmpty {
                Purchases.configure(withAPIKey: Supa.revenueCatIOSKey, appUserID: userId)
            } else {
                Purchases.configure(withAPIKey: Supa.revenueCatIOSKey)
            }
            configured = true
            currentUserId = userId
            startStream()
            Task { await refresh() }
            return
        }
        // Already configured — reconcile the app-user-id.
        if userId != currentUserId {
            Task { await switchUser(to: userId) }
        }
    }

    private func switchUser(to userId: String?) async {
        currentUserId = userId
        if let userId, !userId.isEmpty {
            _ = try? await Purchases.shared.logIn(userId)
        } else {
            _ = try? await Purchases.shared.logOut()
        }
        await refresh()
    }

    private func startStream() {
        streamTask?.cancel()
        streamTask = Task { [weak self] in
            guard let self else { return }
            for await info in Purchases.shared.customerInfoStream {
                await MainActor.run { self.publish(info) }
            }
        }
    }

    /// Refresh offerings + customer info. Safe to call repeatedly (e.g. from
    /// CreditsSheet on appear).
    func refresh() async {
        guard configured else { return }
        if let offerings = try? await Purchases.shared.offerings() {
            self.offerings = offerings
        }
        if let info = try? await Purchases.shared.customerInfo() {
            publish(info)
        }
    }

    private func publish(_ info: CustomerInfo) {
        customerInfo = info
        let ent = info.entitlements[Self.entitlementPro]
        let rcActive = ent?.isActive == true
        isPro = rcActive || serverPro
        // RC exposes periodType on the entitlement; INTRO == in the free trial.
        if let ent, ent.isActive {
            isTrial = ent.periodType == .intro
            trialEndDate = ent.expirationDate
            activeProductId = ent.productIdentifier
        } else {
            isTrial = false
            trialEndDate = nil
            activeProductId = nil
        }
    }

    /// Called after Supabase profile loads. `true` unlocks all Pro-gated
    /// features (mirrors Android `RcBilling.setServerPro`).
    func setServerPro(_ value: Bool) {
        serverPro = value
        let rcActive = customerInfo?.entitlements[Self.entitlementPro]?.isActive == true
        isPro = rcActive || serverPro
    }

    /// Kick off a purchase for a package. Throws on cancel / failure — caller
    /// should surface a toast via the returned `PurchaseResultData`.
    func purchase(package: Package) async throws -> PurchaseResultData {
        let result = try await Purchases.shared.purchase(package: package)
        publish(result.customerInfo)
        return result
    }

    func restore() async throws -> CustomerInfo {
        let info = try await Purchases.shared.restorePurchases()
        publish(info)
        return info
    }

    /// Grant credits for a pack SKU after a successful purchase. Idempotent —
    /// we store the transaction id in `reference_id` on `credit_transactions`
    /// and skip the insert if a row with the same reference already exists.
    /// Mirrors Android `grantCreditsForSku`.
    func grantCreditsIfNeeded(productId: String, transactionId: String) async {
        // Match the SKU against our client-side grant table by exact id.
        // (contains-match is unsafe — `_credits_500` is a substring of
        // `_credits_5000`, which would grant the wrong amount.)
        let credits: Int? = Supa.creditPackGrants[productId]
        guard let credits, credits > 0 else { return }
        guard let uid = Repo.shared.userId else { return }
        // Idempotency: check for an existing row with this reference_id.
        struct IdRow: Decodable { let id: String? }
        let existing: [IdRow] = (try? await Supa.client
            .from("credit_transactions")
            .select("id")
            .eq("user_id", value: uid)
            .eq("reference_id", value: transactionId)
            .limit(1)
            .execute()
            .value) ?? []
        if !existing.isEmpty { return }
        // Compute new balance and insert. `addCredits` doesn't accept a
        // reference — we insert directly here so the idempotency key is set.
        let current = (try? await Repo.shared.credits()) ?? 0
        let next = current + credits
        let insert = CreditTxInsert(
            user_id: uid,
            amount: credits,
            kind: "iap_\(productId)",
            balance_after: next,
            reference_id: transactionId
        )
        _ = try? await Supa.client
            .from("credit_transactions")
            .insert(insert)
            .execute()
        CreditsBus.shared.refreshAsync()
    }
}
