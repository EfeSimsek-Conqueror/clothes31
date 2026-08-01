import SwiftUI
import PhotosUI

/// Sprint 5 — Invitation Decoder. Snap an invitation → OCR + dress-code parse
/// → 3 combos from Studio closet with match scores + rationale + gaps.
/// Tapping a combo routes to Try-on with the piece pre-selected.
struct InvitationDecoderView: View {
    var onClose: () -> Void
    var onOpenPaywall: () -> Void = {}

    @EnvironmentObject var session: SessionStore
    @EnvironmentObject var toasts: ToastBus

    @State private var capturedBytes: Data?
    @State private var capturedImage: UIImage?
    @State private var busy = false
    @State private var stage: Stage = .capture
    @State private var error: String?

    @State private var decoded: InvitationDecoded?
    @State private var combos: [OutfitCombo] = []
    @State private var closet: [ClosetItem] = []

    @State private var showCamera = false
    @State private var pickerItem: PhotosPickerItem?

    private enum Stage { case capture, decoded, suggested }

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    header
                    switch stage {
                    case .capture:   captureBlock
                    case .decoded:   decodedBlock
                    case .suggested: suggestedBlock
                    }
                    Spacer(minLength: 30)
                }
                .padding(20)
            }
            if busy {
                WaitingOverlay(
                    eyebrow: stage == .capture ? "READING" : "MATCHING",
                    title: stage == .capture ? "Decoding the invite" : "Pulling from your closet",
                    tips: [
                        "Reading dress code…",
                        "Weighing your palette…",
                        "Ranking combinations…",
                        "Flagging any closet gaps.",
                    ]
                )
            }
        }
        .task { closet = (try? await Repo.shared.closetItems()) ?? [] }
        .fullScreenCover(isPresented: $showCamera) {
            CameraCaptureView().onDisappear {
                if let d = CameraBus.shared.consume() {
                    capturedBytes = d
                    capturedImage = UIImage(data: d)
                }
            }
        }
        .onChange(of: pickerItem) { _, item in
            Task {
                if let item, let d = try? await item.loadTransferable(type: Data.self) {
                    let p = CameraModel.processJpeg(d) ?? d
                    capturedBytes = p
                    capturedImage = UIImage(data: p)
                }
            }
        }
    }

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 6) {
                Eyebrow(text: "OCCASION")
                Text("Decode the invite.")
                    .font(Serif.display(30))
                    .foregroundStyle(Palette.ink)
            }
            Spacer()
            Button(action: onClose) {
                Image(systemName: "xmark").foregroundStyle(Palette.ink).frame(width: 36, height: 36)
            }
        }
    }

    // MARK: - Stage 1 · capture

    private var captureBlock: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Snap the invitation, sign, or venue photo. Hem parses the dress code and pulls combinations from your Studio closet.")
                .font(Serif.italic(15))
                .foregroundStyle(Palette.muted)

            RoundedRectangle(cornerRadius: 20)
                .fill(Palette.card)
                .aspectRatio(3.0/4.0, contentMode: .fit)
                .overlay(
                    Group {
                        if let img = capturedImage {
                            Image(uiImage: img).resizable().scaledToFill()
                        } else {
                            Text("+ Add invitation photo")
                                .font(Serif.body(15, weight: .semibold))
                                .foregroundStyle(Palette.ink)
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 20))
                )
                .overlay(RoundedRectangle(cornerRadius: 20).stroke(Palette.hairline, lineWidth: 1))

            HStack(spacing: 10) {
                Button(action: { Haptic.tap(); showCamera = true }) { OutlinePillButton(title: "Take photo") }
                PhotosPicker(selection: $pickerItem, matching: .images) {
                    OutlinePillButton(title: "From gallery")
                }
            }
            if let error {
                Text(error).font(Serif.body(13)).foregroundStyle(Palette.bronze)
            }
            PrimaryButton(
                title: busy ? "Decoding…" : "Decode · \(Supa.invitationDecodeCost) credits",
                enabled: capturedBytes != nil && !busy,
                action: decode
            )
        }
    }

    // MARK: - Stage 2 · decoded

    private var decodedBlock: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let img = capturedImage {
                RoundedRectangle(cornerRadius: 14).fill(Palette.card)
                    .aspectRatio(3.0/4.0, contentMode: .fit)
                    .overlay(Image(uiImage: img).resizable().scaledToFill().clipShape(RoundedRectangle(cornerRadius: 14)))
                    .frame(maxWidth: 200)
            }
            Eyebrow(text: "DECODED")
            if let d = decoded {
                VStack(alignment: .leading, spacing: 6) {
                    if let e = d.event_type { row("EVENT", pretty(e)) }
                    if let c = d.dress_code { row("DRESS", pretty(c), highlight: true) }
                    if let v = d.venue { row("VENUE", v) }
                    if let t = d.date_text { row("WHEN", t) }
                    if let n = d.notes {
                        Text(n)
                            .font(Serif.italic(14))
                            .foregroundStyle(Palette.ink.opacity(0.8))
                            .padding(.top, 6)
                    }
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Palette.card)
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.hairline, lineWidth: 1))
            }
            if let error {
                Text(error).font(Serif.body(13)).foregroundStyle(Palette.bronze)
            }
            PrimaryButton(
                title: busy ? "Building combos…" : "See combinations",
                enabled: !busy && decoded != nil && !closet.isEmpty,
                action: suggest
            )
            if closet.isEmpty {
                Text("Your Studio closet is empty — add a few pieces first so Hem has something to work with.")
                    .font(Serif.italic(13))
                    .foregroundStyle(Palette.muted)
            }
            Button(action: reset) {
                Text("Scan a different invite")
                    .font(Serif.body(14))
                    .foregroundStyle(Palette.muted)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private func row(_ label: String, _ value: String, highlight: Bool = false) -> some View {
        HStack(spacing: 8) {
            Text(label)
                .font(.system(size: 10, weight: .semibold)).tracking(1.5)
                .foregroundStyle(Palette.bronze)
                .frame(width: 54, alignment: .leading)
            Text(value)
                .font(Serif.body(14, weight: highlight ? .semibold : .regular))
                .foregroundStyle(highlight ? Palette.bronze : Palette.ink)
        }
    }

    // MARK: - Stage 3 · suggested

    private var suggestedBlock: some View {
        VStack(alignment: .leading, spacing: 14) {
            Eyebrow(text: "YOUR PLAYS")
            if combos.isEmpty {
                Text("No combinations came back — try scanning again or expand your closet.")
                    .font(Serif.italic(14))
                    .foregroundStyle(Palette.muted)
            }
            ForEach(Array(combos.enumerated()), id: \.offset) { _, combo in
                comboCard(combo: combo)
            }
            Button(action: reset) {
                Text("Scan another")
                    .font(Serif.body(14))
                    .foregroundStyle(Palette.muted)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 8)
            }
            Button(action: onClose) {
                OutlinePillButton(title: "Done")
            }
        }
    }

    private func comboCard(combo: OutfitCombo) -> some View {
        let pieces: [ClosetItem] = (combo.piece_ids ?? []).compactMap { pid in
            closet.first(where: { $0.id == pid })
        }
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("MATCH \(combo.score ?? 0)%")
                    .font(.system(size: 10, weight: .semibold)).tracking(1.5)
                    .foregroundStyle(Palette.bronze)
                Spacer()
                if let gap = combo.gap, !gap.isEmpty {
                    Text("GAP")
                        .font(.system(size: 10, weight: .semibold)).tracking(1.5)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(Palette.bronze)
                        .clipShape(Capsule())
                }
            }

            if !pieces.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(pieces, id: \.id) { p in
                            ComboPieceThumb(piece: p)
                        }
                    }
                }
            }

            if let r = combo.rationale {
                Text(r)
                    .font(Serif.body(14))
                    .foregroundStyle(Palette.ink)
            }
            if let gap = combo.gap, !gap.isEmpty {
                Text("Missing: \(gap)")
                    .font(Serif.italic(13))
                    .foregroundStyle(Palette.bronze)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.hairline, lineWidth: 1))
    }

    // MARK: - Actions

    private func decode() {
        guard let bytes = capturedBytes else { return }
        busy = true
        error = nil
        Task {
            defer { busy = false }
            let gate = await CreditsGate.check(Supa.invitationDecodeCost)
            switch gate {
            case .ok: break
            default:
                if case .insufficientBalance = gate { _ = CreditsGate.explainAndBlock(gate); onOpenPaywall() }
                else { _ = CreditsGate.explainAndBlock(gate) }
                return
            }
            do {
                let path = try await Repo.shared.uploadOutfitPhoto(bytes: bytes)
                guard let signed = try await Repo.shared.signedOutfitUrl(path) else {
                    throw NSError(domain: "Invite", code: 1, userInfo: [NSLocalizedDescriptionKey: "Could not sign photo URL"])
                }
                let resp = try await Repo.shared.decodeInvitation(imageUrl: signed)
                if let err = resp.error {
                    throw NSError(domain: "Invite", code: 2, userInfo: [NSLocalizedDescriptionKey: err])
                }
                try? await Repo.shared.spendCredits(amount: Supa.invitationDecodeCost, kind: "invitation_decode")
                decoded = resp.decoded
                stage = .decoded
                Haptic.soft()
            } catch {
                self.error = error.localizedDescription
                toasts.post("Decode failed: \(error.localizedDescription)")
            }
        }
    }

    private func suggest() {
        guard let d = decoded else { return }
        busy = true
        error = nil
        Task {
            defer { busy = false }
            do {
                let resp = try await Repo.shared.suggestOutfits(
                    dressCode: d.dress_code ?? "smart_casual",
                    eventType: d.event_type ?? "other",
                    notes: d.notes,
                    closet: closet,
                    bodyProfile: session.bodyProfile,
                    styleTags: session.profile?.style_tags
                )
                if let err = resp.error {
                    throw NSError(domain: "Invite", code: 3, userInfo: [NSLocalizedDescriptionKey: err])
                }
                combos = resp.combos ?? []
                // Persist as an invitation_read so it shows up later in a history view.
                try? await Repo.shared.saveInvitationRead(imagePath: nil, decoded: d, combos: combos)
                stage = .suggested
                Haptic.soft()
            } catch {
                self.error = error.localizedDescription
                toasts.post("Suggest failed: \(error.localizedDescription)")
            }
        }
    }

    private func reset() {
        capturedBytes = nil
        capturedImage = nil
        decoded = nil
        combos = []
        stage = .capture
        error = nil
    }

    private func pretty(_ raw: String) -> String {
        raw.replacingOccurrences(of: "_", with: " ").capitalized
    }
}

// MARK: - Piece thumbnail

private struct ComboPieceThumb: View {
    let piece: ClosetItem
    @State private var url: String?

    var body: some View {
        VStack(spacing: 4) {
            RoundedRectangle(cornerRadius: 10)
                .fill(Palette.paper)
                .frame(width: 84, height: 100)
                .overlay(
                    Group {
                        if let s = url, let u = URL(string: s) {
                            AsyncImage(url: u) { $0.resizable().scaledToFill() } placeholder: { Color.clear }
                        } else {
                            Text("…").font(Serif.body(12)).foregroundStyle(Palette.muted)
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                )
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.hairline, lineWidth: 1))
            Text((piece.name ?? piece.category ?? "").prefix(14).description)
                .font(Serif.body(11))
                .foregroundStyle(Palette.muted)
                .lineLimit(1)
        }
        .task {
            if let p = piece.image_path {
                url = try? await Repo.shared.signedClosetUrl(p)
            } else {
                url = piece.image_url
            }
        }
    }
}
