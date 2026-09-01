import SwiftUI
import RevenueCat

struct ManageProView: View {
    let onOpenPlans: () -> Void

    @EnvironmentObject var toasts: ToastBus

    @State private var loaded = false
    @State private var info: CustomerInfo?
    @State private var spentThisMonth = 0
    @State private var restoring = false

    /// One entitlement id, owned by `RcBilling` — reading a different string
    /// here is how paying members ended up staring at the upsell.
    private var proEnt: EntitlementInfo? { info?.entitlements.all[RcBilling.entitlementPro] }
    private var isPro: Bool { proEnt?.isActive == true }

    var body: some View {
        SubpageScaffold(
            eyebrow: isPro ? "MEMBERSHIP" : "FITRATER PRO",
            title: isPro ? "You're Pro" : "Fitrater Pro"
        ) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if !loaded {
                        Text("Loading membership…").font(Serif.body(14)).foregroundStyle(Palette.muted)
                    } else if !isPro {
                        NotProContent(onOpenPlans: onOpenPlans)
                    } else if let ent = proEnt {
                        ProContent(ent: ent, spentThisMonth: spentThisMonth)
                    }
                    if loaded { restoreRow }
                    Spacer().frame(height: 40)
                }
                .padding(20)
            }
        }
        .task { await load() }
    }

    private func load() async {
        if Purchases.isConfigured {
            info = try? await Purchases.shared.customerInfo()
        }
        spentThisMonth = (try? await Repo.shared.creditsSpentThisMonth()) ?? 0
        loaded = true
    }

    /// Second home for Restore — the paywall has one, and Apple looks for it
    /// in Settings too. Same RcBilling path, same wording as the paywall.
    private var restoreRow: some View {
        Button {
            Haptic.chip()
            Task { await restore() }
        } label: {
            Text(restoring ? "Restoring…" : "Restore purchases")
                .font(Serif.body(15, weight: .medium)).foregroundStyle(Palette.ink)
                .frame(maxWidth: .infinity).padding(.vertical, 14)
                .overlay(Capsule().stroke(Palette.ink.opacity(0.55), lineWidth: 1))
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
        .disabled(restoring)
        .padding(.top, 8)
    }

    private func restore() async {
        restoring = true
        defer { restoring = false }
        do {
            let restored = try await RcBilling.shared.restore()
            info = restored
            if restored.entitlements.active.keys.contains(RcBilling.entitlementPro) {
                toasts.post("Purchases restored.")
            } else {
                toasts.post("No purchases to restore.")
            }
        } catch {
            toasts.post("Couldn't restore — try again.")
        }
    }
}

private struct NotProContent: View {
    let onOpenPlans: () -> Void
    private let benefits = [
        "i.  Unlimited scoring",
        "ii.  Studio without cap",
        "iii.  Try-on priority",
        "iv.  Monthly Sunday letters",
        "v.  Full Hem memory",
    ]
    var body: some View {
        Text("Unlimited scoring, deeper Studio, Hem's full memory.")
            .font(Serif.body(15)).foregroundStyle(Palette.ink)
        Spacer().frame(height: 20)
        Eyebrow(text: "WHAT YOU GET")
        VStack(alignment: .leading, spacing: 6) {
            ForEach(benefits, id: \.self) { line in
                Text(line).font(Serif.italic(17)).foregroundStyle(Palette.ink)
            }
        }
        Spacer().frame(height: 20)
        PrimaryButton(title: "See plans", action: onOpenPlans)
    }
}

private struct ProContent: View {
    let ent: EntitlementInfo
    let spentThisMonth: Int

    private var cap: Int {
        ent.productIdentifier.lowercased().contains("annual") ? Supa.subAnnualMonthlyCap : Supa.subMonthlyCap
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            if ent.periodType == .intro {
                trialBanner
            }
            Eyebrow(text: "PLAN")
            VStack(spacing: 0) {
                KVRow(k: "Product", v: ent.productIdentifier)
                Hairline()
                KVRow(k: "Renews", v: ent.willRenew ? formatDate(ent.expirationDate) : "Cancels \(formatDate(ent.expirationDate))")
                Hairline()
                KVRow(k: "Auto-renew", v: ent.willRenew ? "Yes" : "No")
            }
            .padding(16)
            .background(Palette.card)
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.hairline, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 14))

            Eyebrow(text: "USAGE THIS MONTH")
            let pct = min(1.0, max(0.0, Double(spentThisMonth) / Double(cap)))
            HStack {
                Text("\(spentThisMonth) / \(cap) credits")
                    .font(Serif.body(15, weight: .semibold)).foregroundStyle(Palette.ink)
                Spacer()
                Text("\(Int(pct * 100))%")
                    .font(Serif.body(13)).foregroundStyle(Palette.muted)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Palette.hairline).frame(height: 6)
                    Capsule().fill(Palette.bronze).frame(width: geo.size.width * pct, height: 6)
                }
            }
            .frame(height: 6)

            Button {
                if let url = URL(string: "https://apps.apple.com/account/subscriptions") {
                    UIApplication.shared.open(url)
                }
            } label: {
                Text("Manage subscription")
                    .font(Serif.body(15, weight: .medium)).foregroundStyle(Palette.ink)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .overlay(Capsule().stroke(Palette.ink.opacity(0.55), lineWidth: 1))
                    .clipShape(Capsule())
            }
            .buttonStyle(.plain)
        }
    }

    private var trialBanner: some View {
        let end = ent.expirationDate.map { formatDate($0) } ?? "soon"
        let days = ent.expirationDate.map { daysUntil($0) } ?? 0
        return VStack(alignment: .leading, spacing: 4) {
            Text("TRIAL ENDS \(end.uppercased())")
                .font(.system(size: 10, weight: .semibold)).tracking(2).foregroundStyle(Palette.bronze)
            Text("\(days) days remaining · \(Supa.trialDailyCap) credits/day")
                .font(Serif.body(14)).foregroundStyle(Palette.ink)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.bronze.opacity(0.15))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.bronze, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

private struct KVRow: View {
    let k: String
    let v: String
    var body: some View {
        HStack {
            Text(k).font(Serif.body(14)).foregroundStyle(Palette.muted)
            Spacer()
            Text(v).font(Serif.body(14, weight: .semibold)).foregroundStyle(Palette.ink)
        }
        .padding(.vertical, 8)
    }
}

private func formatDate(_ d: Date?) -> String {
    guard let d else { return "—" }
    let f = DateFormatter(); f.dateFormat = "MMM d, yyyy"; f.locale = Locale(identifier: "en_US")
    return f.string(from: d)
}
private func daysUntil(_ d: Date) -> Int {
    max(0, Int(d.timeIntervalSinceNow / 86400))
}
