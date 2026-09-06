import SwiftUI
import PhotosUI
import Vision
import CoreImage

/// A vs B — two drop zones, a focus-criteria picker, an analysing pass and
/// Hem's call with a per-criterion breakdown.
///
/// Two things happen on device before anything is uploaded: each shot gets a
/// scene tag (mirror · indoor / outdoor / low light) that both labels the card
/// and rides along to the judge as a hint, and the pair is perceptually hashed
/// so a near-duplicate returns a tie without spending credits.
/// Bytes persist across camera navigation via `VersusBus`.
struct VersusView: View {
    var onClose: () -> Void
    var onOpenPaywall: () -> Void = {}

    @State private var aBytes: Data? = VersusBus.shared.aBytes
    @State private var bBytes: Data? = VersusBus.shared.bBytes
    @State private var aScene: String?
    @State private var bScene: String?
    @State private var busy = false
    @State private var error: String?
    @State private var result: CompareResponse?
    @State private var focus: Set<String> = ["physique", "pose", "light"]
    /// The one answer the photos can't contain: the standard they're judged
    /// against. "Other…" opens a sheet and the typed line is used verbatim.
    @State private var occasion = "Everyday"
    /// The same question answered in the user's own words. It rides alongside
    /// the chip as `intent` instead of replacing it, so the two reinforce.
    @State private var intent = ""
    @State private var showIntentSheet = false

    // Analysing pass — a paced progress read so the wait has structure.
    @State private var progress: Double = 0
    @State private var ticker: Task<Void, Never>?

    @State private var chooseSlot: String?  // "A" | "B"
    @State private var showCamera = false
    @State private var aPickerItem: PhotosPickerItem?
    @State private var bPickerItem: PhotosPickerItem?
    @State private var sharing: UIImage?

    @EnvironmentObject var credits: CreditsBus

    private static let allCriteria = ["physique", "pose", "light", "style", "vibe"]
    private static let occasions = ["Everyday", "Work", "Date", "Night out", "Wedding", "Travel"]

    /// Chip label -> the standard sent to the judge. Not a lowercased label:
    /// the server writes it into a sentence.
    private static let occasionValues: [String: String] = [
        "Everyday": "everyday", "Work": "work", "Date": "a date",
        "Night out": "a night out", "Wedding": "a wedding", "Travel": "travel",
    ]

    /// The same chip, expressed as the brief the scoring rubric understands.
    ///
    /// Six labels onto five occasions: "Night out" is a dressed-up date and
    /// "Travel" is everyday clothes you have to sit in for hours. Carrying a
    /// formality rung here is what lets one chip stand in for the occasion and
    /// the dial together — A vs B is a quick call, and a second control would
    /// cost more than the precision is worth.
    private static let occasionBriefs: [String: (ScoreOccasion, Int)] = [
        "Everyday": (.everyday, 2),
        "Work": (.work, 3),
        "Date": (.date, 3),
        "Night out": (.date, 4),
        "Wedding": (.wedding, 4),
        "Travel": (.everyday, 2),
    ]

    private var compareIntake: ScoreIntake {
        let (occ, formality) = Self.occasionBriefs[occasion] ?? (.everyday, 2)
        return ScoreIntake(occasion: occ, formality: formality)
    }
    /// Article-aware phrasing for our own copy — "for a everyday" is broken English.
    private static let occasionPhrases: [String: String] = [
        "Everyday": "for everyday wear", "Work": "for work", "Date": "for a date",
        "Night out": "for a night out", "Wedding": "for a wedding", "Travel": "for travel",
    ]

    private var occasionValue: String { Self.occasionValues[occasion] ?? occasion.lowercased() }

