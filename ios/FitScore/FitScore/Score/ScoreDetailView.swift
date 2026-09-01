import SwiftUI

/// Full-screen score detail with hero photo + annotations overlay + subline +
/// pull-quote hem_comment + BREAKDOWN grid + SWAPS bulleted list. Ports
/// Android `ScoreDetailScreen`.
struct ScoreDetailView: View {
    let outfitId: String
    var onClose: () -> Void = {}

    @State private var outfit: Outfit?
    @State private var photoUrl: String?
    @State private var average: Double?
    @State private var loaded = false
    // Sprint 2 X-Ray + Fit Map viewer state
    @State private var markupAnnotations: [MarkupAnnotation] = []
    @State private var fitMap: FitMap?
    @State private var showXRay = false
    // Aug 2026: swipeable side view for Studio outfits (outfit_studio +
    // outfit_studio_side sibling).
    @State private var sideUrl: String? = nil
    @State private var heroPagerIndex: Int = 0
    @State private var reportTarget: ReportTarget? = nil

    var body: some View {
        GeometryReader { geo in
            let heroHeight = geo.size.height * 0.65
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    if !loaded {
                        skeleton(heroHeight: heroHeight)
                    } else if let o = outfit {
                        heroBlock(o: o, height: heroHeight)
                        contentBlock(o: o)
                    } else {
                        notFoundBlock
                    }
                }
                .padding(.bottom, 30)
            }
        }
        .background(Palette.paper.ignoresSafeArea())
        .task { await load() }
        .sheet(item: $reportTarget) { t in
            ReportContentSheet(target: t) { reportTarget = nil }
        }
        .fullScreenCover(isPresented: $showXRay) {
            if let url = photoUrl {
                ScoreAnnotationView(
                    photoUrl: url,
                    annotations: markupAnnotations,
                    fitMap: fitMap,
                    hemComment: outfit?.hem_comment ?? outfit?.notes ?? "",
                    onClose: { showXRay = false }
                )
            }
        }
    }

    @ViewBuilder
    private func heroBlock(o: Outfit, height: CGFloat) -> some View {
        let hasSide = sideUrl != nil
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 20)
                .fill(Palette.card)
                .frame(height: height)
                .overlay(
                    Group {
                        if hasSide {
                            // Studio outfit with a side view — swipeable pager.
                            TabView(selection: $heroPagerIndex) {
                                heroImage(url: photoUrl).tag(0)
                                heroImage(url: sideUrl).tag(1)
                            }
                            .tabViewStyle(.page(indexDisplayMode: .never))
                        } else if let s = photoUrl, let u = URL(string: s) {
                            AsyncImage(url: u) { $0.resizable().scaledToFill() } placeholder: { Color.clear }
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 20))
                )
                .overlay(
                    // Annotations overlay (only meaningful for scored fits).
                    AnnotationsOverlay(annotations: hasSide ? [] : (o.annotations ?? []))
                )

            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Palette.ink)
                    .frame(width: 32, height: 32)
                    .background(Palette.paper.opacity(0.9))
                    .clipShape(Circle())
            }
            .padding(12)
        }
        .overlay(alignment: .topTrailing) {
            if let s = o.score, s > 0 {
                ScoreChip(score: s).padding(12)
            }
        }
        .overlay(alignment: .bottom) {
            if hasSide {
                HStack(spacing: 8) {
                    pagerDot(active: heroPagerIndex == 0, label: "FRONT")
                    pagerDot(active: heroPagerIndex == 1, label: "SIDE")
                }
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(Palette.paper.opacity(0.9))
                .clipShape(Capsule())
                .padding(.bottom, 12)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 16)
    }

    private func heroImage(url: String?) -> some View {
        Group {
            if let s = url, let u = URL(string: s) {
                AsyncImage(url: u) { $0.resizable().scaledToFill() } placeholder: { Color.clear }
            } else {
                Color.clear
            }
        }
    }

    private func pagerDot(active: Bool, label: String) -> some View {
        HStack(spacing: 5) {
            Circle()
                .fill(active ? Palette.ink : Palette.hairline)
                .frame(width: 6, height: 6)
            Text(label)
                .font(.system(size: 9, weight: .semibold))
                .tracking(1.5)
                .foregroundStyle(active ? Palette.ink : Palette.muted)
        }
    }

    @ViewBuilder
    private func contentBlock(o: Outfit) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            let delta: Double? = {
                guard let a = average, let s = o.score else { return nil }
                return s - a
            }()
            let subline: String = {
                var parts: [String] = []
                if let d = delta {
                    if d > 0.05 { parts.append(String(format: "%.1f above your average", d)) }
                    else if d < -0.05 { parts.append(String(format: "%.1f below your average", abs(d))) }
                    else { parts.append("on your average") }
                }
                if let occ = o.occasion { parts.append(occ.uppercased()) }
                return parts.joined(separator: " · ")
            }()
            if !subline.isEmpty {
                Text(subline)
                    .font(.system(size: 12, weight: .medium))
                    .tracking(1)
                    .foregroundStyle(Palette.muted)
            }
            if let q = o.hem_comment ?? o.notes, !q.isEmpty {
                PullQuote(quote: q, attribution: nil)
            }
            if !markupAnnotations.isEmpty || fitMap != nil {
                Button(action: { Haptic.tap(); showXRay = true }) {
                    HStack(spacing: 8) {
                        Image(systemName: "eye")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(Palette.bronze)
                        Text("See the read")
                            .font(Serif.body(14, weight: .medium))
                            .foregroundStyle(Palette.ink)
                        Spacer()
                        Image(systemName: "arrow.right")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(Palette.bronze)
                    }
                    .padding(.horizontal, 14).padding(.vertical, 12)
                    .background(Palette.card)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.hairline, lineWidth: 1))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
            }
            if let sub = o.subscores {
                Eyebrow(text: "BREAKDOWN").padding(.top, 8)
                SubscoresView(subscores: sub)
            }
            if let swaps = o.swaps, !swaps.isEmpty {
                Eyebrow(text: "SWAPS").padding(.top, 8)
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(swaps, id: \.self) { s in
                        HStack(alignment: .top, spacing: 8) {
                            Text("•").font(Serif.body(15)).foregroundStyle(Palette.ink)
                            Text(s).font(Serif.body(15)).foregroundStyle(Palette.ink)
                        }
                    }
                }
            }
            // This read was written by AI — let the wearer flag it here.
            Button(action: { Haptic.tap(); reportTarget = ReportTarget(ReportKind.outfit, outfitId) }) {
                HStack(spacing: 6) {
                    Image(systemName: "flag").font(.system(size: 11))
                    Text("Report this critique").font(Serif.body(13))
                }
                .foregroundStyle(Palette.muted)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(.top, 12)
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
    }

    private var notFoundBlock: some View {
        VStack(alignment: .center, spacing: 8) {
            HStack {
                Button(action: onClose) {
                    Image(systemName: "xmark").frame(width: 28, height: 28).foregroundStyle(Palette.ink)
                }
                Spacer()
            }
            Spacer().frame(height: 40)
            Text("Outfit not found.").font(Serif.display(22)).foregroundStyle(Palette.ink)
            Text("It may have been deleted.").font(Serif.body(14)).foregroundStyle(Palette.muted)
        }
        .padding(20)
        .frame(maxWidth: .infinity)
    }

    private func skeleton(heroHeight: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            RoundedRectangle(cornerRadius: 20).fill(Palette.card).frame(height: heroHeight)
            ForEach(0..<4, id: \.self) { _ in
                SkeletonBar(height: 18)
            }
        }
        .padding(20)
    }

    private func load() async {
        outfit = try? await Repo.shared.outfitById(outfitId)
        if let p = outfit?.photo_path {
            photoUrl = try? await Repo.shared.signedOutfitUrl(p)
        }
        // If this is a Studio outfit, try to pick up the linked side view.
        if outfit?.kind == "outfit_studio" {
            if let side = try? await Repo.shared.linkedOutfit(linkedTo: outfitId, kind: "outfit_studio_side"),
               let sp = side.photo_path {
                sideUrl = try? await Repo.shared.signedOutfitUrl(sp)
            }
        }
        average = try? await Repo.shared.averageScore()
        // Sprint 2 — pull the editorial markup + fit map for the "See the read" viewer.
        markupAnnotations = (try? await Repo.shared.loadAnnotations(outfitId: outfitId)) ?? []
        fitMap = try? await Repo.shared.loadFitMap(outfitId: outfitId)
        loaded = true
        if outfit?.score != nil { Haptic.soft() }
    }
}

