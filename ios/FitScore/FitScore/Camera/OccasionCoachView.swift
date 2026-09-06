import SwiftUI
import PhotosUI
import UIKit

/// Occasion Coach — Hem plans 3 curated outfit combinations for the occasion
/// the user types (or picks from a preset chip). Each combo lists pieces;
/// each piece is either matched to a closet item (green check) or shows a
/// Studio generation prompt shortcut.
struct OccasionCoachView: View {
    var onClose: () -> Void
    var onOpenPaywall: () -> Void = {}

    @State private var prompt: String = ""
    @State private var busy = false
    @State private var error: String?
    @State private var combos: [OccasionCombo] = []
    @State private var freeAvailable: Bool = false
    @State private var checkedFree = false
    /// Per-combo render state, keyed by combo id: the finished outfit image,
    /// which combo is currently rendering, and any failure to show in place.
    @State private var renders: [UUID: String] = [:]
    @State private var renderingId: UUID?
    @State private var renderErrors: [UUID: String] = [:]
    @State private var closetPieces: [ClosetItem] = []
    @State private var pieceUrls: [String: String] = [:]
    @State private var chosenIds: Set<String> = []
    @State private var showPhotoPicker = false
    @State private var photoItem: PhotosPickerItem?
    @State private var uploading = false
    @FocusState private var promptFocused: Bool

    /// Occasions, grouped so a long list stays scannable. These are deliberately
    /// more specific than a bare "Wedding" or "Date" — the coach reads the words
    /// it is given, and "Wedding guest" and "Black tie" want different clothes.
    private static let occasionGroups: [(String, [String])] = [
        ("WORK", ["Interview", "Office day", "Client dinner", "Big presentation", "Work offsite", "Conference"]),
        ("NIGHTS OUT", ["First date", "Dinner date", "Cocktail bar", "Birthday party", "House party", "Concert"]),
        ("FORMAL", ["Wedding guest", "Black tie", "Engagement party", "Graduation", "Gallery opening", "Funeral"]),
        ("EVERYDAY & AWAY", ["Sunday errands", "Coffee run", "Airport day", "City break", "Beach day", "Weekend away"]),
    ]

    /// Detail chips. The header asks for "room, hour, who is there", and these
    /// are the three answers worth one tap. They append to the occasion rather
    /// than replacing it, which is why the field reads as a `·`-joined list.
    private static let modifierGroups: [(String, [String])] = [
        ("WHEN", ["Morning", "Afternoon", "Evening", "Late night", "Summer", "Winter"]),
        ("WHERE", ["Indoors", "Outdoors", "Rooftop", "Beach", "Garden"]),
        ("DRESS CODE", ["Relaxed", "Smart casual", "Cocktail", "Formal"]),
    ]

    // MARK: - Prompt as segments
    //
    // The placeholder already promises the grammar — "Wedding · outdoor ·
    // summer, boho…" — so the chips read and write the same `·`-joined list.
    // The field stays free text; anything typed by hand that does not match a
    // chip simply survives as its own segment.

    private var segments: [String] {
        prompt.split(separator: "·")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }

    private func writeSegments(_ parts: [String]) {
        prompt = parts.joined(separator: " · ")
    }

    /// A brief is usually more than one word — "wedding guest, outdoors,
    /// afternoon" — so every chip toggles and several can be on at once. The
    /// ceiling exists because past about six the brief stops being a brief:
    /// the coach starts averaging contradictory instructions instead of
    /// following them.
    private static let maxSegments = 6

    private var canAddMore: Bool { segments.count < Self.maxSegments }

    private func toggle(_ label: String) {
        var parts = segments
        if let idx = parts.firstIndex(where: { $0.caseInsensitiveCompare(label) == .orderedSame }) {
            parts.remove(at: idx)
        } else {
            guard parts.count < Self.maxSegments else { return }
            parts.append(label)
        }
        writeSegments(parts)
    }

    private func isActive(_ label: String) -> Bool {
        segments.contains { $0.caseInsensitiveCompare(label) == .orderedSame }
    }

