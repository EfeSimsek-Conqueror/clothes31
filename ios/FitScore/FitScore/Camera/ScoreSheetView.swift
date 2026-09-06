import SwiftUI
import PhotosUI

/// Score-a-look flow: a photo and a brief.
///
/// The brief is the whole point of scoring v4. Every control on this sheet
/// changes the weight vector the server will grade against, and the two lines
/// under the CTA — the brief sentence and the "grading hardest on" strip — are
/// recomputed locally from `RubricWeights` on every touch. No model call, no
/// credit: the user watches the rubric rearrange before deciding to spend.
///
/// Zero taps is a complete brief. The occasion defaults to Everyday, both dials
/// seed from it, the band-4 answers default to the same values the server would
/// apply, and weather — which nothing here can observe truthfully — is simply
/// left out of the payload.
struct ScoreSheetView: View {
    var onClose: () -> Void
    var onScored: (String) -> Void
    var onOpenPaywall: () -> Void = {}

    // Band 1–4 — the brief.
    @State private var occasion: ScoreOccasion = .everyday
    @State private var formality = Rubric.seedFormality[.everyday] ?? 2
    @State private var presence = Rubric.seedPresence[.everyday] ?? 3
    // Seeded to the server's own defaults so a row scored with zero taps in
    // band 4 carries the rubric it was actually graded against.
    @State private var role: WeddingRole = .guest
    @State private var venue: WeddingVenue = .ballroom
    @State private var room: WorkRoom = .business_casual
    @State private var onFeet: OnFeet = .some

    // Band 5 — cross-cutting.
    /// Nil until the user says so. The server disarms every weather rule when
    /// the block is absent and arms them when it is present, so a guess here
    /// would cost real points on an axis nobody claimed to know about.
    @State private var weatherBand: WeatherBand?
    @State private var rain = false
    @State private var timeOfDay: TimeOfDay = ScoreSheetView.clockTime()
    @State private var intent = ""
    @State private var showIntentSheet = false

    // Photos.
    @State private var capturedBytes: Data?
    @State private var capturedImage: UIImage?
    @State private var backBytes: Data?
    @State private var backImage: UIImage?
    @State private var showCamera = false
    @State private var showBackCamera = false
    @State private var pickerItem: PhotosPickerItem?
    @State private var backPickerItem: PhotosPickerItem?

    @State private var busy = false
    @State private var error: String?
    @State private var scoredResult: HemScored?
    @State private var scoredOutfitId: String?
    /// Set when the wearer taps "Wrong occasion?" on the reveal. The sheet comes
    /// back with their dials exactly where they left them, and the CTA turns
    /// into a free re-score of that same row — the engine replays over the
    /// stored witness statement, so there is no model call and no credit.
    @State private var rebriefOutfitId: String?
    @State private var rescoresLeft = 0

    @EnvironmentObject var session: SessionStore
    @ObservedObject private var credits = CreditsBus.shared
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    // MARK: - Derived brief

    /// What goes on the wire. `has_back` is server-derived from `back_url`, so
    /// it is never sent.
    private var requestIntake: ScoreIntake {
        ScoreIntake(
            occasion: occasion,
            formality: formality,
            presence: presence,
            role: role,
            venue: venue,
            room: room,
            on_feet: onFeet,
            intent: intent.isEmpty ? nil : intent,
            weather: weatherBand.map { ScoreWeather(band: $0, precip: rain) },
            time_of_day: timeOfDay
        )
    }

    /// The same brief plus what we know about the back view — the preview has to
    /// model the fit penalty the server will apply, or the strip lies.
    private var previewIntake: ScoreIntake {
        var i = requestIntake
        i.has_back = backBytes != nil
        return i
    }

    /// What gets stored on the row, so a re-score replays the brief exactly.
    private var storedIntake: ScoreIntake { previewIntake }

    private var primaryAxes: [AxisKey] {
        RubricWeights.primaryAxes(for: previewIntake, intentAxis: previewIntake.intentChipAxis)
    }

    private var cost: Int { Supa.scoreCost + (backBytes == nil ? 0 : Supa.frontBackExtraCost) }