    /// "one step under" / "on the mark" — the gap in words, since the rungs
    /// themselves are an internal scale and mean nothing to a wearer.
    private func rungWord(_ read: Int, _ target: Int) -> String {
        let d = read - target
        if d == 0 { return "right on it" }
        let steps = abs(d) == 1 ? "a step" : "\(abs(d)) steps"
        return d < 0 ? "\(steps) under" : "\(steps) over"
    }
    private var occasionPhrase: String { Self.occasionPhrases[occasion] ?? "for \(occasion.lowercased())" }
    private var trimmedIntent: String {
        String(intent.trimmingCharacters(in: .whitespacesAndNewlines).prefix(160))
    }

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
                        Group {
                            if let r = result, let a = aBytes, let b = bBytes {
                                resultBlock(a: a, b: b, res: r)
                            } else if busy {
                                analysingBlock
                            } else {
                                inputBlock
                            }
                        }
                        .transition(.asymmetric(
                            insertion: .opacity.combined(with: .offset(y: 18)),
                            removal: .opacity
                        ))
                        Spacer(minLength: 24)
                    }
                    .animation(.spring(response: 0.5, dampingFraction: 0.88), value: phase)
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                }
                footerBar
            }
            if let slot = chooseSlot {
                sourcePicker(slot: slot)
            }
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraCaptureView()
                .onDisappear {
                    let (slot, bytes) = CameraBus.shared.consumeWithSlot()
                    if let bytes, let slot { setBytes(bytes, slot: slot) }
                }
        }
        .sheet(item: shareItemBinding) { item in
            ActivityShare(image: item.image)
        }
        .sheet(isPresented: $showIntentSheet) {
            IntentEntrySheet(intent: $intent, onClose: { showIntentSheet = false })
                .presentationDetents([.medium])
        }
        .onChange(of: aPickerItem) { _, item in
            Task {
                if let item, let d = try? await item.loadTransferable(type: Data.self) {
                    setBytes(CameraModel.processJpeg(d) ?? d, slot: "A")
                }
            }
        }
        .onChange(of: bPickerItem) { _, item in
            Task {
                if let item, let d = try? await item.loadTransferable(type: Data.self) {
                    setBytes(CameraModel.processJpeg(d) ?? d, slot: "B")
                }
            }
        }
        .onAppear {
            // Bytes can arrive from the bus before this view ever ran a tagger.
            if aScene == nil, let a = aBytes { tag(a, slot: "A") }
            if bScene == nil, let b = bBytes { tag(b, slot: "B") }
        }
        .onDisappear { ticker?.cancel() }
    }

    // MARK: - Header

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 8) {
                Eyebrow(text: "Hem picks")
                Text(title)
                    .font(Serif.display(40))
                    .foregroundStyle(Palette.ink)
                    .id("title-\(phase.rawValue)")
                    .transition(.asymmetric(
                        insertion: .opacity.combined(with: .offset(y: 10)),
                        removal: .opacity
                    ))
                Text(subtitle)
                    .font(Serif.body(15))
                    .foregroundStyle(Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
                    .id("sub-\(phase.rawValue)")
                    .transition(.opacity)
            }
            Spacer(minLength: 12)
            Button(action: {
                ticker?.cancel()
                VersusBus.shared.clear()
                onClose()
            }) {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Palette.ink)
                    .frame(width: 40, height: 40)
                    .background(Palette.card)
                    .clipShape(Circle())
            }
        }
    }

    /// Drives the cross-fade between the three states of the screen.
    private enum Phase: Int { case input, analysing, result }
    private var phase: Phase {
        if result != nil { return .result }
        return busy ? .analysing : .input
    }

    private var title: String {
        if result != nil { return "The call" }
        if busy { return "Analysing" }
        return "A vs B"
    }

    private var subtitle: String {
        if result != nil { return "Total scores and the breakdown by criterion." }
        if busy { return "Both frames are being scored on \(focus.count) criteria." }
        return "Drop two shots, say what they're for, and let Hem call it."
    }

    /// What each criterion actually measures — shown under the chips so the
    /// scores on the next screen arrive already explained.
    private static let criteriaGlossary: [String: String] = [
        "physique": "how clearly the body reads",
        "pose": "stance, angle and framing",
        "light": "where the light falls",
        "style": "the clothes and how they're worn",
        "vibe": "the presence of the shot",
    ]

    private var criteriaGloss: String {
        selectedCriteria
            .compactMap { key in Self.criteriaGlossary[key].map { "\(key.prefix(1).uppercased() + key.dropFirst()), \($0)" } }
            .joined(separator: " · ")
    }

    /// Criteria in canonical order, filtered to what the user picked.
    private var selectedCriteria: [String] {
        Self.allCriteria.filter { focus.contains($0) }
    }

    // MARK: - Input

    private var inputBlock: some View {
        VStack(alignment: .leading, spacing: 20) {
            HStack(spacing: 12) {
                DropZone(label: "A", bytes: aBytes, caption: aScene, onTap: { chooseSlot = "A" })
                DropZone(label: "B", bytes: bBytes, caption: bScene, onTap: { chooseSlot = "B" })
            }

            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("WHAT IT'S FOR")
                        .font(.system(size: 11, weight: .semibold))
                        .tracking(2)
                        .foregroundStyle(Palette.muted)
                    Spacer(minLength: 8)
                    NotePill(
                        note: trimmedIntent,
                        onTap: { showIntentSheet = true },
                        onClear: { intent = "" }
                    )
                }
                OccasionChips(all: Self.occasions, selection: $occasion)
            }

            VStack(alignment: .leading, spacing: 10) {
                Text("WHAT TO JUDGE ON")
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(2)
                    .foregroundStyle(Palette.muted)
                CriteriaChips(all: Self.allCriteria, selection: $focus)
                Text(criteriaGloss)
                    .font(Serif.italic(13))
                    .foregroundStyle(Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }

            NoteCard(text: tipCopy)

            if let error {
                Text(error).font(Serif.body(13)).foregroundStyle(Palette.bronze)
            }
        }
    }

    /// The tip earns its place: it warns about mismatched conditions when the
    /// on-device tags actually disagree, otherwise it states the house rules.
    private var tipCopy: String {
        let n = focus.count
        let scale = "\(n) \(n == 1 ? "criterion" : "criteria")"
        if let a = aScene, let b = bScene {
            let aOut = a.localizedCaseInsensitiveContains("outdoor")
            let bOut = b.localizedCaseInsensitiveContains("outdoor")
            let aIn = a.localizedCaseInsensitiveContains("indoor")
            let bIn = b.localizedCaseInsensitiveContains("indoor")
            if (aOut && bIn) || (aIn && bOut) {
                return "One indoors, one out. Hem scores light on its own so the setting doesn't decide the whole call — judged \(occasionPhrase), on \(scale)."
            }
        }
        if !trimmedIntent.isEmpty {
            return "Hem judges both shots against your note, on \(scale). Same light and a full-body frame give the sharpest call."
        }
        return "Hem judges both shots \(occasionPhrase), on \(scale). Same light, similar pose and a full-body frame give the sharpest call."
    }

    // MARK: - Analysing

    /// Checklist mirrors what is actually being judged this run.
    private var steps: [String] {
        var out = [trimmedIntent.isEmpty
            ? "Reading the brief — \(occasion.lowercased())"
            : "Reading your note"]
        let picked = selectedCriteria
        if let first = picked.first { out.append("Reading \(first)") }
        let rest = Array(picked.dropFirst())
        if !rest.isEmpty {
            let phrase: String
            if rest.count == 1 { phrase = rest[0] }
            else { phrase = rest.dropLast().joined(separator: ", ") + " and " + rest[rest.count - 1] }
            out.append("Measuring \(phrase)")
        }
        out.append("Comparing scores")
        return out
    }

    private var analysingBlock: some View {
        VStack(alignment: .leading, spacing: 22) {
            HStack(spacing: 12) {
                DropZone(label: "A", bytes: aBytes, caption: "Reading", dimmed: true, onTap: {})
                DropZone(label: "B", bytes: bBytes, caption: "Reading", dimmed: true, onTap: {})
            }

            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .firstTextBaseline) {
                    Text("HEM IS THINKING")
                        .font(.system(size: 11, weight: .semibold))
                        .tracking(2)
                        .foregroundStyle(Palette.bronze)
                    Spacer()
                    Text("\(Int(progress * 100))%")
                        .font(Serif.display(26))
                        .foregroundStyle(Palette.ink)
                        .monospacedDigit()
                        .contentTransition(.numericText())
                }
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Palette.hairline).frame(height: 4)
                        Capsule().fill(Palette.ink)
                            .frame(width: max(4, geo.size.width * progress), height: 4)
                    }
                }
                .frame(height: 4)
            }

            VStack(alignment: .leading, spacing: 14) {
                let all = steps
                ForEach(Array(all.enumerated()), id: \.offset) { idx, step in
                    let done = progress >= Double(idx + 1) / Double(all.count) * 0.95
                    HStack(spacing: 12) {
                        ZStack {
                            Circle()
                                .fill(done ? Palette.ink : Color.clear)
                                .frame(width: 26, height: 26)
                            Circle()
                                .stroke(done ? Palette.ink : Palette.bronze, lineWidth: 1.5)
                                .frame(width: 26, height: 26)
                            if done {
                                Image(systemName: "checkmark")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundStyle(.white)
                            }
                        }
                        Text(step)
                            .font(Serif.body(15))
                            .foregroundStyle(done ? Palette.ink : Palette.muted)
                    }
                    .animation(.easeOut(duration: 0.25), value: done)
                }
            }

            if let error {
                Text(error).font(Serif.body(13)).foregroundStyle(Palette.bronze)
            }
        }
    }

    // MARK: - Result

    @ViewBuilder
    private func resultBlock(a: Data, b: Data, res: CompareResponse) -> some View {
        let winner = res.winner ?? "tie"
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 12) {
                DropZone(
                    label: "A", bytes: a,
                    caption: caption(for: "A", winner: winner),
                    score: res.totalA,
                    highlighted: winner == "A",
                    onTap: {}
                )
                .onLongPressGesture { share(res: res) }
                DropZone(
                    label: "B", bytes: b,
                    caption: caption(for: "B", winner: winner),
                    score: res.totalB,
                    highlighted: winner == "B",
                    onTap: {}
                )
                .onLongPressGesture { share(res: res) }
            }

            if !res.verdictText.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text(verdictEyebrow(winner: winner, res: res))
                        .font(.system(size: 11, weight: .semibold))
                        .tracking(2)
                        .foregroundStyle(.white.opacity(0.6))
                    HStack(alignment: .top, spacing: 12) {
                        if winner != "tie" {
                            Text(winner)
                                .font(Serif.display(30))
                                .foregroundStyle(Palette.goldA)
                        }
                        Text(res.verdictText)
                            .font(Serif.body(15))
                            .foregroundStyle(.white)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .padding(18)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Palette.ink)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .modifier(RiseIn(delay: 0.06))
            }

            VStack(alignment: .leading, spacing: 8) {
                Text(judgedForLine(res))
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(2)
                    .foregroundStyle(Palette.muted)
                if let why = res.why, !why.isEmpty {
                    // Written from the weights, not by the model, so the reason
                    // and the numbers below it can never disagree.
                    Text(why)
                        .font(Serif.italic(16))
                        .foregroundStyle(Palette.ink)
                        .fixedSize(horizontal: false, vertical: true)
                } else if let swing = swingLine(res) {
                    Text(swing)
                        .font(Serif.body(14))
                        .foregroundStyle(Palette.ink)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if let dc = res.dress_code, let ra = dc.read_a, let rb = dc.read_b,
                   let target = dc.target, let caption = dc.caption {
                    Text("Dress code · you're dressing for \(caption.lowercased()) — A reads \(rungWord(ra, target)), B reads \(rungWord(rb, target)).")
                        .font(Serif.body(13))
                        .foregroundStyle(Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 2)
                }
            }
            .modifier(RiseIn(delay: 0.1))

            if let rows = res.axes ?? res.criteria, !rows.isEmpty {
                VStack(alignment: .leading, spacing: 18) {
                    Text("THE BREAKDOWN")
                        .font(.system(size: 11, weight: .semibold))
                        .tracking(2)
                        .foregroundStyle(Palette.muted)
                    ForEach(Array(rows.enumerated()), id: \.element.key) { idx, row in
                        CriterionBar(row: row, index: idx)
                    }
                    if rows.contains(where: { !$0.note_a.isEmpty || !$0.note_b.isEmpty }) {
                        Text("Each line is what Hem could actually see on that garment.")
                            .font(Serif.body(12))
                            .foregroundStyle(Palette.muted)
                    }
                    HStack(spacing: 18) {
                        LegendDot(color: Palette.ink, label: "A")
                        LegendDot(color: Palette.bronze, label: "B")
                    }
                    .modifier(RiseIn(delay: 0.12 + Double(rows.count) * 0.07))
                }
            }

            if let tip = res.next_shot, !tip.isEmpty {
                NoteCard(title: "For your next shot:", text: tip, showIcon: false)
                    .modifier(RiseIn(delay: 0.2 + Double(res.criteria?.count ?? 0) * 0.07))
            }

            if let error {
                Text(error).font(Serif.body(13)).foregroundStyle(Palette.bronze)
            }
        }
    }

    private func caption(for slot: String, winner: String) -> String {
        if winner == "tie" { return "Tie" }
        return winner == slot ? "Winner" : "Runner-up"
    }

    /// The result has to answer the same question the input asked — what this
    /// was judged for, and on which criteria. Server echo first, local fallback.
    private func judgedForLine(_ res: CompareResponse) -> String {
        let note = res.intent?.isEmpty == false ? res.intent! : trimmedIntent
        if !note.isEmpty {
            let rows = res.focus ?? res.criteria?.map(\.key) ?? selectedCriteria
            let names = rows.map { $0.prefix(1).uppercased() + $0.dropFirst() }.joined(separator: ", ")
            return "JUDGED ON YOUR NOTE · \(names.uppercased())"
        }
        let what = (res.occasion?.isEmpty == false ? res.occasion! : occasionValue)
        let rows = res.focus ?? res.criteria?.map(\.key) ?? selectedCriteria
        let names = rows.map { $0.prefix(1).uppercased() + $0.dropFirst() }.joined(separator: ", ")
        return "JUDGED FOR \(what.uppercased()) · \(names.uppercased())"
    }

    /// A total is an average; it never says which read moved it. This names the
    /// criterion that opened the widest gap, so the number has a cause.
    private func swingLine(_ res: CompareResponse) -> String? {
        guard let rows = res.criteria, rows.count > 1 else { return nil }
        guard let top = rows.max(by: { abs($0.a - $0.b) < abs($1.a - $1.b) }) else { return nil }
        let gap = abs(top.a - top.b)
        guard gap >= 4 else {
            return "No single read separates them — every criterion lands within a few points."
        }
        let side = top.a > top.b ? "A" : "B"
        return "\(top.title) decided it — \(side) takes that read by \(gap) points."
    }

    private func verdictEyebrow(winner: String, res: CompareResponse) -> String {
        if winner == "tie" { return "Too close to call" }
        switch res.confidence {
        case "medium": return "Winning shot · narrow"
        case "low":    return "Winning shot · close call"
        default:       return "Winning shot"
        }
    }

    // MARK: - Footer

    private var footerBar: some View {
        VStack(spacing: 10) {
            if result != nil {
                PrimaryButton(title: "New comparison", action: reset)
            } else {
                PrimaryButton(
                    title: busy ? "Analysing…" : "Let Hem call it · \(Supa.versusCost) credits",
                    enabled: aBytes != nil && bBytes != nil && !busy,
                    action: run
                )
            }
            Text(footerNote)
                .font(Serif.body(12))
                .foregroundStyle(Palette.muted)
                .id("note-\(phase.rawValue)")
                .transition(.opacity)
        }
        .animation(.easeInOut(duration: 0.28), value: phase)
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 8)
        .background(Palette.paper)
    }

    private var footerNote: String {
        if result != nil { return "Long-press a card to share the result" }
        if busy { return "Your photos are not stored" }
        let judged = trimmedIntent.isEmpty ? "Judged \(occasionPhrase)" : "Judged on your note"
        if let bal = credits.balance { return "\(judged) · \(bal) credits left" }
        return "\(judged) · result in ~5 seconds"
    }

    // MARK: - Photo intake

    private func setBytes(_ data: Data, slot: String) {
        if slot == "A" {
            aBytes = data; VersusBus.shared.aBytes = data; aScene = nil
        } else {
            bBytes = data; VersusBus.shared.bBytes = data; bScene = nil
        }
        tag(data, slot: slot)
    }

    private func tag(_ data: Data, slot: String) {
        Task {
            let t = await ShotTagger.tag(data)
            await MainActor.run {
                if slot == "A" { aScene = t } else { bScene = t }
            }
        }
    }

    // MARK: - Actions

    private func reset() {
        Haptic.tap()
        ticker?.cancel()
        result = nil
        error = nil
        progress = 0
        aBytes = nil
        bBytes = nil
        aScene = nil
        bScene = nil
        intent = ""
        aPickerItem = nil
        bPickerItem = nil
        VersusBus.shared.clear()
    }

    private func startTicker() {
        ticker?.cancel()
        progress = 0
        ticker = Task { @MainActor in
            // Ease toward 92% over ~6s; `run` snaps it to 100% on completion.
            while !Task.isCancelled && progress < 0.92 {
                try? await Task.sleep(nanoseconds: 120_000_000)
                if Task.isCancelled { return }
                withAnimation(.linear(duration: 0.12)) {
                    progress = min(0.92, progress + (0.92 - progress) * 0.045 + 0.004)
                }
            }
        }
    }

    private func finishTicker() {
        ticker?.cancel()
        withAnimation(.easeOut(duration: 0.2)) { progress = 1 }
    }

    private func run() {
        guard let a = aBytes, let b = bBytes, !busy else { return }
        Haptic.tap()
        busy = true
        error = nil
        startTicker()
        Task {
            let gate = await CreditsGate.check(Supa.versusCost)
            if case .ok = gate {} else {
                busy = false
                ticker?.cancel()
                if case .insufficientBalance = gate { _ = CreditsGate.explainAndBlock(gate); onOpenPaywall() }
                else { _ = CreditsGate.explainAndBlock(gate) }
                return
            }
            // Same or near-identical photo — answer locally, charge nothing.
            let duplicate = a == b ? true : await ShotTagger.nearDuplicate(a, b)
            if duplicate {
                let flat = selectedCriteria.map { CompareCriterion(key: $0, a: 80, b: 80) }
                result = CompareResponse(
                    winner: "tie", score_a: 8.0, score_b: 8.0,
                    comment: "Same frame, twice. Call it a draw.",
                    reason_a: "Same shot as B.", reason_b: "Same shot as A.",
                    total_a: 80, total_b: 80, criteria: flat,
                    verdict: "These are the same frame. Nothing separates them — no credits spent.",
                    next_shot: "Drop a genuinely different second shot — change the angle, the light or the fit — and Hem can actually pick.",
                    confidence: "low",
                    occasion: occasionValue,
                    focus: selectedCriteria
                )
                finishTicker()
                busy = false
                return
            }
            do {
                let aPath = try await Repo.shared.uploadOutfitPhoto(bytes: a)
                let bPath = try await Repo.shared.uploadOutfitPhoto(bytes: b)
                guard let aSigned = try await Repo.shared.signedOutfitUrl(aPath),
                      let bSigned = try await Repo.shared.signedOutfitUrl(bPath) else {
                    throw NSError(domain: "Versus", code: 1, userInfo: [NSLocalizedDescriptionKey: "Sign failed"])
                }
                // The vision endpoint reads exactly one image, so the pair is
                // rendered into a single contact sheet — twice, once per order,
                // so neither position can decide the winner.
                guard let abSheet = ContactSheet.render(left: a, right: b),
                      let baSheet = ContactSheet.render(left: b, right: a) else {
                    throw NSError(domain: "Versus", code: 3, userInfo: [NSLocalizedDescriptionKey: "Could not prepare the pair"])
                }
                async let abPathTask = Repo.shared.uploadOutfitPhoto(bytes: abSheet)
                async let baPathTask = Repo.shared.uploadOutfitPhoto(bytes: baSheet)
                let abPath = try await abPathTask
                let baPath = try await baPathTask
                guard let abSigned = try await Repo.shared.signedOutfitUrl(abPath),
                      let baSigned = try await Repo.shared.signedOutfitUrl(baPath) else {
                    throw NSError(domain: "Versus", code: 1, userInfo: [NSLocalizedDescriptionKey: "Sign failed"])
                }
                let res = await HemService.compare(
                    aUrl: aSigned, bUrl: bSigned, occasion: occasionValue,
                    focus: selectedCriteria,
                    sceneA: aScene, sceneB: bScene,
                    intent: trimmedIntent,
                    sheetAB: abSigned, sheetBA: baSigned,
                    intake: compareIntake
                )
                if let err = res.error {
                    throw NSError(domain: "Versus", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hem: \(err) — \(res.detail ?? "")"])
                }
                try? await Repo.shared.spendCredits(amount: Supa.versusCost, kind: "versus")
                await persist(res: res, aPath: aPath, bPath: bPath)
                finishTicker()
                result = res
                busy = false
                Haptic.tap()
            } catch {
                ticker?.cancel()
                self.error = error.localizedDescription
                ToastBus.shared.post("A vs B failed: \(error.localizedDescription)")
                busy = false
            }
        }
    }

    /// Every call is kept: the winner's frame becomes the journal row, and the
    /// whole comparison — both paths, both totals, the breakdown, the brief —
    /// rides along in `signals.versus` so the pair can be rebuilt anywhere.
    private func persist(res: CompareResponse, aPath: String, bPath: String) async {
        guard let uid = Repo.shared.userId else { return }
        let winner = res.winner ?? "tie"
        let winnerPath = winner == "B" ? bPath : aPath
        let top = max(res.totalA, res.totalB)

        var versus: [String: JSONValue] = [
            "winner": .string(winner),
            "total_a": .number(Double(res.totalA)),
            "total_b": .number(Double(res.totalB)),
            "a_path": .string(aPath),
            "b_path": .string(bPath),
            "occasion": .string(res.occasion ?? occasionValue),
            "focus": .array((res.focus ?? selectedCriteria).map { .string($0) }),
            "confidence": .string(res.confidence ?? ""),
        ]
        if !trimmedIntent.isEmpty { versus["intent"] = .string(trimmedIntent) }
        if let rows = res.criteria, !rows.isEmpty {
            versus["criteria"] = .array(rows.map {
                .object([
                    "key": .string($0.key),
                    "a": .number(Double($0.a)), "b": .number(Double($0.b)),
                    "note_a": .string($0.note_a), "note_b": .string($0.note_b),
                ])
            })
        }

        let insert = OutfitInsert(
            user_id: uid,
            photo_path: winnerPath,
            score: Double(top) / 10,
            occasion: occasion,
            hem_comment: res.verdictText,
            weather_c: nil,
            verdict: res.next_shot,
            kind: "versus",
            signals: .object(["versus": .object(versus)])
        )
        _ = try? await Repo.shared.insertOutfit(insert)
    }


    @MainActor
    private func share(res: CompareResponse) {
        guard let a = aBytes, let b = bBytes else { return }
        Haptic.tap()
        let card = VersusShareCard(
            aImage: UIImage(data: a), bImage: UIImage(data: b), res: res
        )
        let renderer = ImageRenderer(content: card)
        renderer.scale = 2
        renderer.proposedSize = ProposedViewSize(width: 540, height: 960)
        if let img = renderer.uiImage { sharing = img }
    }

    private struct ShareItem: Identifiable { let id = UUID(); let image: UIImage }
    private var shareItemBinding: Binding<ShareItem?> {
        Binding(get: { sharing.map { ShareItem(image: $0) } }, set: { sharing = $0?.image })
    }

    // MARK: - Source picker

    @ViewBuilder
    private func sourcePicker(slot: String) -> some View {
        ZStack {
            Color.black.opacity(0.35).ignoresSafeArea()
                .onTapGesture { chooseSlot = nil }
            VStack(alignment: .leading, spacing: 16) {
                Text("SOURCE FOR \(slot)").font(.system(size: 10, weight: .semibold)).tracking(2).foregroundStyle(Palette.bronze)
                Text("How do you want to add it?").font(Serif.display(22)).foregroundStyle(Palette.ink)
                Button(action: {
                    Haptic.tap()
                    CameraBus.shared.slot = slot
                    chooseSlot = nil
                    showCamera = true
                }) {
                    HStack {
                        Text("📸 Take photo").font(Serif.body(15, weight: .semibold)).foregroundStyle(Palette.ink)
                        Spacer()
                        Text("→").foregroundStyle(Palette.muted)
                    }.padding(.vertical, 12)
                }
                Hairline()
                if slot == "A" {
                    PhotosPicker(selection: $aPickerItem, matching: .images) {
                        HStack {
                            Text("🖼 From gallery").font(Serif.body(15, weight: .semibold)).foregroundStyle(Palette.ink)
                            Spacer()
                            Text("→").foregroundStyle(Palette.muted)
                        }.padding(.vertical, 12)
                    }
                    .onChange(of: aPickerItem) { _, _ in chooseSlot = nil }
                } else {
                    PhotosPicker(selection: $bPickerItem, matching: .images) {
                        HStack {
                            Text("🖼 From gallery").font(Serif.body(15, weight: .semibold)).foregroundStyle(Palette.ink)
                            Spacer()
                            Text("→").foregroundStyle(Palette.muted)
                        }.padding(.vertical, 12)
                    }
                    .onChange(of: bPickerItem) { _, _ in chooseSlot = nil }
                }
            }
            .padding(24)
            .frame(maxWidth: .infinity)
            .background(Palette.paper)
            .clipShape(RoundedRectangle(cornerRadius: 22))
            .padding(.horizontal, 16)
        }
    }
}

