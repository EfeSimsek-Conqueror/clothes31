import SwiftUI

/// Hero card at the top of Home — renders the latest outfit or piece with a
/// score chip (when scored) or a kind pill (try-on / roast / decode / studio).
/// Tap opens `ScoreDetailView` for outfits or `PieceDetailSheet` for pieces.
struct LatestCard: View {
    let activity: LatestActivity
    let averageScore: Double?
    var onOpenOutfit: (String) -> Void = { _ in }
    var onOpenPiece: (ClosetItem, String?) -> Void = { _, _ in }

    var body: some View {
        switch activity {
        case .outfit(let outfit, let url):
            outfitCard(outfit: outfit, url: url)
        case .piece(let piece, let url):
            pieceCard(piece: piece, url: url)
        }
    }

    @ViewBuilder
    private func outfitCard(outfit: Outfit, url: String?) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Eyebrow(text: "LATEST")
            heroBox(url: url) {
                if let s = outfit.displayScore, isScoredKind(outfit.kind), s > 0 {
                    ScoreChip(score: s).padding(14)
                } else if let k = outfit.kind {
                    OverlayKindPill(label: kindLabel(k)).padding(14)
                }
            }
            .onTapGesture {
                Haptic.tap()
                if let id = outfit.id { onOpenOutfit(id) }
                else { ToastBus.shared.post("This look isn't ready yet") }
            }
            let subline = sublineForOutfit(outfit, avg: averageScore)
            if !subline.isEmpty {
                Text(subline)
                    .font(.system(size: 12, weight: .medium))
                    .tracking(1)
                    .foregroundStyle(Palette.muted)
            }
            let q = outfit.hem_comment ?? outfit.notes
            if let q, !q.isEmpty {
                PullQuote(quote: q, attribution: nil).padding(.top, 6)
            }
        }
    }

    @ViewBuilder
    private func pieceCard(piece: ClosetItem, url: String?) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Eyebrow(text: "LATEST")
            heroBox(url: url) {
                OverlayKindPill(label: "STUDIO").padding(14)
            }
            .onTapGesture {
                Haptic.tap()
                onOpenPiece(piece, url)
            }
            let cat = (piece.category ?? "piece").capitalized
            let ago = relativeTime(piece.created_at)
            let sub = [cat, ago].filter { !$0.isEmpty }.joined(separator: " · ").uppercased()
            if !sub.isEmpty {
                Text(sub)
                    .font(.system(size: 12, weight: .medium))
                    .tracking(1)
                    .foregroundStyle(Palette.muted)
            }
            if let name = piece.name, !name.isEmpty {
                Text("\u{201C}\(name)\u{201D}")
                    .font(Serif.italic(19))
                    .foregroundStyle(Palette.ink)
                    .padding(.top, 4)
            }
        }
    }

    @ViewBuilder
    private func heroBox<Overlay: View>(url: String?, @ViewBuilder overlay: () -> Overlay) -> some View {
        ZStack(alignment: .topTrailing) {
            RoundedRectangle(cornerRadius: 20)
                .fill(Palette.card)
                .aspectRatio(0.82, contentMode: .fit)
                .overlay(
                    Group {
                        if let s = url, let u = URL(string: s) {
                            AsyncImage(url: u) { img in
                                img.resizable().scaledToFill()
                            } placeholder: {
                                Color.clear
                            }
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 20))
                )
            overlay()
        }
    }
}

/// Bronze-outlined pill overlaid on the hero corner for non-scored outfits.
struct OverlayKindPill: View {
    let label: String
    var body: some View {
        Text(label)
            .font(.system(size: 10, weight: .semibold))
            .tracking(1.5)
            .foregroundStyle(Palette.bronze)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(Palette.paper.opacity(0.92))
            .overlay(Capsule().stroke(Palette.bronze.opacity(0.7), lineWidth: 1))
            .clipShape(Capsule())
    }
}

// MARK: - helpers (mirror Android)

func isScoredKind(_ kind: String?) -> Bool {
    kind == nil || kind == "score" || kind == "user_scan"
}

func kindLabel(_ kind: String) -> String {
    switch kind {
    case "tryon": return "TRY-ON"
    case "roast": return "ROAST"
    case "decode": return "DECODED"
    case "studio_gen": return "STUDIO"
    default: return kind.uppercased()
    }
}

func sublineForOutfit(_ o: Outfit, avg: Double?) -> String {
    switch o.kind {
    case "tryon":
        let piece = (o.hem_comment ?? "").replacingOccurrences(of: "Try-on: ", with: "").trimmingCharacters(in: .whitespaces)
        return piece.isEmpty ? "TRY-ON" : "TRY-ON · \(piece.uppercased())"
    case "roast":
        let occ = o.occasion?.uppercased() ?? ""
        return occ.isEmpty ? "ROASTED" : "ROASTED · \(occ)"
    case "decode":
        return "DECODED"
    case "studio_gen":
        return "STUDIO"
    default:
        var parts: [String] = []
        if let a = avg, let s = o.displayScore {
            let delta = s - a
            if delta > 0.05 { parts.append(String(format: "%.1f above your average", delta)) }
            else if delta < -0.05 { parts.append(String(format: "%.1f below your average", abs(delta))) }
            else { parts.append("on your average") }
        }
        if let occ = o.occasion, !occ.isEmpty { parts.append(occ.uppercased()) }
        return parts.joined(separator: " · ")
    }
}

func relativeTime(_ iso: String?) -> String {
    guard let iso, !iso.isEmpty else { return "" }
    let f1 = ISO8601DateFormatter()
    f1.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    let f2 = ISO8601DateFormatter()
    let d = f1.date(from: iso) ?? f2.date(from: iso)
    guard let d else { return "" }
    let mins = Int(Date().timeIntervalSince(d) / 60)
    switch mins {
    case ..<1: return "just now"
    case ..<60: return "\(mins) min ago"
    case ..<(60 * 24):
        let h = mins / 60
        return h == 1 ? "1 hour ago" : "\(h) hours ago"
    case ..<(60 * 24 * 7):
        let d2 = mins / (60 * 24)
        return d2 == 1 ? "yesterday" : "\(d2) days ago"
    default:
        let w = mins / (60 * 24 * 7)
        return w == 1 ? "1 week ago" : "\(w) weeks ago"
    }
}
