import SwiftUI
import RevenueCat
import StoreKit

/// Full-screen paywall sheet — credits packs + Pro plans.
struct CreditsSheet: View {
    let onClose: (() -> Void)?
    var paywallContext: String? = nil

    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var creditsBus = CreditsBus.shared
    @ObservedObject private var billing = RcBilling.shared
    @EnvironmentObject var toasts: ToastBus

    @State private var loading = true
    @State private var processing: String? = nil
    @State private var restoring = false

    private var offering: Offering? { billing.offerings?.current }
    private var isPro: Bool { billing.isPro }

    init(onClose: (() -> Void)? = nil, paywallContext: String? = nil) {
        self.onClose = onClose
        self.paywallContext = paywallContext
    }

    private func closeSheet() {
        if let onClose { onClose() } else { dismiss() }
    }

    private var balance: Int { creditsBus.balance ?? 0 }

    private static let packSpecs: [(id: String, name: String, credits: Int, popular: Bool)] = [
        ("fitrater_v2_credits_100", "Starter", 150, false),
        ("fitrater_v2_credits_500", "Popular", 500, true),
        ("fitrater_v2_credits_1500", "Pro Pack", 1200, false),
        ("fitrater_v2_credits_5000", "Mega", 3000, false),
    ]

    private let termsURL = URL(string: "https://fitrater.ai/terms")!
    private let privacyURL = URL(string: "https://fitrater.ai/privacy")!

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                heroIfNeeded
                balanceCard
                packsSection
                planSection
                if loading {
                    Text("Loading store…")
                        .font(.system(size: 12)).foregroundStyle(Palette.muted)
                }
                restoreLink
                legalLinks
                Spacer().frame(height: 24)
            }
            .padding(20)
        }
        .background(Palette.paper.ignoresSafeArea())
        .task { await load() }
    }

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                Eyebrow(text: "CREDITS")
                Text("Top up your closet.").font(Serif.display(30)).foregroundStyle(Palette.ink)
            }
            Spacer()
            Button(action: closeSheet) {
                Text("×").font(Serif.display(28)).foregroundStyle(Palette.ink)
            }
        }
    }

    @ViewBuilder
    private var heroIfNeeded: some View {
        let (title, body): (String?, String?) = {
            switch paywallContext {
            case "tryon": return ("Try-on is Pro.", "Wear any Studio piece on your own photo. Unlimited on Pro.")
            case "brutal": return (nil, nil)  // Brutal mode removed Aug 2026 — case kept for enum stability, no hero shown.
            case "letter": return ("The Sunday Letter is Pro.", "A short Sunday letter — what you wore, what worked, what to try.")
            default: return (nil, nil)
            }
        }()
        if let title, let body {
            HStack(alignment: .top, spacing: 12) {
                Text("✦").font(Serif.display(22, weight: .semibold)).foregroundStyle(Palette.bronze)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(Serif.display(20)).foregroundStyle(Palette.ink)
                    Text(body).font(Serif.body(14)).foregroundStyle(Palette.muted)
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Palette.card)
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.bronze.opacity(0.5), lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 14))
        }
    }

    private var balanceCard: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle().fill(Palette.bronze.opacity(0.15))
                Image(systemName: "star.fill").foregroundStyle(Palette.bronze)
            }.frame(width: 48, height: 48)
            VStack(alignment: .leading, spacing: 2) {
                Text("Current balance").font(.system(size: 12)).foregroundStyle(Palette.muted)
                Text("\(balance) credits").font(Serif.display(24, weight: .semibold)).foregroundStyle(Palette.ink)
            }
            Spacer()
            if isPro {
                Text("PRO").font(.system(size: 10, weight: .semibold)).tracking(2)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 10).padding(.vertical, 4)
                    .background(Palette.bronze)
                    .clipShape(Capsule())
            }
        }
        .padding(16)
        .background(Palette.card)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.bronze.opacity(0.5), lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private var packsSection: some View {
        VStack(spacing: 12) {
            ForEach(Self.packSpecs, id: \.id) { spec in
                let pkg = packagesById[spec.id]
                let price = pkg?.storeProduct.localizedPriceString ?? "—"
                PackCard(
                    name: spec.name, credits: spec.credits, popular: spec.popular, price: price,
                    busy: processing == spec.id,
                    enabled: processing == nil && pkg != nil,
                    comingSoon: false
                ) {
                    if let pkg {
                        Task { await purchase(pkg, grantId: spec.id, credits: spec.credits) }
                    } else {
                        Task { await retryOffering(); toasts.post("Store still syncing — try again in a minute.") }
                    }
                }
            }
        }
    }

    private var planSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Eyebrow(text: "PRO INSTEAD")
            Text("Get monthly refill credits + Pro perks.")
                .font(Serif.body(14)).foregroundStyle(Palette.muted)
            HStack(spacing: 8) {
                PlanTile(label: "Monthly", subline: "1,200 credits / month", trial: introPhrase(for: monthlyPackage),
                         price: monthlyPrice, popular: true,
                         busy: processing == "monthly", enabled: processing == nil && monthlyPackage != nil, comingSoon: false) {
                    if let pkg = monthlyPackage { Task { await purchase(pkg, grantId: "monthly", credits: 0) } }
                    else { Task { await retryOffering(); toasts.post("Store still syncing — try again in a minute.") } }
                }
                PlanTile(label: "Annual", subline: "750 credits / mo", trial: introPhrase(for: annualPackage),
                         price: annualPrice, popular: false,
                         busy: processing == "annual", enabled: processing == nil && annualPackage != nil, comingSoon: false) {
                    if let pkg = annualPackage { Task { await purchase(pkg, grantId: "annual", credits: 0) } }
                    else { Task { await retryOffering(); toasts.post("Store still syncing — try again in a minute.") } }
                }
            }
            if let text = renewalDisclosure {
                Text(text)
                    .font(.system(size: 11))
                    .foregroundStyle(Palette.muted)
                    .multilineTextAlignment(.leading)
                    .padding(.top, 4)
            }
        }
    }

    private var renewalDisclosure: String? {
        // Only show when at least one plan is available (Apple requires it near the price/CTA).
        guard monthlyPackage != nil || annualPackage != nil else { return nil }
        var lines: [String] = []
        // Monthly first — it is the emphasized default tile.
        if let m = monthlyPackage { lines.append(disclosureLine("Monthly", m)) }
        if let a = annualPackage { lines.append(disclosureLine("Annual", a)) }
        lines.append("Auto-renews until cancelled. Cancel anytime in Settings → Apple ID → Subscriptions.")
        return lines.joined(separator: " ")
    }

    /// One plan's terms, every word of it read off the real StoreKit product —
    /// intro offer included, or silently omitted when the product has none.
    private func disclosureLine(_ label: String, _ pkg: Package) -> String {
        let price = pkg.storeProduct.localizedPriceString
        let cadence = renewalNoun(pkg.storeProduct.subscriptionPeriod)
        let tail = cadence.map { "\(price) \($0)" } ?? price
        if let intro = introPhrase(for: pkg) {
            return "\(label): \(intro), then \(tail)."
        }
        return "\(label): \(tail)."
    }

    // MARK: - Intro-offer copy (never hardcoded — always the product's own offer)

    /// The product's introductory offer in words, or nil when there is no offer.
    /// Derived from `introductoryDiscount` so the claim can never drift from
    /// what App Store Connect actually charges.
    private func introPhrase(for pkg: Package?) -> String? {
        guard let offer = pkg?.storeProduct.introductoryDiscount else { return nil }
        let units = offer.subscriptionPeriod.value
        let one = unitNoun(offer.subscriptionPeriod.unit, plural: false)
        switch offer.paymentMode {
        case .freeTrial:
            return "\(units)-\(one) free trial"
        case .payUpFront:
            let total = units * max(1, offer.numberOfPeriods)
            return "\(total) \(unitNoun(offer.subscriptionPeriod.unit, plural: total != 1)) for \(offer.localizedPriceString)"
        case .payAsYouGo:
            let count = max(1, offer.numberOfPeriods)
            return "\(offer.localizedPriceString) per \(one) for \(count) \(unitNoun(offer.subscriptionPeriod.unit, plural: count != 1))"
        @unknown default:
            return nil
        }
    }

    /// "per month" / "every 3 months" — the renewal cadence in words.
    private func renewalNoun(_ period: RevenueCat.SubscriptionPeriod?) -> String? {
        guard let period else { return nil }
        if period.value == 1 { return "per \(unitNoun(period.unit, plural: false))" }
        return "every \(period.value) \(unitNoun(period.unit, plural: true))"
    }

    private func unitNoun(_ unit: RevenueCat.SubscriptionPeriod.Unit, plural: Bool) -> String {
        let noun: String
        switch unit {
        case .day: noun = "day"
        case .week: noun = "week"
        case .month: noun = "month"
        case .year: noun = "year"
        @unknown default: noun = "period"
        }
        return plural ? noun + "s" : noun
    }

    private var legalLinks: some View {
        HStack(spacing: 20) {
            Spacer()
            Link("Terms of Use", destination: termsURL)
                .font(.system(size: 12))
                .underline()
                .foregroundStyle(Palette.muted)
            Link("Privacy Policy", destination: privacyURL)
                .font(.system(size: 12))
                .underline()
                .foregroundStyle(Palette.muted)
            Spacer()
        }
        .padding(.top, 8)
    }

    // MARK: - RC helpers

    private var packagesById: [String: Package] {
        guard let offering else { return [:] }
        var m: [String: Package] = [:]
        for p in offering.availablePackages {
            for spec in Self.packSpecs where p.storeProduct.productIdentifier == spec.id {
                m[spec.id] = p
            }
        }
        return m
    }
    private var monthlyPackage: Package? { offering?.monthly }
    private var annualPackage: Package? { offering?.annual }
    private var monthlyPrice: String { monthlyPackage?.storeProduct.localizedPriceString ?? "—" }
    private var annualPrice: String { annualPackage?.storeProduct.localizedPriceString ?? "—" }

    private func load() async {
        await CreditsBus.shared.refresh()
        await billing.refresh()
        loading = false
    }

    private func retryOffering() async {
        await billing.refresh()
    }

    private func purchase(_ pkg: Package, grantId: String, credits: Int) async {
        processing = grantId
        defer { processing = nil }
        do {
            let result = try await billing.purchase(package: pkg)
            if result.userCancelled { return }
            Haptic.tap()
            if credits > 0 {
                // Grant server-side credits, keyed by the store transaction id
                // for idempotency (mirrors Android grantCreditsForSku).
                let txId = result.transaction?.transactionIdentifier
                    ?? pkg.storeProduct.productIdentifier
                await billing.grantCreditsIfNeeded(
                    productId: pkg.storeProduct.productIdentifier,
                    transactionId: txId
                )
                toasts.post("Added \(credits) credits.")
            } else {
                toasts.post("You're Pro — welcome.")
            }
            try? await Repo.shared.markPaywallShown()
            closeSheet()
        } catch {
            // A tap on Cancel is not a failure — say nothing. Anything else
            // the shopper needs to hear about, or the sheet just sits there.
            if Self.isCancellation(error) { return }
            toasts.post("Purchase failed — \(error.localizedDescription)")
        }
    }

    /// True when the shopper dismissed the App Store sheet themselves.
    private static func isCancellation(_ error: Error) -> Bool {
        let ns = error as NSError
        if ns.domain == ErrorCode.errorDomain,
           ns.code == ErrorCode.purchaseCancelledError.rawValue { return true }
        if ns.domain == SKErrorDomain,
           ns.code == SKError.Code.paymentCancelled.rawValue { return true }
        return false
    }

    private var restoreLink: some View {
        HStack {
            Spacer()
            Button(action: {
                Haptic.chip()
                Task { await restore() }
            }) {
                Text(restoring ? "Restoring…" : "Restore purchases")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Palette.muted)
                    .underline()
            }
            .buttonStyle(.plain)
            .disabled(restoring)
            Spacer()
        }
        .padding(.top, 8)
    }

    private func restore() async {
        restoring = true
        defer { restoring = false }
        do {
            let info = try await billing.restore()
            if info.entitlements.active.keys.contains(RcBilling.entitlementPro) {
                toasts.post("Purchases restored.")
            } else {
                toasts.post("No purchases to restore.")
            }
        } catch {
            toasts.post("Couldn't restore — try again.")
        }
    }
}