// MARK: - Contact sheet

/// Draws the two frames into one image, side by side with a hard divider.
///
/// The judge endpoint accepts a single `image_url` and silently drops any
/// `images` array, so a second photo can only reach the model by being part of
/// the first one. Both panels are letterboxed rather than cropped: cropping
/// would change the very framing the model is asked to score.
enum ContactSheet {
    static func render(left: Data, right: Data, panel: CGSize = CGSize(width: 720, height: 1000)) -> Data? {
        guard let l = UIImage(data: left), let r = UIImage(data: right) else { return nil }
        let gap: CGFloat = 8
        let size = CGSize(width: panel.width * 2 + gap, height: panel.height)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let image = UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            UIColor.white.setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
            draw(l, in: CGRect(x: 0, y: 0, width: panel.width, height: panel.height))
            draw(r, in: CGRect(x: panel.width + gap, y: 0, width: panel.width, height: panel.height))
            UIColor.black.setFill()
            ctx.fill(CGRect(x: panel.width, y: 0, width: gap, height: panel.height))
        }
        return image.jpegData(compressionQuality: 0.9)
    }

    /// Aspect-fit inside the panel — never crop, never distort.
    private static func draw(_ img: UIImage, in rect: CGRect) {
        let s = min(rect.width / img.size.width, rect.height / img.size.height)
        let w = img.size.width * s, h = img.size.height * s
        img.draw(in: CGRect(x: rect.midX - w / 2, y: rect.midY - h / 2, width: w, height: h))
    }
}