// MARK: - Annotations overlay

private func chipColor(_ score: Double) -> Color {
    switch score {
    case 8...: return Color(red: 0x3E/255, green: 0x8C/255, blue: 0x5E/255)
    case 6...: return Palette.goldA
    default:   return Palette.roastRed
    }
}

private struct Placed { let ann: Annotation; let ax: CGFloat; let ay: CGFloat; let left: Bool; var labelY: CGFloat }

private func layoutAnnotations(_ annotations: [Annotation], w: CGFloat, h: CGFloat) -> [Placed] {
    let minGap: CGFloat = 40
    var placed: [Placed] = annotations.map { a in
        let ax = CGFloat(max(0, min(100, a.x_pct)) / 100.0) * w
        let ay = CGFloat(max(0, min(100, a.y_pct)) / 100.0) * h
        return Placed(ann: a, ax: ax, ay: ay, left: a.x_pct < 50, labelY: ay)
    }
    for side in [true, false] {
        let idxs = placed.enumerated().filter { $0.element.left == side }
            .map { $0.offset }
            .sorted { placed[$0].labelY < placed[$1].labelY }
        var prev: CGFloat = -.greatestFiniteMagnitude
        for i in idxs {
            if placed[i].labelY < prev + minGap { placed[i].labelY = prev + minGap }
            prev = placed[i].labelY
        }
        for i in idxs {
            if placed[i].labelY < 12 { placed[i].labelY = 12 }
            if placed[i].labelY > h - 12 { placed[i].labelY = h - 12 }
        }
    }
    return placed
}

