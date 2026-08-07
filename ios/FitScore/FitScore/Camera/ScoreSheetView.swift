import SwiftUI
import PhotosUI

private let SCORE_OCCASIONS = ["Work", "Date", "Wedding", "Casual", "Everyday"]

/// Score-a-look flow: photo (camera or gallery), occasion chips, and score
/// button. On success, uploads photo, invokes `score-outfit`, inserts the
/// outfit row, spends credits, then navigates via `onScored(id)`.
struct ScoreSheetView: View {
    var onClose: () -> Void
    var onScored: (String) -> Void
    var onOpenPaywall: () -> Void = {}

    @State private var pickedOccasion = "Everyday"
    @State private var capturedBytes: Data?
    @State private var capturedImage: UIImage?
    @State private var busy = false
    @State private var error: String?
    @State private var showCamera = false
    @State private var pickerItem: PhotosPickerItem?
    @State private var scoredResult: HemScored?
    @State private var scoredOutfitId: String?
    // Sprint 2 — voice/text intent + back photo (dual shot)
    @State private var intent: String = ""
    @State private var showIntentSheet = false
    @State private var backBytes: Data?
    @State private var backImage: UIImage?
    @State private var showBackCamera = false
    @State private var backPickerItem: PhotosPickerItem?
    @EnvironmentObject var session: SessionStore

    var body: some View {
        ZStack(alignment: .top) {
            Palette.paper.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    photoTile
                    HStack(spacing: 10) {
                        PhotosPicker(selection: $pickerItem, matching: .images) {
                            OutlinePillButton(title: "From gallery")
                        }
                        Button(action: {
                            Haptic.tap()
                            showCamera = true
                        }) { OutlinePillButton(title: "Take photo") }
                    }
                    Text("Occasion")
                        .font(Serif.body(15, weight: .semibold))
                        .foregroundStyle(Palette.ink)
                    ChipRow(options: SCORE_OCCASIONS, selection: $pickedOccasion)

                    // Intent + back-shot extras (Sprint 2)
                    HStack(spacing: 8) {
                        Button(action: { showIntentSheet = true }) {
                            HStack(spacing: 6) {
                                Image(systemName: "mic.fill")
                                    .font(.system(size: 11))
                                Text(intent.isEmpty ? "Add intent" : truncate(intent, 34))
                                    .font(Serif.body(13))
                                if !intent.isEmpty {
                                    Button(action: { intent = "" }) {
                                        Image(systemName: "xmark").font(.system(size: 10))
                                    }
                                    .buttonStyle(.plain)
                                    .padding(.leading, 4)
                                }
                            }
                            .foregroundStyle(intent.isEmpty ? Palette.bronze : .white)
                            .padding(.horizontal, 12).padding(.vertical, 8)
                            .background(intent.isEmpty ? Color.clear : Palette.ink)
                            .overlay(Capsule().stroke(intent.isEmpty ? Palette.bronze : Palette.ink, lineWidth: 1))
                            .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)

                        Button(action: { Haptic.tap(); showBackCamera = true }) {
                            HStack(spacing: 6) {
                                Image(systemName: backImage == nil ? "arrow.uturn.backward" : "checkmark")
                                    .font(.system(size: 11))
                                Text(backImage == nil ? "Add back view" : "Back added")
                                    .font(Serif.body(13))
                            }
                            .foregroundStyle(backImage == nil ? Palette.bronze : .white)
                            .padding(.horizontal, 12).padding(.vertical, 8)
                            .background(backImage == nil ? Color.clear : Palette.ink)
                            .overlay(Capsule().stroke(backImage == nil ? Palette.bronze : Palette.ink, lineWidth: 1))
                            .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                    if backImage != nil {
                        Text("Front + back scored together · +\(Supa.frontBackExtraCost) credits")
                            .font(Serif.italic(12))
                            .foregroundStyle(Palette.muted)
                    }

                    if let error {
                        Text(error).font(Serif.body(13)).foregroundStyle(Palette.bronze)
                    }
                    PrimaryButton(
                        title: busy ? "Analyzing…" : (backImage == nil ? "Score it · \(Supa.scoreCost) credits" : "Score it · \(Supa.scoreCost + Supa.frontBackExtraCost) credits"),
                        enabled: capturedBytes != nil && !busy,
                        action: score
                    )
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
                        "Cross-checking against your occasion.",
                    ]
                )
            }

            if let scored = scoredResult {
                ScoreRevealOverlay(result: scored,
                                   onDone: {
                                       if let id = scoredOutfitId { onScored(id) }
                                       else { onClose() }
                                   })
            }
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraCaptureView()
                .onDisappear {
                    if let d = CameraBus.shared.consume() {
                        setBytes(d)
                    }
                }
        }
        .fullScreenCover(isPresented: $showBackCamera) {
            CameraCaptureView()
                .onDisappear {
                    if let d = CameraBus.shared.consume() {
                        backBytes = d
                        backImage = UIImage(data: d)
                    }
                }
        }
        .onChange(of: pickerItem) { _, item in
            Task {
                if let item, let d = try? await item.loadTransferable(type: Data.self) {
                    let processed = CameraModel.processJpeg(d) ?? d
                    setBytes(processed)
                }
            }
        }
        .onChange(of: backPickerItem) { _, item in
            Task {
                if let item, let d = try? await item.loadTransferable(type: Data.self) {
                    let p = CameraModel.processJpeg(d) ?? d
                    backBytes = p
                    backImage = UIImage(data: p)
                }
            }
        }
        .sheet(isPresented: $showIntentSheet) {
            IntentEntrySheet(intent: $intent, onClose: { showIntentSheet = false })
                .presentationDetents([.medium])
        }
        .task {
            if let d = CameraBus.shared.consume() { setBytes(d) }
        }
    }

    private func truncate(_ s: String, _ n: Int) -> String {
        s.count <= n ? s : String(s.prefix(n)) + "…"
    }

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Score a look")
                    .font(Serif.display(28))
                    .foregroundStyle(Palette.ink)
                Text("Snap it, pick an occasion, and Hem takes it from there.")
                    .font(Serif.body(14))
                    .foregroundStyle(Palette.muted)
            }
            Spacer()
            Button(action: onClose) {
                Image(systemName: "xmark")
                    .foregroundStyle(Palette.ink)
                    .frame(width: 36, height: 36)
            }
        }
    }

    private var photoTile: some View {
        RoundedRectangle(cornerRadius: 20)
            .fill(Palette.card)
            .aspectRatio(0.82, contentMode: .fit)
            .overlay(
                Group {
                    if let img = capturedImage {
                        Image(uiImage: img).resizable().scaledToFill()
                    } else {
                        Text("No photo yet").font(Serif.body(14)).foregroundStyle(Palette.muted)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 20))
            )
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(Palette.hairline, lineWidth: 1))
    }

    private func setBytes(_ d: Data) {
        capturedBytes = d
        capturedImage = UIImage(data: d)
    }

    // MARK: - Score

    private func score() {
        guard let bytes = capturedBytes else { error = "Add a photo first"; return }
        guard !busy else { return }
        busy = true
        error = nil
        Task {
            let cost = Supa.scoreCost + (backBytes == nil ? 0 : Supa.frontBackExtraCost)
            let gate = await CreditsGate.check(cost)
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
                var backPath: String? = nil
                var backSigned: String? = nil
                if let back = backBytes {
                    backPath = try await Repo.shared.uploadOutfitPhoto(bytes: back)
                    backSigned = try await Repo.shared.signedOutfitUrl(backPath!)
                }
                let profile = try? await Repo.shared.currentProfile()
                let honesty = profile?.honesty ?? "honest"
                let scored = try await HemService.score(
                    imageUrl: signed,
                    occasion: pickedOccasion,
                    honesty: honesty,
                    intent: intent.isEmpty ? nil : intent,
                    backUrl: backSigned,
                    bodyProfile: session.bodyProfile
                )
                guard let uid = Repo.shared.userId else {
                    throw NSError(domain: "Score", code: 2, userInfo: [NSLocalizedDescriptionKey: "Not signed in"])
                }
                let row = try await Repo.shared.insertOutfit(OutfitInsert(
                    user_id: uid,
                    photo_path: path,
                    score: scored.score,
                    occasion: pickedOccasion,
                    hem_comment: scored.hemComment,
                    weather_c: nil,
                    verdict: nil,
                    subscores: scored.subscores,
                    swaps: scored.swaps,
                    annotations: scored.annotations,
                    kind: nil,
                    linked_piece_id: nil
                ))
                // Persist Sprint 2 extras (annotations + fit_map + back path + intent).
                if let oid = row.id {
                    if !scored.markupAnnotations.isEmpty {
                        try? await Repo.shared.saveAnnotations(outfitId: oid, scored.markupAnnotations)
                    }
                    if let fm = scored.fitMap {
                        try? await Repo.shared.saveFitMap(outfitId: oid, fm)
                    }
                }
                try? await Repo.shared.spendCredits(amount: cost, kind: "outfit_score")
                Haptic.soft()
                scoredResult = scored
                scoredOutfitId = row.id
                busy = false
            } catch {
                self.error = error.localizedDescription
                ToastBus.shared.post("Scoring failed: \(error.localizedDescription)")
                busy = false
            }
        }
    }
}

