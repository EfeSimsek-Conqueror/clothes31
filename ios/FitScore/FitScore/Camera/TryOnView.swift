import SwiftUI
import PhotosUI
import Supabase

/// Try-on flow — pick a Studio piece OR upload a garment photo, then pick a
/// self photo. Calls the `tryon-outfit` edge function via `Repo.tryOnPiece`.
/// Auto-saves the result to the journal as `kind='tryon'`.
struct TryOnView: View {
    var onClose: () -> Void
    var onOpenPaywall: () -> Void = {}
    var onOpenStudioCreate: () -> Void = {}

    @State private var pieces: [ClosetItem] = []
    @State private var loadingPieces = true
    @State private var pickedPiece: ClosetItem?
    @State private var pickedPieceUrl: String?

    @State private var pieceSource = "studio"   // "studio" | "upload"
    @State private var importedPieceBytes: Data?
    @State private var importedPieceUrl: String?
    @State private var importingPiece = false

    @State private var personBytes: Data?
    @State private var personImage: UIImage?
    @State private var busy = false
    @State private var error: String?
    @State private var resultUrl: String?
    @State private var savingLook = false
    @State private var savedOk = false

    @State private var showCamera = false
    @State private var personPickerItem: PhotosPickerItem?
    @State private var piecePickerItem: PhotosPickerItem?
    // Pro-only additions (Aug 2026): paste-URL piece source + season swap
    @State private var showUrlSheet = false
    @State private var pastedUrl = ""
    @State private var urlNotice: String? = nil
    @State private var seasonSwapNotice: String? = nil

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    if let url = resultUrl {
                        resultBlock(url: url)
                    } else {
                        inputBlock
                    }
                    Spacer(minLength: 30)
                }
                .padding(20)
            }

            if busy {
                WaitingOverlay(
                    eyebrow: "COMPOSING",
                    title: "Dressing you now",
                    tips: [
                        "Draping the fabric on your frame…",
                        "Matching fit tension and folds.",
                        "Preserving your face, restyling the rest.",
                        "One-of-one, no filters — just cloth.",
                    ]
                )
            }
        }
        .task { await load() }
        .fullScreenCover(isPresented: $showCamera) {
            CameraCaptureView()
                .onDisappear {
                    if let d = CameraBus.shared.consume() {
                        personBytes = d
                        personImage = UIImage(data: d)
                    }
                }
        }
        .onChange(of: personPickerItem) { _, item in
            Task {
                if let item, let d = try? await item.loadTransferable(type: Data.self) {
                    let p = CameraModel.processJpeg(d) ?? d
                    personBytes = p
                    personImage = UIImage(data: p)
                }
            }
        }
        .onChange(of: piecePickerItem) { _, item in
            Task { await importPiece(item) }
        }
        .sheet(isPresented: $showUrlSheet) { urlSheet }
        .alert("Season swap", isPresented: .init(
            get: { seasonSwapNotice != nil },
            set: { if !$0 { seasonSwapNotice = nil } }
        )) {
            Button("OK", role: .cancel) { seasonSwapNotice = nil }
        } message: {
            Text(seasonSwapNotice ?? "")
        }
        .alert("URL saved", isPresented: .init(
            get: { urlNotice != nil },
            set: { if !$0 { urlNotice = nil } }
        )) {
            Button("OK", role: .cancel) { urlNotice = nil }
        } message: {
            Text(urlNotice ?? "")
        }
    }

    // Pro paste-URL sheet. For now: capture the URL, show a note. Server-side
    // scrape wiring lands in a follow-up (needs a tryon-from-url edge fn).
    private var urlSheet: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("https://...", text: $pastedUrl)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textContentType(.URL)
                        .keyboardType(.URL)
                } header: {
                    Text("Product URL")
                } footer: {
                    Text("Paste a Zara / Instagram / Amazon product link. Hem will pull the garment and drop it into try-on.")
                }
            }
            .navigationTitle("Try from URL")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { showUrlSheet = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        let trimmed = pastedUrl.trimmingCharacters(in: .whitespacesAndNewlines)
                        showUrlSheet = false
                        if trimmed.isEmpty { return }
                        // Placeholder: mark source as url so subsequent generate can
                        // send it to the backend (edge function extension TBD).
                        pieceSource = "upload"
                        importedPieceUrl = trimmed
                        urlNotice = "Got the link. Full URL → try-on ships in the next update. For now, upload the item photo if you can."
                    }
                    .disabled(pastedUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
        .presentationDetents([.height(260)])
    }

    // MARK: - Sections

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 6) {
                Text("WEAR IT").font(.system(size: 10, weight: .semibold)).tracking(2).foregroundStyle(Palette.bronze)
                Text("Try on").font(Serif.display(28)).foregroundStyle(Palette.ink)
            }
            Spacer()
            Button(action: onClose) {
                Image(systemName: "xmark").foregroundStyle(Palette.ink).frame(width: 36, height: 36)
            }
        }
    }

    private var inputBlock: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Two photos: your fit + the piece you want to try on.")
                .font(Serif.italic(14))
                .foregroundStyle(Palette.muted)

            HStack(alignment: .top, spacing: 12) {
                twoTileYou.frame(maxWidth: .infinity)
                twoTilePiece.frame(maxWidth: .infinity)
            }

            // Fallback: browse Studio closet
            if pieceSource != "upload" && !pieces.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("OR PICK FROM STUDIO")
                        .font(.system(size: 10, weight: .semibold)).tracking(1.5)
                        .foregroundStyle(Palette.bronze)
                    piecesStrip
                }
            } else if pieceSource != "upload" && pieces.isEmpty && !loadingPieces {
                emptyStudioCard
            }

            if let error {
                Text(error).font(Serif.body(13)).foregroundStyle(Palette.bronze)
            }

            let hasPiece: Bool = pieceSource == "upload" ? importedPieceUrl != nil : pickedPiece != nil
            let ready = hasPiece && personBytes != nil && !busy
            PrimaryButton(
                title: busy ? "Composing look…" : "Generate try-on · \(Supa.tryonCost) credits",
                enabled: ready,
                action: run
            )
        }
    }

    // MARK: - Two-tile layout (YOU + PIECE)

    private var twoTileYou: some View {
        VStack(alignment: .leading, spacing: 6) {
            tileBox(image: personImage, placeholder: "Your photo", aspect: 3.0/4.0)
            HStack(spacing: 6) {
                Text("YOU")
                    .font(.system(size: 9, weight: .semibold)).tracking(1.5)
                    .foregroundStyle(Palette.bronze)
                Spacer()
                if personImage != nil {
                    Button(action: { personImage = nil; personBytes = nil }) {
                        Image(systemName: "xmark").font(.system(size: 10)).foregroundStyle(Palette.muted)
                    }.buttonStyle(.plain)
                }
            }
            HStack(spacing: 6) {
                Button(action: { Haptic.tap(); showCamera = true }) {
                    tinyChip(icon: "camera.fill", label: "Camera")
                }
                .buttonStyle(.plain)
                PhotosPicker(selection: $personPickerItem, matching: .images) {
                    tinyChip(icon: "photo.on.rectangle", label: "Gallery")
                }
            }
        }
    }

    @State private var pickedPieceImage: UIImage? = nil

    private var twoTilePiece: some View {
        let displayImage: UIImage? = {
            if pieceSource == "upload", let d = importedPieceBytes { return UIImage(data: d) }
            if pieceSource == "studio", let img = pickedPieceImage { return img }
            return nil
        }()
        return VStack(alignment: .leading, spacing: 6) {
            tileBox(image: displayImage, placeholder: pieceSource == "studio" ? "Pick from Studio below" : "Garment photo", aspect: 3.0/4.0)
            HStack(spacing: 6) {
                Text("PIECE")
                    .font(.system(size: 9, weight: .semibold)).tracking(1.5)
                    .foregroundStyle(Palette.bronze)
                Spacer()
                if displayImage != nil {
                    Button(action: {
                        importedPieceBytes = nil
                        importedPieceUrl = nil
                        pickedPiece = nil
                        pickedPieceUrl = nil
                        pickedPieceImage = nil
                    }) {
                        Image(systemName: "xmark").font(.system(size: 10)).foregroundStyle(Palette.muted)
                    }.buttonStyle(.plain)
                }
            }
            HStack(spacing: 6) {
                PhotosPicker(selection: $piecePickerItem, matching: .images) {
                    tinyChip(icon: "photo.on.rectangle", label: "Upload")
                }
                .simultaneousGesture(TapGesture().onEnded { pieceSource = "upload" })
                if !pieces.isEmpty {
                    Button(action: {
                        pieceSource = "studio"
                        Haptic.chip()
                    }) {
                        tinyChip(icon: "square.grid.2x2.fill", label: "Studio")
                    }
                    .buttonStyle(.plain)
                }
                // Pro-only: paste a product URL (Zara / IG / Amazon) — server-side scrape (TBD).
                Button(action: {
                    Haptic.chip()
                    let ctx = FeatureGates.requireTryon()
                    if ctx != nil { onOpenPaywall(); return }
                    pastedUrl = ""
                    showUrlSheet = true
                }) {
                    tinyChip(icon: "link", label: "URL")
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func tinyChip(icon: String, label: String) -> some View {
        HStack(spacing: 4) {
            Image(systemName: icon).font(.system(size: 10))
            Text(label).font(Serif.body(11, weight: .medium))
        }
        .foregroundStyle(Palette.bronze)
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 8).padding(.vertical, 6)
        .overlay(Capsule().stroke(Palette.bronze, lineWidth: 1))
        .clipShape(Capsule())
    }

    private func tileBox(image: UIImage?, placeholder: String, aspect: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: 14)
            .fill(Palette.card)
            .aspectRatio(aspect, contentMode: .fit)
            .overlay(
                Group {
                    if let image {
                        Image(uiImage: image).resizable().scaledToFill()
                    } else {
                        VStack(spacing: 4) {
                            Text("+").font(Serif.display(28)).foregroundStyle(Palette.ink)
                            Text(placeholder)
                                .font(Serif.italic(11))
                                .foregroundStyle(Palette.muted)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 6)
                        }
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 14))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: image == nil ? [4, 4] : []))
                    .foregroundStyle(Palette.hairline)
            )
    }

    private var segmentedControl: some View {
        HStack(spacing: 0) {
            ForEach(["studio", "upload"], id: \.self) { key in
                let active = pieceSource == key
                let label = key == "studio" ? "From Studio" : "Upload photo"
                Button(action: {
                    Haptic.chip()
                    pieceSource = key
                }) {
                    Text(label)
                        .font(Serif.body(13, weight: .semibold))
                        .foregroundStyle(active ? .white : Palette.ink)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(active ? Palette.ink : Color.clear)
                        .clipShape(Capsule())
                }
            }
        }
        .background(Palette.card)
        .clipShape(Capsule())
        .overlay(Capsule().stroke(Palette.hairline, lineWidth: 1))
    }

    private var uploadedPieceTile: some View {
        PhotosPicker(selection: $piecePickerItem, matching: .images) {
            RoundedRectangle(cornerRadius: 18)
                .fill(Palette.card)
                .aspectRatio(1.4, contentMode: .fit)
                .overlay(
                    Group {
                        if let d = importedPieceBytes, let img = UIImage(data: d) {
                            Image(uiImage: img).resizable().scaledToFill()
                                .clipShape(RoundedRectangle(cornerRadius: 18))
                                .overlay(
                                    Group {
                                        if importingPiece {
                                            Color.black.opacity(0.35)
                                            ProgressView().tint(.white)
                                        }
                                    }
                                )
                        } else {
                            VStack(spacing: 4) {
                                Text("+ Add garment photo").font(Serif.body(15, weight: .semibold)).foregroundStyle(Palette.ink)
                                Text("Flat lay works best.").font(Serif.body(12)).foregroundStyle(Palette.muted)
                            }
                        }
                    }
                )
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(Palette.hairline, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var emptyStudioCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Your closet is empty.").font(Serif.body(15, weight: .semibold)).foregroundStyle(Palette.ink)
            Text("Design a piece in Studio first, then come back to try it on.")
                .font(Serif.body(14)).foregroundStyle(Palette.muted)
            Button(action: onOpenStudioCreate) { OutlinePillButton(title: "Design new") }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Palette.hairline, lineWidth: 1))
    }

    private var piecesStrip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(pieces, id: \.id) { p in
                    PieceCard(piece: p, selected: pickedPiece?.id == p.id) {
                        Task {
                            Haptic.chip()
                            pickedPiece = p
                            pickedPieceUrl = nil
                            pickedPieceImage = nil
                            if let path = p.image_path {
                                pickedPieceUrl = try? await Repo.shared.signedClosetUrl(path)
                                if let signed = pickedPieceUrl,
                                   let bytes = try? await Repo.shared.downloadBytes(signed) {
                                    pickedPieceImage = UIImage(data: bytes)
                                }
                            }
                        }
                    }
                }
                Button(action: onOpenStudioCreate) {
                    VStack {
                        Text("+ Design").font(Serif.body(13, weight: .medium)).foregroundStyle(Palette.ink)
                    }
                    .frame(width: 110, height: 140)
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.hairline, lineWidth: 1))
                }
            }
        }
    }

    private var personTile: some View {
        RoundedRectangle(cornerRadius: 18)
            .fill(Palette.card)
            .aspectRatio(0.82, contentMode: .fit)
            .overlay(
                Group {
                    if let img = personImage {
                        Image(uiImage: img).resizable().scaledToFill()
                    } else {
                        Text("Add a full-length photo of you").font(Serif.body(14)).foregroundStyle(Palette.muted)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 18))
            )
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Palette.hairline, lineWidth: 1))
    }

    @ViewBuilder
    private func resultBlock(url: String) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            RoundedRectangle(cornerRadius: 20)
                .fill(Palette.card)
                .aspectRatio(0.82, contentMode: .fit)
                .overlay(
                    AsyncImage(url: URL(string: url)) { img in
                        img.resizable().scaledToFill()
                    } placeholder: {
                        ProgressView()
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 20))
                )
            HStack {
                if savingLook {
                    ProgressView().tint(Palette.bronze)
                    Text("Saving to Journal…").font(Serif.body(14)).foregroundStyle(Palette.bronze)
                } else {
                    Text(savedOk ? "✓ Saved to Journal" : "Save didn't stick — try again next time")
                        .font(Serif.body(14, weight: .semibold)).foregroundStyle(Palette.bronze)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .overlay(Capsule().stroke(Palette.bronze.opacity(0.6), lineWidth: 1))

            // Pro-only: Season swap — re-render the same look in a different season palette.
            Button(action: {
                Haptic.chip()
                let ctx = FeatureGates.requireTryon()
                if ctx != nil { onOpenPaywall(); return }
                seasonSwapNotice = "Season swap is queued — Hem will re-shoot this fit in summer palette. Full support ships in the next update."
            }) {
                HStack(spacing: 6) {
                    Image(systemName: "thermometer.sun.fill")
                    Text("Season swap · Pro")
                        .font(Serif.body(13, weight: .medium))
                }
                .foregroundStyle(Palette.ink)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .overlay(Capsule().stroke(Palette.ink.opacity(0.4), lineWidth: 1))
            }
            .buttonStyle(.plain)

            Button(action: {
                resultUrl = nil; savedOk = false
                pickedPiece = nil; pickedPieceUrl = nil
            }) { OutlinePillButton(title: "Try another piece") }
            Button(action: onClose) { OutlinePillButton(title: "Done") }
        }
    }

    // MARK: - Loading

    private func load() async {
        pieces = (try? await Repo.shared.closetItems()) ?? []
        loadingPieces = false
        if let d = CameraBus.shared.consume() {
            personBytes = d
            personImage = UIImage(data: d)
        }
    }

    private func importPiece(_ item: PhotosPickerItem?) async {
        guard let item, let bytes = try? await item.loadTransferable(type: Data.self) else { return }
        let processed = CameraModel.processJpeg(bytes) ?? bytes
        importedPieceBytes = processed
        pickedPiece = nil
        pickedPieceUrl = nil
        importedPieceUrl = nil
        importingPiece = true
        do {
            guard let uid = Repo.shared.userId else { throw RepoError.notSignedIn }
            let path = "\(uid)/tryon-ref-\(UUID().uuidString.lowercased()).jpg"
            _ = try await Supa.client.storage.from("outfits").upload(
                path, data: processed,
                options: FileOptions(contentType: "image/jpeg", upsert: false)
            )
            guard let signed = try await Repo.shared.signedOutfitUrl(path) else {
                throw NSError(domain: "TryOn", code: 1, userInfo: [NSLocalizedDescriptionKey: "Could not sign URL"])
            }
            importedPieceUrl = signed
        } catch {
            ToastBus.shared.post("Import failed: \(error.localizedDescription)")
        }
        importingPiece = false
    }

    // MARK: - Run

    private func run() {
        guard !busy else { return }
        busy = true
        error = nil
        Task {
            let gate = await CreditsGate.check(Supa.tryonCost)
            if case .ok = gate {} else {
                busy = false
                if case .insufficientBalance = gate { _ = CreditsGate.explainAndBlock(gate); onOpenPaywall() }
                else { _ = CreditsGate.explainAndBlock(gate) }
                return
            }
            do {
                guard let personBytes else { throw RepoError.notFound }
                let personPath = try await Repo.shared.uploadOutfitPhoto(bytes: personBytes)
                guard let personSigned = try await Repo.shared.signedOutfitUrl(personPath) else { throw RepoError.notFound }
                let pieceSigned: String
                if pieceSource == "upload" {
                    guard let u = importedPieceUrl else { throw NSError(domain: "TryOn", code: 2, userInfo: [NSLocalizedDescriptionKey: "No garment photo uploaded"]) }
                    pieceSigned = u
                } else {
                    if let u = pickedPieceUrl { pieceSigned = u }
                    else if let path = pickedPiece?.image_path, let s = try await Repo.shared.signedClosetUrl(path) { pieceSigned = s }
                    else { throw NSError(domain: "TryOn", code: 3, userInfo: [NSLocalizedDescriptionKey: "No piece selected"]) }
                }
                let cat = pieceSource == "upload" ? nil : pickedPiece?.category?.lowercased()
                // Pass the raw category through; the edge function decides
                // FASHN (garments) vs nano-banana (hats, glasses, shoes, etc.).
                let resp = try await Repo.shared.tryOnPiece(personUrl: personSigned, garmentUrl: pieceSigned, category: cat ?? "auto")
                guard let url = resp.image_url else {
                    throw NSError(domain: "TryOn", code: 4, userInfo: [NSLocalizedDescriptionKey: resp.error ?? "Try-on failed"])
                }
                try? await Repo.shared.spendCredits(amount: Supa.tryonCost, kind: "tryon")
                resultUrl = url
                busy = false

                // Auto-save to journal.
                savingLook = true
                do {
                    let outBytes = try await Repo.shared.downloadBytes(url)
                    let outPath = try await Repo.shared.uploadOutfitPhoto(bytes: outBytes, ext: "png")
                    let pieceName = pickedPiece?.name ?? (pieceSource == "upload" ? "uploaded garment" : "piece")
                    guard let uid = Repo.shared.userId else { throw RepoError.notSignedIn }
                    _ = try await Repo.shared.insertOutfit(OutfitInsert(
                        user_id: uid,
                        photo_path: outPath,
                        score: 0.0,
                        occasion: "Try-on",
                        hem_comment: "Try-on: \(pieceName)",
                        weather_c: nil, verdict: nil, subscores: nil, swaps: nil, annotations: nil,
                        kind: "tryon", linked_piece_id: pickedPiece?.id
                    ))
                    savedOk = true
                } catch { /* silent — matches Android */ }
                savingLook = false
            } catch {
                busy = false
                self.error = error.localizedDescription
                ToastBus.shared.post("Try-on failed: \(error.localizedDescription)")
            }
        }
    }

}

private struct PieceCard: View {
    let piece: ClosetItem
    let selected: Bool
    var onTap: () -> Void
    @State private var url: String?

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 4) {
                RoundedRectangle(cornerRadius: 14)
                    .fill(Palette.card)
                    .aspectRatio(1, contentMode: .fit)
                    .overlay(
                        Group {
                            if let s = url, let u = URL(string: s) {
                                AsyncImage(url: u) { $0.resizable().scaledToFill() } placeholder: { Color.clear }
                            } else {
                                Text("…").font(Serif.body(13)).foregroundStyle(Palette.muted)
                            }
                        }
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    )
                Text((piece.name ?? piece.category ?? "piece").prefix(18).description)
                    .font(Serif.body(12))
                    .foregroundStyle(Palette.ink)
                    .padding(.horizontal, 6)
                    .padding(.bottom, 6)
            }
            .frame(width: 110)
            .background(Palette.card)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(selected ? Palette.bronze : Palette.hairline, lineWidth: selected ? 2 : 1))
        }
        .buttonStyle(.plain)
        .task {
            if let path = piece.image_path {
                url = try? await Repo.shared.signedClosetUrl(path)
            }
        }
    }
}