private struct AnnotationsOverlay: View {
    let annotations: [Annotation]

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            let placed = layoutAnnotations(annotations, w: w, h: h)
            ZStack {
                Canvas { ctx, size in
                    for p in placed {
                        let labelX: CGFloat = p.left ? 6 : (size.width - 6)
                        var path = Path()
                        path.move(to: CGPoint(x: p.ax, y: p.ay))
                        path.addLine(to: CGPoint(x: labelX, y: p.labelY))
                        ctx.stroke(path, with: .color(Color.black.opacity(0.35)), lineWidth: 2.4)
                        ctx.stroke(path, with: .color(Color.white.opacity(0.85)), lineWidth: 1.2)
                        let dot = Path(ellipseIn: CGRect(x: p.ax - 4, y: p.ay - 4, width: 8, height: 8))
                        ctx.fill(dot, with: .color(.white))
                        ctx.stroke(dot, with: .color(Palette.ink), lineWidth: 1.5)
                    }
                }
                ForEach(Array(placed.enumerated()), id: \.offset) { _, p in
                    let bg = chipColor(p.ann.score)
                    let s = Int(p.ann.score)
                    let text = "\(p.ann.label) \(s)"
                    HStack(spacing: 0) {
                        if !p.left { Spacer() }
                        Text(text)
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(bg)
                            .clipShape(Capsule())
                        if p.left { Spacer() }
                    }
                    .position(x: p.left ? 6 + textWidth(text) / 2 + 10 : w - 6 - textWidth(text) / 2 - 10,
                              y: p.labelY)
                }
            }
        }
    }

    // Rough sizing heuristic — good enough for centering the chip.
    private func textWidth(_ s: String) -> CGFloat { CGFloat(s.count) * 7.5 }
}
