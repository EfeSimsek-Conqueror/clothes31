import SwiftUI
import PhotosUI
import Supabase

/// Try-on — two states of one editorial screen.
///
/// COMPOSE: a dashed 16:11 drop zone for the person photo, a bleeding
/// horizontal strip of Studio pieces (or an upload zone), a real product-link
/// importer, and a sticky ink footer that always states the price.
///
/// WORN: the composited result on a dashed 3:4 card with a burned-in caption,
/// a drag-to-wipe before/after, Hem's verdict (absorbed into the try-on price —
/// nothing extra is charged), an honest tri-state save row, and two real
/// secondary action: "try another piece".
///
/// Calls `tryon-outfit` via `Repo.tryOnPiece`, then `score-outfit` via
/// `HemService.score`, then auto-saves to the journal as `kind='tryon'`.
struct TryOnView: View {
    var onClose: () -> Void
    var onOpenPaywall: () -> Void = {}
    var onOpenStudioCreate: () -> Void = {}

    // MARK: - Model

    enum Phase: Equatable {
        case compose
        case busy(eyebrow: String, title: String)
        case result(String)
    }

    enum SaveState: Equatable { case idle, saving, saved, failed }

    enum ComposeBlock: Equatable {
        case needPerson
        case needPiece
        case needCredits(Int)
        case ready
    }

    // Studio pieces
    @State private var pieces: [ClosetItem] = []
    /// Whether the closet had anything at all before the no-image filter ran, so
    /// the empty state can tell "you own nothing" apart from "none of what you
    /// own has a photo".
    @State private var closetHadItems = false
    @State private var loadingPieces = true
    @State private var pickedPiece: ClosetItem?
    @State private var pickedPieceUrl: String?

    // Piece source. "studio" | "upload" — each remembers its own selection.
    @State private var pieceSource = "studio"
    @State private var importedPieceBytes: Data?
    /// Storage PATH, never a signed URL — signed URLs expire after an hour and
    /// a stale one surfaces as an opaque fal_error at generate time.
    @State private var importedPiecePath: String?
    @State private var importedPieceName: String?
    @State private var importingPiece = false

    // Person photo
    @State private var personBytes: Data?
    @State private var personImage: UIImage?

    // Flow
    @State private var phase: Phase = .compose
    @State private var error: String?
    @State private var saveState: SaveState = .idle
    @State private var savedOutfitId: String?

    /// Captured at generate time. The result screen and the journal row must not
    /// read live compose-screen state: "Try another piece" clears the selection
    /// while a ~10s save is still in flight.
    @State private var resultPieceName: String = "the piece"
    @State private var resultPieceId: String?
    /// Monotonic. Every state assignment made by `saveLook` is guarded on it so a
    /// stale save can never paint run A's verdict under run B's image.
    @State private var runToken = 0
    @State private var saveTask: Task<Void, Never>?

    /// What the reference actually held, and the flat lay of it with the wearer
    /// removed. Both come back from the try-on itself, so a look costs one call
    /// and yields both the render and the pieces it was made of.
    @State private var readItems: [TryOnItem] = []
    @State private var plateUrl: String?
    /// Set while re-running a single piece off the plate, so the strip can show
    /// which one is being worked on.
    @State private var isolating: String?
    /// The render on screen before an isolate started, so a failure returns
    /// there instead of dumping the wearer back on the compose sheet.
    @State private var lastResultUrl: String?
    /// Storage path of the photograph this run started from. Kept so the saved
    /// row can carry its own "before" and the comparison outlives the session.
    @State private var beforePath: String?

    // Hem's verdict on the composite
    @State private var verdict: HemScored?
    @State private var verdictLoading = false
    @State private var verdictFailed = false

    // Pickers
    @State private var showCamera = false
    @State private var personPickerItem: PhotosPickerItem?
    @State private var piecePickerItem: PhotosPickerItem?

    // Product link
    @State private var showUrlSheet = false
    @State private var pastedUrl = ""
    @State private var urlBusy = false
    @State private var urlError: String?

    // Before/after wipe
    @State private var wipe: CGFloat = 0
    @State private var cardWidth: CGFloat = 1

    @State private var reportTarget: ReportTarget?

    @ObservedObject private var credits = CreditsBus.shared
    /// Designing a piece opens Studio over this screen, so `.task` never runs
    /// again on the way back. Watching the closet is what makes a piece appear
    /// in the strip the moment it exists.
    @ObservedObject private var closet = ClosetBus.shared
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// One-line kill switch if review pushes back on client-side page reads.
    private let productLinkEnabled = true

    // MARK: - Copy

    private static let composingTips = [
        "Draping the fabric on your frame…",
        "Matching fit tension and folds.",
        "Preserving your face, restyling the rest.",
        "One-of-one, no filters — just cloth.",
    ]
    private static let importFailedCopy = "That photo didn't import. Try another one."
    private static let creditSyncCopy = "Credits didn't sync — your balance may look high until the next refresh."
    private static let tryOnFailedCopy = "That didn't come out. Nothing was charged."
    private static let verdictFailedCopy = "Hem couldn't read this one. The look is still yours."

    // MARK: - Derived

    /// The tile takes the photograph's own shape: portrait shots get a portrait
    /// box, landscape shots a landscape one. Combined with `scaledToFit` this
    /// means the image fills its tile exactly — nothing cropped, and no dead
    /// letterbox bars either.
    ///
    /// Bounded because the tile still has to share a scrolling sheet with the
    /// occasion controls and the CTA: a 9:16 phone shot would otherwise push the
    /// button off-screen, and a panorama would collapse to a strip.
    private static func tileAspect(_ image: UIImage?) -> CGFloat {
        guard let image, image.size.height > 0 else { return 1.84 }
        let raw = image.size.width / image.size.height
        return min(max(raw, 0.62), 1.9)
    }

