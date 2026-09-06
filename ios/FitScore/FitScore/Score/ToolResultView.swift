import SwiftUI
import Kingfisher

/// A saved run of one of the camera tools, reopened as what it actually was.
///
/// Every tool writes an `outfits` row, and until now every row opened as a
/// score sheet — so a try-on showed a rating instead of the garment on you, and
/// a decode showed a photo with nothing read off it. This screen leads with the
/// tool's own output and lets the score be a footnote where a score even
/// applies.
///
/// `ScoreDetailView` routes here by `kind`; plain scored looks still open as the
/// full score sheet, which is the right screen for them.
struct ToolResultView: View {
    let outfit: Outfit
    var onClose: () -> Void = {}

    @State private var heroUrl: String?
    @State private var pieceItem: ClosetItem?
    @State private var pieceUrl: String?
    @State private var sideUrl: String?
    /// The photograph this try-on was composited onto, when the row kept one.
    @State private var beforeUrl: String?
    /// 0 shows the result whole; dragging right reveals the original beneath.
    @State private var wipe: CGFloat = 0
    @State private var heroWidth: CGFloat = 1
    @State private var loaded = false

    private var kind: String { outfit.kind ?? "" }

    /// A row saved before `before_photo_path` existed simply renders without the
    /// comparison rather than pretending to have one.
    private var hasBefore: Bool { beforeUrl != nil }

