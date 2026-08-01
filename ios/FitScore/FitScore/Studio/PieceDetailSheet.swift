import SwiftUI

/// Piece detail bottom sheet — ports Android `PieceDetailSheet.kt`.
/// Shows the hero image, generated/imported pill, meta, history chain and
/// "WORN N TIMES" bronze eyebrow when the piece has associated try-ons.
struct PieceDetailSheet: View {
    let item: ClosetItem
    let imageUrl: String?
    let onEdit: (_ referenceUrl: String?) -> Void
    let onDismiss: () -> Void

    @State private var current: ClosetItem
    @State private var chain: [ClosetItem] = []
    @State private var chainUrls: [String: String] = [:]
    @State private var wornCount: Int = 0

    init(item: ClosetItem, imageUrl: String?, onEdit: @escaping (_ referenceUrl: String?) -> Void, onDismiss: @escaping () -> Void) {
        self.item = item
        self.imageUrl = imageUrl
        self.onEdit = onEdit
        self.onDismiss = onDismiss
        self._current = State(initialValue: item)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                pill
                    .padding(.top, 12)
                hero
                Text(current.name ?? "Piece")
                    .font(Serif.display(24))
                    .foregroundStyle(Palette.ink)
                let meta = [current.subcategory ?? current.category, current.color_hex ?? current.color]
                    .compactMap { $0 }
                    .filter { !$0.isEmpty }
                    .joined(separator: " · ")
                if !meta.isEmpty {
                    Text(meta)
                        .font(Serif.body(13))
                        .foregroundStyle(Palette.muted)
                }
                if wornCount > 0 {
                    Eyebrow(text: "WORN \(wornCount) TIME\(wornCount == 1 ? "" : "S")")
                        .padding(.top, 4)
                }
                if !chain.isEmpty {
                    Eyebrow(text: "HISTORY · \(chain.count + 1)")
                        .padding(.top, 16)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(chain + [current], id: \.id) { c in
                                historyTile(c)
                            }
                        }
                    }
                }
                Button {
                    onEdit(current.id.flatMap { chainUrls[$0] } ?? imageUrl)
                } label: {
                    HStack(spacing: 8) {
                        Text("✎").foregroundStyle(.white)
                        Text("Edit piece")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(.white)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
                    .background(Palette.ink)
                    .clipShape(Capsule())
                }
                .padding(.top, 20)
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 32)
        }
        .background(Palette.paper.ignoresSafeArea())
        .task(id: current.id) {
            await loadContext()
        }
    }

    private var pill: some View {
        let isImported = (current.image_path ?? "").contains("references/")
        return Text(isImported ? "IMPORTED" : "GENERATED")
            .font(.system(size: 10, weight: .semibold))
            .tracking(1)
            .foregroundStyle(Palette.ink)
            .padding(.horizontal, 10).padding(.vertical, 4)
            .background(isImported ? Palette.card : Palette.ink.opacity(0.08))
            .overlay(Capsule().stroke(Palette.hairline, lineWidth: 1))
            .clipShape(Capsule())
    }

    private var hero: some View {
        let effective = current.id.flatMap { chainUrls[$0] } ?? imageUrl
        return ZStack {
            Palette.card
            if let url = effective, let u = URL(string: url) {
                AsyncImage(url: u) { phase in
                    switch phase {
                    case .success(let img): img.resizable().scaledToFit()
                    default: ProgressView().tint(Palette.muted)
                    }
                }
            } else {
                Text("👕").font(.system(size: 96)).foregroundStyle(Palette.muted)
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .frame(maxWidth: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private func historyTile(_ c: ClosetItem) -> some View {
        let isCurrent = c.id == current.id
        let u = c.id.flatMap { chainUrls[$0] } ?? c.image_url
        return Button {
            if !isCurrent { current = c }
        } label: {
            ZStack {
                Palette.card
                if let s = u, let url = URL(string: s) {
                    AsyncImage(url: url) { phase in
                        switch phase {
                        case .success(let img): img.resizable().scaledToFill()
                        default: Color.clear
                        }
                    }
                }
            }
            .frame(width: 56, height: 56)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8)
                .stroke(isCurrent ? Palette.bronze : Palette.hairline,
                        lineWidth: isCurrent ? 2 : 1))
        }
        .buttonStyle(.plain)
    }

    private func loadContext() async {
        guard let id = current.id else { return }
        wornCount = (try? await Repo.shared.tryOnCountForPiece(id)) ?? 0

        // Walk parent chain upward
        var parents: [ClosetItem] = []
        var cursor: ClosetItem? = current
        var visited = Set<String>()
        while let cur = cursor, let pid = cur.parent_id, !visited.contains(pid) {
            visited.insert(pid)
            guard let p = try? await Repo.shared.closetItemById(pid) else { break }
            parents.insert(p, at: 0)
            cursor = p
        }
        let kids = (try? await Repo.shared.closetChildren(parentId: id)) ?? []
        let combined = parents + kids
        chain = combined
        var urls: [String: String] = [:]
        for c in combined {
            guard let cid = c.id else { continue }
            if let direct = c.image_url, !direct.isEmpty { urls[cid] = direct; continue }
            guard let path = c.image_path else { continue }
            if let signed = try? await Repo.shared.signedClosetUrl(path) { urls[cid] = signed }
        }
        chainUrls = urls
    }
}