    /// Portrait tiles are allowed to be taller — that is the whole point of
    /// matching the orientation — but not unbounded.
    private static func tileMaxHeight(_ image: UIImage?) -> CGFloat {
        tileAspect(image) < 1 ? 420 : 250
    }

    private var activePieceReady: Bool {
        pieceSource == "upload" ? importedPiecePath != nil : pickedPiece != nil
    }

    private var pieceDisplayName: String {
        if pieceSource == "upload" {
            let n = importedPieceName?.trimmingCharacters(in: .whitespacesAndNewlines)
            return (n?.isEmpty == false) ? n! : "your upload"
        }
        let n = pickedPiece?.name?.trimmingCharacters(in: .whitespacesAndNewlines)
        return (n?.isEmpty == false) ? n! : "the piece"
    }

    private var composeBlock: ComposeBlock {
        if personBytes == nil { return .needPerson }
        if !activePieceReady { return .needPiece }
        // CreditsGate returns .ok immediately for server-granted Pro, so telling a
        // comp/ops account it is short would be a disabled button with a false reason.
        if let b = credits.balance, b < Supa.tryonCost, !RcBilling.shared.hasServerPro {
            return .needCredits(Supa.tryonCost - b)
        }
        return .ready
    }

    private var isBusy: Bool {
        if case .busy = phase { return true }
        return false
    }

    // MARK: - Body

    /// Row written by the finished run; its output opens over this screen.
    @State private var justSavedId: String?

    var body: some View {
        tryOnBody.justSaved($justSavedId)
    }

