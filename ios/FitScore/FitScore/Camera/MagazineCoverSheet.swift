import SwiftUI
import PhotosUI

private let COVER_MOODS = ["Quiet luxury", "Editorial", "Brutalist", "Romantic", "Sport", "Minimal", "Punk", "Maximalist"]

/// Sprint 3 (v2) — magazine-cover composer with two-photo flow.
///   • YOU tile: your outfit / any subject photo
///   • REFERENCE tile (optional): a magazine cover whose style Hem should mimic
///   • MOOD chip: bias the treatment
///   • MASTHEAD: override the default "FITRATER" name at the top
///
/// The edge function (`compose-cover` v3) runs a 3-pass pipeline:
///   1) analyzes your photo for face + safe crop
///   2) analyzes the reference cover for style DNA
///   3) renders the photo via nano-banana, then overlays real typography on top
struct MagazineCoverSheet: View {
    /// Bound outfit id (opens with that outfit's photo already loaded).
    var outfitId: String?
    var onClose: () -> Void

    @EnvironmentObject var session: SessionStore
    @EnvironmentObject var toasts: ToastBus

    // Subject
    @State private var subjectImage: UIImage?
    @State private var subjectBytes: Data?
    @State private var subjectUrl: String?

    // Reference
    @State private var referenceImage: UIImage?
    @State private var referenceBytes: Data?
    @State private var referenceUrl: String?

    // Options
    @State private var mood: String = ""
    @State private var masthead: String = "FITRATER"
    @State private var showMastheadEditor = false

    @State private var busy = false
    @State private var error: String?
    @State private var cover: ComposeCoverResponse?

    // Likeness consent — asked once, then remembered. Gate sits in front of
    // every compose, including the one launched from the Edit sheet.
    @AppStorage("cover_likeness_ack_v1") private var likenessAcknowledged = false
    @State private var showConsent = false
    @State private var consentConfirmed = false
    @State private var pendingCost = 0

    // Camera / picker
    @State private var showSubjectCamera = false
    @State private var subjectPicker: PhotosPickerItem?
    @State private var referencePicker: PhotosPickerItem?
    @State private var showMyCovers = false
    @State private var reportTarget: ReportTarget? = nil
    @State private var savedTemplates: [MagazineCover] = []