    /// Kinds whose stored row is a tool output rather than a scored look.
    /// A vs B is deliberately absent — it has its own two-frame screen.
    static func handles(_ kind: String?) -> Bool {
        ["tryon", "decode", "studio_gen", "outfit_studio", "magazine_cover"].contains(kind ?? "")
    }

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()
            VStack(spacing: 0) {
                header
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                    .padding(.bottom, 6)
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        hero
                        if kind == "tryon", let piece = pieceItem {
                            triedOnRow(piece)
                        }
                        if let decode = decodeRead { decodeBlock(decode) }
                        if let cover = coverRead, let quote = cover.pullQuote, !quote.isEmpty {
                            PullQuote(quote: quote, attribution: nil)
                        }
                        if let call = outfit.hem_comment, !call.isEmpty, call != title,
                           kind != "magazine_cover" {
                            PullQuote(quote: call, attribution: "— Hem")
                        }
                        if let next = outfit.verdict, !next.isEmpty, next != outfit.hem_comment {
                            noteCard(next)
                        }
                        footerFacts
                        Spacer(minLength: 30)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                }
            }
        }
        .task { await load() }
    }

    // MARK: - Header

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 8) {
                Eyebrow(text: eyebrow)
                Text(title)
                    .font(Serif.display(34))
                    .foregroundStyle(Palette.ink)
                Text(subtitle)
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

    private var eyebrow: String {
        switch kind {
        case "tryon":                       return "Try-on"
        case "decode":                      return "Decoded"
        case "studio_gen":                  return "Studio"
        case "outfit_studio", "outfit_studio_side": return "Studio outfit"
        case "magazine_cover":              return "Cover"
        default:                            return "Hem"
        }
    }

    private var title: String {
        switch kind {
        case "tryon":          return "On you"
        case "decode":         return "The read"
        case "studio_gen":     return "The piece"
        case "outfit_studio":  return "The outfit"
        case "magazine_cover": return coverRead?.headline ?? "The cover"
        default:               return "The look"
        }
    }

    private var subtitle: String {
        switch kind {
        case "tryon":          return "How the piece actually sits, and what Hem makes of it."
        case "decode":         return "Every piece Hem could name in the shot."
        case "studio_gen":     return "Generated in the studio\(dateLine.isEmpty ? "" : " · \(dateLine)")"
        case "outfit_studio":  return "Built in the studio\(dateLine.isEmpty ? "" : " · \(dateLine)")"
        case "magazine_cover": return coverRead.map { c in
                                    [c.volume.map { "Vol. \($0)" }, dateLine.isEmpty ? nil : dateLine]
                                        .compactMap { $0 }.joined(separator: " · ")
                                } ?? dateLine
        default:               return dateLine
        }
    }

    private var dateLine: String {
        guard let iso = outfit.created_at else { return "" }
        let parser = ISO8601DateFormatter()
        parser.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let date = parser.date(from: iso) ?? ISO8601DateFormatter().date(from: iso) else { return "" }
        let out = DateFormatter()
        out.dateFormat = "d MMMM"
        return out.string(from: date)
    }

    // MARK: - Hero

    private var hero: some View {
        ZStack(alignment: .topTrailing) {
            Color.clear
                .aspectRatio(3.0 / 4.0, contentMode: .fit)
                .overlay(Palette.card)
                .overlay(
                    ZStack {
                        // The original sits underneath and the result is masked
                        // back from the left, so dragging wipes between them in
                        // place. Two images in one frame rather than side by
                        // side: the whole point is that the pose, the light and
                        // the room are identical and only the clothes moved.
                        if let b = beforeUrl, let bu = URL(string: b) {
                            KFImage(bu)
                                .placeholder { Rectangle().fill(Palette.muted.opacity(0.12)) }
                                .resizable()
                                .scaledToFit()
                        }
                        if let s = heroUrl, let u = URL(string: s) {
                            KFImage(u)
                                .placeholder { Rectangle().fill(Palette.muted.opacity(0.12)) }
                                .resizable()
                                .scaledToFit()
                                .mask(alignment: .trailing) {
                                    Rectangle().padding(.leading, max(0, wipe) * heroWidth)
                                }
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()
                )
                .clipShape(RoundedRectangle(cornerRadius: 6))
                .overlay(alignment: .leading) {
                    if hasBefore, wipe > 0.02 {
                        Rectangle()
                            .fill(Color.white.opacity(0.9))
                            .frame(width: 1)
                            .offset(x: wipe * heroWidth)
                            .allowsHitTesting(false)
                    }
                }
                .overlay(alignment: .bottomLeading) {
                    if hasBefore {
                        Text(wipe > 0.02 ? "BEFORE" : "DRAG TO COMPARE")
                            .font(.system(size: 10, weight: .semibold))
                            .tracking(1.6)
                            .foregroundStyle(.white)
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .background(Color.black.opacity(0.45))
                            .clipShape(Capsule())
                            .padding(12)
                    }
                }
                .background(
                    GeometryReader { geo in
                        Color.clear
                            .onAppear { heroWidth = max(geo.size.width, 1) }
                            .onChange(of: geo.size.width) { _, w in heroWidth = max(w, 1) }
                    }
                )
                // .simultaneousGesture, not .highPriorityGesture: a high-priority
                // drag would claim the whole gesture and kill the enclosing
                // scroll, so a vertical swipe on the photo would do nothing.
                .simultaneousGesture(
                    hasBefore
                        ? DragGesture(minimumDistance: 12)
                            .onChanged { v in
                                guard abs(v.translation.width) > abs(v.translation.height) else { return }
                                wipe = min(max(v.location.x / heroWidth, 0), 1)
                            }
                            .onEnded { _ in
                                guard wipe > 0 else { return }
                                withAnimation(.easeOut(duration: 0.22)) { wipe = 0 }
                            }
                        : nil
                )
            if let s = outfit.score, s > 0 {
                Text(String(format: "%.1f", s))
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 8).padding(.vertical, 4)
                    .background(Palette.bronze)
                    .clipShape(Capsule())
                    .padding(12)
            }
        }
    }

    // MARK: - Try-on: what was put on

    private func triedOnRow(_ piece: ClosetItem) -> some View {
        HStack(spacing: 12) {
            Color.clear
                .frame(width: 56, height: 72)
                .overlay(Palette.paper)
                .overlay(
                    Group {
                        if let s = pieceUrl ?? piece.image_url, let u = URL(string: s) {
                            KFImage(u).resizable().scaledToFill()
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()
                )
                .clipShape(RoundedRectangle(cornerRadius: 4))
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(Palette.hairline, lineWidth: 1))
            VStack(alignment: .leading, spacing: 4) {
                Text("THE PIECE")
                    .font(.system(size: 10, weight: .semibold))
                    .tracking(2)
                    .foregroundStyle(Palette.bronze)
                Text(piece.name ?? piece.subcategory ?? piece.category ?? "From your closet")
                    .font(Serif.body(16, weight: .medium))
                    .foregroundStyle(Palette.ink)
                if let c = piece.color, !c.isEmpty {
                    Text(c).font(Serif.body(13)).foregroundStyle(Palette.muted)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    // MARK: - Decode: the read

    private struct DecodeRead {
        var signature: String
        var palette: [String]
        var pieces: [(name: String, detail: String)]
    }

    private var decodeRead: DecodeRead? {
        guard kind == "decode", let d = outfit.signals?["decode"] else { return nil }
        let pieces = (d["pieces"]?.arrayValue ?? []).compactMap { p -> (String, String)? in
            guard let n = p["name"]?.stringValue, !n.isEmpty else { return nil }
            return (n, p["detail"]?.stringValue ?? "")
        }
        let palette = (d["palette_hex"]?.arrayValue ?? []).compactMap { $0.stringValue }
        let signature = d["style_signature"]?.stringValue ?? ""
        guard !pieces.isEmpty || !palette.isEmpty else { return nil }
        return DecodeRead(signature: signature, palette: palette, pieces: pieces)
    }

    private func decodeBlock(_ read: DecodeRead) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            if !read.pieces.isEmpty {
                Text("THE PIECES")
                    .font(.system(size: 10, weight: .semibold))
                    .tracking(2)
                    .foregroundStyle(Palette.bronze)
                VStack(spacing: 0) {
                    ForEach(Array(read.pieces.enumerated()), id: \.offset) { idx, piece in
                        HStack(alignment: .top, spacing: 12) {
                            Text(String(format: "%02d", idx + 1))
                                .font(Serif.display(15))
                                .foregroundStyle(Palette.bronze)
                                .frame(width: 26, alignment: .leading)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(piece.name)
                                    .font(Serif.body(16, weight: .medium))
                                    .foregroundStyle(Palette.ink)
                                if !piece.detail.isEmpty {
                                    Text(piece.detail)
                                        .font(Serif.body(13))
                                        .foregroundStyle(Palette.muted)
                                }
                            }
                            Spacer(minLength: 0)
                        }
                        .padding(.vertical, 12)
                        if idx < read.pieces.count - 1 { Hairline() }
                    }
                }
            }
            if !read.palette.isEmpty {
                Text("THE PALETTE")
                    .font(.system(size: 10, weight: .semibold))
                    .tracking(2)
                    .foregroundStyle(Palette.bronze)
                HStack(spacing: 8) {
                    ForEach(Array(read.palette.prefix(6).enumerated()), id: \.offset) { _, hex in
                        RoundedRectangle(cornerRadius: 4)
                            .fill(Color(hex: hex) ?? Palette.card)
                            .frame(height: 40)
                            .overlay(RoundedRectangle(cornerRadius: 4).stroke(Palette.hairline, lineWidth: 1))
                    }
                }
            }
        }
    }

    // MARK: - Cover

    private struct CoverRead {
        var headline: String?
        var pullQuote: String?
        var volume: Int?
    }

    private var coverRead: CoverRead? {
        guard kind == "magazine_cover", let c = outfit.signals?["cover"] else { return nil }
        return CoverRead(
            headline: c["headline"]?.stringValue,
            pullQuote: c["pull_quote"]?.stringValue,
            volume: c["vol_number"]?.doubleValue.map(Int.init)
        )
    }

    // MARK: - Trailing bits

    private func noteCard(_ text: String) -> some View {
        Text(text)
            .font(Serif.body(14))
            .foregroundStyle(Palette.muted)
            .fixedSize(horizontal: false, vertical: true)
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Palette.card)
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var footerFacts: some View {
        HStack(spacing: 16) {
            if let occ = outfit.occasion, !occ.isEmpty,
               !["Decoded", "Cover", "Studio"].contains(occ) {
                fact("OCCASION", occ)
            }
            if kind == "studio_gen", let c = outfit.hem_comment,
               c.hasPrefix("Generated: ") {
                fact("PIECE", String(c.dropFirst("Generated: ".count)))
            }
            if !dateLine.isEmpty { fact("SAVED", dateLine) }
            Spacer(minLength: 0)
        }
        .padding(.top, 4)
    }

    private func fact(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 10, weight: .semibold))
                .tracking(1.5)
                .foregroundStyle(Palette.muted)
            Text(value).font(Serif.body(14)).foregroundStyle(Palette.ink)
        }
    }

    // MARK: - Loading

    private func load() async {
        if let p = outfit.photo_path {
            heroUrl = (try? await Repo.shared.signedOutfitUrl(p)) ?? nil
        } else {
            heroUrl = outfit.image_url
        }
        if kind == "tryon", let bp = outfit.before_photo_path {
            beforeUrl = try? await Repo.shared.signedOutfitUrl(bp)
        }
        if kind == "tryon", let pid = outfit.linked_piece_id {
            pieceItem = try? await Repo.shared.closetItemById(pid)
            if let path = pieceItem?.image_path {
                pieceUrl = (try? await Repo.shared.signedClosetUrl(path)) ?? nil
            }
        }
        loaded = true
    }
}