private struct PackCard: View {
    let name: String
    let credits: Int
    let popular: Bool
    let price: String
    let busy: Bool
    let enabled: Bool
    var comingSoon: Bool = false
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 8) {
                        Text(name).font(Serif.body(17, weight: .semibold)).foregroundStyle(Palette.ink)
                        if popular {
                            Text("BEST VALUE").font(.system(size: 10, weight: .semibold)).tracking(1.5)
                                .foregroundStyle(.white)
                                .padding(.horizontal, 8).padding(.vertical, 3)
                                .background(Palette.bronze)
                                .clipShape(Capsule())
                        }
                        if comingSoon {
                            Text("SOON").font(.system(size: 10, weight: .semibold)).tracking(1.5)
                                .foregroundStyle(Palette.muted)
                                .padding(.horizontal, 8).padding(.vertical, 3)
                                .overlay(Capsule().stroke(Palette.muted.opacity(0.4), lineWidth: 1))
                        }
                    }
                    Text("\(credits) credits").font(Serif.body(14)).foregroundStyle(Palette.muted)
                }
                Spacer()
                Text(busy ? "Processing…" : price)
                    .font(Serif.body(16, weight: .semibold))
                    .foregroundStyle(comingSoon ? Palette.muted : Palette.ink)
            }
            .padding(16)
            .background(Palette.card)
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(popular ? Palette.bronze : Palette.hairline, lineWidth: popular ? 2 : 1))
            .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

private struct PlanTile: View {
    let label: String
    let subline: String
    /// Intro-offer terms for this exact product, or nil when it has none.
    var trial: String? = nil
    let price: String
    let popular: Bool
    let busy: Bool
    let enabled: Bool
    var comingSoon: Bool = false
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Text(label).font(Serif.body(15, weight: .semibold)).foregroundStyle(Palette.ink)
                Text(subline).font(.system(size: 12)).foregroundStyle(Palette.muted)
                if let trial {
                    Text(trial)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(Palette.bronze)
                        .multilineTextAlignment(.center)
                }
                Text(busy ? "Processing…" : price).font(Serif.body(14, weight: .semibold)).foregroundStyle(Palette.ink)
            }
            .frame(maxWidth: .infinity)
            .padding(14)
            .background(Palette.card)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(popular ? Palette.bronze : Palette.hairline, lineWidth: popular ? 2 : 1))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}