    // Edit-cover fields
    @State private var showEdit = false
    @State private var userPrompt: String = ""
    @State private var customMasthead: String = ""
    @State private var customHeadline: String = ""
    @State private var customPullQuote: String = ""
    @State private var editCoverLines: [String] = []
    @FocusState private var promptFocused: Bool

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    header
                    if let cover, let url = cover.cover_url {
                        finishedBlock(coverUrl: url, cover: cover)
                    } else {
                        composerBlock
                    }
                    Spacer(minLength: 30)
                }
                .padding(20)
                .contentShape(Rectangle())
                .onTapGesture { dismissKeyboard() }
            }
            .scrollDismissesKeyboard(.interactively)
            if busy {
                WaitingOverlay(
                    eyebrow: "COMPOSING",
                    title: "Printing your cover",
                    tips: [
                        "Reading your photo for face and crop…",
                        "Studying the reference style…",
                        "Rendering the base image…",
                        "Setting the type on top…",
                        "Usually 30-60 seconds.",
                    ]
                )
            }
        }
        .task {
            await loadIfBound()
            savedTemplates = (try? await Repo.shared.loadCoverTemplates()) ?? []
        }
        .fullScreenCover(isPresented: $showSubjectCamera) {
            CameraCaptureView().onDisappear {
                if let d = CameraBus.shared.consume() { setSubject(d) }
            }
        }
        .onChange(of: subjectPicker) { _, item in
            Task {
                if let item, let d = try? await item.loadTransferable(type: Data.self) {
                    setSubject(CameraModel.processJpeg(d) ?? d)
                }
            }
        }
        .onChange(of: referencePicker) { _, item in
            Task {
                if let item, let d = try? await item.loadTransferable(type: Data.self) {
                    setReference(CameraModel.processJpeg(d) ?? d)
                }
            }
        }
        .sheet(item: $reportTarget) { t in
            ReportContentSheet(target: t) { reportTarget = nil }
        }
        .sheet(isPresented: $showMastheadEditor) {
            MastheadEditorSheet(masthead: $masthead) { showMastheadEditor = false }
                .presentationDetents([.height(280)])
        }
        .fullScreenCover(isPresented: $showEdit) {
            let cost = referenceImage == nil ? Supa.magazineCoverCost : Supa.magazineCoverCost + 3
            EditCoverSheet(
                userPrompt: $userPrompt,
                customMasthead: $customMasthead,
                customHeadline: $customHeadline,
                customPullQuote: $customPullQuote,
                coverLines: $editCoverLines,
                referenceMode: referenceImage != nil,
                composeCostCredits: cost,
                onCompose: {
                    showEdit = false
                    compose(cost: cost)
                },
                onClose: { showEdit = false }
            )
        }
        .sheet(isPresented: $showConsent, onDismiss: {
            guard consentConfirmed else { return }
            consentConfirmed = false
            compose(cost: pendingCost)
        }) {
            LikenessConsentSheet(
                onConfirm: {
                    likenessAcknowledged = true
                    consentConfirmed = true
                    showConsent = false
                },
                onCancel: { showConsent = false }
            )
            .presentationDetents([.height(440)])
        }
    }

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 6) {
                Eyebrow(text: "EDITORIAL")
                Text("Compose a cover.")
                    .font(Serif.display(30))
                    .foregroundStyle(Palette.ink)
            }
            Spacer()
            Button(action: onClose) {
                Image(systemName: "xmark").foregroundStyle(Palette.ink).frame(width: 36, height: 36)
            }
        }
    }

    // MARK: - Composer

    private var composerBlock: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Two photos + a prompt. Tell Hem what to change on the cover.")
                .font(Serif.italic(15))
                .foregroundStyle(Palette.muted)

            HStack(alignment: .top, spacing: 12) {
                subjectTile.frame(maxWidth: .infinity)
                referenceTile.frame(maxWidth: .infinity)
            }

            // Prompt field — always visible so user knows what shapes the output.
            VStack(alignment: .leading, spacing: 8) {
                Eyebrow(text: "YOUR PROMPT")
                TextField(
                    "What should Hem do? e.g. 'put me in the corset dress and keep the pose'",
                    text: $userPrompt,
                    axis: .vertical
                )
                .font(Serif.body(15))
                .lineLimit(3, reservesSpace: true)
                .autocorrectionDisabled()
                .focused($promptFocused)
                .toolbar {
                    ToolbarItemGroup(placement: .keyboard) {
                        Spacer()
                        Button("Done") { promptFocused = false }
                            .foregroundStyle(Palette.bronze)
                            .fontWeight(.semibold)
                    }
                }
                .padding(12)
                .background(Palette.card)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.hairline, lineWidth: 1))
            }

            // Mood picker
            VStack(alignment: .leading, spacing: 8) {
                Eyebrow(text: "MOOD (OPT)")
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(COVER_MOODS, id: \.self) { m in
                            let on = mood == m
                            Button(action: {
                                Haptic.chip()
                                mood = on ? "" : m
                            }) {
                                Text(m)
                                    .font(Serif.body(13))
                                    .foregroundStyle(on ? .white : Palette.ink)
                                    .padding(.horizontal, 12).padding(.vertical, 8)
                                    .background(on ? Palette.ink : Color.clear)
                                    .overlay(Capsule().stroke(on ? Palette.ink : Palette.hairline, lineWidth: 1))
                                    .clipShape(Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }

            // Masthead
            Button(action: { showMastheadEditor = true }) {
                HStack(spacing: 8) {
                    Image(systemName: "textformat.abc")
                        .foregroundStyle(Palette.bronze)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("MASTHEAD")
                            .font(.system(size: 10, weight: .semibold)).tracking(1.5)
                            .foregroundStyle(Palette.bronze)
                        Text(masthead)
                            .font(Serif.display(16))
                            .foregroundStyle(Palette.ink)
                    }
                    Spacer()
                    Image(systemName: "pencil").font(.system(size: 12)).foregroundStyle(Palette.muted)
                }
                .padding(12)
                .background(Palette.card)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.hairline, lineWidth: 1))
            }
            .buttonStyle(.plain)

            if let error {
                Text(error).font(Serif.body(13)).foregroundStyle(Palette.bronze)
            }

            let cost = referenceImage == nil ? Supa.magazineCoverCost : Supa.magazineCoverCost + 3
            HStack(spacing: 8) {
                Button(action: { Haptic.tap(); showEdit = true }) {
                    Text("Edit")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Palette.ink)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .overlay(Capsule().stroke(Palette.ink.opacity(0.5), lineWidth: 1))
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
                .disabled(subjectImage == nil || busy)
                .opacity(subjectImage == nil ? 0.4 : 1)
                PrimaryButton(
                    title: busy ? "Composing…" : "Compose · \(cost)",
                    enabled: subjectImage != nil && !busy,
                    action: { compose(cost: cost) }
                )
            }
        }
    }

    // MARK: - Subject (YOU) tile

    private var subjectTile: some View {
        VStack(alignment: .leading, spacing: 6) {
            tileBox(image: subjectImage, placeholder: "Your photo", aspect: 3.0/4.0)
            HStack(spacing: 6) {
                Text("YOU")
                    .font(.system(size: 9, weight: .semibold)).tracking(1.5)
                    .foregroundStyle(Palette.bronze)
                Spacer()
                if subjectImage != nil {
                    Button(action: {
                        subjectImage = nil; subjectBytes = nil; subjectUrl = nil
                    }) {
                        Image(systemName: "xmark").font(.system(size: 10)).foregroundStyle(Palette.muted)
                    }.buttonStyle(.plain)
                }
            }
            HStack(spacing: 6) {
                Button(action: { Haptic.tap(); showSubjectCamera = true }) {
                    sourceChip(icon: "camera.fill", label: "Camera")
                }
                .buttonStyle(.plain)
                PhotosPicker(selection: $subjectPicker, matching: .images) {
                    sourceChip(icon: "photo.on.rectangle", label: "Gallery")
                }
            }
            Text("You, or someone who said yes. No impersonating anyone.")
                .font(Serif.italic(10))
                .foregroundStyle(Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    // MARK: - Reference tile with Studio picker

    private var referenceTile: some View {
        VStack(alignment: .leading, spacing: 6) {
            tileBox(image: referenceImage, placeholder: "A cover style to borrow", aspect: 3.0/4.0)
            HStack(spacing: 6) {
                Text("REFERENCE (OPT)")
                    .font(.system(size: 9, weight: .semibold)).tracking(1.5)
                    .foregroundStyle(Palette.bronze)
                Spacer()
                if referenceImage != nil {
                    Button(action: {
                        referenceImage = nil; referenceBytes = nil; referenceUrl = nil
                    }) {
                        Image(systemName: "xmark").font(.system(size: 10)).foregroundStyle(Palette.muted)
                    }.buttonStyle(.plain)
                }
            }
            HStack(spacing: 6) {
                PhotosPicker(selection: $referencePicker, matching: .images) {
                    sourceChip(icon: "photo.on.rectangle", label: "Gallery")
                }
                Button(action: {
                    Haptic.tap()
                    showMyCovers = true
                }) {
                    sourceChip(
                        icon: "square.grid.2x2.fill",
                        label: savedTemplates.isEmpty ? "Studio" : "Studio (\(savedTemplates.count))"
                    )
                }
                .buttonStyle(.plain)
                .disabled(savedTemplates.isEmpty)
                .opacity(savedTemplates.isEmpty ? 0.4 : 1)
            }
        }
        .sheet(isPresented: $showMyCovers) {
            MyCoversPicker(
                templates: savedTemplates,
                onPick: { cover in
                    if let path = cover.image_path {
                        let publicUrl = "https://ilrzqifdmjvooeyqvexd.supabase.co/storage/v1/object/public/magazine_covers/\(path)"
                        referenceUrl = publicUrl
                        Task {
                            if let bytes = try? await Repo.shared.downloadBytes(publicUrl) {
                                referenceBytes = bytes
                                referenceImage = UIImage(data: bytes)
                            }
                        }
                    }
                    showMyCovers = false
                },
                onClose: { showMyCovers = false }
            )
            .presentationDetents([.large])
        }
    }

    private func sourceChip(icon: String, label: String) -> some View {
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

    // MARK: - Photo tile

    @ViewBuilder
    private func photoTile<Picker: View>(
        label: String,
        image: UIImage?,
        placeholderTitle: String,
        aspect: CGFloat,
        onTap: (() -> Void)?,
        onPick: Picker,
        onClear: (() -> Void)?
    ) -> some View {
        VStack(spacing: 6) {
            Group {
                if let onTap {
                    Button(action: onTap) { tileBox(image: image, placeholder: placeholderTitle, aspect: aspect) }
                        .buttonStyle(.plain)
                } else {
                    tileBox(image: image, placeholder: placeholderTitle, aspect: aspect)
                }
            }
            HStack(spacing: 6) {
                Text(label)
                    .font(.system(size: 9, weight: .semibold)).tracking(1.5)
                    .foregroundStyle(Palette.bronze)
                Spacer()
                if let onClear {
                    Button(action: onClear) {
                        Image(systemName: "xmark").font(.system(size: 10)).foregroundStyle(Palette.muted)
                    }.buttonStyle(.plain)
                }
                onPick
            }
        }
    }

    @ViewBuilder
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
                            Text("+")
                                .font(Serif.display(28))
                                .foregroundStyle(Palette.ink)
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
                    .strokeBorder(
                        style: StrokeStyle(lineWidth: 1, dash: image == nil ? [4, 4] : [])
                    )
                    .foregroundStyle(Palette.hairline)
            )
    }

    // MARK: - Result

    @ViewBuilder
    private func finishedBlock(coverUrl: String, cover: ComposeCoverResponse) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            AsyncImage(url: URL(string: coverUrl)) { img in
                img.resizable().scaledToFit()
            } placeholder: {
                RoundedRectangle(cornerRadius: 16).fill(Palette.card).aspectRatio(9.0/16.0, contentMode: .fit)
            }
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .shadow(color: .black.opacity(0.15), radius: 12, y: 6)

            if let h = cover.headline {
                Text(h)
                    .font(Serif.display(20))
                    .foregroundStyle(Palette.ink)
            }
            if let q = cover.pull_quote {
                Text("“\(q)”")
                    .font(Serif.italic(14))
                    .foregroundStyle(Palette.muted)
            }

            HStack(spacing: 10) {
                Button(action: { share(url: coverUrl) }) { OutlinePillButton(title: "Share") }
                Button(action: regenHeadline) { OutlinePillButton(title: "New headline") }
            }
            Button(action: reset) { OutlinePillButton(title: "Try another") }
            Button(action: onClose) { OutlinePillButton(title: "Done") }
            // The cover is AI-generated — reporting lives on the artifact itself.
            Button(action: {
                Haptic.tap()
                reportTarget = ReportTarget(ReportKind.cover, cover.cover_id ?? outfitId ?? coverUrl)
            }) {
                HStack(spacing: 6) {
                    Image(systemName: "flag").font(.system(size: 11))
                    Text("Report this cover").font(Serif.body(13))
                }
                .foregroundStyle(Palette.muted)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(.top, 2)
        }
    }

    // MARK: - Actions

    private func setSubject(_ d: Data) {
        subjectBytes = d; subjectImage = UIImage(data: d); subjectUrl = nil
    }
    private func setReference(_ d: Data) {
        referenceBytes = d; referenceImage = UIImage(data: d); referenceUrl = nil
    }

    private func loadIfBound() async {
        guard let outfitId, subjectImage == nil else { return }
        if let outfit = try? await Repo.shared.outfitById(outfitId),
           let p = outfit.photo_path,
           let signed = try? await Repo.shared.signedOutfitUrl(p) {
            subjectUrl = signed
            if let bytes = try? await Repo.shared.downloadBytes(signed) {
                setSubject(bytes)
                subjectUrl = signed   // preserve signed url
            }
        }
    }

    private func compose(cost: Int) {
        guard subjectImage != nil else { return }
        guard likenessAcknowledged else {
            pendingCost = cost
            // Compose can be fired straight from the Edit cover, so give a
            // dismissing presentation a beat before we put the gate up —
            // back-to-back presentations get dropped.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { showConsent = true }
            return
        }
        busy = true
        error = nil
        Task {
            defer { busy = false }
            let gate = await CreditsGate.check(cost)
            if case .ok = gate {} else {
                _ = CreditsGate.explainAndBlock(gate)
                return
            }
            do {
                // 1) subject signed url
                var subjectSigned = subjectUrl
                if subjectSigned == nil, let bytes = subjectBytes {
                    let path = try await Repo.shared.uploadOutfitPhoto(bytes: bytes)
                    subjectSigned = try await Repo.shared.signedOutfitUrl(path)
                }
                guard let subjectSigned else {
                    throw NSError(domain: "Cover", code: 1, userInfo: [NSLocalizedDescriptionKey: "Could not sign subject photo."])
                }
                // 2) reference signed url (optional)
                var referenceSigned = referenceUrl
                if referenceSigned == nil, let bytes = referenceBytes {
                    let path = try await Repo.shared.uploadOutfitPhoto(bytes: bytes)
                    referenceSigned = try await Repo.shared.signedOutfitUrl(path)
                }
                // 3) compose
                let resp = try await Repo.shared.composeCover(
                    sourceImageUrl: subjectSigned,
                    outfitId: outfitId,
                    userName: session.displayName,
                    seedHeadline: nil,
                    referenceCoverUrl: referenceSigned,
                    masthead: masthead.isEmpty ? nil : masthead,
                    mood: mood.isEmpty ? nil : mood,
                    customHeadline: customHeadline.isEmpty ? nil : customHeadline,
                    customPullQuote: customPullQuote.isEmpty ? nil : customPullQuote,
                    coverLines: editCoverLines.isEmpty ? nil : editCoverLines,
                    userPrompt: userPrompt.isEmpty ? nil : userPrompt,
                    customMasthead: customMasthead.isEmpty ? nil : customMasthead
                )
                if let err = resp.error {
                    let msg: String = {
                        if err == "content_flagged" {
                            return "The photo was flagged by our safety filter. Try a photo with a shirt on."
                        }
                        if err == "swap_failed" {
                            return "Couldn't compose the swap — try a clearer subject photo or a different reference."
                        }
                        if err.contains("text_rejected") {
                            return "Cover text can't include profanity or slurs. Edit the wording and try again."
                        }
                        return err
                    }()
                    throw NSError(domain: "Cover", code: 2, userInfo: [NSLocalizedDescriptionKey: msg])
                }
                try? await Repo.shared.spendCredits(amount: cost, kind: "magazine_cover")
                Haptic.soft()
                cover = resp
            } catch {
                self.error = error.localizedDescription
                toasts.post("Cover failed: \(error.localizedDescription)")
            }
        }
    }

    private func regenHeadline() {
        guard let cover, let cid = cover.cover_id else { return }
        Task {
            let resp = try? await Repo.shared.regenerateCoverHeadline(coverId: cid)
            if let h = resp?.headline {
                self.cover = ComposeCoverResponse(
                    cover_url: cover.cover_url,
                    headline: h,
                    pull_quote: resp?.pull_quote ?? cover.pull_quote,
                    vol_number: cover.vol_number,
                    cover_id: cover.cover_id,
                    error: nil
                )
                Haptic.chip()
            }
        }
    }

    private func reset() {
        cover = nil
        subjectImage = nil; subjectBytes = nil; subjectUrl = nil
        referenceImage = nil; referenceBytes = nil; referenceUrl = nil
    }

    private func share(url: String) {
        guard let u = URL(string: url) else { return }
        Task {
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

// MARK: - My covers picker

private struct MyCoversPicker: View {
    let templates: [MagazineCover]
    var onPick: (MagazineCover) -> Void
    var onClose: () -> Void

    private let cols = [GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: cols, spacing: 12) {
                    ForEach(templates) { cover in
                        Button(action: { onPick(cover) }) {
                            VStack(alignment: .leading, spacing: 4) {
                                if let path = cover.image_path {
                                    let url = "https://ilrzqifdmjvooeyqvexd.supabase.co/storage/v1/object/public/magazine_covers/\(path)"
                                    AsyncImage(url: URL(string: url)) { img in
                                        img.resizable().scaledToFill()
                                    } placeholder: {
                                        RoundedRectangle(cornerRadius: 12).fill(Palette.card)
                                    }
                                    .aspectRatio(9.0/16.0, contentMode: .fit)
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.hairline, lineWidth: 1))
                                }
                                if let h = cover.headline {
                                    Text(h)
                                        .font(Serif.body(12, weight: .semibold))
                                        .foregroundStyle(Palette.ink)
                                        .lineLimit(1)
                                }
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(16)
            }
            .background(Palette.paper.ignoresSafeArea())
            .navigationTitle("My covers")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done", action: onClose)
                        .foregroundStyle(Palette.bronze)
                }
            }
        }
    }
}