    private var tryOnBody: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    if case .result(let url) = phase {
                        resultBody(url: url)
                    } else {
                        composeBody
                    }
                }
                .padding(.horizontal, 20)
                .frame(maxWidth: 560)
                .frame(maxWidth: .infinity)
            }
            .scrollIndicators(.hidden)

            if case .busy(let e, let t) = phase {
                WaitingOverlay(
                    eyebrow: e,
                    title: t,
                    tips: Self.composingTips
                )
            }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) { footer }
        .task {
            if FeatureGates.requireTryon() != nil {
                onClose()
                onOpenPaywall()
                return
            }
            await load()
        }
        .onChange(of: closet.revision) { _, _ in
            Task { await reloadPieces() }
        }
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
            guard let item else { return }
            Task {
                if let d = try? await item.loadTransferable(type: Data.self) {
                    let p = CameraModel.processJpeg(d) ?? d
                    personBytes = p
                    personImage = UIImage(data: p)
                } else {
                    ToastBus.shared.post(Self.importFailedCopy)
                }
                // PhotosPickerItem is Equatable — without this reset, picking the
                // very same photo twice in a row fires no onChange at all.
                personPickerItem = nil
            }
        }
        .onChange(of: piecePickerItem) { _, item in
            guard let item else { return }
            Task {
                if let d = try? await item.loadTransferable(type: Data.self) {
                    await importPiece(bytes: d, name: nil)
                } else {
                    ToastBus.shared.post(Self.importFailedCopy)
                }
                piecePickerItem = nil
            }
        }
        .sheet(isPresented: $showUrlSheet) { productLinkSheet }
        .sheet(item: $reportTarget) { t in
            ReportContentSheet(target: t) { reportTarget = nil }
        }
    }

    // MARK: - Shared chrome

    private var eyebrowRow: some View {
        HStack(alignment: .top) {
            HStack(spacing: 8) {
                Eyebrow(text: "WEAR IT", size: 10)
                ProBadge(style: .filled)
            }
            .padding(.top, 2)
            Spacer()
            CircleCloseButton(label: "Close try-on", action: onClose)
        }
        .padding(.top, 8)
    }

    private func heading(_ title: String, _ subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title)
                .font(Serif.display(36))
                .foregroundStyle(Palette.ink)
                .padding(.top, 10)
            Text(subtitle)
                .font(Serif.body(14))
                .foregroundStyle(Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 6)
        }
    }

    private func sectionLabel(_ text: String) -> some View {
        Eyebrow(text: text, size: 10)
    }

    // MARK: - COMPOSE

    @ViewBuilder
    private var composeBody: some View {
        eyebrowRow
        heading("Try on", "Two photos: your fit + the piece you want to try on.")

        sectionLabel("YOU").padding(.top, 28)
        personZone.padding(.top, 10)

        ViewThatFits(in: .horizontal) {
            HStack {
                sectionLabel("PIECE")
                Spacer(minLength: 12)
                SegmentedPill(
                    options: [(key: "studio", label: "From Studio"), (key: "upload", label: "Upload")],
                    selection: $pieceSource
                )
            }
            VStack(alignment: .leading, spacing: 10) {
                sectionLabel("PIECE")
                SegmentedPill(
                    options: [(key: "studio", label: "From Studio"), (key: "upload", label: "Upload")],
                    selection: $pieceSource
                )
            }
        }
        .padding(.top, 24)

        Group {
            if pieceSource == "studio" {
                studioPieceSection
            } else {
                uploadZone
            }
        }
        .padding(.top, 12)

        if productLinkEnabled {
            productLinkRow.padding(.top, 20)
        }

        Spacer(minLength: 24)
    }

    /// The empty state carries its own two buttons rather than sitting inside a
    /// PhotosPicker: SwiftUI does not hit-test a control nested inside another
    /// control's label, so Camera and Gallery have to be real siblings on the
    /// tile, not children of a picker wrapping the whole zone.
    private var personZone: some View {
        DashedDropZone(
            corner: 14,
            aspect: Self.tileAspect(personImage),
            dashed: personImage == nil,
            maxHeight: Self.tileMaxHeight(personImage)
        ) {
            if let img = personImage {
                // Fit, never fill: this photo is judged on hem length, shoulder
                // line and footwear, and a fill crop is exactly what removes
                // them. Letterboxing onto the card is the honest trade.
                Image(uiImage: img)
                    .resizable()
                    .scaledToFit()
            } else {
                VStack(spacing: 16) {
                    Text("Add a full-length photo of you")
                        .font(Serif.body(14))
                        .foregroundStyle(Palette.muted)
                    HStack(spacing: 12) {
                        Button {
                            Haptic.tap()
                            showCamera = true
                        } label: {
                            TileActionPill(title: "Camera")
                        }
                        .buttonStyle(.plain)

                        PhotosPicker(selection: $personPickerItem, matching: .images) {
                            TileActionPill(title: "Gallery")
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
                .multilineTextAlignment(.center)
            }
        }
        .overlay(alignment: .topTrailing) {
            if personImage != nil {
                Button {
                    Haptic.chip()
                    personImage = nil
                    personBytes = nil
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(Palette.ink)
                        .frame(width: 26, height: 26)
                        .background(Circle().fill(Palette.card.opacity(0.92)))
                        .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Remove your photo")
                .padding(10)
            }
        }
    }

    @ViewBuilder
    private var studioPieceSection: some View {
        if loadingPieces {
            HStack(alignment: .top, spacing: 12) {
                ForEach(0..<3, id: \.self) { _ in
                    SkeletonBar(width: 96, height: 150, corner: 10)
                }
            }
            .padding(.top, 10)
        } else if pieces.isEmpty {
            emptyStudioCard
        } else {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 12) {
                    ForEach(pieces.indices, id: \.self) { i in
                        let p = pieces[i]
                        PieceStripCard(
                            piece: p,
                            index: i,
                            selected: pickedPiece?.id != nil && pickedPiece?.id == p.id
                        ) {
                            selectPiece(p)
                        }
                    }
                    AddDesignCard(action: onOpenStudioCreate)
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)
                .padding(.bottom, 4)
            }
            .padding(.horizontal, -20)
            .scrollClipDisabled()
        }
    }

    private var emptyStudioCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(closetHadItems
                ? "None of your Studio pieces has a photo yet, and a try-on needs one. Design a piece, or upload the garment instead."
                : "Nothing in your Studio yet. Design a piece and try it on.")
                .font(Serif.body(14))
                .foregroundStyle(Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
            OutlinePill(title: "Open Studio", action: onOpenStudioCreate)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [5, 4]))
                .foregroundStyle(Palette.hairline)
        )
    }

    private var uploadZone: some View {
        let uploaded = importedPieceBytes.flatMap { UIImage(data: $0) }
        return PhotosPicker(selection: $piecePickerItem, matching: .images) {
            DashedDropZone(
                corner: 14,
                aspect: uploaded == nil ? 16.0 / 11.0 : Self.tileAspect(uploaded),
                dashed: importedPieceBytes == nil,
                maxHeight: uploaded == nil ? 232 : Self.tileMaxHeight(uploaded)
            ) {
                ZStack {
                    if let img = uploaded {
                        Image(uiImage: img).resizable().scaledToFit()
                    } else {
                        VStack(spacing: 6) {
                            Text("+").font(Serif.display(24)).foregroundStyle(Palette.ink)
                            Text("Garment photo")
                                .font(Serif.body(14, weight: .medium))
                                .foregroundStyle(Palette.ink)
                            Text("Flat lay works best.")
                                .font(Serif.body(12))
                                .foregroundStyle(Palette.muted)
                        }
                        .multilineTextAlignment(.center)
                    }
                    if importingPiece {
                        Color.black.opacity(0.35)
                        ProgressView().tint(.white)
                    }
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(importedPieceBytes == nil ? "Add a garment photo" : "Replace the garment photo")
    }

    private var productLinkRow: some View {
        Button {
            Haptic.chip()
            if FeatureGates.requireTryon() != nil { onOpenPaywall(); return }
            urlError = nil
            showUrlSheet = true
        } label: {
            HStack {
                Text("Try from a product link")
                    .font(Serif.body(14))
                    .foregroundStyle(Palette.muted)
                Spacer(minLength: 12)
                ProBadge(style: .text)
            }
            .padding(.horizontal, 18)
            .frame(height: 52)
            .frame(maxWidth: .infinity)
            .overlay(RoundedRectangle(cornerRadius: 26).stroke(Palette.hairline, lineWidth: 1))
            .contentShape(RoundedRectangle(cornerRadius: 26))
        }
        .buttonStyle(.plain)
    }

    // MARK: - WORN

    @ViewBuilder
    private func resultBody(url: String) -> some View {
        eyebrowRow
        heading("Worn", "Hem composited the piece onto your frame.")

        resultCard(url: url).padding(.top, 24)

        verdictBlock.padding(.top, 16)

        saveRow(url: url).padding(.top, 16)

        piecesSection.padding(.top, 24)

        SecondaryTile(title: "Try another piece", pro: false) { reset() }
            .padding(.top, 28)

        Spacer(minLength: 24)
    }

    /// THE PIECES — the flat lay of what the reference actually held, and a chip
    /// per item.
    ///
    /// It earns its place twice: it is the receipt for what the read decided,
    /// so a wrong item is visible rather than mysterious, and it is how the
    /// wearer isolates one piece. Tapping a chip re-runs the try-on against that
    /// piece alone — a fresh paid render, and the copy says so rather than
    /// letting a tap quietly cost credits.
    @ViewBuilder
    private var piecesSection: some View {
        if plateUrl != nil || !readItems.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                Eyebrow(text: "THE PIECES", size: 10)

                if let p = plateUrl, let u = URL(string: p) {
                    AsyncImage(url: u) { img in
                        img.resizable().scaledToFit()
                    } placeholder: {
                        RoundedRectangle(cornerRadius: 14).fill(Palette.card)
                            .frame(height: 160)
                    }
                    .frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.hairline, lineWidth: 1))
                }

                if !readItems.isEmpty {
                    Text("Wear one on its own · \(Supa.tryonCost) credits")
                        .font(Serif.body(12))
                        .foregroundStyle(Palette.muted)

                    // FlowChipRow is a selector, not a button row, so the tap
                    // arrives through the binding's setter. Nothing stays
                    // selected: picking a piece is an action, not a state.
                    FlowChipRow(
                        options: readItems,
                        selection: Binding(
                            get: { readItems.first(where: { $0.id == isolating }) ?? readItems[0] },
                            set: { isolatePiece($0) }
                        ),
                        label: { $0.display }
                    )
                    .opacity(isolating == nil ? 1 : 0.5)
                    .disabled(isolating != nil)
                }
            }
        }
    }

    private func resultCard(url: String) -> some View {
        // No height cap: `.aspectRatio(_, contentMode: .fit)` shrinks BOTH axes to
        // satisfy one, which left the hero card leading-aligned with dead paper
        // down the right edge. It fills the 20pt gutter, same as Android.
        DashedDropZone(corner: 14, aspect: 3.0 / 4.0, dashed: true) {
            ZStack {
                if let p = personImage {
                    Image(uiImage: p).resizable().scaledToFill()
                }
                AsyncImage(url: URL(string: url)) { img in
                    img.resizable().scaledToFill()
                } placeholder: {
                    Color.clear.overlay(ProgressView().tint(Palette.bronze))
                }
                .mask(alignment: .trailing) {
                    Rectangle().padding(.leading, max(0, wipe) * cardWidth)
                }
            }
        }
        .burnedCaption(
            leading: resultPieceName,
            trailingItalic: wipe > 0.02 ? "yours" : "on you",
            corner: 14
        )
        .overlay(alignment: .leading) {
            if wipe > 0.02 {
                Rectangle()
                    .fill(Color.white.opacity(0.9))
                    .frame(width: 1)
                    .offset(x: wipe * cardWidth)
                    .allowsHitTesting(false)
            }
        }
        .overlay(alignment: .topTrailing) {
            Button {
                Haptic.chip()
                reportTarget = ReportTarget(ReportKind.outfit, savedOutfitId ?? url)
            } label: {
                Image(systemName: "flag")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 30, height: 30)
                    .background(Circle().fill(Color.black.opacity(0.35)))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Report this try-on")
            .padding(10)
        }
        .background(
            GeometryReader { geo in
                Color.clear
                    .onAppear { cardWidth = max(geo.size.width, 1) }
                    .onChange(of: geo.size.width) { _, w in cardWidth = max(w, 1) }
            }
        )
        // .simultaneousGesture, never .highPriorityGesture: a high-priority drag
        // wins over the enclosing ScrollView's pan for the whole gesture, so a
        // vertical swipe anywhere on this card would do nothing at all. Running
        // alongside the scroll view costs nothing — the wipe only tracks drags
        // that are horizontal-dominant, and the scroll view only claims vertical.
        .simultaneousGesture(
            DragGesture(minimumDistance: 12)
                .onChanged { v in
                    guard abs(v.translation.width) > abs(v.translation.height) else { return }
                    wipe = min(max(v.location.x / cardWidth, 0), 1)
                }
                .onEnded { _ in
                    guard wipe > 0 else { return }
                    if reduceMotion { wipe = 0 }
                    else { withAnimation(.easeOut(duration: 0.2)) { wipe = 0 } }
                }
        )
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Try-on result: \(resultPieceName) on you")
    }

    @ViewBuilder
    private var verdictBlock: some View {
        if verdictLoading {
            HStack(spacing: 12) {
                SkeletonBar(width: 56, height: 30, corner: 15)
                SkeletonBar(height: 12)
            }
        } else if let v = verdict {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .top, spacing: 12) {
                    ScoreChip(score: v.headlineScore)
                    Text(v.hemComment)
                        .font(Serif.italic(16))
                        .foregroundStyle(Palette.ink)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if !v.swaps.isEmpty {
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(Array(v.swaps.prefix(2)), id: \.self) { s in
                            Hairline()
                            Text(s)
                                .font(Serif.body(13))
                                .foregroundStyle(Palette.muted)
                                .fixedSize(horizontal: false, vertical: true)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.vertical, 10)
                        }
                    }
                    .padding(.top, 4)
                }
            }
        } else if verdictFailed {
            Text(Self.verdictFailedCopy)
                .font(Serif.body(13))
                .foregroundStyle(Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    @ViewBuilder
    private func saveRow(url: String) -> some View {
        switch saveState {
        case .idle:
            EmptyView()
        case .saving:
            HStack(spacing: 10) {
                ProgressView().tint(Palette.bronze).frame(width: 18, height: 18)
                Text("Saving to Journal…")
                    .font(Serif.body(13))
                    .foregroundStyle(Palette.muted)
            }
        case .saved:
            HStack(spacing: 10) {
                ZStack {
                    Circle().fill(Palette.ink).frame(width: 18, height: 18)
                    Image(systemName: "checkmark")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(.white)
                }
                Text("Saved to Journal · linked to \(resultPieceName)")
                    .font(Serif.body(13))
                    .foregroundStyle(Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        case .failed:
            HStack(spacing: 10) {
                Text("Not saved — the look is still on screen.")
                    .font(Serif.body(13))
                    .foregroundStyle(Palette.bronze)
                    .fixedSize(horizontal: false, vertical: true)
                Button {
                    let token = runToken
                    saveTask?.cancel()
                    saveTask = Task { await saveLook(url, token: token) }
                } label: {
                    Text("Retry")
                        .font(Serif.body(13, weight: .medium))
                        .underline()
                        .foregroundStyle(Palette.ink)
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - Footer

    @ViewBuilder
    private var footer: some View {
        if isBusy {
            // safeAreaInset content composites ABOVE the view it modifies, so a
            // footer rendered during a paid render would sit on top of the busy
            // overlay and read as a tappable primary action. Nothing renders here.
            EmptyView()
        } else if case .result = phase {
            StickyFooter {
                VStack(spacing: 8) {
                    if let error {
                        Text(error)
                            .font(Serif.body(12.5))
                            .foregroundStyle(Palette.bronze)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                            .transition(.opacity)
                    }
                    PrimaryButton(title: "Done", height: 62, corner: 8, action: onClose)
                }
            }
        } else {
            StickyFooter {
                VStack(spacing: 8) {
                    if let error {
                        Text(error)
                            .font(Serif.body(12.5))
                            .foregroundStyle(Palette.bronze)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                            .transition(.opacity)
                    }
                    PrimaryButton(
                        title: "Try it on · \(Supa.tryonCost) credits",
                        enabled: composeBlock == .ready && !isBusy,
                        height: 62,
                        corner: 8,
                        action: run
                    )
                    footnote
                }
            }
        }
    }

    @ViewBuilder
    private var footnote: some View {
        if credits.balance == nil {
            SkeletonBar(width: 150, height: 11)
        } else {
            Text(footnoteText)
                .font(Serif.body(12))
                .foregroundStyle(Palette.muted)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .dynamicTypeSize(...DynamicTypeSize.accessibility2)
        }
    }

    private var footnoteText: String {
        switch composeBlock {
        case .needPerson:          return "Start with a full-length photo of you"
        case .needPiece:           return "Pick a piece to continue"
        case .needCredits(let n):  return "Need \(n) more credits"
        case .ready:               return "\(credits.balance ?? 0) credits left · result in ~30 seconds"
        }
    }

    // MARK: - Product link sheet

    private var productLinkSheet: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                Eyebrow(text: "PRODUCT LINK", size: 10)
                Spacer()
                CircleCloseButton(label: "Close") { showUrlSheet = false }
            }
            Text("Paste the page")
                .font(Serif.display(24))
                .foregroundStyle(Palette.ink)
                .padding(.top, 8)
            Text("Hem pulls the item's own photo. You'll see it before anything is charged.")
                .font(Serif.body(13))
                .foregroundStyle(Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 6)

            TextField("https://…", text: $pastedUrl)
                .font(Serif.body(14))
                .foregroundStyle(Palette.ink)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .textContentType(.URL)
                .keyboardType(.URL)
                .disabled(urlBusy)
                .padding(.horizontal, 16)
                .frame(height: 48)
                .background(Palette.card)
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.hairline, lineWidth: 1))
                .padding(.top, 16)

            if let urlError {
                Text(urlError)
                    .font(Serif.body(12.5))
                    .foregroundStyle(Palette.bronze)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 8)
            }

            PrimaryButton(
                title: urlBusy ? "Reading the page…" : "Fetch",
                enabled: !urlBusy && !pastedUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                height: 52,
                corner: 8,
                action: fetchProductLink
            )
            .padding(.top, 14)

            Button {
                showUrlSheet = false
            } label: {
                Text("Cancel")
                    .font(Serif.body(13))
                    .foregroundStyle(Palette.muted)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
            }
            .buttonStyle(.plain)
            .disabled(urlBusy)

            Spacer(minLength: 0)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.paper.ignoresSafeArea())
        .presentationDetents([.height(360)])
    }

    // MARK: - Loading

    /// The Studio strip on its own, so a newly designed piece can arrive without
    /// disturbing a photo the wearer has already chosen.
    private func reloadPieces() async {
        // Only pieces that actually have a garment image. A closet row without
        // one renders as an empty tinted card with nothing but its "STUDIO 01"
        // label — and it cannot be tried on anyway, since the compositor needs a
        // picture of the garment. Offering it is a dead tap.
        let all = (try? await Repo.shared.closetItems()) ?? []
        closetHadItems = !all.isEmpty
        let usable = all.filter { item in
            let path = item.image_path?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let url = item.image_url?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            return !path.isEmpty || !url.isEmpty
        }
        // A piece that arrives while the strip is on screen slides in rather
        // than appearing between frames.
        if pieces.isEmpty || reduceMotion {
            pieces = usable
        } else {
            withAnimation(.easeInOut(duration: 0.25)) { pieces = usable }
        }
        loadingPieces = false
    }

    private func load() async {
        await reloadPieces()
        if personBytes == nil, let d = CameraBus.shared.consume() {
            personBytes = d
            personImage = UIImage(data: d)
        }
        await credits.refresh()
    }

    private func selectPiece(_ p: ClosetItem) {
        Haptic.chip()
        // Re-tapping the selected card unselects it (parity with Studio detail).
        if pickedPiece?.id != nil && pickedPiece?.id == p.id {
            pickedPiece = nil
            pickedPieceUrl = nil
            return
        }
        pickedPiece = p
        pickedPieceUrl = nil
        Task {
            if let path = p.image_path {
                pickedPieceUrl = try? await Repo.shared.signedClosetUrl(path)
            }
        }
    }

    /// Shared by the gallery picker and the product-link importer. Uploads into
    /// our own bucket so the garment URL handed to fal is always one of ours.
    private func importPiece(bytes: Data, name: String?) async {
        let processed = CameraModel.processJpeg(bytes) ?? bytes
        importedPieceBytes = processed
        importedPiecePath = nil
        importedPieceName = name
        pieceSource = "upload"
        importingPiece = true
        do {
            guard let uid = Repo.shared.userId else { throw RepoError.notSignedIn }
            let path = "\(uid)/tryon-ref-\(UUID().uuidString.lowercased()).jpg"
            _ = try await Supa.client.storage.from("outfits").upload(
                path, data: processed,
                options: FileOptions(contentType: "image/jpeg", upsert: false)
            )
            importedPiecePath = path
        } catch {
            importedPieceBytes = nil
            importedPieceName = nil
            ToastBus.shared.post(Self.importFailedCopy)
        }
        importingPiece = false
    }

    // MARK: - Product link import (client-side, no edge function)

    private enum LinkFailure: Error {
        case notHttps
        case unreachable
        case noImage
    }

    private static let desktopUA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    private static let htmlCap = 256 * 1024
    private static let imageCap = 8 * 1024 * 1024

    private func fetchProductLink() {
        let raw = pastedUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let u = URL(string: raw), u.scheme?.lowercased() == "https", u.host != nil else {
            urlError = "That needs to be an https link."
            return
        }
        urlError = nil
        urlBusy = true
        Task {
            do {
                let (html, finalURL) = try await Self.loadPage(u)
                guard let rawImage = Self.metaContent(html, attr: "property", value: "og:image")
                        ?? Self.metaContent(html, attr: "name", value: "twitter:image"),
                      let imageURL = Self.resolve(rawImage, against: finalURL),
                      imageURL.scheme?.lowercased() == "https"
                else { throw LinkFailure.noImage }

                let bytes = try await Self.loadImage(imageURL)
                guard UIImage(data: bytes) != nil else { throw LinkFailure.noImage }

                let title = Self.metaContent(html, attr: "property", value: "og:title")?
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                let name = (title?.isEmpty == false) ? String(title!.prefix(40)) : nil

                urlBusy = false
                showUrlSheet = false
                pastedUrl = ""
                await importPiece(bytes: bytes, name: name)
            } catch LinkFailure.noImage {
                urlBusy = false
                urlError = "That page didn't hand over a picture. Screenshot the item and upload it instead."
            } catch {
                urlBusy = false
                urlError = "Couldn't reach that page. Some stores block automated reads — upload a screenshot instead."
            }
        }
    }

    /// Reads at most the first 256KB of a page. The HTML is never rendered
    /// anywhere — only `<meta>` attributes are parsed out of it.
    private static func loadPage(_ url: URL) async throws -> (String, URL) {
        var req = URLRequest(url: url)
        req.timeoutInterval = 15
        req.setValue(desktopUA, forHTTPHeaderField: "User-Agent")
        req.setValue("text/html,application/xhtml+xml", forHTTPHeaderField: "Accept")
        let (data, resp) = try await URLSession.shared.data(for: req)
        if let http = resp as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw LinkFailure.unreachable
        }
        let html = String(decoding: data.prefix(htmlCap), as: UTF8.self)
        return (html, resp.url ?? url)
    }

    private static func loadImage(_ url: URL) async throws -> Data {
        var req = URLRequest(url: url)
        req.timeoutInterval = 15
        req.setValue(desktopUA, forHTTPHeaderField: "User-Agent")
        req.setValue("image/*", forHTTPHeaderField: "Accept")
        let (data, resp) = try await URLSession.shared.data(for: req)
        if let http = resp as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw LinkFailure.unreachable
        }
        if resp.expectedContentLength > Int64(imageCap) { throw LinkFailure.noImage }
        guard data.count <= imageCap, !data.isEmpty else { throw LinkFailure.noImage }
        return data
    }

    /// `<meta property="og:image" content="…">` in either attribute order,
    /// either quote style.
    private static func metaContent(_ html: String, attr: String, value: String) -> String? {
        let v = NSRegularExpression.escapedPattern(for: value)
        let patterns = [
            "<meta[^>]+\(attr)\\s*=\\s*[\"']\(v)[\"'][^>]*content\\s*=\\s*[\"']([^\"']*)[\"']",
            "<meta[^>]+content\\s*=\\s*[\"']([^\"']*)[\"'][^>]*\(attr)\\s*=\\s*[\"']\(v)[\"']",
        ]
        let ns = html as NSString
        for p in patterns {
            guard let re = try? NSRegularExpression(pattern: p, options: [.caseInsensitive]) else { continue }
            guard let m = re.firstMatch(in: html, range: NSRange(location: 0, length: ns.length)),
                  m.numberOfRanges > 1 else { continue }
            let s = ns.substring(with: m.range(at: 1)).trimmingCharacters(in: .whitespacesAndNewlines)
            if !s.isEmpty { return decodeEntities(s) }
        }
        return nil
    }

    private static func decodeEntities(_ s: String) -> String {
        var out = s
        for (k, v) in [("&amp;", "&"), ("&quot;", "\""), ("&#39;", "'"), ("&apos;", "'"),
                       ("&lt;", "<"), ("&gt;", ">"), ("&nbsp;", " ")] {
            out = out.replacingOccurrences(of: k, with: v)
        }
        return out
    }

    /// Handles absolute, protocol-relative (`//cdn/...`) and root/relative paths
    /// against the post-redirect URL.
    private static func resolve(_ raw: String, against base: URL) -> URL? {
        if raw.hasPrefix("//") {
            return URL(string: "\(base.scheme ?? "https"):\(raw)")
        }
        return URL(string: raw, relativeTo: base)?.absoluteURL
    }

    // MARK: - Run the try-on

    /// Wear one piece off the plate on its own.
    ///
    /// A fresh paid render, and deliberately so: it is a new composite, not a
    /// crop of the last one. The garment sent is the PLATE, with the styling
    /// prompt narrowed to the chosen item — the plate is already the wearer-free
    /// reference, so isolating a piece costs no extra extraction.
    private func isolatePiece(_ item: TryOnItem) {
        guard isolating == nil, phase != .compose else { return }
        Haptic.tap()
        isolating = item.id
        error = nil
        runToken += 1
        let token = runToken
        saveTask?.cancel()
        phase = .busy(eyebrow: "TRYING ON", title: "Just the \(item.name)")
        Task {
            defer { isolating = nil }
            let gate = await CreditsGate.check(Supa.tryonCost)
            if case .ok = gate {} else {
                phase = .result(lastResultUrl ?? "")
                _ = CreditsGate.explainAndBlock(gate)
                if case .insufficientBalance = gate { onOpenPaywall() }
                credits.refreshAsync()
                return
            }
            do {
                guard let personBytes else { throw RepoError.notFound }
                let personPath = try await Repo.shared.uploadOutfitPhoto(bytes: personBytes)
                beforePath = personPath
                guard let personSigned = try await Repo.shared.signedOutfitUrl(personPath) else {
                    throw RepoError.notFound
                }
                // The ORIGINAL reference, not the plate: the server cuts a
                // fresh single-item plate from it, because a try-on model wears
                // everything it is shown and a four-piece plate cannot be
                // narrowed with words.
                guard let refPath = importedPiecePath,
                      let refUrl = try await Repo.shared.signedOutfitUrl(refPath) else {
                    throw RepoError.notFound
                }
                let resp = try await Repo.shared.tryOnPiece(
                    personUrl: personSigned, garmentUrl: refUrl,
                    category: item.slot, only: item.name
                )
                guard let url = resp.image_url, !url.isEmpty else { throw RepoError.notFound }

                do {
                    try await Repo.shared.spendCredits(amount: Supa.tryonCost, kind: "tryon")
                } catch {
                    ToastBus.shared.post(Self.creditSyncCopy)
                }

                wipe = 0
                verdict = nil
                verdictFailed = false
                saveState = .idle
                savedOutfitId = nil
                resultPieceName = item.name
                withAnimation(.easeInOut(duration: 0.22)) { phase = .result(url) }
                saveTask = Task { await saveLook(url, token: token) }
            } catch {
                phase = .result(lastResultUrl ?? "")
                self.error = Self.tryOnFailedCopy
            }
        }
    }

    private func run() {
        guard phase == .compose else { return }
        Haptic.tap()
        error = nil
        // Freeze the piece identity now. The compose screen can change under a
        // save that takes ~10s (download + upload + score + insert).
        let nameNow = pieceDisplayName
        let idNow = pieceSource == "studio" ? pickedPiece?.id : nil
        runToken += 1
        let token = runToken
        saveTask?.cancel()
        phase = .busy(eyebrow: "COMPOSING", title: "Dressing you now")
        Task {
            let gate = await CreditsGate.check(Supa.tryonCost)
            if case .ok = gate {} else {
                phase = .compose
                _ = CreditsGate.explainAndBlock(gate)
                if case .insufficientBalance = gate { onOpenPaywall() }
                credits.refreshAsync()
                return
            }
            do {
                guard let personBytes else { throw RepoError.notFound }
                let personPath = try await Repo.shared.uploadOutfitPhoto(bytes: personBytes)
                beforePath = personPath
                guard let personSigned = try await Repo.shared.signedOutfitUrl(personPath) else {
                    throw RepoError.notFound
                }

                // Sign the garment URL NOW — a cached signed URL older than an
                // hour comes back from fal as an opaque error.
                let garmentUrl: String
                if pieceSource == "upload" {
                    guard let p = importedPiecePath,
                          let s = try await Repo.shared.signedOutfitUrl(p) else { throw RepoError.notFound }
                    garmentUrl = s
                } else {
                    if let path = pickedPiece?.image_path,
                       let s = try await Repo.shared.signedClosetUrl(path) {
                        garmentUrl = s
                    } else if let u = pickedPieceUrl {
                        garmentUrl = u
                    } else {
                        throw RepoError.notFound
                    }
                }

                let cat = pieceSource == "upload" ? "auto" : (pickedPiece?.category?.lowercased() ?? "auto")
                let resp = try await Repo.shared.tryOnPiece(
                    personUrl: personSigned, garmentUrl: garmentUrl, category: cat
                )
                // invokeJSON turns HTTP errors into GenerateResponse(error:), so a
                // nil image_url is the normal failure shape here.
                guard let url = resp.image_url, !url.isEmpty else { throw RepoError.notFound }

                do {
                    try await Repo.shared.spendCredits(amount: Supa.tryonCost, kind: "tryon")
                } catch {
                    ToastBus.shared.post(Self.creditSyncCopy)
                }

                wipe = 0
                resultPieceName = nameNow
                resultPieceId = idNow
                savedOutfitId = nil
                readItems = resp.items ?? []
                plateUrl = resp.plate_url
                lastResultUrl = url
                withAnimation(.easeInOut(duration: 0.22)) { phase = .result(url) }
                saveTask = Task { await saveLook(url, token: token) }
            } catch {
                phase = .compose
                self.error = Self.tryOnFailedCopy
            }
        }
    }

    /// One upload, two uses: the signed URL is what `score-outfit` fetches
    /// server-side, and the path is what the journal row stores. Scoring is
    /// absorbed into the try-on price — no gate, no extra spend.
    private func saveLook(_ url: String, token: Int) async {
        guard token == runToken else { return }
        saveState = .saving
        verdict = nil
        verdictLoading = true
        verdictFailed = false
        do {
            let outBytes = try await Repo.shared.downloadBytes(url)
            let outPath = try await Repo.shared.uploadOutfitPhoto(bytes: outBytes, ext: "png")

            var scored: HemScored?
            if let signed = try? await Repo.shared.signedOutfitUrl(outPath) {
                let profile = try? await Repo.shared.currentProfile()
                let honesty = profile?.honesty ?? "honest"
                // A try-on has no brief behind it — the user never answered
                // anything — so it is graded on the neutral everyday rubric,
                // seeded dials and all.
                scored = try? await HemService.score(
                    imageUrl: signed, occasion: "Everyday", honesty: honesty,
                    intake: ScoreIntake(occasion: .everyday)
                )
            }
            guard token == runToken else { return }
            verdict = scored
            verdictFailed = (scored == nil)
            verdictLoading = false

            guard let uid = Repo.shared.userId else { throw RepoError.notSignedIn }
            var comment = scored?.hemComment ?? "Try-on: \(resultPieceName)"
            let row = try await Repo.shared.insertOutfit(OutfitInsert(
                user_id: uid,
                photo_path: outPath,
                score: scored?.headlineScore ?? 0,
                occasion: "Everyday",
                hem_comment: comment,
                weather_c: nil,
                verdict: scored?.verdict,
                subscores: scored?.subscores,
                swaps: scored?.swaps,
                annotations: nil,
                kind: "tryon",
                linked_piece_id: resultPieceId,
                // Without this the row's headline would later be re-averaged
                // from the legacy subscores, handing back every point the v4
                // caps took off.
                scoring_version: scored?.scoringVersion,
                rubric_id: scored?.rubric?.id,
                rubric_version: scored?.rubric?.version,
                intake: ScoreIntake(occasion: .everyday),
                axes: scored?.axes,
                score_breakdown: scored?.scoreBreakdown,
                dress_code: scored?.dressCode,
                presence_check: scored?.presenceCheck,
                lever: scored?.lever,
                caveats: scored?.caveats,
                pieces: scored?.pieces,
                signals: scored?.signals,
                raw_axes: scored?.rawAxes,
                before_photo_path: beforePath,
                fits_you: scored?.fitsYou
            ))
            guard token == runToken else { return }
            savedOutfitId = row.id
            saveState = .saved
            // The run is over — show what it produced, over the tool.
            justSavedId = row.id
        } catch {
            guard token == runToken else { return }
            verdictLoading = false
            if verdict == nil { verdictFailed = true }
            saveState = .failed
        }
    }

    private func reset() {
        Haptic.chip()
        // Invalidate any in-flight save so it cannot paint this run's verdict or
        // save state over the next one.
        runToken += 1
        saveTask?.cancel()
        saveTask = nil
        withAnimation(.easeInOut(duration: 0.22)) { phase = .compose }
        verdict = nil
        verdictFailed = false
        verdictLoading = false
        saveState = .idle
        savedOutfitId = nil
        error = nil
        wipe = 0
        // Clear whichever source is actually loaded — otherwise "try another
        // piece" returns to a ready footer holding the very same garment.
        if pieceSource == "upload" {
            importedPiecePath = nil
            importedPieceBytes = nil
            importedPieceName = nil
        } else {
            pickedPiece = nil
            pickedPieceUrl = nil
        }
    }
}