    @ViewBuilder
    private func chip(_ label: String) -> some View {
        let active = isActive(label)
        // At the ceiling the unpicked chips dim rather than disappear, so the
        // limit reads as "that is enough" instead of as a broken tap.
        let blocked = !active && !canAddMore
        Button {
            Haptic.chip()
            toggle(label)
        } label: {
            Text(label.uppercased())
                .font(.system(size: 11, weight: .semibold))
                .tracking(1.4)
                .foregroundStyle(active ? Palette.paper : Palette.ink)
                .padding(.horizontal, 14).padding(.vertical, 8)
                .background(active ? Palette.ink : Palette.card)
                .overlay(Capsule().stroke(active ? Color.clear : Palette.hairline, lineWidth: 1))
                .clipShape(Capsule())
        }
        .disabled(blocked)
        .opacity(blocked ? 0.35 : 1)
    }

    @ViewBuilder
    private func chipGroup(_ title: String, _ labels: [String]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Eyebrow(text: title)
            FlowLayout(spacing: 8) {
                ForEach(labels, id: \.self) { label in
                    chip(label)
                }
            }
        }
    }

    /// Sits above the chips so the ceiling is known before it is hit.
    @ViewBuilder
    private var pickCounter: some View {
        let n = segments.count
        Text(n == 0
             ? "PICK UP TO \(Self.maxSegments)"
             : "\(n) OF \(Self.maxSegments) PICKED")
            .font(.system(size: 9.5, weight: .semibold))
            .tracking(1.4)
            .foregroundStyle(canAddMore ? Palette.muted : Palette.bronze)
    }

    // MARK: - Bring your own pieces

    /// Everything in the closet, offered as a strip the wearer picks from. The
    /// coach designs the rest of each combo around whatever is selected here.
    @ViewBuilder
    private var piecePicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Eyebrow(text: "USE MY PIECES")
                Spacer()
                if !chosenIds.isEmpty {
                    Text("\(chosenIds.count) CHOSEN")
                        .font(.system(size: 9.5, weight: .semibold))
                        .tracking(1.4)
                        .foregroundStyle(Palette.bronze)
                }
            }
            Text("Optional. Anything you pick appears in all three combos — the rest is designed from scratch.")
                .font(Serif.body(12))
                .foregroundStyle(Palette.muted)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 10) {
                    uploadTile
                    ForEach(closetPieces) { piece in
                        pieceTile(piece)
                    }
                }
                .padding(.vertical, 2)
            }
        }
    }

    @ViewBuilder
    private var uploadTile: some View {
        Button {
            Haptic.chip()
            showPhotoPicker = true
        } label: {
            VStack(spacing: 8) {
                RoundedRectangle(cornerRadius: 10)
                    .fill(Palette.card)
                    .frame(width: 84, height: 110)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(Palette.hairline, style: StrokeStyle(lineWidth: 1, dash: [5, 4]))
                    )
                    .overlay(
                        VStack(spacing: 6) {
                            if uploading {
                                ProgressView()
                            } else {
                                Image(systemName: "photo")
                                    .font(.system(size: 20))
                                    .foregroundStyle(Palette.muted)
                                Text("PHOTO")
                                    .font(.system(size: 9, weight: .semibold))
                                    .tracking(1.3)
                                    .foregroundStyle(Palette.muted)
                            }
                        }
                    )
                Text("Upload")
                    .font(Serif.body(11))
                    .foregroundStyle(Palette.muted)
                    .frame(width: 84)
            }
        }
        .disabled(uploading)
    }

    @ViewBuilder
    private func pieceTile(_ piece: ClosetItem) -> some View {
        let picked = piece.id.map { chosenIds.contains($0) } ?? false
        Button {
            Haptic.chip()
            guard let id = piece.id else { return }
            if chosenIds.contains(id) { chosenIds.remove(id) } else { chosenIds.insert(id) }
        } label: {
            VStack(spacing: 8) {
                RoundedRectangle(cornerRadius: 10)
                    .fill(Palette.card)
                    .frame(width: 84, height: 110)
                    .overlay(
                        Group {
                            if let s = piece.image_path.flatMap({ pieceUrls[$0] }) ?? piece.image_url,
                               let u = URL(string: s) {
                                // Fit with an inset: a garment cut out on a pale
                                // backdrop reads as the whole piece or not at
                                // all, so nothing may be cropped, and the inset
                                // keeps it clear of the rounded corners.
                                AsyncImage(url: u) { $0.resizable().scaledToFit() } placeholder: { Color.clear }
                                    .padding(6)
                            }
                        }
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(picked ? Palette.ink : Palette.hairline, lineWidth: picked ? 2 : 1)
                    )
                    .overlay(alignment: .topTrailing) {
                        if picked {
                            Image(systemName: "checkmark")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundStyle(Palette.paper)
                                .padding(5)
                                .background(Circle().fill(Palette.ink))
                                .padding(5)
                        }
                    }
                Text(piece.name ?? piece.category ?? "Piece")
                    .font(Serif.body(11))
                    .foregroundStyle(picked ? Palette.ink : Palette.muted)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
                    .frame(width: 84)
            }
        }
    }

    /// The chosen rows themselves, in the order the strip shows them.
    private var chosenPieces: [ClosetItem] {
        closetPieces.filter { $0.id.map { chosenIds.contains($0) } ?? false }
    }

    private func loadCloset() async {
        let all = (try? await Repo.shared.closetItems(limit: 100)) ?? []
        // A row with no picture cannot be rendered into a look, so offering it
        // would be a dead tap.
        let usable = all.filter { ($0.image_path?.isEmpty == false) || ($0.image_url?.isEmpty == false) }
        pieceUrls = await Repo.shared.signedClosetUrls(usable.compactMap(\.image_path))
        closetPieces = usable
    }

    private func adoptPhoto(_ data: Data) async {
        guard let uid = Repo.shared.userId else { return }
        uploading = true
        defer { uploading = false }
        do {
            let path = try await Repo.shared.uploadClosetPhoto(bytes: data)
            let item = try await Repo.shared.insertClosetItem(ClosetItemInsert(
                user_id: uid,
                name: "My piece",
                category: "top",
                subcategory: nil,
                image_path: path,
                color_hex: nil,
                parent_id: nil,
                source: "occasion_upload"
            ))
            await loadCloset()
            // Uploading it is the choice; making the wearer tap it again would
            // be asking the same question twice.
            if let id = item.id { chosenIds.insert(id) }
        } catch {
            ToastBus.shared.post("Upload failed: \(error.localizedDescription)")
        }
    }

    /// What the wall says while Hem writes the three combos.
    private static let planLines = [
        "Reading the occasion",
        "Going through your closet",
        "Building three combos",
        "Naming what is missing",
    ]

    /// What it says while a combo is being rendered — this one is genuinely
    /// slow, since it makes a garment image per missing piece before stitching.
    private static let renderLines = [
        "Cutting the missing pieces",
        "Filing them in your closet",
        "Dressing the mannequin",
        "Steaming the look",
    ]

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()
            VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    header
                    if combos.isEmpty {
                        composer
                    } else {
                        resultBlock
                    }
                    Spacer(minLength: 30)
                }
                .padding(20)
            }
                if combos.isEmpty && !busy {
                    composerFooter
                }
            }

            // A wall, not an inline spinner. The chip list is long enough that
            // an indicator placed after it sits below the fold, so tapping the
            // pinned button looked like it did nothing at all.
            if busy || renderingId != nil {
                LoadingWall(lines: busy ? Self.planLines : Self.renderLines)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: busy)
        .animation(.easeInOut(duration: 0.2), value: renderingId)
        .photosPicker(isPresented: $showPhotoPicker, selection: $photoItem, matching: .images)
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self) {
                    await adoptPhoto(data)
                }
                photoItem = nil
            }
        }
        .task {
            await refreshFree()
            await loadCloset()
        }
    }

    // MARK: - Header

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 6) {
                Eyebrow(text: "Occasion Coach")
                Text("What's the occasion?")
                    .font(Serif.display(28))
                    .foregroundStyle(Palette.ink)
            }
            Spacer()
            Button(action: onClose) {
                Image(systemName: "xmark").foregroundStyle(Palette.ink).frame(width: 36, height: 36)
            }
        }
    }

    // MARK: - Composer

    private var composer: some View {
        VStack(alignment: .leading, spacing: 16) {
            TextField("Wedding · outdoor · summer, boho…", text: $prompt, axis: .vertical)
                .font(Serif.body(16))
                .foregroundStyle(Palette.ink)
                .focused($promptFocused)
                .lineLimit(1...4)
                .padding(14)
                .background(Palette.card)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Palette.hairline, lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 8))

            VStack(alignment: .leading, spacing: 18) {
                pickCounter
                ForEach(Array(Self.occasionGroups.enumerated()), id: \.offset) { _, g in
                    chipGroup(g.0, g.1)
                }
                Hairline()
                ForEach(Array(Self.modifierGroups.enumerated()), id: \.offset) { _, g in
                    chipGroup(g.0, g.1)
                }
                Hairline()
                piecePicker
            }

            if busy {
                loadingBlock
            } else if let error {
                Text(error).font(Serif.body(13)).foregroundStyle(Palette.bronze)
            }
        }
    }

    /// The primary action is pinned below the scroll view: the chip list is now
    /// long enough that a button placed after it would sit off-screen on first
    /// open, which is exactly when it is most likely to be wanted.
    @ViewBuilder
    private var composerFooter: some View {
        VStack(spacing: 0) {
            Hairline()
            PrimaryButton(
                title: buttonTitle,
                enabled: !prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                action: run
            )
            .padding(.horizontal, 20)
            .padding(.top, 14)
            .padding(.bottom, 8)
        }
        .background(Palette.paper)
    }

    private var buttonTitle: String {
        let paid = "Get 3 combos · \(Supa.occasionCost) credits"
        if !checkedFree { return paid }
        return freeAvailable ? "Get 3 combos · free this week" : paid
    }

    private var loadingBlock: some View {
        HStack(spacing: 10) {
            HemMonogram(size: 22)
            Text("Hem writing")
                .font(Serif.italic(16))
                .foregroundStyle(Palette.muted)
            DotsAnimation()
        }
        .frame(maxWidth: .infinity, alignment: .center)
        .padding(.vertical, 30)
    }

    // MARK: - Result

    private var resultBlock: some View {
        VStack(alignment: .leading, spacing: 20) {
            ForEach(combos) { combo in
                comboCard(combo)
            }
            Button {
                Haptic.chip()
                combos = []
                error = nil
                renders = [:]
                renderErrors = [:]
            } label: {
                Text("Try another occasion")
                    .font(.system(size: 12, weight: .semibold))
                    .tracking(1.6)
                    .foregroundStyle(Palette.ink)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .overlay(RoundedRectangle(cornerRadius: 4).stroke(Palette.ink, lineWidth: 1))
            }
        }
    }

    @ViewBuilder
    private func comboCard(_ combo: OccasionCombo) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(combo.title)
                .font(Serif.display(18))
                .foregroundStyle(Palette.ink)
            if !combo.rationale.isEmpty {
                Text(combo.rationale)
                    .font(Serif.italic(14))
                    .foregroundStyle(Palette.muted)
            }
            if let palette = combo.palette_hex, !palette.isEmpty {
                paletteRow(palette)
            }
            Hairline()
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(combo.pieces.enumerated()), id: \.offset) { idx, p in
                    if idx > 0 { Hairline() }
                    pieceRow(p)
                }
            }
            if let url = renders[combo.id] {
                renderedLook(url)
            }
            if let e = renderErrors[combo.id] {
                Text(e).font(Serif.body(12)).foregroundStyle(Palette.bronze)
            }
            HStack(spacing: 10) {
                generateButton(combo)
                Spacer(minLength: 8)
                Button {
                    Haptic.tap()
                    Task { await saveCombo(combo) }
                } label: {
                    Text("Save")
                        .font(.system(size: 11, weight: .semibold))
                        .tracking(1.5)
                        .foregroundStyle(Palette.bronze)
                        .padding(.horizontal, 12).padding(.vertical, 6)
                        .overlay(Capsule().stroke(Palette.bronze, lineWidth: 1))
                }
            }
        }
        .padding(16)
        .background(Palette.card)
        .overlay(RoundedRectangle(cornerRadius: 4).stroke(Palette.bronze.opacity(0.35), lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 4))
    }

    @ViewBuilder
    private func pieceRow(_ p: OccasionPiece) -> some View {
        HStack(alignment: .center, spacing: 12) {
            Circle()
                .fill(Self.colorFromName(p.color) ?? Palette.muted.opacity(0.4))
                .frame(width: 22, height: 22)
                .overlay(Circle().stroke(Palette.hairline, lineWidth: 1))
            VStack(alignment: .leading, spacing: 3) {
                Text(p.category.uppercased())
                    .font(.system(size: 9.5, weight: .semibold))
                    .tracking(1.4)
                    .foregroundStyle(Palette.bronze)
                Text(p.name)
                    .font(Serif.body(14, weight: .medium))
                    .foregroundStyle(Palette.ink)
                if !p.meta.isEmpty {
                    Text(p.meta)
                        .font(Serif.body(11))
                        .foregroundStyle(Palette.muted)
                }
            }
            Spacer(minLength: 10)
            // Only the pieces that have to be made are marked. Owning something
            // is the quiet default, and badging every row you already own just
            // spends attention on the rows that need none.
            if !p.isOwned {
                Text("NEW")
                    .font(.system(size: 9.5, weight: .semibold))
                    .tracking(1.4)
                    .foregroundStyle(Palette.muted)
            }
        }
        .padding(.vertical, 10)
    }

    // MARK: - Combo palette, render, generate

    @ViewBuilder
    private func paletteRow(_ hexes: [String]) -> some View {
        HStack(spacing: 6) {
            ForEach(hexes, id: \.self) { hex in
                RoundedRectangle(cornerRadius: 4)
                    .fill(Self.colorFromHex(hex) ?? Palette.muted.opacity(0.35))
                    .frame(height: 26)
                    .overlay(RoundedRectangle(cornerRadius: 4).stroke(Palette.hairline, lineWidth: 1))
            }
        }
    }

    @ViewBuilder
    private func renderedLook(_ url: String) -> some View {
        AsyncImage(url: URL(string: url)) { img in
            img.resizable().scaledToFit()
        } placeholder: {
            Rectangle().fill(Palette.paper)
                .frame(height: 180)
                .overlay(ProgressView())
        }
        .frame(maxWidth: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Palette.hairline, lineWidth: 1))
    }

    @ViewBuilder
    private func generateButton(_ combo: OccasionCombo) -> some View {
        let rendering = renderingId == combo.id
        let done = renders[combo.id] != nil
        Button {
            Haptic.tap()
            Task { await generate(combo) }
        } label: {
            Text(generateTitle(combo, rendering: rendering, done: done))
                .font(.system(size: 11, weight: .semibold))
                .tracking(1.5)
                .foregroundStyle(done ? Palette.muted : Palette.paper)
                .padding(.horizontal, 14).padding(.vertical, 9)
                .background(done ? Color.clear : Palette.ink)
                .overlay(Capsule().stroke(done ? Palette.hairline : Color.clear, lineWidth: 1))
                .clipShape(Capsule())
        }
        .disabled(rendering || done || renderingId != nil)
    }

    private func generateTitle(_ combo: OccasionCombo, rendering: Bool, done: Bool) -> String {
        if done { return "GENERATED ✓" }
        if rendering { return "GENERATING…" }
        let n = combo.missingPieces.count
        // Saying how many pieces are being made explains the price without a
        // second line of copy — an all-closet combo reads as "just the stitch".
        let what = n == 0 ? "STITCH" : "GENERATE \(n) PIECE\(n == 1 ? "" : "S")"
        return "\(what) · \(combo.renderCost)C"
    }

    private func generate(_ combo: OccasionCombo) async {
        guard renderingId == nil, renders[combo.id] == nil else { return }
        let cost = combo.renderCost
        let gate = await CreditsGate.check(cost)
        guard case .ok = gate else {
            if case .insufficientBalance = gate {
                _ = CreditsGate.explainAndBlock(gate); onOpenPaywall()
            } else {
                _ = CreditsGate.explainAndBlock(gate)
            }
            return
        }
        renderingId = combo.id
        renderErrors[combo.id] = nil
        defer { renderingId = nil }
        do {
            let occasion = prompt.trimmingCharacters(in: .whitespacesAndNewlines)
            let out = try await OccasionCoachService.generateOutfit(combo: combo, occasion: occasion)
            // Charged only once the images exist, so a failed render is free.
            try? await Repo.shared.spendCredits(amount: cost, kind: "occasion_outfit")
            renders[combo.id] = out.imageUrl
            ToastBus.shared.post("Outfit ready — it is in your closet and Try On.")
        } catch {
            renderErrors[combo.id] = "Couldn't render that combo. \(error.localizedDescription)"
        }
    }

    // MARK: - Actions

    private func run() {
        guard !busy else { return }
        promptFocused = false
        busy = true
        error = nil
        let trimmed = prompt.trimmingCharacters(in: .whitespacesAndNewlines)
        Task {
            // Weekly-free path skips the credit gate; paid path uses full gate.
            let useFree = freeAvailable
            if !useFree {
                let gate = await CreditsGate.check(Supa.occasionCost)
                if case .ok = gate {} else {
                    busy = false
                    if case .insufficientBalance = gate {
                        _ = CreditsGate.explainAndBlock(gate); onOpenPaywall()
                    } else {
                        _ = CreditsGate.explainAndBlock(gate)
                    }
                    return
                }
            }
            do {
                let result = try await OccasionCoachService.plan(prompt: trimmed, chosen: chosenPieces)
                if result.isEmpty {
                    error = "Hem couldn't compose 3 combos — try rewording."
                    busy = false
                    return
                }
                if useFree {
                    await OccasionCoachService.recordWeeklyFreeUse()
                    freeAvailable = false
                } else {
                    try? await Repo.shared.spendCredits(amount: Supa.occasionCost, kind: "occasion_coach")
                }
                combos = result
                busy = false
            } catch {
                self.error = error.localizedDescription
                ToastBus.shared.post("Occasion Coach failed: \(error.localizedDescription)")
                busy = false
            }
        }
    }

    private func saveCombo(_ combo: OccasionCombo) async {
        guard let uid = Repo.shared.userId else {
            ToastBus.shared.post("Not signed in.")
            return
        }
        // Use the first matched closet piece's image as photo_path, or fall
        // back to an empty string (photo_path is required by the schema).
        var photoPath = ""
        for p in combo.pieces {
            if let cid = p.matched_closet_id,
               let item = try? await Repo.shared.closetItemById(cid),
               let path = item.image_path {
                photoPath = path
                break
            }
        }
        let hem = combo.rationale.isEmpty ? combo.title : combo.rationale
        _ = try? await Repo.shared.insertOutfit(OutfitInsert(
            user_id: uid,
            photo_path: photoPath,
            score: 0.0,
            occasion: prompt.trimmingCharacters(in: .whitespacesAndNewlines),
            hem_comment: hem,
            weather_c: nil, verdict: combo.title, subscores: nil, swaps: nil, annotations: nil,
            kind: "occasion_plan", linked_piece_id: nil
        ))
        ToastBus.shared.post("Saved to journal.")
    }

    private func refreshFree() async {
        freeAvailable = await OccasionCoachService.weeklyFreeAvailable()
        checkedFree = true
    }

    // MARK: - Color helpers

    private static func colorFromName(_ name: String) -> Color? {
        let n = name.lowercased().trimmingCharacters(in: .whitespaces)
        if n.hasPrefix("#") { return colorFromHex(n) }
        switch n {
        case "black", "ink": return Palette.ink
        case "white", "cream", "ivory", "bone": return Color(red: 0.96, green: 0.94, blue: 0.89)
        case "beige", "sand", "tan", "khaki": return Color(red: 0.84, green: 0.77, blue: 0.64)
        case "brown", "chocolate", "cocoa": return Color(red: 0.36, green: 0.23, blue: 0.13)
        case "bronze", "camel": return Palette.bronze
        case "gray", "grey", "charcoal", "slate": return Color(red: 0.36, green: 0.36, blue: 0.36)
        case "navy", "midnight": return Color(red: 0.10, green: 0.15, blue: 0.32)
        case "blue", "powder-blue", "sky": return Color(red: 0.35, green: 0.52, blue: 0.75)
        case "green", "olive", "sage", "forest": return Color(red: 0.32, green: 0.45, blue: 0.30)
        case "red", "wine", "rust", "terracotta": return Color(red: 0.70, green: 0.25, blue: 0.20)
        case "pink", "peach", "blush": return Color(red: 0.95, green: 0.72, blue: 0.68)
        case "yellow", "butter", "mustard": return Color(red: 0.90, green: 0.75, blue: 0.30)
        case "purple", "lavender", "violet": return Color(red: 0.55, green: 0.40, blue: 0.68)
        case "gold": return Palette.goldA
        case "silver": return Color(red: 0.75, green: 0.75, blue: 0.78)
        default: return nil
        }
    }

    private static func colorFromHex(_ hex: String) -> Color? {
        var s = hex.uppercased()
        if s.hasPrefix("#") { s.removeFirst() }
        guard s.count == 6, let v = UInt32(s, radix: 16) else { return nil }
        let r = Double((v >> 16) & 0xFF) / 255
        let g = Double((v >> 8) & 0xFF) / 255
        let b = Double(v & 0xFF) / 255
        return Color(red: r, green: g, blue: b)
    }
}