// MARK: - Likeness consent

/// One-time gate in front of the first cover compose. The composer transplants
/// a face onto a cover, so the rule that only your own likeness (or a likeness
/// you have permission to use) may go on it has to live here, at the upload,
/// not buried in the terms on the website. Acknowledgement is remembered.
private struct LikenessConsentSheet: View {
    var onConfirm: () -> Void
    var onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Eyebrow(text: "BEFORE WE PRINT")
                Spacer()
                Button(action: onCancel) { Image(systemName: "xmark").foregroundStyle(Palette.ink) }
            }
            Text("Whose face is this?")
                .font(Serif.display(24))
                .foregroundStyle(Palette.ink)
            Text("The composer puts the person in your photo on the cover. Two rules before you do that.")
                .font(Serif.italic(14))
                .foregroundStyle(Palette.muted)

            VStack(alignment: .leading, spacing: 10) {
                rule("the person in the photo is you, or has agreed to being on it")
                rule("you won't use the cover to impersonate anyone, or to suggest they endorsed something")
            }
            .padding(.top, 2)

            Text("No public figures, no photos of people who didn't say yes. Covers that break this get removed.")
                .font(Serif.italic(12))
                .foregroundStyle(Palette.muted)

            Spacer()

            HStack(spacing: 8) {
                Button(action: onCancel) { OutlinePillButton(title: "Not now") }
                PrimaryButton(title: "I confirm", enabled: true, action: onConfirm)
            }
        }
        .padding(20)
        .background(Palette.paper.ignoresSafeArea())
    }

    private func rule(_ text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Circle()
                .fill(Palette.bronze)
                .frame(width: 5, height: 5)
                .padding(.top, 7)
            Text(text)
                .font(Serif.body(14))
                .foregroundStyle(Palette.ink)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
    }
}

