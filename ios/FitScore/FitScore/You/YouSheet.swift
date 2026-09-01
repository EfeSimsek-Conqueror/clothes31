import SwiftUI
import Kingfisher
import PhotosUI

/// "You" tab content — profile / stats / honesty / links.
struct YouSheet: View {
    @EnvironmentObject var session: SessionStore
    @EnvironmentObject var toasts: ToastBus
    @ObservedObject private var credits = CreditsBus.shared

    @State private var profile: Profile?
    @State private var honesty = "honest"
    @State private var stats: Stats?
    @State private var latest: Outfit?
    @State private var avatarUrl: String?
    @State private var uploadingAvatar = false
    @State private var loaded = false
    @State private var pickerItem: PhotosPickerItem?

    @State private var showCredits = false
    @State private var showPaywall = false
    @State private var paywallContext: PaywallContext? = nil
    @State private var showStyleProfile = false
    @State private var showStyleDna = false
    @State private var showHelpPrivacy = false
    @State private var showBodyProfile = false

    @State private var goWeekly = false
    @State private var goAppearance = false
    @State private var goManagePro = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    LowCreditsBanner()
                    statsGrid
                    // honestySection removed Aug 2026 — single "honest" tone
                    // is now the only mode. Kept the helper below in case we
                    // reintroduce a toggle later.
                    quoteCard
                    rowsSection
                    signOutButton
                    Spacer().frame(height: 24)
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
            }
            .background(Palette.paper.ignoresSafeArea())
            .navigationDestination(isPresented: $goWeekly) {
                WeeklyLetterView(isPro: session.isPro) {
                    paywallContext = .letter
                    showPaywall = true
                }
            }
            .navigationDestination(isPresented: $goAppearance) { AppearanceView() }
            .navigationDestination(isPresented: $goManagePro) {
                ManageProView(onOpenPlans: { showCredits = true })
            }
            .sheet(isPresented: $showCredits) {
                CreditsSheet(onClose: { showCredits = false })
                    .presentationDetents([.large])
            }
            .sheet(isPresented: $showPaywall) {
                PaywallView(context: paywallContext)
            }
            .sheet(isPresented: $showStyleDna) {
                StyleDnaView(monthKey: currentMonthKey(), onClose: { showStyleDna = false })
            }
            .sheet(isPresented: $showStyleProfile) {
                StyleProfileSheet(onClose: { showStyleProfile = false })
                    .presentationDetents([.large])
            }
            .sheet(isPresented: $showBodyProfile) {
                if let bp = session.bodyProfile {
                    BodyProfileRevealView(
                        profile: bp,
                        onDone: { showBodyProfile = false },
                        onRecalibrate: {
                            session.bodyProfile = nil
                        }
                    )
                    .presentationDetents([.large])
                } else {
                    BodyCalibrationView(onFinished: { profile in
                        if profile != nil {
                            // stay in sheet so reveal renders
                        } else {
                            showBodyProfile = false
                        }
                    })
                    .presentationDetents([.large])
                }
            }
            .sheet(isPresented: $showHelpPrivacy) {
                HelpPrivacySheet(onClose: { showHelpPrivacy = false })
                    .presentationDetents([.large])
            }
            .refreshable { await load() }
            .task { await load() }
            .onChange(of: pickerItem) { _, newItem in
                Task { await uploadAvatar(newItem) }
            }
        }
    }

    // MARK: - Sections

    private var display: String {
        profile?.display_name ?? Repo.shared.userEmail.map { String($0.split(separator: "@").first ?? "You") } ?? "You"
    }
    private var email: String? { Repo.shared.userEmail }

    private var header: some View {
        HStack(alignment: .center, spacing: 12) {
            PhotosPicker(selection: $pickerItem, matching: .images) {
                ZStack {
                    Circle().fill(Palette.card)
                    if let s = avatarUrl, let u = URL(string: s) {
                        KFImage(u).resizable().scaledToFill()
                            .frame(width: 56, height: 56)
                            .clipShape(Circle())
                    } else {
                        Text(display.prefix(1).uppercased())
                            .font(Serif.display(24))
                            .foregroundStyle(Palette.ink)
                    }
                    if uploadingAvatar {
                        Circle().fill(Color.black.opacity(0.3))
                        Text("…").foregroundStyle(.white)
                    }
                }
                .frame(width: 56, height: 56)
                .overlay(Circle().stroke(Palette.hairline, lineWidth: 1))
            }
            .disabled(uploadingAvatar)
            VStack(alignment: .leading, spacing: 2) {
                Text(display).font(Serif.display(24)).foregroundStyle(Palette.ink)
                if let email {
                    Text(email).font(Serif.body(13)).foregroundStyle(Palette.muted)
                }
            }
            Spacer()
            if profile?.is_pro == true {
                Text("PRO").font(.system(size: 10, weight: .semibold)).tracking(2)
                    .foregroundStyle(Palette.bronze)
                    .padding(.horizontal, 10).padding(.vertical, 4)
                    .overlay(Capsule().stroke(Palette.bronze, lineWidth: 1))
                    .background(Palette.card)
                    .clipShape(Capsule())
            }
        }
    }

    private var statsGrid: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                StatCard(value: "\(stats?.pieces ?? 0)", label: "pieces", loading: !loaded)
                StatCard(value: "\(stats?.looks ?? 0)", label: "looks", loading: !loaded)
                StatCard(value: stats?.bestScore.map { String(format: "%.1f", $0) } ?? "–", label: "best score", loading: !loaded)
            }
            HStack(spacing: 8) {
                StatCard(value: "\(stats?.creditsUsed ?? 0)", label: "credits used", loading: !loaded)
                StatCard(value: "\(stats?.streakDays ?? 0)d", label: "streak", loading: !loaded)
                let d = stats?.monthDelta
                let dLabel: String = {
                    guard let d else { return "–" }
                    return d >= 0 ? "+\(String(format: "%.1f", d))" : String(format: "%.1f", d)
                }()
                StatCard(value: dLabel, label: "month rank", loading: !loaded)
            }
        }
    }

    private var honestySection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Eyebrow(text: "HOW HONEST SHOULD HEM BE?")
            HStack(spacing: 0) {
                ForEach(["kind","honest","brutal"], id: \.self) { opt in
                    let sel = honesty == opt
                    let locked = opt == "brutal" && !session.isPro
                    ZStack(alignment: .topTrailing) {
                        Text(opt.uppercased())
                            .font(.system(size: 11, weight: .semibold)).tracking(2)
                            .foregroundStyle(locked ? Palette.ink.opacity(0.5) : (sel ? .white : Palette.ink))
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                            .background(sel && !locked ? Palette.ink : Color.clear)
                        if locked {
                            Image(systemName: "lock.fill")
                                .font(.system(size: 10)).foregroundStyle(Palette.bronze)
                                .padding(4)
                        }
                    }
                    .contentShape(Rectangle())
                    .onTapGesture {
                        if locked {
                            Haptic.chip()
                            paywallContext = .brutal
                            showPaywall = true
                        } else {
                            Haptic.chip()
                            honesty = opt
                            AppScope.launch { try? await Repo.shared.updateHonesty(opt) }
                        }
                    }
                }
            }
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.hairline, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
    }

    @ViewBuilder
    private var quoteCard: some View {
        if let q = latest?.hem_comment, !q.isEmpty {
            HStack(alignment: .top, spacing: 0) {
                Rectangle().fill(Palette.bronze).frame(width: 3)
                VStack(alignment: .leading, spacing: 4) {
                    Text("\u{201C}\(q)\u{201D}").font(Serif.italic(18)).foregroundStyle(Palette.ink)
                    Text("— Hem").font(Serif.italic(14)).foregroundStyle(Palette.muted)
                }
                .padding(16)
            }
            .background(Palette.card)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.hairline, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    private var rowsSection: some View {
        VStack(spacing: 0) {
            Hairline()
            row("Buy credits") { showCredits = true }
            Hairline()
            row("Weekly letter") {
                if let ctx = FeatureGates.requireLetter() {
                    paywallContext = ctx
                    showPaywall = true
                } else {
                    goWeekly = true
                }
            }
            Hairline()
            row("Style profile") { showStyleProfile = true }
            Hairline()
            row(session.bodyProfile == nil ? "Body profile · not set" : "Body profile") {
                showBodyProfile = true
            }
            Hairline()
            row("Appearance") { goAppearance = true }
            Hairline()
            row("Manage Fitrater Pro") { goManagePro = true }
            Hairline()
            row("Help & privacy") { showHelpPrivacy = true }
            Hairline()
            rateRow
            Hairline()
        }
    }

    private func currentMonthKey() -> String {
        let d = Date()
        let f = DateFormatter(); f.dateFormat = "yyyy-MM"; f.timeZone = .init(identifier: "UTC")
        return f.string(from: d)
    }

    private func row(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(title).font(Serif.body(16, weight: .medium)).foregroundStyle(Palette.ink)
                Spacer()
                Image(systemName: "chevron.right").font(.system(size: 12)).foregroundStyle(Palette.muted)
            }
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    /// A tap opens the App Store review form. Deliberately NOT
    /// `SKStoreReviewController` — Apple reserves that for moments the app
    /// chooses, never for a button, and the system throttle means a tapped
    /// request usually does nothing at all. Nothing is granted, promised, or
    /// gated on this row: the rating is a favour, not a transaction.
    private var rateRow: some View {
        Button {
            Haptic.chip()
            openWriteReview()
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Rate on App Store")
                        .font(Serif.body(16, weight: .medium))
                        .foregroundStyle(Palette.ink)
                    Text("Opens the App Store.")
                        .font(Serif.body(13)).foregroundStyle(Palette.muted)
                }
                Spacer()
                Image(systemName: "chevron.right").font(.system(size: 12)).foregroundStyle(Palette.muted)
            }
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func openWriteReview() {
        // App Store app first; the https form is the fallback. `canOpenURL`
        // is no help here — itms-apps is not a declared query scheme — so we
        // let `open` tell us whether it landed.
        let store = URL(string: "itms-apps://apps.apple.com/app/id\(Self.appStoreId)?action=write-review")!
        let web = URL(string: "https://apps.apple.com/app/id\(Self.appStoreId)?action=write-review")!
        UIApplication.shared.open(store) { opened in
            if !opened { UIApplication.shared.open(web) }
        }
    }

    /// App Store Connect app id for com.fitrater.app.
    private static let appStoreId = "6794940458"

    private var signOutButton: some View {
        Button {
            AppScope.launch {
                try? await Supa.client.auth.signOut()
            }
        } label: {
            Text("Sign out")
                .font(Serif.display(18, weight: .medium))
                .foregroundStyle(Palette.roastRed)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
        }
        .buttonStyle(.plain)
    }

    // MARK: - Loading + avatar

    private func load() async {
        await CreditsBus.shared.refresh()
        profile = try? await Repo.shared.currentProfile()
        honesty = profile?.honesty ?? "honest"
        avatarUrl = profile?.avatar_url
        stats = try? await Repo.shared.stats()
        latest = try? await Repo.shared.outfits(limit: 1).first
        loaded = true
    }

    private func uploadAvatar(_ item: PhotosPickerItem?) async {
        guard let item else { return }
        uploadingAvatar = true
        defer { uploadingAvatar = false }
        do {
            guard let data = try await item.loadTransferable(type: Data.self) else { return }
            let url = try await Repo.shared.uploadAvatar(data)
            try await Repo.shared.updateAvatarUrl(url)
            avatarUrl = "\(url)?ts=\(Int(Date().timeIntervalSince1970))"
            toasts.post("Avatar updated")
        } catch {
            toasts.post("Couldn't upload avatar: \(error.localizedDescription)")
        }
    }
}

private struct StatCard: View {
    let value: String
    let label: String
    let loading: Bool
    var body: some View {
        VStack(spacing: 4) {
            if loading {
                SkeletonBar(width: 40, height: 22)
                SkeletonBar(width: 60, height: 10)
            } else {
                Text(value).font(Serif.display(22, weight: .semibold)).foregroundStyle(Palette.ink)
                Text(label).font(.system(size: 11)).foregroundStyle(Palette.muted)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14).padding(.horizontal, 8)
        .background(Palette.card)
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.hairline, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