// MARK: - Piece strip card

/// The two buttons that live inside the empty person tile. Paper fill, not
/// `Palette.card` — they sit on top of a card-filled tile and would otherwise
/// disappear into it.
private struct TileActionPill: View {
    let title: String
    var body: some View {
        Text(title)
            .font(Serif.body(14))
            .foregroundStyle(Palette.ink)
            .padding(.horizontal, 22)
            .padding(.vertical, 11)
            .background(Palette.paper)
            .overlay(Capsule().stroke(Palette.hairline, lineWidth: 1))
            .clipShape(Capsule())
            .contentShape(Capsule())
    }
}

private struct PieceStripCard: View {
    let piece: ClosetItem
    let index: Int
    let selected: Bool
    var onTap: () -> Void

    @State private var url: String?
    @ScaledMetric(relativeTo: .body) private var cardH: CGFloat = 150
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var name: String {
        let n = (piece.name ?? piece.category ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        return n.isEmpty ? "Untitled piece" : n
    }

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 0) {
                RoundedRectangle(cornerRadius: 10)
                    .fill(Palette.card)
                    .frame(width: 96, height: selected ? cardH + 12 : cardH)
                    .overlay(
                        Group {
                            if let s = url, let u = URL(string: s) {
                                AsyncImage(url: u) { $0.resizable().scaledToFit() } placeholder: { Color.clear }
                            } else {
                                Color.clear
                            }
                        }
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    )
                    .burnedCaption(leading: String(format: "STUDIO %02d", index + 1), corner: 10)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(selected ? Palette.ink.opacity(0.55) : Palette.hairline, lineWidth: 1)
                    )

