import SwiftUI

/// Top-of-funnel home feed. Loads profile, latest activity, hem note,
/// recent outfits, sunday letter, and closet count in parallel — then composes
/// the header + banners + LATEST card + Sunday letter +
/// recents strip + best/journal 2-up row. Ports Android `HomeScreen`.
struct HomeView: View {
    @EnvironmentObject var session: SessionStore
    @ObservedObject private var credits = CreditsBus.shared
    // Observed, not just read: the RevenueCat entitlement usually lands after the
    // first render, and an unobserved read would leave the Pro badge missing
    // until something else happened to redraw the header.
    @ObservedObject private var billing = RcBilling.shared

    @State private var latestAct: LatestActivity?
    @State private var averageScore: Double?
    @State private var bestOutfit: Outfit?
    @State private var displayName: String?
    @State private var email: String?
    @State private var hemNoteBody: String?
    @State private var loaded = false
    @State private var recentOutfits: [Outfit] = []
    @State private var recentUrls: [String: String] = [:]
    @State private var sundayLetter: SundayLetter?
    @State private var firstRunDone = true
    @State private var closetCount = 0
    @State private var lastWeekOutfits: [Outfit] = []
    @State private var lastVersus: Outfit?
    @State private var versusUrls: (a: String?, b: String?) = (nil, nil)