// MARK: - Stored comparison

/// The A vs B payload as written into `outfits.signals.versus`. Anything that
/// wants to show a past call — the journal, the dashboard — reads it through
/// here rather than reaching into the blob.
struct VersusSummary {
    var winner: String
    var totalA: Int
    var totalB: Int
    var aPath: String
    var bPath: String
    var occasion: String
    var focus: [String]
    var intent: String?
    var criteria: [CompareCriterion] = []
    var confidence: String = ""

    var winnerScore: Int { winner == "B" ? totalB : totalA }
    var loserScore: Int { winner == "B" ? totalA : totalB }

    /// What the two shots were measured against, for a one-line recap.
    var judgedFor: String {
        if let i = intent, !i.isEmpty { return i }
        return occasion.isEmpty ? "everyday" : occasion
    }
}

extension Outfit {
    var versusSummary: VersusSummary? {
        guard kind == "versus", let v = signals?["versus"] else { return nil }
        guard let a = v["a_path"]?.stringValue, let b = v["b_path"]?.stringValue else { return nil }
        return VersusSummary(
            winner: v["winner"]?.stringValue ?? "tie",
            totalA: Int(v["total_a"]?.doubleValue ?? 0),
            totalB: Int(v["total_b"]?.doubleValue ?? 0),
            aPath: a,
            bPath: b,
            occasion: v["occasion"]?.stringValue ?? "",
            focus: v["focus"]?.arrayValue?.compactMap { $0.stringValue } ?? [],
            intent: v["intent"]?.stringValue,
            criteria: v["criteria"]?.arrayValue?.compactMap { row in
                guard let key = row["key"]?.stringValue else { return nil }
                return CompareCriterion(
                    key: key,
                    a: Int(row["a"]?.doubleValue ?? 0),
                    b: Int(row["b"]?.doubleValue ?? 0),
                    note_a: row["note_a"]?.stringValue ?? "",
                    note_b: row["note_b"]?.stringValue ?? ""
                )
            } ?? [],
            confidence: v["confidence"]?.stringValue ?? ""
        )
    }
}