                Text(name)
                    .font(Serif.body(12))
                    .foregroundStyle(Palette.ink)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                    .underline(selected)
                    .frame(width: 96, alignment: .leading)
                    // Two lines reserved so baselines don't jitter as selection moves.
                    .frame(minHeight: 32, alignment: .top)
                    .padding(.top, 8)
            }
            .frame(width: 96, alignment: .top)
            .offset(y: selected ? -10 : 0)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .animation(reduceMotion ? nil : .spring(response: 0.3, dampingFraction: 0.82), value: selected)
        .wardrobeEntrance(index: index)
        .accessibilityLabel(name)
        .accessibilityAddTraits(selected ? [.isSelected] : [])
        .task {
            if url == nil, let path = piece.image_path {
                url = try? await Repo.shared.signedClosetUrl(path)
            }
        }
    }
}

// MARK: - "+ Design" tile

private struct AddDesignCard: View {
    let action: () -> Void
    @ScaledMetric(relativeTo: .body) private var cardH: CGFloat = 150

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 0) {
                DashedDropZone(corner: 10) {
                    VStack(spacing: 2) {
                        Text("+").font(Serif.display(22)).foregroundStyle(Palette.ink)
                        Text("Design").font(Serif.body(11, weight: .medium)).foregroundStyle(Palette.ink)
                    }
                }
                .frame(width: 96, height: cardH)
                Color.clear.frame(width: 96, height: 32).padding(.top, 8)
            }
            .frame(width: 96, alignment: .top)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Design a new piece in Studio")
    }
}

// MARK: - Secondary tile (result screen)

private struct SecondaryTile: View {
    let title: String
    var pro: Bool = false
    let action: () -> Void

    @ScaledMetric(relativeTo: .body) private var height: CGFloat = 56

    var body: some View {
        Button(action: action) {
            label
                .frame(maxWidth: .infinity)
                .frame(height: height)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.hairline, lineWidth: 1))
                .contentShape(RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
    }

    private var label: some View {
        let base = Text(title).font(Serif.body(13, weight: .medium)).foregroundStyle(Palette.ink)
        let sup = Text(" PRO")
            .font(.system(size: 8, weight: .semibold))
            .baselineOffset(6)
            .foregroundStyle(Palette.bronze)
        return (pro ? base + sup : base)
            .lineLimit(1)
            .minimumScaleFactor(0.75)
            .padding(.horizontal, 10)
    }
}