    // Presentation
    @State private var openedOutfitId: String?
    @State private var openedPiece: ClosetItem?
    @State private var openedPieceUrl: String?
    @State private var showScoreSheet = false
    @State private var showCredits = false
    @State private var showPaywall = false
    @State private var paywallContext: PaywallContext? = nil
    @State private var showCameraMenu = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                header
                if !loaded {
                    homeSkeleton
                } else {
                    content
                }
                Spacer(minLength: 30)
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
        }
        .background(Palette.paper.ignoresSafeArea())
        .refreshable { await load() }
        .task { await load() }
        .fullScreenCover(item: openedOutfitBinding) { wrapped in
            ScoreDetailView(outfitId: wrapped.id, onClose: { openedOutfitId = nil })
        }
        .sheet(isPresented: pieceBinding) {
            if let p = openedPiece {
                PieceDetailSheet(
                    item: p,
                    imageUrl: openedPieceUrl,
                    onEdit: { _ in openedPiece = nil; openedPieceUrl = nil },
                    onDismiss: { openedPiece = nil; openedPieceUrl = nil }
                )
            }
        }
        .fullScreenCover(isPresented: $showScoreSheet) {
            ScoreSheetView(
                onClose: { showScoreSheet = false },
                onScored: { id in
                    showScoreSheet = false
                    openedOutfitId = id
                },
                onOpenPaywall: { showPaywall = true }
            )
        }
        .sheet(isPresented: $showCredits) { CreditsSheet() }
        .sheet(isPresented: $showPaywall) { PaywallView(context: paywallContext) }
        .sheet(isPresented: $showCameraMenu) {
            CameraMenuSheet()
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
    }

    // MARK: - Subviews

    private var greetingName: String {
        if let n = displayName?.split(separator: " ").first { return String(n) }
        if let e = email?.split(separator: "@").first { return String(e) }
        return ""
    }

    private var isFirstRun: Bool {
        loaded && !firstRunDone && recentOutfits.isEmpty && closetCount == 0
    }

    private var header: some View {
        HomeHeader(
            name: greetingName,
            // Weather removed Sep 2026 — see Util/Weather.swift. `nil` here is
            // permanent; HomeHeader should drop the chip entirely.
            initial: String(greetingName.first.map(String.init)?.uppercased() ?? "•"),
            credits: credits.balance,
            isPro: session.isPro || billing.isPro,
            onOpenCredits: { showCredits = true }
        )
    }

    @ViewBuilder
    private var content: some View {
        LowCreditsBanner(onOpenCredits: { showCredits = true })

        // Stylist chat invite — persistent top-of-feed entry point.
        StylistHomeInviteBar(onTap: { CameraMenuBus.shared.request(.chat) })

        if let body = hemNoteBody, !body.isEmpty, !isFirstRun {
            HemMorningCard(body_: body)
        }

        if isFirstRun {
            FirstRunBanner(
                onOpenCamera: { showCameraMenu = true },
                onOpenStudio: {}
            )
        } else {
            latestSection
            if let v = lastVersus, let summary = v.versusSummary {
                HomeVersusCard(
                    outfit: v,
                    summary: summary,
                    aUrl: versusUrls.a,
                    bUrl: versusUrls.b,
                    onOpen: { if let id = v.id { openedOutfitId = id } }
                )
            }
            PrimaryButton(title: "✦ Score a look", action: {
                Haptic.tap()
                showScoreSheet = true
            })
            HomeWeeklyWrappedCard(lastWeekOutfits: lastWeekOutfits)
            SundayLetterPreviewCard(
                letter: sundayLetter,
                isPro: session.isPro,
                onOpenPaywall: {
                    paywallContext = .letter
                    showPaywall = true
                }
            )
            if !recentOutfits.isEmpty { recentStrip }
            twoUpRow
        }
    }

    @ViewBuilder
    private var latestSection: some View {
        if let act = latestAct {
            LatestCard(
                activity: act,
                averageScore: averageScore,
                onOpenOutfit: { id in openedOutfitId = id },
                onOpenPiece: { p, url in
                    openedPiece = p
                    openedPieceUrl = url
                }
            )
        } else {
            emptyLatest
        }
    }

    private var recentStrip: some View {
        VStack(alignment: .leading, spacing: 10) {
            Eyebrow(text: "RECENT")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(recentOutfits, id: \.id) { o in
                        recentTile(o)
                    }
                }
            }
        }
    }

    private var homeSkeleton: some View {
        VStack(alignment: .leading, spacing: 16) {
            RoundedRectangle(cornerRadius: 14).fill(Palette.card).frame(height: 90)
            SkeletonBar(width: 120, height: 10)
            RoundedRectangle(cornerRadius: 20).fill(Palette.card).aspectRatio(0.82, contentMode: .fit)
            RoundedRectangle(cornerRadius: 4).fill(Palette.card).frame(height: 56)
        }
    }

    private var emptyLatest: some View {
        TanCard {
            VStack(alignment: .leading, spacing: 6) {
                Text("You haven't scored a look yet.")
                    .font(Serif.display(20))
                    .foregroundStyle(Palette.ink)
                Text("Tap the camera below and let Hem take the first read.")
                    .font(Serif.body(14))
                    .foregroundStyle(Palette.muted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var twoUpRow: some View {
        let best = bestOutfit
        let bestText: String = {
            if let s = best?.score { return String(format: "%.1f · view", s) }
            return "No fits yet"
        }()
        return HStack(alignment: .top, spacing: 12) {
            Button(action: {
                Haptic.chip()
                if let id = best?.id { openedOutfitId = id }
            }) {
                VStack(alignment: .leading, spacing: 6) {
                    Eyebrow(text: "YOUR BEST FIT")
                    Text("\(bestText) →")
                        .font(Serif.body(15))
                        .foregroundStyle(Palette.ink)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            Rectangle().fill(Palette.hairline).frame(width: 1, height: 40)
            Button(action: { Haptic.chip() }) {
                VStack(alignment: .leading, spacing: 6) {
                    Eyebrow(text: "THIS WEEK")
                    Text("Open journal →")
                        .font(Serif.body(15))
                        .foregroundStyle(Palette.ink)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(.vertical, 8)
    }

    @ViewBuilder
    private func recentTile(_ o: Outfit) -> some View {
        let url = o.id.flatMap { recentUrls[$0] }
        Button(action: {
            Haptic.chip()
            if let id = o.id { openedOutfitId = id }
        }) {
            ZStack {
                RoundedRectangle(cornerRadius: 6).fill(Palette.card)
                if let s = url, let u = URL(string: s) {
                    AsyncImage(url: u) { $0.resizable().scaledToFill() } placeholder: { Color.clear }
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                }
            }
            .frame(width: 48, height: 60)
            .overlay(RoundedRectangle(cornerRadius: 6).stroke(Palette.hairline, lineWidth: 1))
        }
    }

    // MARK: - Bindings

    private var openedOutfitBinding: Binding<IdentifiableString?> {
        Binding(
            get: { openedOutfitId.map { IdentifiableString(id: $0) } },
            set: { openedOutfitId = $0?.id }
        )
    }

    private var pieceBinding: Binding<Bool> {
        Binding(
            get: { openedPiece != nil },
            set: { if !$0 { openedPiece = nil; openedPieceUrl = nil } }
        )
    }

    // MARK: - Loading

    @MainActor
    private func load() async {
        let profile = try? await Repo.shared.currentProfile()
        displayName = profile?.display_name
        email = Repo.shared.userEmail
        firstRunDone = profile?.first_run_done ?? false

        latestAct = try? await Repo.shared.latestActivity()
        averageScore = try? await Repo.shared.averageScore()
        bestOutfit = try? await Repo.shared.bestOutfit()
        hemNoteBody = (try? await Repo.shared.latestHemNote())?.body

        let recents = (try? await Repo.shared.outfits(limit: 5)) ?? []
        recentOutfits = recents

        var urls: [String: String] = [:]
        for o in recents {
            if let id = o.id, let path = o.photo_path {
                if let signed = try? await Repo.shared.signedOutfitUrl(path) {
                    urls[id] = signed
                }
            }
        }
        recentUrls = urls
        lastVersus = try? await Repo.shared.latestVersus()
        if let summary = lastVersus?.versusSummary {
            async let a = Repo.shared.signedOutfitUrl(summary.aPath)
            async let b = Repo.shared.signedOutfitUrl(summary.bPath)
            versusUrls = ((try? await a) ?? nil, (try? await b) ?? nil)
        } else {
            versusUrls = (nil, nil)
        }
        sundayLetter = try? await Repo.shared.latestSundayLetter()
        closetCount = (try? await Repo.shared.closetItemCount()) ?? 0

        // Last-week outfits for wrapped card.
        let cal = Calendar.current
        let start = startOfWeek(cal)
        if let lastMon = cal.date(byAdding: .day, value: -7, to: start),
           let endLastSun = cal.date(byAdding: .second, value: -1, to: start) {
            let f = ISO8601DateFormatter()
            f.formatOptions = [.withInternetDateTime]
            let s = f.string(from: lastMon)
            let e = f.string(from: endLastSun)
            lastWeekOutfits = (try? await Repo.shared.outfitsInWindow(startIso: s, endIso: e, limit: 10)) ?? []
        }

        if !firstRunDone && (!recents.isEmpty || closetCount > 0) {
            try? await Repo.shared.markFirstRunDone()
            firstRunDone = true
        }

        loaded = true
    }

    private func startOfWeek(_ cal: Calendar) -> Date {
        var c = cal
        c.firstWeekday = 2
        let comps = c.dateComponents([.yearForWeekOfYear, .weekOfYear], from: Date())
        return c.date(from: comps) ?? Date()
    }
}

/// Wrap a String so it can drive an item-based sheet/cover binding.
/// Dashboard echo of the last A vs B — the pair as it was judged, the two
/// totals, and the call. Tapping opens the winner's journal entry.
private struct HomeVersusCard: View {
    let outfit: Outfit
    let summary: VersusSummary
    let aUrl: String?
    let bUrl: String?
    var onOpen: () -> Void

    var body: some View {
        Button(action: { Haptic.chip(); onOpen() }) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .firstTextBaseline) {
                    Eyebrow(text: "Last call")
                    Spacer()
                    Text(judgedFor.uppercased())
                        .font(.system(size: 10, weight: .semibold))
                        .tracking(1.5)
                        .foregroundStyle(Palette.muted)
                        .lineLimit(1)
                }
                HStack(spacing: 10) {
                    shot(url: aUrl, letter: "A", score: summary.totalA, isWinner: summary.winner == "A")
                    shot(url: bUrl, letter: "B", score: summary.totalB, isWinner: summary.winner == "B")
                }
                if let call = outfit.hem_comment, !call.isEmpty {
                    Text(call)
                        .font(Serif.body(14))
                        .foregroundStyle(Palette.ink)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Palette.card)
            .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
    }

    private var judgedFor: String {
        let what = summary.intent?.isEmpty == false ? "your note" : summary.occasion
        return what.isEmpty ? "A vs B" : "Judged for \(what)"
    }

    private func shot(url: String?, letter: String, score: Int, isWinner: Bool) -> some View {
        ZStack(alignment: .bottomLeading) {
            Color.clear
                .aspectRatio(1.15, contentMode: .fit)
                .overlay(Palette.paper)
                .overlay(
                    Group {
                        if let s = url, let u = URL(string: s) {
                            AsyncImage(url: u) { $0.resizable().scaledToFill() } placeholder: { Color.clear }
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()
                )
                .overlay(LinearGradient(colors: [.clear, .black.opacity(0.5)], startPoint: .center, endPoint: .bottom))
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(isWinner ? Palette.bronze : Palette.hairline, lineWidth: isWinner ? 2 : 1)
                )
            HStack(alignment: .bottom) {
                Text(isWinner ? "WINNER" : letter)
                    .font(.system(size: 9, weight: .semibold))
                    .tracking(1.5)
                    .foregroundStyle(.white)
                Spacer(minLength: 4)
                Text("\(score)")
                    .font(Serif.display(20))
                    .foregroundStyle(.white)
            }
            .padding(.horizontal, 8)
            .padding(.bottom, 6)
        }
    }
}

private struct IdentifiableString: Identifiable, Hashable { let id: String }