// MARK: - Masthead editor

private struct MastheadEditorSheet: View {
    @Binding var masthead: String
    var onClose: () -> Void
    @State private var text: String = ""
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Eyebrow(text: "MASTHEAD")
                Spacer()
                Button(action: onClose) { Image(systemName: "xmark").foregroundStyle(Palette.ink) }
            }
            Text("What's on the top of your cover?")
                .font(Serif.display(20))
                .foregroundStyle(Palette.ink)
            Text("Default is FITRATER. Keep it short — 3-10 chars.")
                .font(Serif.italic(13))
                .foregroundStyle(Palette.muted)

            TextField("FITRATER", text: $text)
                .font(Serif.display(20))
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .focused($focused)
                .padding(12)
                .background(Palette.card)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.hairline, lineWidth: 1))

            Spacer()

            HStack(spacing: 8) {
                Button(action: { masthead = "FITRATER"; onClose() }) {
                    OutlinePillButton(title: "Reset")
                }
                PrimaryButton(title: "Use this", enabled: !text.trimmingCharacters(in: .whitespaces).isEmpty) {
                    let cleaned = String(text.uppercased().trimmingCharacters(in: .whitespaces).prefix(20))
                    masthead = cleaned
                    onClose()
                }
            }
        }
        .padding(20)
        .background(Palette.paper.ignoresSafeArea())
        .onAppear {
            text = masthead
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { focused = true }
        }
    }
}
