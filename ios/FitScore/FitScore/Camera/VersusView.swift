import SwiftUI
import PhotosUI

/// A vs B — two drop zones + Hem's judgement. Same-photo shortcut returns a
/// tie without spending credits. Uses `VersusBus` to persist bytes across
/// camera navigation.
struct VersusView: View {
    var onClose: () -> Void
    var onOpenPaywall: () -> Void = {}

    @State private var aBytes: Data? = VersusBus.shared.aBytes
    @State private var bBytes: Data? = VersusBus.shared.bBytes
    @State private var busy = false
    @State private var error: String?
    @State private var result: CompareResponse?

    @State private var chooseSlot: String?  // "A" | "B"
    @State private var showCamera = false
    @State private var aPickerItem: PhotosPickerItem?
    @State private var bPickerItem: PhotosPickerItem?

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    if let r = result, let a = aBytes, let b = bBytes {
                        resultBlock(a: a, b: b, res: r)
                    } else {
                        inputBlock
                    }
                    Spacer(minLength: 30)
                }
                .padding(20)
            }
            if let slot = chooseSlot {
                sourcePicker(slot: slot)
            }
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraCaptureView()
                .onDisappear {
                    let (slot, bytes) = CameraBus.shared.consumeWithSlot()
                    if let bytes {
                        if slot == "A" { aBytes = bytes; VersusBus.shared.aBytes = bytes }
                        else if slot == "B" { bBytes = bytes; VersusBus.shared.bBytes = bytes }
                    }
                }
        }
        .onChange(of: aPickerItem) { _, item in
            Task {
                if let item, let d = try? await item.loadTransferable(type: Data.self) {
                    let p = CameraModel.processJpeg(d) ?? d
                    aBytes = p; VersusBus.shared.aBytes = p
                }
            }
        }
        .onChange(of: bPickerItem) { _, item in
            Task {
                if let item, let d = try? await item.loadTransferable(type: Data.self) {
                    let p = CameraModel.processJpeg(d) ?? d
                    bBytes = p; VersusBus.shared.bBytes = p
                }
            }
        }
    }

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 6) {
                Text("HEM PICKS").font(.system(size: 10, weight: .semibold)).tracking(2).foregroundStyle(Palette.bronze)
                Text("A vs B").font(Serif.display(28)).foregroundStyle(Palette.ink)
            }
            Spacer()
            Button(action: {
                VersusBus.shared.clear()
                onClose()
            }) {
                Image(systemName: "xmark").foregroundStyle(Palette.ink).frame(width: 36, height: 36)
            }
        }
    }

    private var inputBlock: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                DropZone(label: "A", bytes: aBytes, onTap: { chooseSlot = "A" })
                DropZone(label: "B", bytes: bBytes, onTap: { chooseSlot = "B" })
            }
            if let error {
                Text(error).font(Serif.body(13)).foregroundStyle(Palette.bronze)
            }
            PrimaryButton(
                title: busy ? "Judging…" : "Let Hem call it · \(Supa.versusCost) credits",
                enabled: aBytes != nil && bBytes != nil && !busy,
                action: run
            )
        }
    }

    @ViewBuilder
    private func resultBlock(a: Data, b: Data, res: CompareResponse) -> some View {
        let winner = res.winner ?? "tie"
        HStack(spacing: 12) {
            WinnerCard(bytes: a, label: "A", score: res.score_a, reason: res.reason_a, isWinner: winner == "A")
            WinnerCard(bytes: b, label: "B", score: res.score_b, reason: res.reason_b, isWinner: winner == "B")
        }
        if let comment = res.comment, !comment.isEmpty {
            Text("\u{201C}\(comment)\u{201D}")
                .font(Serif.italic(22))
                .foregroundStyle(Palette.ink)
                .padding(.top, 12)
            Text("— Hem").font(Serif.italic(15)).foregroundStyle(Palette.muted)
        }
        PrimaryButton(title: "Done", action: {
            VersusBus.shared.clear()
            onClose()
        })
        .padding(.top, 12)
    }

    private func run() {
        guard let a = aBytes, let b = bBytes, !busy else { return }
        busy = true
        error = nil
        Task {
            let gate = await CreditsGate.check(Supa.versusCost)
            if case .ok = gate {} else {
                busy = false
                if case .insufficientBalance = gate { _ = CreditsGate.explainAndBlock(gate); onOpenPaywall() }
                else { _ = CreditsGate.explainAndBlock(gate) }
                return
            }
            // Same-photo shortcut.
            if a == b {
                result = CompareResponse(
                    winner: "tie", score_a: 8.0, score_b: 8.0,
                    comment: "Same photo, twice. Call it a draw.",
                    reason_a: "Same fit as B.", reason_b: "Same fit as A."
                )
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
                let res = await HemService.compare(aUrl: aSigned, bUrl: bSigned, occasion: "everyday")
                if let err = res.error {
                    throw NSError(domain: "Versus", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hem: \(err) — \(res.detail ?? "")"])
                }
                try? await Repo.shared.spendCredits(amount: Supa.versusCost, kind: "versus")
                result = res
                busy = false
            } catch {
                self.error = error.localizedDescription
                ToastBus.shared.post("A vs B failed: \(error.localizedDescription)")
                busy = false
            }
        }
    }

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

private struct DropZone: View {
    let label: String
    let bytes: Data?
    var onTap: () -> Void
    var body: some View {
        Button(action: {
            Haptic.chip()
            onTap()
        }) {
            RoundedRectangle(cornerRadius: 18)
                .fill(Palette.card)
                .aspectRatio(1, contentMode: .fit)
                .overlay(
                    Group {
                        if let d = bytes, let img = UIImage(data: d) {
                            Image(uiImage: img).resizable().scaledToFill()
                        } else {
                            VStack(spacing: 4) {
                                Text(label).font(Serif.display(34)).foregroundStyle(Palette.ink)
                                Text("tap to add").font(Serif.body(12)).foregroundStyle(Palette.muted)
                            }
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 18))
                )
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(Palette.hairline, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

private struct WinnerCard: View {
    let bytes: Data
    let label: String
    let score: Double?
    let reason: String?
    let isWinner: Bool
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ZStack(alignment: .top) {
                RoundedRectangle(cornerRadius: 18)
                    .fill(Palette.card)
                    .aspectRatio(1, contentMode: .fit)
                    .overlay(
                        Group {
                            if let img = UIImage(data: bytes) {
                                Image(uiImage: img).resizable().scaledToFill()
                            }
                        }
                        .clipShape(RoundedRectangle(cornerRadius: 18))
                    )
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(
                        isWinner ? Palette.bronze : Palette.hairline,
                        lineWidth: isWinner ? 2 : 1
                    ))
                if isWinner {
                    Text("WINNER")
                        .font(.system(size: 10, weight: .semibold))
                        .tracking(2)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 12).padding(.vertical, 5)
                        .background(Palette.bronze)
                        .clipShape(Capsule())
                        .padding(.top, 10)
                }
            }
            HStack {
                Text(label).font(.system(size: 10, weight: .semibold)).tracking(2).foregroundStyle(Palette.muted)
                Spacer()
                Text(score.map { String(format: "%.1f", $0) } ?? "–")
                    .font(Serif.display(22, weight: .medium))
                    .foregroundStyle(Palette.ink)
            }
            if let r = reason, !r.isEmpty {
                Text(r).font(Serif.body(12)).foregroundStyle(Palette.muted)
            }
        }
    }
}