// MARK: - Intent entry sheet

struct IntentEntrySheet: View {
    @Binding var intent: String
    var onClose: () -> Void
    @State private var text: String = ""
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Eyebrow(text: "SAY THE INTENT")
                Spacer()
                Button(action: onClose) {
                    Image(systemName: "xmark").foregroundStyle(Palette.ink)
                }
            }
            Text("What are you going for?")
                .font(Serif.display(22))
                .foregroundStyle(Palette.ink)
            Text("One line. Hem weighs the critique against it instead of a generic aesthetic.")
                .font(Serif.italic(13))
                .foregroundStyle(Palette.muted)

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

            Spacer()

            HStack(spacing: 8) {
                Button(action: { text = ""; intent = ""; onClose() }) {
                    OutlinePillButton(title: "Clear")
                }
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

// MARK: - Score reveal overlay (subtle summary before jumping to detail)

private struct ScoreRevealOverlay: View {
    let result: HemScored
    var onDone: () -> Void
    var body: some View {
        VStack(spacing: 14) {
            Spacer()
            VStack(spacing: 12) {
                Eyebrow(text: "HEM SAYS")
                Text(String(format: "%.1f", result.score))
                    .font(Serif.display(64, weight: .semibold))
                    .foregroundStyle(Palette.ink)
                if !result.hemComment.isEmpty {
                    Text(result.hemComment)
                        .font(Serif.italic(18))
                        .multilineTextAlignment(.center)
                        .foregroundStyle(Palette.ink)
                        .padding(.horizontal, 30)
                }
                PrimaryButton(title: "See breakdown", action: onDone)
                    .padding(.top, 8)
            }
            .padding(24)
            .frame(maxWidth: .infinity)
            .background(Palette.paper)
            .clipShape(RoundedRectangle(cornerRadius: 24))
            .padding(.horizontal, 20)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.opacity(0.35).ignoresSafeArea())
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

