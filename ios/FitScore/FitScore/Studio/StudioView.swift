import SwiftUI
import PhotosUI

/// Studio tab — grid of generated + imported closet pieces, grouped by category.
/// Ports Android `StudioScreen.kt`. Header shows a credits pill; body renders
/// horizontal per-category rails; footer offers Import / Create-new actions.
struct StudioView: View {
    @StateObject private var credits = CreditsBus.shared
    @StateObject private var closet = ClosetBus.shared

    @State private var items: [ClosetItem] = []
    @State private var imageUrls: [String: String] = [:]
    @State private var loaded = false
    @State private var busy = false
    @State private var error: String? = nil

    @State private var selected: ClosetItem? = nil
    @State private var editingItem: ClosetItem? = nil
    @State private var editingRefUrl: String? = nil
    @State private var showCreate = false
    @State private var showCoverComposer = false
    @State private var showCoverTemplate = false
    @State private var covers: [MagazineCover] = []
    @State private var previewCover: IdentifiedCover? = nil

    /// Wrapper that guarantees a non-nil id so `.fullScreenCover(item:)`
    /// actually fires — MagazineCover.id is String? on the wire.
    struct IdentifiedCover: Identifiable {
        let id: String
        let cover: MagazineCover
    }

    // Photo import
    @State private var pickerItem: PhotosPickerItem? = nil

    private let categoryOrder: [String] = ["top", "bottom", "outerwear", "dress", "shoes", "accessory"]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                header
                    .padding(.horizontal, 20)
                    .padding(.top, 16)

                Text("Design a piece, then wear it.")
                    .font(Serif.body(14))
                    .foregroundStyle(Palette.muted)
                    .padding(.horizontal, 20)
                    .padding(.top, 4)
                    .padding(.bottom, 20)

                if !loaded {
                    skeletonRow
                        .padding(.horizontal, 20)
                } else if items.isEmpty {
                    emptyState
                        .padding(.horizontal, 20)
                } else {
                    grid
                }

                if let error {
                    Text(error)
                        .font(Serif.body(13))
                        .foregroundStyle(Palette.bronze)
                        .padding(.horizontal, 20)
                        .padding(.top, 8)
                }

