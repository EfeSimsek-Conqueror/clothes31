import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

/// Decode a reference photo into pieces + palette + style signature. Auto-
/// inserts a journal entry (kind='decode'), and offers an "Add to closet"
/// action that writes each decoded piece as a `closet_items` row with
/// `source='decoded'`.
///
/// Four cleanly separated stages: `.idle`, `.loading`, `.result`, `.failed`.
struct DecodeView: View {
    var onClose: () -> Void
    var onOpenPaywall: () -> Void = {}

    // MARK: - State

    @State private var pickedBytes: Data?
    @State private var pickedImage: UIImage?
    @State private var busy = false
    @State private var error: String?
    @State private var result: DecodeResponse?
    @State private var saving = false
    @State private var savedOk = false
    @State private var pickerItem: PhotosPickerItem?
    @State private var showPhotoPicker = false
    @State private var showFileImporter = false
    @State private var stageIndex = 0

    @ObservedObject private var credits = CreditsBus.shared

    /// Stored, not constructed in `body` — a publisher rebuilt on every render
    /// restarts its interval, and `credits` publishes often enough to starve
    /// the stage rotation entirely.
    private let stageTimer = Timer.publish(every: 3.5, on: .main, in: .common).autoconnect()

    // MARK: - Stage

    private enum Stage { case idle, loading, result, failed }

    private var stage: Stage {
        if busy { return .loading }
        if result != nil { return .result }
        if error != nil { return .failed }
        return .idle
    }

    private struct LoadingStage {
        let title: String
        let sub: String
    }

    private static let loadingStages: [LoadingStage] = [
        .init(title: "Reading the room",      sub: "Light, cut, context…"),
        .init(title: "Naming the pieces",     sub: "Cut, cloth, colour…"),
        .init(title: "Sampling the palette",  sub: "The five that carry it…"),
        .init(title: "Writing the signature", sub: "One line, no cliches…")
    ]

    // MARK: - Root

    /// Row written by the finished decode; its read opens over this screen.
    @State private var justSavedId: String?

    var body: some View {
        decodeBody.justSaved($justSavedId)
    }

    private var decodeBody: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()

