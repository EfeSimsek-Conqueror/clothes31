import SwiftUI
import PhotosUI

private let ROAST_OCCASIONS = ["Work", "Date", "Wedding", "Casual", "Everyday"]

/// Brutal-mode scoring flow with share-card output. Free tier limited to 1
/// roast/day (checked via `Repo.roastsToday()`).
struct RoastView: View {
    var onClose: () -> Void
    var onOpenPaywall: () -> Void = {}

    @EnvironmentObject var session: SessionStore

    @State private var capturedBytes: Data?
    @State private var capturedImage: UIImage?
    @State private var pickedOccasion = "Everyday"
    @State private var busy = false
    @State private var error: String?
    @State private var roastComment: String?

    @State private var showCamera = false
    @State private var pickerItem: PhotosPickerItem?

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    if let roast = roastComment, let img = capturedImage {
                        resultBlock(image: img, roast: roast)
                    } else {
                        inputBlock
                    }
                    Spacer(minLength: 30)
                }
                .padding(20)
            }
        }
        .task {
            if let d = CameraBus.shared.consume() {
                capturedBytes = d
                capturedImage = UIImage(data: d)
            }
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraCaptureView()
                .onDisappear {
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
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 6) {
                Text("BRUTAL MODE").font(.system(size: 10, weight: .semibold)).tracking(2).foregroundStyle(Palette.bronze)
                Text("Roast this").font(Serif.display(28)).foregroundStyle(Palette.ink)
            }
            Spacer()
            Button(action: onClose) {
                Image(systemName: "xmark").foregroundStyle(Palette.ink).frame(width: 36, height: 36)
            }
        }
    }

    private var inputBlock: some View {
        VStack(alignment: .leading, spacing: 14) {
            photoTile
            HStack(spacing: 10) {
                Button(action: { Haptic.tap(); showCamera = true }) { OutlinePillButton(title: "Take photo") }
                PhotosPicker(selection: $pickerItem, matching: .images) {
                    OutlinePillButton(title: "From gallery")
                }
            }
            Text("Occasion").font(Serif.body(15, weight: .semibold)).foregroundStyle(Palette.ink)
            ChipRow(options: ROAST_OCCASIONS, selection: $pickedOccasion)
            if let error {
                Text(error).font(Serif.body(13)).foregroundStyle(Palette.bronze)
            }
            PrimaryButton(
                title: busy ? "Roasting…" : "Roast it · \(Supa.roastCost) credits",
                enabled: capturedBytes != nil && !busy,
                action: run
            )
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

    @ViewBuilder
    private func resultBlock(image: UIImage, roast: String) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            RoundedRectangle(cornerRadius: 20)
                .fill(Palette.card)
                .aspectRatio(0.82, contentMode: .fit)
                .overlay(Image(uiImage: image).resizable().scaledToFill().clipShape(RoundedRectangle(cornerRadius: 20)))
            Text("\u{201C}\(roast)\u{201D}")
                .font(Serif.italic(24))
                .foregroundStyle(Palette.ink)
            Text("— Hem").font(Serif.italic(15)).foregroundStyle(Palette.muted)

            ShareLink(item: shareText(roast: roast)) {
                Text("SHARE THE ROAST")
                    .font(.system(size: 13, weight: .semibold))
                    .tracking(2)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Palette.ink)
                    .clipShape(RoundedRectangle(cornerRadius: 4))
            }
            Button(action: onClose) { OutlinePillButton(title: "Done") }
        }
    }

    private func shareText(roast: String) -> String {
        "Hem roasted my fit: \"\(roast)\" — Fitrater"
    }

    private func run() {
        guard !busy else { return }
        busy = true
        error = nil
        Task {
            if !session.isPro {
                let used = (try? await Repo.shared.roastsToday()) ?? 0
                if used >= 1 {
                    busy = false
                    ToastBus.shared.post("Free plan: 1 roast/day. Upgrade for unlimited.")
                    onOpenPaywall()
                    return
                }
            }
            let gate = await CreditsGate.check(Supa.roastCost)
            if case .ok = gate {} else {
                busy = false
                if case .insufficientBalance = gate { _ = CreditsGate.explainAndBlock(gate); onOpenPaywall() }
                else { _ = CreditsGate.explainAndBlock(gate) }
                return
            }
            do {
                guard let bytes = capturedBytes else { throw RepoError.notFound }
                let path = try await Repo.shared.uploadOutfitPhoto(bytes: bytes)
                guard let signed = try await Repo.shared.signedOutfitUrl(path) else {
                    throw NSError(domain: "Roast", code: 1, userInfo: [NSLocalizedDescriptionKey: "Could not sign URL"])
                }
                let scored = try await HemService.score(imageUrl: signed, occasion: pickedOccasion, honesty: "brutal")
                guard let uid = Repo.shared.userId else { throw RepoError.notSignedIn }
                _ = try await Repo.shared.insertOutfit(OutfitInsert(
                    user_id: uid,
                    photo_path: path,
                    score: scored.score,
                    occasion: pickedOccasion,
                    hem_comment: scored.hemComment,
                    weather_c: nil, verdict: nil,
                    subscores: scored.subscores,
                    swaps: scored.swaps,
                    annotations: scored.annotations,
                    kind: "roast", linked_piece_id: nil
                ))
                try? await Repo.shared.spendCredits(amount: Supa.roastCost, kind: "roast")
                roastComment = scored.hemComment
                busy = false
            } catch {
                self.error = error.localizedDescription
                ToastBus.shared.post("Roast failed: \(error.localizedDescription)")
                busy = false
            }
        }
    }
}
