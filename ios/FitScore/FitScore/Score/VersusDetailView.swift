import SwiftUI

/// A saved A vs B, reopened. The journal stores the whole comparison in
/// `signals.versus`, so this rebuilds the call as it was made: both frames,
/// both totals, the verdict, and the per-criterion reads that produced it.
///
/// `ScoreDetailView` hands off to this screen whenever a row's `kind` is
/// "versus" — a comparison shown as a single scored photo would be a lie about
/// what the user actually did.
struct VersusDetailView: View {
    let outfit: Outfit
    let summary: VersusSummary
    var onClose: () -> Void = {}

    @State private var aUrl: String?
    @State private var bUrl: String?
    @State private var loaded = false
    @State private var sharing: UIImage?

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()
            VStack(spacing: 0) {
                header
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                    .padding(.bottom, 4)
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        pair
                        if let call = outfit.hem_comment, !call.isEmpty {
                            verdictCard(call)
                        }
                        judgedForLine
                        if !summary.criteria.isEmpty { breakdown }
                        if let next = outfit.verdict, !next.isEmpty {
                            NoteRow(title: "For your next shot:", text: next)
                        }
                        Spacer(minLength: 30)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                }
            }
        }
        .sheet(item: shareItemBinding) { ActivityShareSheet(image: $0.image) }
        .task { await load() }
    }

    // MARK: - Header

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 8) {
                Eyebrow(text: "Hem picks")
                Text("The call")
                    .font(Serif.display(36))
                    .foregroundStyle(Palette.ink)
                Text(dateLine)
                    .font(Serif.body(14))
                    .foregroundStyle(Palette.muted)
            }
            Spacer(minLength: 12)
            Button(action: { Haptic.tap(); onClose() }) {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Palette.ink)
                    .frame(width: 40, height: 40)
                    .background(Palette.card)
                    .clipShape(Circle())
            }
        }
    }

    private var dateLine: String {
        guard let iso = outfit.created_at else { return "A past comparison." }
        let parser = ISO8601DateFormatter()
        parser.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = parser.date(from: iso) ?? ISO8601DateFormatter().date(from: iso)
        guard let date else { return "A past comparison." }
        let out = DateFormatter()
        out.dateFormat = "d MMMM"
        return "Judged \(out.string(from: date))"
    }

    // MARK: - The pair

    private var pair: some View {
        HStack(spacing: 12) {
            frame(url: aUrl, letter: "A", score: summary.totalA, isWinner: summary.winner == "A")
            frame(url: bUrl, letter: "B", score: summary.totalB, isWinner: summary.winner == "B")
        }
        .onLongPressGesture { share() }
    }

    private func frame(url: String?, letter: String, score: Int, isWinner: Bool) -> some View {
        let caption = summary.winner == "tie" ? "Tie" : (isWinner ? "Winner" : "Runner-up")
        return ZStack(alignment: .bottomLeading) {
            Color.clear
                .aspectRatio(0.72, contentMode: .fit)
                .overlay(Palette.card)
                .overlay(
                    Group {
                        if let s = url, let u = URL(string: s) {
                            AsyncImage(url: u) { $0.resizable().scaledToFill() } placeholder: {
                                if !loaded { ShimmerFill() }
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()
                )
                .overlay(LinearGradient(colors: [.clear, .black.opacity(0.55)], startPoint: .center, endPoint: .bottom))
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(isWinner ? Palette.bronze : Palette.hairline, lineWidth: isWinner ? 2 : 1)
                )
                .overlay(alignment: .topLeading) {
                    Text(letter)
                        .font(Serif.display(15))
                        .foregroundStyle(Palette.ink)
                        .frame(width: 28, height: 28)
                        .background(Palette.paper.opacity(0.9))
                        .clipShape(Circle())
                        .padding(10)
                }
            HStack(alignment: .bottom) {
                Text(caption.uppercased())
                    .font(.system(size: 10, weight: .semibold))
                    .tracking(2)
                    .foregroundStyle(.white)
                Spacer(minLength: 6)
                Text("\(score)")
                    .font(Serif.display(30))
                    .foregroundStyle(.white)
            }
            .padding(.horizontal, 12)
            .padding(.bottom, 10)
        }
    }

    // MARK: - Verdict + reads

    private func verdictCard(_ call: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(eyebrowText)
                .font(.system(size: 11, weight: .semibold))
                .tracking(2)
                .foregroundStyle(.white.opacity(0.6))
            HStack(alignment: .top, spacing: 12) {
                if summary.winner != "tie" {
                    Text(summary.winner)
                        .font(Serif.display(30))
                        .foregroundStyle(Palette.goldA)
                }
                Text(call)
                    .font(Serif.body(15))
                    .foregroundStyle(.white)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.ink)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var eyebrowText: String {
        if summary.winner == "tie" { return "Too close to call" }
        switch summary.confidence {
        case "medium": return "Winning shot · narrow"
        case "low":    return "Winning shot · close call"
        default:       return "Winning shot"
        }
    }

    private var judgedForLine: some View {
        let names = summary.criteria.isEmpty
            ? summary.focus.map { $0.prefix(1).uppercased() + $0.dropFirst() }
            : summary.criteria.map(\.title)
        let what = summary.intent?.isEmpty == false ? "your note" : summary.judgedFor
        return VStack(alignment: .leading, spacing: 8) {
            Text("JUDGED FOR \(what.uppercased()) · \(names.joined(separator: ", ").uppercased())")
                .font(.system(size: 11, weight: .semibold))
                .tracking(2)
                .foregroundStyle(Palette.muted)
            if let swing { Text(swing).font(Serif.body(14)).foregroundStyle(Palette.ink) }
        }
    }

    /// Which read opened the widest gap — the reason behind the totals.
    private var swing: String? {
        guard summary.criteria.count > 1,
              let top = summary.criteria.max(by: { abs($0.a - $0.b) < abs($1.a - $1.b) })
        else { return nil }
        let gap = abs(top.a - top.b)
        guard gap >= 4 else {
            return "No single read separates them — every criterion lands within a few points."
        }
        return "\(top.title) decided it — \(top.a > top.b ? "A" : "B") takes that read by \(gap) points."
    }

    private var breakdown: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("THE BREAKDOWN")
                .font(.system(size: 11, weight: .semibold))
                .tracking(2)
                .foregroundStyle(Palette.muted)
            ForEach(summary.criteria, id: \.key) { row in
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text(row.title)
                            .font(Serif.body(15, weight: .medium))
                            .foregroundStyle(Palette.ink)
                        Spacer()
                        Text("\(row.a) · \(row.b)")
                            .font(Serif.body(14))
                            .foregroundStyle(Palette.muted)
                            .monospacedDigit()
                    }
                    GeometryReader { geo in
                        let half = max(2, geo.size.width / 2 - 2)
                        HStack(spacing: 4) {
                            ZStack(alignment: .trailing) {
                                Capsule().fill(Palette.hairline).frame(height: 4)
                                Capsule().fill(Palette.ink)
                                    .frame(width: max(2, half * CGFloat(row.a) / 100), height: 4)
                            }
                            .frame(width: half)
                            ZStack(alignment: .leading) {
                                Capsule().fill(Palette.hairline).frame(height: 4)
                                Capsule().fill(Palette.bronze)
                                    .frame(width: max(2, half * CGFloat(row.b) / 100), height: 4)
                            }
                            .frame(width: half)
                        }
                    }
                    .frame(height: 4)
                    if !row.note_a.isEmpty || !row.note_b.isEmpty {
                        VStack(alignment: .leading, spacing: 4) {
                            if !row.note_a.isEmpty { reason("A", row.note_a, Palette.ink) }
                            if !row.note_b.isEmpty { reason("B", row.note_b, Palette.bronze) }
                        }
                        .padding(.top, 2)
                    }
                }
            }
            HStack(spacing: 18) {
                legend(Palette.ink, "A")
                legend(Palette.bronze, "B")
            }
        }
    }

    private func reason(_ letter: String, _ text: String, _ color: Color) -> some View {
        HStack(alignment: .top, spacing: 6) {
            Text(letter)
                .font(.system(size: 10, weight: .bold))
                .foregroundStyle(color)
                .frame(width: 10, alignment: .leading)
            Text(text)
                .font(Serif.body(13))
                .foregroundStyle(Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func legend(_ color: Color, _ label: String) -> some View {
        HStack(spacing: 7) {
            Circle().fill(color).frame(width: 9, height: 9)
            Text(label).font(Serif.body(13)).foregroundStyle(Palette.muted)
        }
    }

    // MARK: - Share

    @MainActor
    private func share() {
        Haptic.tap()
        let renderer = ImageRenderer(content: SavedVersusShareCard(summary: summary, call: outfit.hem_comment ?? "", aUrl: aUrl, bUrl: bUrl))
        renderer.scale = 2
        renderer.proposedSize = ProposedViewSize(width: 540, height: 720)
        if let img = renderer.uiImage { sharing = img }
    }

    private struct ShareItem: Identifiable { let id = UUID(); let image: UIImage }
    private var shareItemBinding: Binding<ShareItem?> {
        Binding(get: { sharing.map { ShareItem(image: $0) } }, set: { sharing = $0?.image })
    }

    // MARK: - Loading

    private func load() async {
        async let a = Repo.shared.signedOutfitUrl(summary.aPath)
        async let b = Repo.shared.signedOutfitUrl(summary.bPath)
        aUrl = (try? await a) ?? nil
        bUrl = (try? await b) ?? nil
        loaded = true
    }
}

private struct ShimmerFill: View {
    var body: some View { Palette.card }
}

private struct NoteRow: View {
    let title: String
    let text: String
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text(title).font(Serif.body(14, weight: .semibold)).foregroundStyle(Palette.ink)
            + Text(" \(text)").font(Serif.body(14)).foregroundStyle(Palette.muted)
        }
        .fixedSize(horizontal: false, vertical: true)
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

/// Share image for a saved call — the pair, the totals, the verdict.
private struct SavedVersusShareCard: View {
    let summary: VersusSummary
    let call: String
    let aUrl: String?
    let bUrl: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 6) {
                Text("HEM PICKS")
                    .font(.system(size: 11, weight: .semibold)).tracking(2)
                    .foregroundStyle(Palette.bronze)
                Text("The call").font(Serif.display(32)).foregroundStyle(Palette.ink)
            }
            HStack(spacing: 12) {
                shot(aUrl, "A", summary.totalA, summary.winner == "A")
                shot(bUrl, "B", summary.totalB, summary.winner == "B")
            }
            if !call.isEmpty {
                Text(call)
                    .font(Serif.body(14))
                    .foregroundStyle(.white)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Palette.ink)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            Spacer(minLength: 0)
            HStack(spacing: 6) {
                Text("\u{2726}").font(.system(size: 12)).foregroundStyle(Palette.bronze)
                Text("FITRATER").font(.system(size: 11, weight: .semibold)).tracking(3).foregroundStyle(Palette.ink)
            }
        }
        .padding(26)
        .frame(width: 540, height: 720)
        .background(Palette.paper)
    }

    private func shot(_ url: String?, _ letter: String, _ score: Int, _ isWinner: Bool) -> some View {
        ZStack(alignment: .bottomLeading) {
            Color.clear
                .aspectRatio(0.72, contentMode: .fit)
                .overlay(Palette.card)
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
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(isWinner ? Palette.bronze : Palette.hairline, lineWidth: isWinner ? 2 : 1)
                )
            HStack(alignment: .bottom) {
                Text(isWinner ? "WINNER" : letter)
                    .font(.system(size: 10, weight: .semibold)).tracking(2)
                    .foregroundStyle(.white)
                Spacer()
                Text("\(score)").font(Serif.display(26)).foregroundStyle(.white)
            }
            .padding(.horizontal, 10).padding(.bottom, 8)
        }
    }
}

private struct ActivityShareSheet: UIViewControllerRepresentable {
    let image: UIImage
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [image], applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}