                actionsFooter
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
                    .padding(.bottom, 40)
            }
        }
        .background(Palette.paper)
        .refreshable {
            await credits.refresh()
            await refresh()
        }
        .task {
            await credits.refresh()
            await refresh()
            loaded = true
        }
        // The create flow opens over this grid, so `.task` never runs again on
        // the way back and a piece the wearer just designed is missing from
        // their own closet until they leave and return.
        .onChange(of: closet.revision) { _, _ in
            Task { await refresh() }
        }
        .fullScreenCover(isPresented: $showCreate) {
            // Studio 2.0 gateway (Aug 2026): picks single-piece vs outfit wizard.
            StudioCreateGateway(onDone: {
                showCreate = false
                Task { await refresh() }
            })
        }
        // Covers are shelved behind a flag rather than removed, so the three
        // cover presentations stay wired up and only their contents are gated —
        // flipping the flag back on restores the flow with no structural edit.
        .fullScreenCover(isPresented: $showCoverComposer) {
            if Supa.magazineCoversEnabled {
                MagazineCoverSheet(outfitId: nil, onClose: { showCoverComposer = false })
            }
        }
        .fullScreenCover(isPresented: $showCoverTemplate) {
            if Supa.magazineCoversEnabled {
                CoverTemplateCreatorView(onClose: { showCoverTemplate = false })
            }
        }
        .fullScreenCover(item: $previewCover) { wrap in
            if Supa.magazineCoversEnabled {
                CoverPreviewSheet(cover: wrap.cover, onClose: { previewCover = nil })
            }
        }
        .fullScreenCover(item: $editingItem) { item in
            StudioCreateView(
                editingPieceId: item.id,
                presetType: item.category?.capitalized,
                presetReferenceUrl: editingRefUrl,
                onDone: {
                    editingItem = nil
                    editingRefUrl = nil
                    Task { await refresh() }
                }
            )
        }
        .sheet(item: $selected) { item in
            PieceDetailSheet(
                item: item,
                imageUrl: item.id.flatMap { imageUrls[$0] },
                onEdit: { url in
                    editingRefUrl = url
                    editingItem = item
                    selected = nil
                },
                onDismiss: { selected = nil }
            )
        }
        .onChange(of: pickerItem) { _, new in
            guard let new else { return }
            handlePicked(new)
        }
    }

    // MARK: - Header

    private var header: some View {
        HStack(alignment: .firstTextBaseline) {
            Text("Studio")
                .font(Serif.display(30))
                .foregroundStyle(Palette.ink)
            Spacer()
            if let bal = credits.balance {
                creditsPill(bal)
            }
        }
    }

    private func creditsPill(_ n: Int) -> some View {
        HStack(spacing: 6) {
            Text("✦").foregroundStyle(Palette.bronze)
            Text("\(n)")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Palette.ink)
        }
        .padding(.horizontal, 12).padding(.vertical, 6)
        .background(Palette.card)
        .overlay(Capsule().stroke(Palette.bronze.opacity(0.6), lineWidth: 1))
        .clipShape(Capsule())
    }

    // MARK: - Skeleton

    private var skeletonRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                ForEach(0..<4, id: \.self) { _ in
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Palette.card)
                        .frame(width: 160, height: 200)
                        .overlay(SkeletonBar(width: 100, height: 12).opacity(0.6))
                }
            }
        }
    }

    // MARK: - Empty

    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Spacer()
                hangerIllustration
                Spacer()
            }
            .padding(.vertical, 24)

            Text("Your studio is empty.")
                .font(Serif.display(22))
                .foregroundStyle(Palette.ink)

            Text("Import a piece you already own, or design one from a template.")
                .font(Serif.body(14))
                .foregroundStyle(Palette.muted)
                .padding(.top, 6)

            HStack(spacing: 12) {
                importPhotoPicker {
                    emptyCard(glyph: "↑", title: "Import a piece", subtitle: "From your camera roll")
                }
                Button { showCreate = true } label: {
                    emptyCard(glyph: "✦", title: "Create new", subtitle: "Design with a template")
                }
            }
            .padding(.top, 20)
        }
    }

    private var hangerIllustration: some View {
        // Minimal hanger — 3 strokes on a cream circle.
        ZStack {
            Circle().fill(Palette.card).frame(width: 140, height: 140)
            Path { p in
                p.move(to: CGPoint(x: 20, y: 50))
                p.addLine(to: CGPoint(x: 100, y: 50))
                p.move(to: CGPoint(x: 60, y: 50))
                p.addLine(to: CGPoint(x: 60, y: 20))
                p.addArc(center: CGPoint(x: 60, y: 14), radius: 6, startAngle: .degrees(90), endAngle: .degrees(-90), clockwise: true)
            }
            .stroke(Palette.bronze, style: StrokeStyle(lineWidth: 1.2, lineCap: .round, lineJoin: .round))
            .frame(width: 120, height: 70)
        }
    }

    private func emptyCard(glyph: String, title: String, subtitle: String) -> some View {
        VStack(alignment: .leading) {
            Text(glyph)
                .font(Serif.display(30))
                .foregroundStyle(Palette.bronze)
            Spacer()
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Palette.ink)
                Text(subtitle)
                    .font(Serif.body(12))
                    .foregroundStyle(Palette.muted)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(height: 150)
        .padding(16)
        .background(Palette.card)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.hairline, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    // MARK: - My Covers rail

    @ViewBuilder
    private var coversRail: some View {
        if Supa.magazineCoversEnabled, !covers.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Eyebrow(text: "MY COVERS · \(covers.count)")
                    .padding(.horizontal, 20)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(covers) { cover in
                            Button {
                                Haptic.chip()
                                if let id = cover.id {
                                    previewCover = IdentifiedCover(id: id, cover: cover)
                                }
                            } label: {
                                coverThumb(cover)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 20)
                }
            }
        }
    }

    private func coverThumb(_ cover: MagazineCover) -> some View {
        let url = cover.image_path.map {
            "https://ilrzqifdmjvooeyqvexd.supabase.co/storage/v1/object/public/magazine_covers/\($0)"
        }
        return VStack(alignment: .leading, spacing: 4) {
            AsyncImage(url: url.flatMap(URL.init)) { img in
                img.resizable().scaledToFill()
            } placeholder: {
                RoundedRectangle(cornerRadius: 12).fill(Palette.card)
            }
            .frame(width: 110, height: 195)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.hairline, lineWidth: 1))

            if let h = cover.headline, !h.isEmpty {
                Text(h)
                    .font(Serif.body(11, weight: .semibold))
                    .foregroundStyle(Palette.ink)
                    .lineLimit(1)
                    .frame(width: 110, alignment: .leading)
            } else {
                Text(cover.outfit_id == nil ? "Template" : "Cover")
                    .font(Serif.italic(11))
                    .foregroundStyle(Palette.muted)
                    .frame(width: 110, alignment: .leading)
            }
        }
    }

    // MARK: - Grid

    private var grid: some View {
        let grouped = Dictionary(grouping: items) { ($0.category ?? "other").lowercased() }
        let orderedKeys = categoryOrder.filter { grouped[$0] != nil } +
            grouped.keys.filter { !categoryOrder.contains($0) }
        return VStack(alignment: .leading, spacing: 20) {
            coversRail
            ForEach(orderedKeys, id: \.self) { cat in
                let list = grouped[cat] ?? []
                if !list.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Eyebrow(text: "\(prettyCategoryPlural(cat)) · \(list.count)")
                            .padding(.horizontal, 20)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(Array(list.enumerated()), id: \.element.id) { idx, it in
                                    Button { selected = it } label: {
                                        pieceCard(it, imageUrl: it.id.flatMap { imageUrls[$0] })
                                    }
                                    .buttonStyle(.plain)
                                    .wardrobeEntrance(index: idx)
                                }
                            }
                            .padding(.horizontal, 20)
                        }
                    }
                }
            }
        }
    }

    private func pieceCard(_ item: ClosetItem, imageUrl: String?) -> some View {
        let tint = resolveTint(item.color_hex ?? item.color)
        return VStack(alignment: .leading, spacing: 8) {
            ZStack {
                LinearGradient(colors: [tint.opacity(0.5), tint], startPoint: .top, endPoint: .bottom)
                if let url = imageUrl ?? item.image_url, let u = URL(string: url) {
                    AsyncImage(url: u) { phase in
                        switch phase {
                        case .success(let img):
                            // Fit, not fill: a garment cropped to a tile loses the
                            // hem, the sleeve or the shoulder — the exact things
                            // the piece is recognised by. The colour gradient
                            // behind it carries the empty space.
                            img.resizable().scaledToFit()
                        default:
                            Color.clear
                        }
                    }
                } else {
                    Text("👕").font(.system(size: 48)).foregroundStyle(.white)
                }
            }
            .frame(width: 144, height: 160)
            .clipShape(RoundedRectangle(cornerRadius: 12))

            Text(item.name ?? item.category ?? "Piece")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Palette.ink)
                .lineLimit(1)
            let sub = item.subcategory ?? item.category ?? ""
            if !sub.isEmpty {
                Text(sub)
                    .font(Serif.body(12))
                    .foregroundStyle(Palette.muted)
                    .lineLimit(1)
            }
        }
        .padding(8)
        .frame(width: 160)
        .background(tint.opacity(0.15))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    // MARK: - Footer actions

    private var actionsFooter: some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                Button { showCreate = true } label: {
                    actionCard(title: "+ Create New", subtitle: "Guided AI design")
                }.buttonStyle(.plain)

                importPhotoPicker {
                    actionCard(title: busy ? "Uploading…" : "↑ Import", subtitle: "Photo or icon")
                }
            }
            if Supa.magazineCoversEnabled {
                Button { Haptic.tap(); showCoverComposer = true } label: {
                    coverActionCard(
                        icon: "text.book.closed.fill",
                        title: "Compose a Cover",
                        subtitle: "Two photos → editorial magazine cover"
                    )
                }
                .buttonStyle(.plain)

                Button { Haptic.tap(); showCoverTemplate = true } label: {
                    coverActionCard(
                        icon: "square.grid.2x2.fill",
                        title: "Create Cover Template",
                        subtitle: "Design a style — no photo. Reuse later."
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func coverActionCard(icon: String, title: String, subtitle: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 18))
                .foregroundStyle(Palette.bronze)
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Palette.ink)
                Text(subtitle)
                    .font(Serif.body(13))
                    .foregroundStyle(Palette.muted)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Palette.bronze)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.card)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.hairline, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private func actionCard(title: String, subtitle: String) -> some View {
        VStack(alignment: .leading) {
            Text(title)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Palette.ink)
            Spacer()
            Text(subtitle)
                .font(Serif.body(13))
                .foregroundStyle(Palette.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(height: 120)
        .padding(16)
        .background(Palette.card)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.hairline, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    @ViewBuilder
    private func importPhotoPicker<Label: View>(@ViewBuilder label: () -> Label) -> some View {
        PhotosPicker(selection: $pickerItem, matching: .images, photoLibrary: .shared()) {
            label()
        }
        .disabled(busy)
    }

    // MARK: - Actions

    private func handlePicked(_ item: PhotosPickerItem) {
        busy = true
        error = nil
        Task {
            defer { busy = false; pickerItem = nil }
            do {
                guard let data = try await item.loadTransferable(type: Data.self) else {
                    throw RepoError.notFound
                }
                let path = try await Repo.shared.uploadClosetPhoto(bytes: data)
                guard let uid = Repo.shared.userId else { throw RepoError.notSignedIn }
                _ = try await Repo.shared.insertClosetItem(
                    ClosetItemInsert(
                        user_id: uid,
                        name: "New piece",
                        category: "top",
                        subcategory: nil,
                        image_path: path,
                        color_hex: nil,
                        parent_id: nil,
                        source: "import"
                    )
                )
                await refresh()
            } catch {
                self.error = error.localizedDescription
                ToastBus.shared.post("Upload failed: \(error.localizedDescription)")
            }
        }
    }

    private func refresh() async {
        // Only overwrite items if the fetch actually succeeded — a transient
        // failure returning nil-coalesced [] would otherwise blank the grid.
        if let list = try? await Repo.shared.closetItems(limit: 200) {
            items = list
            // One signing request for the whole grid. Signing per piece meant a
            // full closet cost two hundred sequential round-trips before the
            // last tile could draw.
            let needsSigning = list.compactMap { it -> String? in
                if let direct = it.image_url, !direct.isEmpty { return nil }
                return it.image_path
            }
            let signed = await Repo.shared.signedClosetUrls(needsSigning)
            var urls: [String: String] = [:]
            for it in list {
                guard let id = it.id else { continue }
                if let direct = it.image_url, !direct.isEmpty { urls[id] = direct; continue }
                if let path = it.image_path, let u = signed[path], !u.isEmpty { urls[id] = u }
            }
            imageUrls = urls
        }
        // While covers are shelved nothing renders them, so skip the fetch
        // rather than paying for a round-trip whose result is never shown.
        if Supa.magazineCoversEnabled, let c = try? await Repo.shared.loadCovers() {
            covers = c
        }
    }
}

// MARK: - Cover preview sheet

struct CoverPreviewSheet: View {
    let cover: MagazineCover
    var onClose: () -> Void

    private var publicUrl: String? {
        cover.image_path.map {
            "https://ilrzqifdmjvooeyqvexd.supabase.co/storage/v1/object/public/magazine_covers/\($0)"
        }
    }

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        Eyebrow(text: cover.outfit_id == nil ? "TEMPLATE" : "COVER")
                        Spacer()
                        Button(action: onClose) {
                            Image(systemName: "xmark").foregroundStyle(Palette.ink).frame(width: 36, height: 36)
                        }
                    }
                    if let h = cover.headline, !h.isEmpty {
                        Text(h)
                            .font(Serif.display(26))
                            .foregroundStyle(Palette.ink)
                    }
                    if let u = publicUrl {
                        AsyncImage(url: URL(string: u)) { img in
                            img.resizable().scaledToFit()
                        } placeholder: {
                            RoundedRectangle(cornerRadius: 16).fill(Palette.card).aspectRatio(9.0/16.0, contentMode: .fit)
                        }
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .shadow(color: .black.opacity(0.15), radius: 12, y: 6)
                    }
                    if let q = cover.pull_quote, !q.isEmpty {
                        Text("“\(q)”")
                            .font(Serif.italic(15))
                            .foregroundStyle(Palette.muted)
                    }
                    if let u = publicUrl {
                        Button(action: { share(url: u) }) { OutlinePillButton(title: "Share") }
                    }
                    Button(action: onClose) { OutlinePillButton(title: "Done") }
                    Spacer(minLength: 30)
                }
                .padding(20)
            }
        }
    }

    private func share(url: String) {
        guard let u = URL(string: url) else { return }
        Task { @MainActor in
            let bytes = try? await Repo.shared.downloadBytes(url)
            let items: [Any] = (bytes.flatMap { UIImage(data: $0) }).map { [$0, "Made on Fitrater · fitrater.ai"] as [Any] } ?? [u]
            let av = UIActivityViewController(activityItems: items, applicationActivities: nil)
            if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
               let root = scene.windows.first?.rootViewController {
                root.present(av, animated: true)
            }
        }
    }
}

// MARK: - Helpers (file-scoped, shared with PieceDetailSheet & StudioCreateView)

func prettyCategoryPlural(_ cat: String) -> String {
    switch cat.lowercased() {
    case "top": return "TOPS"
    case "bottom": return "BOTTOMS"
    case "outerwear": return "OUTERWEAR"
    case "dress": return "DRESSES"
    case "shoes": return "SHOES"
    case "accessory": return "ACCESSORIES"
    default: return cat.uppercased()
    }
}

func resolveTint(_ src: String?) -> Color {
    let fallback = Color(red: 0x8A/255, green: 0x6B/255, blue: 0x4A/255)
    guard let src, !src.isEmpty else { return fallback }
    let hex = src.hasPrefix("#") ? String(src.dropFirst()) : src
    guard hex.count == 6, let v = UInt32(hex, radix: 16) else { return fallback }
    let r = Double((v >> 16) & 0xFF) / 255.0
    let g = Double((v >> 8) & 0xFF) / 255.0
    let b = Double(v & 0xFF) / 255.0
    return Color(red: r, green: g, blue: b)
}