    var body: some View {
        ZStack(alignment: .top) {
            Palette.paper.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    header
                    photoBand
                    occasionBand
                    DialControl(
                        eyebrow: "HOW DRESSED UP",
                        leftLabel: "Easy",
                        rightLabel: "Formal",
                        captions: Rubric.formalityCaptions[occasion] ?? [],
                        selection: $formality
                    )
                    DialControl(
                        eyebrow: "HOW MUCH DO YOU WANT TO BE LOOKED AT",
                        leftLabel: "Blend in",
                        rightLabel: "Be looked at",
                        captions: Rubric.presenceCaptions,
                        selection: $presence
                    )
                    band4
                    extrasBand
                    if let error {
                        Text(error).font(Serif.body(13)).foregroundStyle(Palette.bronze)
                    }
                    ctaBand
                    Spacer(minLength: 30)
                }
                .padding(20)
            }

            if busy {
                WaitingOverlay(
                    eyebrow: "ANALYZING",
                    title: "Reading your fit",
                    tips: [
                        "Silhouette, palette, proportion…",
                        "Weighing fabric, fit, and mood.",
                        "Hem is being honest.",
                        "Measuring the look against your brief.",
                    ]
                )
            }

            if let scored = scoredResult, let oid = scoredOutfitId {
                ScoreRevealOverlay(
                    result: scored,
                    outfitId: oid,
                    onDone: { onScored(oid) },
                    onRescore: {
                        rescoresLeft = scored.rescoresRemaining ?? 0
                        rebriefOutfitId = oid
                        scoredResult = nil
                    }
                )
            }
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraCaptureView()
                .onDisappear {
                    if let d = CameraBus.shared.consume() { setBytes(d) }
                }
        }
        .fullScreenCover(isPresented: $showBackCamera) {
            CameraCaptureView()
                .onDisappear {
                    if let d = CameraBus.shared.consume() { setBackBytes(d) }
                }
        }
        .onChange(of: pickerItem) { _, item in
            Task {
                if let item, let d = try? await item.loadTransferable(type: Data.self) {
                    setBytes(CameraModel.processJpeg(d) ?? d)
                }
            }
        }
        .onChange(of: backPickerItem) { _, item in
            Task {
                if let item, let d = try? await item.loadTransferable(type: Data.self) {
                    setBackBytes(CameraModel.processJpeg(d) ?? d)
                }
            }
        }
        .onChange(of: occasion) { _, new in
            // The snap is the first visible proof that the chip did something:
            // both dials move to the new occasion's seed under the finger.
            let f = Rubric.seedFormality[new] ?? 3
            let p = Rubric.seedPresence[new] ?? 3
            if reduceMotion {
                formality = f; presence = p
            } else {
                withAnimation(.spring(response: 0.34, dampingFraction: 0.78)) {
                    formality = f; presence = p
                }
            }
        }
        .sheet(isPresented: $showIntentSheet) {
            IntentEntrySheet(intent: $intent, onClose: { showIntentSheet = false })
                .presentationDetents([.medium, .large])
        }
        .task {
            if let d = CameraBus.shared.consume() { setBytes(d) }
            await credits.refresh()
        }
    }

    // MARK: - Header

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 6) {
                Eyebrow(text: "SCORE SHEET")
                Text("Score a look")
                    .font(Serif.display(28))
                    .foregroundStyle(Palette.ink)
                Text("Set the brief, then Hem grades the look against it.")
                    .font(Serif.body(14))
                    .foregroundStyle(Palette.muted)
            }
            Spacer()
            CircleCloseButton(action: onClose)
        }
    }

    // MARK: - Band 0 — photo

    private var photoBand: some View {
        VStack(alignment: .leading, spacing: 12) {
            photoTile
            HStack(spacing: 10) {
                PhotosPicker(selection: $pickerItem, matching: .images) {
                    OutlinePillButton(title: "From gallery")
                }
                Button(action: {
                    Haptic.tap()
                    showCamera = true
                }) { OutlinePillButton(title: "Take photo") }
                .buttonStyle(.plain)
            }
            backRow
        }
    }

    private var photoTile: some View {
        DashedDropZone(corner: 20, aspect: 0.82, dashed: capturedImage == nil) {
            Group {
                if let img = capturedImage {
                    Image(uiImage: img).resizable().scaledToFill()
                } else {
                    VStack(spacing: 6) {
                        Text("Drop the look here")
                            .font(Serif.body(15, weight: .medium))
                            .foregroundStyle(Palette.ink)
                        Text("Full length, straight on, decent light.")
                            .font(Serif.italic(13))
                            .foregroundStyle(Palette.muted)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
        }
    }

    private var backRow: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Button(action: { Haptic.tap(); showBackCamera = true }) {
                    pill(
                        title: backImage == nil ? "Add back view" : "Back view added",
                        icon: backImage == nil ? "arrow.uturn.backward" : "checkmark",
                        filled: backImage != nil
                    )
                }
                .buttonStyle(.plain)

                if backImage == nil {
                    PhotosPicker(selection: $backPickerItem, matching: .images) {
                        pill(title: "From gallery", icon: nil, filled: false)
                    }
                } else {
                    Button(action: {
                        Haptic.chip()
                        backBytes = nil
                        backImage = nil
                        backPickerItem = nil
                    }) {
                        pill(title: "Remove", icon: nil, filled: false)
                    }
                    .buttonStyle(.plain)
                }
            }
            Text(backImage == nil
                 ? "Without a back view the fit read is a partial one, and it is weighted as one."
                 : "Front + back scored together · +\(Supa.frontBackExtraCost) credits")
                .font(Serif.italic(12))
                .foregroundStyle(Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    // MARK: - Band 1 — occasion

    private var occasionBand: some View {
        FlowChipRow(
            eyebrow: "WHAT'S THIS FOR",
            options: ScoreOccasion.allCases,
            selection: $occasion,
            label: { $0.title }
        )
    }

    // MARK: - Band 4 — the occasion's own question

    @ViewBuilder
    private var band4: some View {
        switch occasion {
        case .wedding:
            VStack(alignment: .leading, spacing: 18) {
                FlowChipRow(
                    eyebrow: "WHOSE DAY IS IT",
                    options: WeddingRole.allCases,
                    selection: $role,
                    label: { $0.label }
                )
                FlowChipRow(
                    eyebrow: "THE ROOM",
                    options: WeddingVenue.allCases,
                    selection: $venue,
                    label: { $0.label }
                )
            }
        case .work:
            FlowChipRow(
                eyebrow: "WHAT KIND OF ROOM",
                options: WorkRoom.allCases,
                selection: $room,
                label: { $0.label }
            )
        case .everyday:
            FlowChipRow(
                eyebrow: "ON YOUR FEET TODAY",
                options: OnFeet.allCases,
                selection: $onFeet,
                label: { $0.label }
            )
        case .date, .casual:
            // Deliberately nothing. Neither occasion has a fourth question that
            // moves a weight, and inventing one would teach the user that the
            // controls are decoration.
            EmptyView()
        }
    }

    // MARK: - Band 5 — weather, time, intent

    private var extrasBand: some View {
        VStack(alignment: .leading, spacing: 12) {
            Eyebrow(text: "ANYTHING ELSE")
            weatherRow
            Text(weatherBand == nil
                 ? "Weather not set — Hem will not judge the look for the day it is."
                 : "Tap the band again to unset it.")
                .font(Serif.italic(12))
                .foregroundStyle(Palette.muted)

            Button(action: {
                Haptic.chip()
                timeOfDay = timeOfDay == .daytime ? .evening : .daytime
            }) {
                Text("Grading as \(timeOfDay.label.lowercased()) · tap to change")
                    .font(Serif.body(13))
                    .foregroundStyle(Palette.ink)
                    .underline(true, color: Palette.bronze.opacity(0.6))
            }
            .buttonStyle(.plain)

            HStack(spacing: 8) {
                Button(action: { Haptic.tap(); showIntentSheet = true }) {
                    pill(
                        title: intent.isEmpty ? "Add intent" : truncate(intent, 30),
                        icon: nil,
                        filled: !intent.isEmpty
                    )
                }
                .buttonStyle(.plain)
                if !intent.isEmpty {
                    Button(action: { Haptic.chip(); intent = "" }) {
                        pill(title: "Clear", icon: nil, filled: false)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var weatherRow: some View {
        HStack(spacing: 8) {
            ForEach(WeatherBand.allCases, id: \.self) { band in
                let active = weatherBand == band
                Button(action: {
                    Haptic.chip()
                    withAnimation(reduceMotion ? nil : .easeOut(duration: 0.18)) {
                        // Re-tapping unsets: "I don't know" has to stay reachable.
                        weatherBand = active ? nil : band
                        if weatherBand == nil { rain = false }
                    }
                }) {
                    pill(title: band.label, icon: nil, filled: active)
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(active ? [.isSelected] : [])
            }
            Button(action: {
                Haptic.chip()
                withAnimation(reduceMotion ? nil : .easeOut(duration: 0.18)) { rain.toggle() }
            }) {
                pill(title: "Rain", icon: rain ? "checkmark" : nil, filled: rain)
            }
            .buttonStyle(.plain)
            .disabled(weatherBand == nil)
            .opacity(weatherBand == nil ? 0.4 : 1)
        }
    }

    // MARK: - CTA + the free preview

    private var ctaBand: some View {
        VStack(alignment: .leading, spacing: 14) {
            if rebriefOutfitId != nil {
                PrimaryButton(
                    title: busy ? "Re-reading…" : "Score it again · free",
                    enabled: !busy,
                    action: rescore
                )
            } else {
                PrimaryButton(
                    title: busy ? "Analyzing…" : "Score it · \(cost) credits",
                    enabled: capturedBytes != nil && !busy,
                    action: score
                )
            }
            BriefLine(text: briefLine(previewIntake))
            RubricPreviewStrip(labels: primaryAxes.map(\.label))
            if rebriefOutfitId != nil {
                // The re-brief is free because it costs nothing to serve, but it
                // is capped — this tap is for correcting a wrong answer, not for
                // dialling until the number flatters you.
                Text(rescoresLeft > 0
                    ? "Fix the brief and I'll read it again — \(rescoresLeft) left, no credits."
                    : "No re-reads left on this look.")
                    .font(Serif.italic(12))
                    .foregroundStyle(Palette.muted)
            } else if let balance = credits.balance {
                Text("\(balance) credits left")
                    .font(Serif.body(12))
                    .foregroundStyle(Palette.muted)
            }
        }
    }

    // MARK: - Small parts

    private func pill(title: String, icon: String?, filled: Bool) -> some View {
        HStack(spacing: 6) {
            if let icon {
                Image(systemName: icon).font(.system(size: 11))
            }
            Text(title).font(Serif.body(13)).lineLimit(1)
        }
        .foregroundStyle(filled ? .white : Palette.ink)
        .padding(.horizontal, 14)
        .frame(minHeight: 38)
        .background(filled ? Palette.ink : Color.clear)
        .overlay(Capsule().stroke(filled ? Palette.ink : Palette.ink.opacity(0.5), lineWidth: 1))
        .clipShape(Capsule())
        .contentShape(Capsule())
    }

    private func truncate(_ s: String, _ n: Int) -> String {
        s.count <= n ? s : String(s.prefix(n)) + "…"
    }

    /// Evening after 17:00 and before 05:00 — a seed, not a claim; one tap
    /// overrides it.
    private static func clockTime() -> TimeOfDay {
        let h = Calendar.current.component(.hour, from: Date())
        return (h >= 17 || h < 5) ? .evening : .daytime
    }

    private func setBytes(_ d: Data) {
        capturedBytes = d
        capturedImage = UIImage(data: d)
    }

    private func setBackBytes(_ d: Data) {
        backBytes = d
        backImage = UIImage(data: d)
    }

    // MARK: - Re-brief

    /// Replay the engine over the row's stored witness statement with the
    /// corrected brief. No model call, no upload, no credit — the whole point is
    /// that the user gets to watch their own answer move the number.
    private func rescore() {
        guard let oid = rebriefOutfitId, !busy else { return }
        busy = true
        error = nil
        Task {
            do {
                // `/rescore` writes the row itself — the new brief, the recomputed
                // axes and the ledger — so there is nothing to persist here.
                let rescored = try await HemService.rescore(outfitId: oid, intake: storedIntake)
                Haptic.soft()
                rescoresLeft = rescored.rescoresRemaining ?? max(0, rescoresLeft - 1)
                scoredOutfitId = oid
                scoredResult = rescored
                rebriefOutfitId = nil
            } catch {
                self.error = error.localizedDescription
                ToastBus.shared.post("Couldn't re-read that: \(error.localizedDescription)")
            }
            busy = false
        }
    }

    // MARK: - Score

    private func score() {
        guard let bytes = capturedBytes else { error = "Add a photo first"; return }
        guard !busy else { return }
        busy = true
        error = nil
        Task {
            let spend = cost
            let gate = await CreditsGate.check(spend)
            switch gate {
            case .ok: break
            default:
                busy = false
                if case .insufficientBalance = gate {
                    _ = CreditsGate.explainAndBlock(gate); onOpenPaywall()
                } else { _ = CreditsGate.explainAndBlock(gate) }
                return
            }
            do {
                let path = try await Repo.shared.uploadOutfitPhoto(bytes: bytes)
                guard let signed = try await Repo.shared.signedOutfitUrl(path) else {
                    throw NSError(domain: "Score", code: 1, userInfo: [NSLocalizedDescriptionKey: "Could not sign photo URL"])
                }
                var backPath: String?
                var backSigned: String?
                if let back = backBytes {
                    let p = try await Repo.shared.uploadOutfitPhoto(bytes: back)
                    backPath = p
                    backSigned = try await Repo.shared.signedOutfitUrl(p)
                }
                let profile = try? await Repo.shared.currentProfile()
                let honesty = profile?.honesty ?? "honest"
                let sent = requestIntake
                let scored = try await HemService.score(
                    imageUrl: signed,
                    occasion: sent.legacyOccasion,
                    honesty: honesty,
                    intake: sent,
                    intent: sent.intent,
                    backUrl: backSigned,
                    bodyProfile: session.bodyProfile,
                    clientFeatures: ["axes_v4", "xray_v4"],
                    locale: "en"
                )
                guard let uid = Repo.shared.userId else {
                    throw NSError(domain: "Score", code: 2, userInfo: [NSLocalizedDescriptionKey: "Not signed in"])
                }
                // The whole v4 envelope goes down with the row. `signals` and
                // `raw_axes` are the model's witness statement: without them the
                // free re-scores are gone, so they are written verbatim.
                let row = try await Repo.shared.insertOutfit(OutfitInsert(
                    user_id: uid,
                    photo_path: path,
                    score: scored.headlineScore,
                    occasion: sent.legacyOccasion,
                    hem_comment: scored.hemComment,
                    weather_c: nil,
                    verdict: scored.verdict,
                    subscores: scored.subscores,
                    swaps: scored.swaps,
                    annotations: scored.annotations,
                    kind: nil,
                    linked_piece_id: nil,
                    scoring_version: scored.scoringVersion ?? (scored.headline == nil ? nil : "v4"),
                    rubric_id: scored.rubric?.id,
                    rubric_version: scored.rubric?.version,
                    intake: storedIntake,
                    axes: scored.axes.isEmpty ? nil : scored.axes,
                    score_breakdown: scored.scoreBreakdown,
                    dress_code: scored.dressCode,
                    presence_check: scored.presenceCheck,
                    lever: scored.lever,
                    caveats: scored.caveats.isEmpty ? nil : scored.caveats,
                    pieces: scored.pieces.isEmpty ? nil : scored.pieces,
                    signals: scored.signals,
                    raw_axes: scored.rawAxes,
                    intent: scored.intent ?? sent.intent,
                    back_photo_path: backPath,
                    fits_you: scored.fitsYou,
                    dominant_colors: scored.paletteHex.isEmpty ? nil : scored.paletteHex
                ))
                if let oid = row.id {
                    if !scored.markupAnnotations.isEmpty {
                        try? await Repo.shared.saveAnnotations(outfitId: oid, scored.markupAnnotations)
                    }
                    if let fm = scored.fitMap {
                        try? await Repo.shared.saveFitMap(outfitId: oid, fm)
                    }
                }
                try? await Repo.shared.spendCredits(amount: spend, kind: "outfit_score")
                credits.refreshAsync()
                Haptic.soft()
                busy = false
                guard let oid = row.id else {
                    // The row is saved but we have no id to re-score or open
                    // against; close rather than show a reveal that leads nowhere.
                    ToastBus.shared.post("Scored — find it in your journal")
                    onClose()
                    return
                }
                scoredOutfitId = oid
                scoredResult = scored
            } catch {
                self.error = error.localizedDescription
                ToastBus.shared.post("Scoring failed: \(error.localizedDescription)")
                busy = false
            }
        }
    }
}

// MARK: - Brief sentence

/// Client-side port of `briefLine()` from
/// `supabase/functions/_shared/rubric/brief.ts`. Composed from a format string
/// with named slots, never by concatenation, so word order survives
/// translation — and so the sentence under the CTA is the same sentence the
/// server will echo back on `rubric.brief_line`.
private let BRIEF_FORMAT = "{occasion} look{role}{qualifier}, meant to read {presence}."

private let OCCASION_NP: [ScoreOccasion: String] = [
    .work: "A work",
    .date: "A date",
    .wedding: "A wedding",
    .casual: "A casual",
    .everyday: "An everyday",
]

private let ROLE_SLOT: [WeddingRole: String] = [
    .guest: ", as a guest",
    .wedding_party: ", in the wedding party",
    .couple: ", as the couple",
]

/// "meant to read ___" — adjectives, not the dial captions, because "meant to
/// read be looked at" is not a sentence.
private let PRESENCE_ADJ = ["invisible", "quiet", "balanced", "noticed", "unmissable"]

private func briefLine(_ intake: ScoreIntake) -> String {
    let caption = intake.formalityCaption
    let slots: [String: String] = [
        "occasion": OCCASION_NP[intake.occasion] ?? "A",
        "role": intake.occasion == .wedding ? (intake.role.flatMap { ROLE_SLOT[$0] } ?? "") : "",
        "qualifier": caption.isEmpty ? "" : ", for \(caption.lowercased())",
        "presence": (1...PRESENCE_ADJ.count).contains(intake.presence)
            ? PRESENCE_ADJ[intake.presence - 1] : "balanced",
    ]
    var out = BRIEF_FORMAT
    for (key, value) in slots {
        out = out.replacingOccurrences(of: "{\(key)}", with: value)
    }
    return out
        .replacingOccurrences(of: " +", with: " ", options: .regularExpression)
        .trimmingCharacters(in: .whitespaces)
}

// MARK: - Intent entry sheet

/// The four chips are the intents the client can map to an axis on its own, so
/// picking one moves the preview strip immediately. Free text is the escape
/// hatch: the server maps it and answers on `intent_axis`.
struct IntentEntrySheet: View {
    @Binding var intent: String
    var onClose: () -> Void
    @State private var text: String = ""
    @FocusState private var focused: Bool

    private let limit = 140

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Eyebrow(text: "SAY THE INTENT")
                Spacer()
                CircleCloseButton(action: onClose)
            }
            Text("What are you going for?")
                .font(Serif.display(22))
                .foregroundStyle(Palette.ink)
            Text("One line. Hem weighs the critique against it instead of a generic aesthetic.")
                .font(Serif.italic(13))
                .foregroundStyle(Palette.muted)

            FlowLayout(spacing: 8) {
                ForEach(Rubric.intentChips) { chip in
                    let active = text.caseInsensitiveCompare(chip.label) == .orderedSame
                    Button(action: {
                        Haptic.chip()
                        text = active ? "" : chip.label
                    }) {
                        Text(chip.label)
                            .font(Serif.body(13))
                            .foregroundStyle(active ? .white : Palette.ink)
                            .lineLimit(1)
                            .padding(.horizontal, 14)
                            .frame(minHeight: 38)
                            .background(active ? Palette.ink : Color.clear)
                            .overlay(Capsule().stroke(active ? Palette.ink : Palette.ink.opacity(0.5), lineWidth: 1))
                            .clipShape(Capsule())
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }

            TextField(
                "e.g. put-together but not trying too hard",
                text: $text,
                axis: .vertical
            )
            .font(Serif.body(15))
            .lineLimit(3, reservesSpace: true)
            .focused($focused)
            .padding(12)
            .background(Palette.card)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.hairline, lineWidth: 1))
            .onChange(of: text) { _, new in
                if new.count > limit { text = String(new.prefix(limit)) }
            }

            Text("\(text.count)/\(limit)")
                .font(Serif.body(11))
                .foregroundStyle(Palette.muted)

            Spacer(minLength: 0)

            HStack(spacing: 8) {
                Button(action: { text = ""; intent = ""; onClose() }) {
                    OutlinePillButton(title: "Clear")
                }
                .buttonStyle(.plain)
                PrimaryButton(title: "Use this", enabled: !text.trimmingCharacters(in: .whitespaces).isEmpty) {
                    intent = text.trimmingCharacters(in: .whitespaces)
                    onClose()
                }
            }
        }
        .padding(20)
        .background(Palette.paper.ignoresSafeArea())
        .onAppear {
            text = intent
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { focused = true }
        }
    }
}

// MARK: - Shared widgets used by camera flows

/// Full-screen "please wait" overlay shown while Score / Try-on is running.
/// Editorial look: cream paper backdrop, big serif title with animated dots,
/// rotating italic tip below, subtle bronze spinner.
struct WaitingOverlay: View {
    let eyebrow: String
    let title: String
    let tips: [String]

    @State private var dotPhase = 0
    @State private var tipIndex = 0
    private let dotTimer = Timer.publish(every: 0.5, on: .main, in: .common).autoconnect()
    private let tipTimer = Timer.publish(every: 3.0, on: .main, in: .common).autoconnect()

    var body: some View {
        ZStack {
            Palette.paper.opacity(0.98).ignoresSafeArea()
            VStack(spacing: 20) {
                Spacer()
                Eyebrow(text: eyebrow)
                Text(title + String(repeating: "·", count: dotPhase))
                    .font(Serif.display(30))
                    .foregroundStyle(Palette.ink)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 30)
                if !tips.isEmpty {
                    Text(tips[tipIndex % tips.count])
                        .font(Serif.italic(16))
                        .foregroundStyle(Palette.muted)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                        .transition(.opacity)
                        .id(tipIndex)
                }
                ProgressView().tint(Palette.bronze).padding(.top, 6)
                Spacer()
                Text("This usually takes 10–20 seconds. Please keep the app open.")
                    .font(Serif.body(12))
                    .foregroundStyle(Palette.muted)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)
                    .padding(.bottom, 30)
            }
        }
        .transition(.opacity)
        .onReceive(dotTimer) { _ in
            dotPhase = (dotPhase + 1) % 4
        }
        .onReceive(tipTimer) { _ in
            withAnimation(.easeInOut(duration: 0.4)) {
                tipIndex += 1
            }
        }
    }
}

struct OutlinePillButton: View {
    let title: String
    var body: some View {
        Text(title)
            .font(Serif.body(14, weight: .medium))
            .foregroundStyle(Palette.ink)
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .overlay(Capsule().stroke(Palette.ink.opacity(0.5), lineWidth: 1))
    }
}

struct ChipRow: View {
    let options: [String]
    @Binding var selection: String
    var body: some View {
        HStack(spacing: 8) {
            ForEach(options, id: \.self) { opt in
                let selected = selection == opt
                Button(action: {
                    Haptic.chip()
                    selection = opt
                }) {
                    Text(opt)
                        .font(Serif.body(13))
                        .foregroundStyle(selected ? .white : Palette.ink)
                        .frame(maxWidth: .infinity, minHeight: 38)
                        .background(selected ? Palette.ink : Color.clear)
                        .overlay(Capsule().stroke(Palette.ink.opacity(0.5), lineWidth: 1))
                        .clipShape(Capsule())
                }
            }
        }
    }
}