// MARK: - Drop zone

/// Tall dashed slot: letter badge, empty-state prompt or the photo itself, and
/// a bottom gradient carrying a caption (scene tag / status / verdict) plus an
/// optional score.
private struct DropZone: View {
    let label: String
    let bytes: Data?
    var caption: String? = nil
    var score: Int? = nil
    var highlighted: Bool = false
    var dimmed: Bool = false
    var onTap: () -> Void

    /// Score ticks up from zero when the card flips into its result state —
    /// the number lands at the same time the bars finish filling.
    @State private var shownScore: Int = 0

    var body: some View {
        Button(action: {
            Haptic.chip()
            onTap()
        }) {
            // `Color.clear` sets the card's size; everything else is an overlay
            // on top of it, so a scaledToFill photo can never grow the layout.
            Color.clear
                .aspectRatio(0.72, contentMode: .fit)
                .overlay(Palette.card)
                .overlay(photoLayer)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .strokeBorder(
                            highlighted ? Palette.bronze : Palette.ink.opacity(0.25),
                            style: StrokeStyle(
                                lineWidth: highlighted ? 2 : 1,
                                dash: highlighted ? [] : [5, 4]
                            )
                        )
                )
                .overlay(alignment: .topLeading) { badge }
        }
        .buttonStyle(.plain)
        .animation(.easeOut(duration: 0.3), value: bytes)
        .animation(.easeOut(duration: 0.3), value: caption)
        .animation(.spring(response: 0.4, dampingFraction: 0.8), value: highlighted)
        .task(id: score) { await countUp() }
    }

    private func countUp() async {
        guard let target = score else { shownScore = 0; return }
        let steps = 22
        for i in 1...steps {
            try? await Task.sleep(nanoseconds: 26_000_000)
            if Task.isCancelled { return }
            // Ease-out so the last digits settle instead of snapping.
            let t = Double(i) / Double(steps)
            let eased = 1 - pow(1 - t, 3)
            shownScore = Int((Double(target) * eased).rounded())
        }
        shownScore = target
    }

    private var photoLayer: some View {
        ZStack(alignment: .bottomLeading) {
            if let d = bytes, let img = UIImage(data: d) {
                GeometryReader { geo in
                    Image(uiImage: img)
                        .resizable()
                        .scaledToFill()
                        .frame(width: geo.size.width, height: geo.size.height)
                        .clipped()
                }
                .opacity(dimmed ? 0.75 : 1)
                .transition(.opacity)
            } else {
                VStack(spacing: 6) {
                    Image(systemName: "photo")
                        .font(.system(size: 22, weight: .light))
                        .foregroundStyle(Palette.muted)
                    Text("\(label) · drop photo")
                        .font(Serif.body(14, weight: .semibold))
                        .foregroundStyle(Palette.ink)
                    Text("or browse files")
                        .font(Serif.body(12))
                        .foregroundStyle(Palette.muted)
                        .underline()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }

            if caption != nil || score != nil {
                LinearGradient(
                    colors: [.clear, .black.opacity(0.55)],
                    startPoint: .center, endPoint: .bottom
                )
                HStack(alignment: .bottom) {
                    if let caption {
                        Text(caption.uppercased())
                            .font(.system(size: 10, weight: .semibold))
                            .tracking(2)
                            .foregroundStyle(.white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                    }
                    Spacer(minLength: 6)
                    if score != nil {
                        Text("\(shownScore)")
                            .font(Serif.display(30))
                            .foregroundStyle(.white)
                            .monospacedDigit()
                            .contentTransition(.numericText())
                    }
                }
                .padding(.horizontal, 12)
                .padding(.bottom, 10)
            }
        }
    }

    private var badge: some View {
        Text(label)
            .font(Serif.display(15))
            .foregroundStyle(Palette.ink)
            .frame(width: 28, height: 28)
            .background(Palette.paper.opacity(0.9))
            .clipShape(Circle())
            .padding(10)
    }
}

// MARK: - Criteria chips

/// Multi-select focus chips — these decide which criteria get scored at all.
/// At least one stays on; an empty set would leave nothing to judge.
private struct CriteriaChips: View {
    let all: [String]
    @Binding var selection: Set<String>

    var body: some View {
        FlowRow(spacing: 8) {
            ForEach(all, id: \.self) { key in
                let on = selection.contains(key)
                Button(action: {
                    Haptic.chip()
                    if on {
                        if selection.count > 1 { selection.remove(key) }
                    } else {
                        selection.insert(key)
                    }
                }) {
                    Text(key.prefix(1).uppercased() + key.dropFirst())
                        .font(Serif.body(14, weight: on ? .semibold : .regular))
                        .foregroundStyle(on ? .white : Palette.muted)
                        .padding(.horizontal, 18)
                        .frame(height: 40)
                        .background(on ? Palette.ink : Color.clear)
                        .overlay(Capsule().stroke(on ? Palette.ink : Palette.ink.opacity(0.25), lineWidth: 1))
                        .clipShape(Capsule())
                        .scaleEffect(on ? 1 : 0.97)
                        .animation(.spring(response: 0.3, dampingFraction: 0.65), value: on)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

/// Single-select purpose chips. The last option opens a sheet and then wears
/// the typed line as its label, so a custom occasion still reads as a choice.
private struct OccasionChips: View {
    let all: [String]
    @Binding var selection: String

    var body: some View {
        // Wrapping, not a rail: a horizontal rail cut "Wedding" in half at the
        // screen edge, which reads as breakage rather than as an invitation to
        // scroll. Two tidy rows cost 48pt and show every option at once.
        FlowRow(spacing: 8) {
            Group {
                ForEach(all, id: \.self) { opt in
                    let on = selection == opt
                    Button(action: {
                        Haptic.chip()
                        selection = opt
                    }) {
                        Text(opt)
                            .font(Serif.body(14, weight: on ? .semibold : .regular))
                            .foregroundStyle(on ? .white : Palette.muted)
                            .lineLimit(1)
                            .padding(.horizontal, 18)
                            .frame(height: 40)
                            .background(on ? Palette.ink : Color.clear)
                            .overlay(Capsule().stroke(on ? Palette.ink : Palette.ink.opacity(0.25), lineWidth: 1))
                            .clipShape(Capsule())
                            .scaleEffect(on ? 1 : 0.97)
                            .animation(.spring(response: 0.3, dampingFraction: 0.65), value: on)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

/// The occasion question, answered in words. Sits on the eyebrow row so it
/// reads as an alternative to the chips rather than a third question.
private struct NotePill: View {
    let note: String
    var onTap: () -> Void
    var onClear: () -> Void

    var body: some View {
        HStack(spacing: 6) {
            Button(action: { Haptic.tap(); onTap() }) {
                HStack(spacing: 6) {
                    Image(systemName: note.isEmpty ? "pencil" : "quote.opening")
                        .font(.system(size: 10, weight: .semibold))
                    Text(note.isEmpty ? "Say it in words" : String(note.prefix(30)))
                        .font(Serif.body(12))
                        .lineLimit(1)
                }
                .foregroundStyle(note.isEmpty ? Palette.bronze : .white)
                .padding(.horizontal, 12)
                .frame(height: 28)
                .background(note.isEmpty ? Color.clear : Palette.ink)
                .overlay(Capsule().stroke(note.isEmpty ? Palette.bronze : Palette.ink, lineWidth: 1))
                .clipShape(Capsule())
            }
            .buttonStyle(.plain)
            if !note.isEmpty {
                Button(action: { Haptic.tap(); onClear() }) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 15))
                        .foregroundStyle(Palette.muted)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

/// Minimal wrapping HStack — chips fall to the next line on narrow phones.
private struct FlowRow: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0
        for v in subviews {
            let s = v.sizeThatFits(.unspecified)
            if x > 0 && x + s.width > maxWidth {
                x = 0; y += rowHeight + spacing; rowHeight = 0
            }
            x += s.width + spacing
            rowHeight = max(rowHeight, s.height)
        }
        return CGSize(width: maxWidth == .infinity ? x : maxWidth, height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowHeight: CGFloat = 0
        for v in subviews {
            let s = v.sizeThatFits(.unspecified)
            if x > bounds.minX && x + s.width > bounds.maxX {
                x = bounds.minX; y += rowHeight + spacing; rowHeight = 0
            }
            v.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(s))
            x += s.width + spacing
            rowHeight = max(rowHeight, s.height)
        }
    }
}

// MARK: - Breakdown

/// One criterion: label, "A · B" numbers, and two bars mirrored from the
/// centre — ink grows left for A, bronze grows right for B.
private struct CriterionBar: View {
    let row: CompareCriterion
    var index: Int = 0

    /// Bars grow out of the centre line, one row after the next.
    @State private var filled = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(row.title)
                    .font(Serif.body(15, weight: .medium))
                    .foregroundStyle(Palette.ink)
                // How much this axis counted under the chosen brief. Without it
                // a wide bar on footwear reads as decisive when it carried 7%.
                if row.weightPercent > 0 {
                    Text("·\(row.weightPercent)%")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(Palette.bronze)
                }
                Spacer(minLength: 8)
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
                            .frame(width: filled ? max(2, half * CGFloat(row.a) / 100) : 2, height: 4)
                    }
                    .frame(width: half)
                    ZStack(alignment: .leading) {
                        Capsule().fill(Palette.hairline).frame(height: 4)
                        Capsule().fill(Palette.bronze)
                            .frame(width: filled ? max(2, half * CGFloat(row.b) / 100) : 2, height: 4)
                    }
                    .frame(width: half)
                }
            }
            .frame(height: 4)

            if !row.note_a.isEmpty || !row.note_b.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    if !row.note_a.isEmpty { reason(letter: "A", text: row.note_a, color: Palette.ink) }
                    if !row.note_b.isEmpty { reason(letter: "B", text: row.note_b, color: Palette.bronze) }
                }
                .padding(.top, 2)
            }
        }
        .onAppear {
            withAnimation(.spring(response: 0.55, dampingFraction: 0.9).delay(0.12 + Double(index) * 0.07)) {
                filled = true
            }
        }
    }

    /// One frame's reason, tagged with the letter and colour of its own bar.
    private func reason(letter: String, text: String, color: Color) -> some View {
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
}

/// Fade + lift used to stagger the result blocks into place.
private struct RiseIn: ViewModifier {
    var delay: Double = 0
    @State private var shown = false

    func body(content: Content) -> some View {
        content
            .opacity(shown ? 1 : 0)
            .offset(y: shown ? 0 : 14)
            .onAppear {
                withAnimation(.spring(response: 0.5, dampingFraction: 0.9).delay(delay)) { shown = true }
            }
    }
}

private struct LegendDot: View {
    let color: Color
    let label: String
    var body: some View {
        HStack(spacing: 7) {
            Circle().fill(color).frame(width: 9, height: 9)
            Text(label).font(Serif.body(13)).foregroundStyle(Palette.muted)
        }
    }
}

// MARK: - Note card

private struct NoteCard: View {
    var title: String? = nil
    let text: String
    var showIcon: Bool = true

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            if showIcon {
                Text("\u{25C7}")
                    .font(.system(size: 15))
                    .foregroundStyle(Palette.bronze)
            }
            Group {
                if let title {
                    Text(title).font(Serif.body(14, weight: .semibold)).foregroundStyle(Palette.ink)
                    + Text(" \(text)").font(Serif.body(14)).foregroundStyle(Palette.muted)
                } else {
                    Text(text).font(Serif.body(14)).foregroundStyle(Palette.muted)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(16)
        .background(Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

// MARK: - On-device shot analysis

/// Everything here runs locally on the JPEG bytes — no upload, no credits.
/// `tag` produces the card caption ("MIRROR · INDOOR", "OUTDOOR", "LOW LIGHT")
/// which doubles as a hint for the judge; `nearDuplicate` catches the very
/// common "user picked the same photo twice" case before any spend.
enum ShotTagger {
    private static let outdoorWords = [
        "outdoor", "sky", "beach", "mountain", "forest", "park", "garden", "street",
        "city", "field", "snow", "ocean", "sea", "sunset", "road", "tree", "grass",
        "building_exterior", "landscape", "sidewalk",
    ]
    private static let indoorWords = [
        "indoor", "room", "bedroom", "living_room", "kitchen", "bathroom", "office",
        "interior", "furniture", "sofa", "bed", "curtain", "lamp", "wall", "ceiling",
        "staircase", "gym", "closet",
    ]
    private static let mirrorWords = ["mirror", "reflection", "selfie"]

    /// Short, uppercase-ready scene tag, or nil when nothing reads confidently.
    static func tag(_ data: Data) async -> String? {
        await withCheckedContinuation { cont in
            DispatchQueue.global(qos: .userInitiated).async {
                cont.resume(returning: compute(data))
            }
        }
    }

    private static func compute(_ data: Data) -> String? {
        guard let image = UIImage(data: data), let cg = image.cgImage else { return nil }
        let handler = VNImageRequestHandler(cgImage: cg, options: [:])

        var outdoor = 0.0, indoor = 0.0, mirror = 0.0
        let classify = VNClassifyImageRequest()
        try? handler.perform([classify])
        if let obs = classify.results {
            for o in obs.prefix(40) where o.confidence > 0.05 {
                let id = o.identifier.lowercased()
                let c = Double(o.confidence)
                if outdoorWords.contains(where: { id.contains($0) }) { outdoor += c }
                if indoorWords.contains(where: { id.contains($0) }) { indoor += c }
                if mirrorWords.contains(where: { id.contains($0) }) { mirror += c }
            }
        }

        var tokens: [String] = []
        if mirror > 0.25 { tokens.append("mirror") }
        if max(outdoor, indoor) > 0.20 {
            tokens.append(outdoor > indoor ? "outdoor" : "indoor")
        }

        if tokens.isEmpty {
            // No scene read — fall back to what the frame itself shows.
            if let framing = framingToken(handler: handler) { tokens.append(framing) }
        }
        if tokens.count < 2, isDim(cg) { tokens.append("low light") }

        guard !tokens.isEmpty else { return nil }
        return tokens.prefix(2).joined(separator: " · ")
    }

    private static func framingToken(handler: VNImageRequestHandler) -> String? {
        let req = VNDetectHumanRectanglesRequest()
        req.upperBodyOnly = false
        try? handler.perform([req])
        guard let box = req.results?
            .max(by: { $0.boundingBox.height < $1.boundingBox.height })?.boundingBox else { return nil }
        return box.height > 0.7 ? "full body" : "close-up"
    }

    /// Mean luminance of the frame, thresholded — cheap "was this shot in the
    /// dark" read that the light criterion would otherwise punish silently.
    private static func isDim(_ cg: CGImage) -> Bool {
        let ci = CIImage(cgImage: cg)
        let ctx = CIContext(options: [.workingColorSpace: NSNull()])
        guard let filter = CIFilter(name: "CIAreaAverage", parameters: [
            kCIInputImageKey: ci,
            kCIInputExtentKey: CIVector(cgRect: ci.extent),
        ]), let out = filter.outputImage else { return false }
        var px = [UInt8](repeating: 0, count: 4)
        ctx.render(out, toBitmap: &px, rowBytes: 4, bounds: CGRect(x: 0, y: 0, width: 1, height: 1),
                   format: .RGBA8, colorSpace: CGColorSpaceCreateDeviceRGB())
        let luma = 0.299 * Double(px[0]) + 0.587 * Double(px[1]) + 0.114 * Double(px[2])
        return luma < 70
    }

    /// True when the two frames are the same picture in all but bytes — a
    /// re-encode, a crop-free re-save, or the same shot picked twice.
    static func nearDuplicate(_ a: Data, _ b: Data) async -> Bool {
        await withCheckedContinuation { cont in
            DispatchQueue.global(qos: .userInitiated).async {
                guard let ha = aHash(a), let hb = aHash(b) else {
                    cont.resume(returning: false); return
                }
                cont.resume(returning: (ha ^ hb).nonzeroBitCount <= 4)
            }
        }
    }

    /// 8×8 average hash — downscale to 64 grey pixels, one bit per pixel for
    /// "above the frame's mean brightness".
    private static func aHash(_ data: Data) -> UInt64? {
        guard let img = UIImage(data: data), let cg = img.cgImage else { return nil }
        let side = 8
        var pixels = [UInt8](repeating: 0, count: side * side)
        guard let ctx = CGContext(
            data: &pixels, width: side, height: side, bitsPerComponent: 8, bytesPerRow: side,
            space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGImageAlphaInfo.none.rawValue
        ) else { return nil }
        ctx.interpolationQuality = .low
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: side, height: side))
        let mean = pixels.reduce(0) { $0 + Int($1) } / pixels.count
        var hash: UInt64 = 0
        for (i, p) in pixels.enumerated() where Int(p) >= mean {
            hash |= (1 << UInt64(i))
        }
        return hash
    }
}

// MARK: - Share card

/// Rendered off-screen on long-press: the two shots, their totals and the
/// verdict, on paper stock.
private struct VersusShareCard: View {
    let aImage: UIImage?
    let bImage: UIImage?
    let res: CompareResponse

    var body: some View {
        let winner = res.winner ?? "tie"
        VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 6) {
                Text("HEM PICKS")
                    .font(.system(size: 11, weight: .semibold)).tracking(2)
                    .foregroundStyle(Palette.bronze)
                Text("The call").font(Serif.display(34)).foregroundStyle(Palette.ink)
            }
            HStack(spacing: 12) {
                shot(aImage, label: "A", score: res.totalA, isWinner: winner == "A")
                shot(bImage, label: "B", score: res.totalB, isWinner: winner == "B")
            }
            if !res.verdictText.isEmpty {
                Text(res.verdictText)
                    .font(Serif.body(14))
                    .foregroundStyle(.white)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Palette.ink)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
            }
            if let rows = res.criteria, !rows.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    ForEach(rows, id: \.key) { row in
                        HStack {
                            Text(row.title).font(Serif.body(13)).foregroundStyle(Palette.ink)
                            Spacer()
                            Text("\(row.a) · \(row.b)").font(Serif.body(13)).foregroundStyle(Palette.muted)
                        }
                    }
                }
            }
            Spacer(minLength: 0)
            HStack(spacing: 6) {
                Text("\u{2726}").font(.system(size: 13)).foregroundStyle(Palette.bronze)
                Text("FITRATER").font(.system(size: 12, weight: .semibold)).tracking(3).foregroundStyle(Palette.ink)
            }
        }
        .padding(28)
        .frame(width: 540, height: 960)
        .background(Palette.paper)
    }

    private func shot(_ img: UIImage?, label: String, score: Int, isWinner: Bool) -> some View {
        ZStack(alignment: .bottomLeading) {
            // Same clipping rule as DropZone: Color.clear owns the size, the
            // fill-scaled photo lives in its overlay and gets clipped there.
            Color.clear
                .aspectRatio(0.72, contentMode: .fit)
                .overlay(Palette.card)
                .overlay(
                    Group {
                        if let img { Image(uiImage: img).resizable().scaledToFill() }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()
                )
                .overlay(LinearGradient(colors: [.clear, .black.opacity(0.55)], startPoint: .center, endPoint: .bottom))
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(isWinner ? Palette.bronze : Palette.hairline, lineWidth: isWinner ? 2 : 1)
                )
            HStack(alignment: .bottom) {
                Text(isWinner ? "WINNER" : label)
                    .font(.system(size: 10, weight: .semibold)).tracking(2)
                    .foregroundStyle(.white)
                Spacer()
                Text("\(score)").font(Serif.display(28)).foregroundStyle(.white)
            }
            .padding(.horizontal, 12).padding(.bottom, 10)
        }
    }
}

// MARK: - Share sheet wrapper

private struct ActivityShare: UIViewControllerRepresentable {
    let image: UIImage
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [image], applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}