            VStack(spacing: 0) {
                if stage != .loading {
                    header
                        .padding(.horizontal, 20)
                        .padding(.top, 18)
                        .padding(.bottom, 16)
                }

                ScrollView {
                    scrollBody
                        .padding(.horizontal, 20)
                        .padding(.bottom, 24)
                }

                if stage != .loading {
                    footer
                }
            }
        }
        .interactiveDismissDisabled(stage == .loading)
        .photosPicker(isPresented: $showPhotoPicker, selection: $pickerItem, matching: .images)
        .fileImporter(
            isPresented: $showFileImporter,
            allowedContentTypes: [.image],
            allowsMultipleSelection: false
        ) { outcome in
            switch outcome {
            case .success(let urls):
                guard let url = urls.first else { return }
                let scoped = url.startAccessingSecurityScopedResource()
                defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                guard let data = try? Data(contentsOf: url) else {
                    error = "That file couldn't be opened."
                    return
                }
                adopt(data)
            case .failure(let err):
                error = err.localizedDescription
            }
        }
        .onChange(of: pickerItem) { _, item in
            Task {
                if let item, let d = try? await item.loadTransferable(type: Data.self) {
                    adopt(d)
                }
            }
        }
        .onReceive(stageTimer) { _ in
            guard stage == .loading else { return }
            stageIndex = (stageIndex + 1) % Self.loadingStages.count
        }
    }

    // MARK: - Header

    private var header: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 6) {
                Eyebrow(text: "READ THE ROOM")
                Text("Decode style")
                    .font(Serif.display(28))
                    .foregroundStyle(Palette.ink)
                Text(stage == .result
                     ? "One signature, the pieces behind it, and the palette."
                     : "Pick a look you want to understand.\nPhotos or files.")
                    .font(Serif.body(13))
                    .foregroundStyle(Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Palette.ink)
                    .frame(width: 36, height: 36)
                    .background(Circle().fill(Palette.card))
                    .overlay(Circle().stroke(Palette.hairline, lineWidth: 1))
            }
        }
    }

    // MARK: - Scroll body

    @ViewBuilder
    private var scrollBody: some View {
        switch stage {
        case .loading:
            loadingBody
                .padding(.top, 40)
        case .idle:
            dropZone(aspect: 1.15)
                .padding(.top, 4)
        case .failed:
            VStack(alignment: .leading, spacing: 16) {
                dropZone(aspect: 1.15)
                errorCard
            }
            .padding(.top, 4)
        case .result:
            if let res = result {
                VStack(alignment: .leading, spacing: 18) {
                    dropZone(aspect: 2.4)
                    if !res.style_signature.isEmpty { signatureSection(res) }
                    if !res.pieces.isEmpty {
                        Hairline()
                        piecesSection(res)
                    }
                    paletteSection(res)
                    if !res.styling_notes.isEmpty {
                        Hairline()
                        stylingNotesSection(res)
                    }
                    if !res.make_it_yours.isEmpty {
                        Hairline()
                        swapsSection(res)
                    }
                }
                .padding(.top, 4)
            }
        }
    }

    // MARK: - Drop zone

    @ViewBuilder
    private func dropZone(aspect: CGFloat) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 16).fill(Palette.card)

            if let img = pickedImage {
                // Fit, not fill: a reference look is the whole point of this
                // screen, and cropping it to the tile hides the shoes or the
                // hemline the read is about.
                Image(uiImage: img)
                    .resizable()
                    .scaledToFit()
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(alignment: .bottomTrailing) {
                        Text("CHANGE")
                            .font(.system(size: 10, weight: .semibold))
                            .tracking(1.8)
                            .foregroundStyle(Palette.ink)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Capsule().fill(Palette.card))
                            .overlay(Capsule().stroke(Palette.hairline, lineWidth: 1))
                            .padding(10)
                    }
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(Palette.hairline, lineWidth: 1))
            } else {
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Palette.hairline, style: StrokeStyle(lineWidth: 1, dash: [6, 5]))

                VStack(spacing: 10) {
                    Image(systemName: "photo")
                        .font(.system(size: 26))
                        .foregroundStyle(Palette.muted.opacity(0.65))
                    Text("Reference photo")
                        .font(Serif.body(13))
                        .foregroundStyle(Palette.ink)
                    // The library, not the file browser. This screen reads a
                    // photograph of a look; asking for a file sends people into
                    // iCloud Drive looking for something that lives in Photos.
                    Button { showPhotoPicker = true } label: {
                        Text("or choose from library")
                            .font(Serif.body(12))
                            .underline()
                            .foregroundStyle(Palette.muted)
                    }
                }
            }
        }
        .aspectRatio(aspect, contentMode: .fit)
        .frame(maxWidth: .infinity)
        .contentShape(RoundedRectangle(cornerRadius: 16))
        .onTapGesture {
            result = nil
            savedOk = false
            error = nil
            showPhotoPicker = true
        }
    }

    // MARK: - Loading

    private var loadingBody: some View {
        let s = Self.loadingStages[stageIndex % Self.loadingStages.count]
        return VStack(spacing: 16) {
            VStack(spacing: 10) {
                Eyebrow(text: "READING")
                Text(s.title)
                    .font(Serif.display(26))
                    .foregroundStyle(Palette.ink)
                    .multilineTextAlignment(.center)
                Text(s.sub)
                    .font(Serif.italic(14))
                    .foregroundStyle(Palette.muted)
                    .multilineTextAlignment(.center)
                Spacer().frame(height: 26)   // + the VStack's own 10pt = the specified 36
                DecodeSpinner()
            }
            .id(stageIndex)
            .transition(.opacity)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 20)
            .padding(.vertical, 120)
            .background(RoundedRectangle(cornerRadius: 28).fill(Palette.card))
            .overlay(RoundedRectangle(cornerRadius: 28).stroke(Palette.hairline, lineWidth: 1))

            Text("This usually takes 10–20 seconds. Please keep the app open.")
                .font(.system(size: 11))
                .foregroundStyle(Palette.muted)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
        }
        .animation(.easeInOut(duration: 0.45), value: stageIndex)
    }

    // MARK: - Result sections

    @ViewBuilder
    private func signatureSection(_ res: DecodeResponse) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Eyebrow(text: "STYLE SIGNATURE")
            Text(res.style_signature.isEmpty ? "—" : res.style_signature)
                .font(Serif.display(22))
                .foregroundStyle(Palette.ink)
                .fixedSize(horizontal: false, vertical: true)
            if !res.signature_note.isEmpty {
                Text(res.signature_note)
                    .font(Serif.body(14))
                    .foregroundStyle(Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if !res.dress_code.isEmpty || !res.mood_tags.isEmpty || !res.where_it_works.isEmpty {
                readTags(res)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Dress code, mood and where it works, as one wrapped run of chips. They
    /// are all short labels answering "what is this, and when", so they read
    /// better together than as three separate titled sections.
    @ViewBuilder
    private func readTags(_ res: DecodeResponse) -> some View {
        let tags: [(String, Bool)] =
            (res.dress_code.isEmpty ? [] : [(res.dress_code, true)])
            + res.mood_tags.map { ($0, false) }
            + res.where_it_works.map { ($0, false) }
        FlowLayout(spacing: 6) {
            ForEach(Array(tags.enumerated()), id: \.offset) { _, t in
                Text(t.0.uppercased())
                    .font(.system(size: 9.5, weight: .semibold))
                    .tracking(1.3)
                    .foregroundStyle(t.1 ? Palette.paper : Palette.ink)
                    .padding(.horizontal, 9).padding(.vertical, 5)
                    .background(t.1 ? Palette.ink : Palette.card)
                    .overlay(Capsule().stroke(Palette.hairline, lineWidth: t.1 ? 0 : 1))
                    .clipShape(Capsule())
            }
        }
        .padding(.top, 2)
    }

    @ViewBuilder
    private func stylingNotesSection(_ res: DecodeResponse) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Eyebrow(text: "WHY IT WORKS")
            VStack(alignment: .leading, spacing: 8) {
                ForEach(Array(res.styling_notes.enumerated()), id: \.offset) { i, note in
                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                        Text(String(format: "%02d", i + 1))
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundStyle(Palette.bronze)
                        Text(note)
                            .font(Serif.body(14))
                            .foregroundStyle(Palette.ink)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private func swapsSection(_ res: DecodeResponse) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Eyebrow(text: "MAKE IT YOURS")
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(res.make_it_yours.enumerated()), id: \.offset) { i, sw in
                    if i > 0 { Hairline() }
                    HStack(alignment: .firstTextBaseline) {
                        Text(sw.swap)
                            .font(Serif.body(15))
                            .foregroundStyle(Palette.ink)
                        Spacer(minLength: 12)
                        Text(sw.why)
                            .font(Serif.body(11))
                            .foregroundStyle(Palette.muted)
                            .multilineTextAlignment(.trailing)
                    }
                    .padding(.vertical, 11)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private func piecesSection(_ res: DecodeResponse) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Eyebrow(text: "PIECES")
                Spacer()
                if !res.pieces.isEmpty { closetAction(res) }
            }
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(res.pieces.enumerated()), id: \.offset) { idx, p in
                    if idx > 0 { Hairline() }
                    PieceRow(piece: p)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private func closetAction(_ res: DecodeResponse) -> some View {
        Button {
            save(res)
        } label: {
            Text(savedOk ? "ADDED ✓" : (saving ? "ADDING…" : "ADD TO CLOSET →"))
                .font(.system(size: 11, weight: .semibold))
                .tracking(1.8)
                .foregroundStyle(Palette.bronze)
        }
        .disabled(saving || savedOk)
    }

    @ViewBuilder
    private func paletteSection(_ res: DecodeResponse) -> some View {
        if !res.palette_hex.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Eyebrow(text: "PALETTE")
                HStack(spacing: 6) {
                    ForEach(Array(res.palette_hex.enumerated()), id: \.offset) { i, hex in
                        PaletteSwatch(hex: hex,
                                      color: Self.colorFromHex(hex) ?? Palette.muted,
                                      name: res.paletteName(at: i))
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: - Error

    private var errorCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Eyebrow(text: "COULDN'T READ IT", color: Palette.bronze)
            Text(error ?? "Something went wrong.")
                .font(Serif.body(13))
                .foregroundStyle(Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 16).fill(Palette.card))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Palette.hairline, lineWidth: 1))
    }

    // MARK: - Footer (pinned)

    @ViewBuilder
    private var footer: some View {
        VStack(spacing: 0) {
            Hairline()
            VStack(spacing: 10) {
                switch stage {
                case .result:
                    PrimaryButton(title: "Decode another", action: reset)
                    Text("The only read that grows your closet")
                        .font(.system(size: 11))
                        .foregroundStyle(Palette.muted)
                case .failed:
                    PrimaryButton(title: "Try again", enabled: pickedBytes != nil, action: run)
                    Button(action: onClose) {
                        Text("Close")
                            .font(.system(size: 12))
                            .foregroundStyle(Palette.muted)
                    }
                default:
                    PrimaryButton(
                        title: "Decode it · \(Supa.decodeCost) credits",
                        enabled: pickedBytes != nil && !busy,
                        action: run
                    )
                    Text(creditsCaption)
                        .font(.system(size: 11))
                        .foregroundStyle(Palette.muted)
                }
            }
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 8)
        }
    }

    private var creditsCaption: String {
        if let b = credits.balance {
            return "\(b) credits left · result in ~15 seconds"
        }
        return "Result in ~15 seconds"
    }

    // MARK: - Actions

    /// Single funnel for "I have new image bytes" — shared by the photo picker
    /// and the file importer so the two paths cannot drift.
    private func adopt(_ data: Data) {
        let p = CameraModel.processJpeg(data) ?? data
        pickedBytes = p
        pickedImage = UIImage(data: p)
        result = nil
        savedOk = false
        error = nil
    }

    private func reset() {
        result = nil
        pickedBytes = nil
        pickedImage = nil
        pickerItem = nil
        savedOk = false
        saving = false
        error = nil
        stageIndex = 0
    }

    private func run() {
        guard !busy else { return }
        busy = true
        error = nil
        result = nil
        stageIndex = 0
        Task {
            let gate = await CreditsGate.check(Supa.decodeCost)
            if case .ok = gate {} else {
                busy = false
                if case .insufficientBalance = gate { _ = CreditsGate.explainAndBlock(gate); onOpenPaywall() }
                else { _ = CreditsGate.explainAndBlock(gate) }
                return
            }
            do {
                guard let bytes = pickedBytes else { throw RepoError.notFound }
                let path = try await Repo.shared.uploadClosetPhoto(bytes: bytes)
                guard let signed = try await Repo.shared.signedClosetUrl(path) else {
                    throw NSError(domain: "Decode", code: 1, userInfo: [NSLocalizedDescriptionKey: "Could not sign URL"])
                }
                let res = await HemService.decode(imageUrl: signed)
                if let err = res.error {
                    if err.lowercased().contains("timeout") {
                        throw NSError(domain: "Decode", code: 2, userInfo: [NSLocalizedDescriptionKey:
                            "Hem took too long. Your credits weren't spent — try again."])
                    }
                    throw NSError(domain: "Decode", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hem: \(err) — \(res.detail ?? "")"])
                }
                try? await Repo.shared.spendCredits(amount: Supa.decodeCost, kind: "decode")
                result = res
                busy = false
                // Auto-insert journal entry. Deliberately after `busy = false` —
                // this re-uploads the photo and the user should not sit on the
                // non-dismissible loading card for a second upload.
                if let uid = Repo.shared.userId {
                    let outPath = (try? await Repo.shared.uploadOutfitPhoto(bytes: bytes)) ?? path
                    let row = try? await Repo.shared.insertOutfit(OutfitInsert(
                        user_id: uid,
                        photo_path: outPath,
                        score: 0.0,
                        occasion: "Decoded",
                        hem_comment: res.style_signature.isEmpty ? "Decoded look" : res.style_signature,
                        weather_c: nil, verdict: nil, subscores: nil, swaps: nil, annotations: nil,
                        kind: "decode", linked_piece_id: nil,
                        // The read itself — pieces and palette — was being
                        // thrown away, so a saved decode had nothing to show
                        // but the photo it started from.
                        signals: .object([
                            "decode": .object([
                                "style_signature": .string(res.style_signature),
                                "palette_hex": .array(res.palette_hex.map { .string($0) }),
                                "pieces": .array(res.pieces.map { p in
                                    .object([
                                        "name": .string(p.displayName),
                                        "detail": .string(p.displayMeta),
                                        "type": .string(p.type),
                                        "colors": .array(p.colors.map { .string($0) }),
                                    ])
                                }),
                            ])
                        ])
                    ))
                    justSavedId = row?.id
                }
            } catch {
                self.error = error.localizedDescription
                ToastBus.shared.post("Decode failed: \(error.localizedDescription)")
                busy = false
            }
        }
    }

    private func save(_ res: DecodeResponse) {
        saving = true
        Task {
            guard let uid = Repo.shared.userId else {
                saving = false
                ToastBus.shared.post("Not signed in.")
                return
            }
            var ok = 0
            for p in res.pieces {
                let name = p.displayName
                let category = categoryFor(p.type.lowercased())
                let hex = res.palette_hex.first.map { String($0.prefix(7)) }
                do {
                    _ = try await Repo.shared.insertClosetItem(ClosetItemInsert(
                        user_id: uid,
                        name: name,
                        category: category,
                        subcategory: p.type.lowercased().isEmpty ? nil : p.type.lowercased(),
                        image_path: nil,
                        color_hex: hex,
                        parent_id: nil,
                        source: "decoded"
                    ))
                    ok += 1
                } catch { /* skip */ }
            }
            saving = false
            if ok > 0 {
                savedOk = true
                ToastBus.shared.post("Added \(ok) piece\(ok > 1 ? "s" : "") to your closet.")
            } else {
                ToastBus.shared.post("Save failed — try again.")
            }
        }
    }

    private func categoryFor(_ type: String) -> String {
        switch type {
        case "trousers", "pants", "jeans", "shorts", "skirt": return "bottom"
        case "boots", "shoes", "sneakers", "sandals", "heels": return "footwear"
        case "bag", "belt", "hat", "cap", "scarf", "sunglasses", "watch", "necklace": return "accessory"
        case "blazer", "jacket", "coat", "cardigan": return "outerwear"
        default: return "top"
        }
    }

    /// Accepts `#RRGGBB`, `RRGGBB`, `#RGB` and `RGB`. The UI prints the hex
    /// string next to the swatch, so shorthand must render rather than fall
    /// back to a grey chip.
    static func colorFromHex(_ hex: String) -> Color? {
        var s = hex.trimmingCharacters(in: .whitespaces).uppercased()
        if s.hasPrefix("#") { s.removeFirst() }
        if s.count == 3 {
            s = s.map { "\($0)\($0)" }.joined()
        }
        guard s.count == 6, let v = UInt32(s, radix: 16) else { return nil }
        let r = Double((v >> 16) & 0xFF) / 255
        let g = Double((v >> 8) & 0xFF) / 255
        let b = Double(v & 0xFF) / 255
        return Color(red: r, green: g, blue: b)
    }
}

// MARK: - Piece row

private struct PieceRow: View {
    let piece: DecodePiece
    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(piece.displayName)
                .font(Serif.body(15))
                .foregroundStyle(Palette.ink)
            Spacer(minLength: 12)
            Text(piece.displayMeta)
                .font(Serif.body(11))
                .foregroundStyle(Palette.muted)
                .multilineTextAlignment(.trailing)
        }
        .padding(.vertical, 11)
    }
}

// MARK: - Palette swatch

private struct PaletteSwatch: View {
    let hex: String
    let color: Color
    /// Human colour name, when the read supplied one for this swatch. The hex
    /// stays underneath it — the name is what a person repeats, the hex is what
    /// they paste somewhere.
    var name: String?
    var body: some View {
        VStack(spacing: 4) {
            RoundedRectangle(cornerRadius: 10)
                .fill(color)
                .frame(height: 54)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.hairline, lineWidth: 1))
            if let name, !name.isEmpty {
                Text(name)
                    .font(Serif.body(10))
                    .foregroundStyle(Palette.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            Text(hex.uppercased())
                .font(.system(size: 8, weight: .medium))
                .foregroundStyle(Palette.muted)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Spinner

private struct DecodeSpinner: View {
    @State private var spin: Double = 0
    var body: some View {
        ZStack {
            Circle().stroke(Palette.hairline, lineWidth: 2.5)
            Circle()
                .trim(from: 0, to: 0.22)
                .stroke(Palette.bronze, style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
                .rotationEffect(.degrees(spin))
        }
        .frame(width: 30, height: 30)
        .onAppear {
            withAnimation(.linear(duration: 0.9).repeatForever(autoreverses: false)) {
                spin = 360
            }
        }
    }
}
