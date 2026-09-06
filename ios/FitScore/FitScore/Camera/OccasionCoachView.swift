import SwiftUI
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
        }
        .task { await refreshFree() }
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
            // Ownership is the only status worth a badge here: it is what
            // decides whether this piece costs anything to render.
            if p.isOwned {
                HStack(spacing: 4) {
                    Image(systemName: "checkmark").font(.system(size: 11, weight: .semibold))
                    Text("IN CLOSET").font(.system(size: 9.5, weight: .semibold)).tracking(1.4)
                }
                .foregroundStyle(Color(red: 0.20, green: 0.55, blue: 0.32))
            } else {
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
                let result = try await OccasionCoachService.plan(prompt: trimmed)
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