// MARK: - Loading dots

private struct DotsAnimation: View {
    @State private var phase = 0
    var body: some View {
        HStack(spacing: 3) {
            ForEach(0..<3) { i in
                Circle()
                    .fill(Palette.muted)
                    .frame(width: 5, height: 5)
                    .opacity(phase == i ? 1.0 : 0.35)
            }
        }
        .onAppear {
            Timer.scheduledTimer(withTimeInterval: 0.35, repeats: true) { _ in
                phase = (phase + 1) % 3
            }
        }
    }
}

// MARK: - Loading wall

/// Full-screen cover shown while the coach is working. It blocks the content
/// underneath on purpose: both operations take long enough that a half-visible
/// screen invites a second tap on a button that is already running.
private struct LoadingWall: View {
    let lines: [String]

    @State private var index = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()
            VStack(spacing: 16) {
                HemMonogram(size: 34)
                Text(lines.isEmpty ? "Working" : lines[min(index, lines.count - 1)])
                    .font(Serif.italic(19))
                    .foregroundStyle(Palette.ink)
                    .multilineTextAlignment(.center)
                    .id(index)
                    .transition(.opacity)
                Text("This one takes a moment.")
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(1.4)
                    .foregroundStyle(Palette.muted)
            }
            .padding(40)
        }
        // Swallow taps so the screen underneath cannot be driven while it runs.
        .contentShape(Rectangle())
        .onTapGesture { }
        .task {
            guard lines.count > 1 else { return }
            // Advancing on a timer rather than on real progress: the work is a
            // chain of opaque model calls with no milestones to report.
            while !Task.isCancelled && index < lines.count - 1 {
                try? await Task.sleep(nanoseconds: 3_200_000_000)
                if Task.isCancelled { return }
                if reduceMotion { index += 1 } else {
                    withAnimation(.easeInOut(duration: 0.35)) { index += 1 }
                }
            }
        }
    }
}
