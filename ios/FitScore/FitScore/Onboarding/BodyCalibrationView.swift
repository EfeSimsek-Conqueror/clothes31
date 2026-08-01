import SwiftUI
import PhotosUI

/// One-time body calibration. Users upload a front photo (side optional), we
/// send it to the `analyze-body` edge function, and store a BodyProfile that
/// every future rating personalizes against. Shown as the last onboarding step
/// for new users and as an on-demand sheet for existing users.
struct BodyCalibrationView: View {
    /// Called after a profile is successfully saved. Also called when the
    /// user taps "Skip for now" — pass `skipped: true` in that case.
    var onFinished: (_ profile: BodyProfile?) -> Void

    @EnvironmentObject var session: SessionStore
    @EnvironmentObject var toasts: ToastBus

    @State private var frontBytes: Data?
    @State private var frontImage: UIImage?
    @State private var sideBytes: Data?
    @State private var sideImage: UIImage?

    @State private var busy = false
    @State private var error: String?

    @State private var showCameraFront = false
    @State private var showCameraSide = false
    @State private var frontPickerItem: PhotosPickerItem?
    @State private var sidePickerItem: PhotosPickerItem?

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Spacer(minLength: 20)

                    Eyebrow(text: "PERSONALIZE")
                    Text("Calibrate your fit.")
                        .font(Serif.display(34))
                        .foregroundStyle(Palette.ink)
                    Text("One photo. Every rating from now on is measured against you, not a mannequin.")
                        .font(Serif.italic(15))
                        .foregroundStyle(Palette.muted)
                        .padding(.bottom, 4)

                    HStack(spacing: 12) {
                        photoTile(
                            image: frontImage,
                            label: "FRONT",
                            required: true,
                            onCamera: { Haptic.tap(); showCameraFront = true },
                            onClear: { frontBytes = nil; frontImage = nil }
                        )
                        photoTile(
                            image: sideImage,
                            label: "SIDE (OPT)",
                            required: false,
                            onCamera: { Haptic.tap(); showCameraSide = true },
                            onClear: { sideBytes = nil; sideImage = nil }
                        )
                    }

                    HStack(spacing: 10) {
                        PhotosPicker(selection: $frontPickerItem, matching: .images) {
                            OutlinePillButton(title: "Front from gallery")
                        }
                        if frontImage != nil {
                            PhotosPicker(selection: $sidePickerItem, matching: .images) {
                                OutlinePillButton(title: "Side from gallery")
                            }
                        }
                    }

                    Text("Wear something fitted so we can read your proportions. We never estimate weight, height, or BMI — only your silhouette and coloring.")
                        .font(Serif.italic(12))
                        .foregroundStyle(Palette.muted)
                        .padding(.top, 4)

                    if let error {
                        Text(error).font(Serif.body(13)).foregroundStyle(Palette.bronze)
                    }

                    Spacer(minLength: 30)

                    PrimaryButton(
                        title: busy ? "Analyzing…" : "Analyze me",
                        enabled: frontBytes != nil && !busy,
                        action: analyze
                    )

                    Button {
                        onFinished(nil)
                    } label: {
                        Text("Skip for now")
                            .font(Serif.body(14))
                            .foregroundStyle(Palette.muted)
                            .padding(.top, 10)
                            .frame(maxWidth: .infinity)
                    }
                    .padding(.bottom, 24)
                }
                .padding(.horizontal, 24)
            }

            if busy {
                WaitingOverlay(
                    eyebrow: "READING",
                    title: "Learning your frame",
                    tips: [
                        "Measuring your proportions…",
                        "Sampling your natural palette…",
                        "Locking in your baseline…",
                        "This is one photo, one time.",
                    ]
                )
            }
        }
        .fullScreenCover(isPresented: $showCameraFront) {
            CameraCaptureView().onDisappear {
                if let d = CameraBus.shared.consume() {
                    frontBytes = d
                    frontImage = UIImage(data: d)
                }
            }
        }
        .fullScreenCover(isPresented: $showCameraSide) {
            CameraCaptureView().onDisappear {
                if let d = CameraBus.shared.consume() {
                    sideBytes = d
                    sideImage = UIImage(data: d)
                }
            }
        }
        .onChange(of: frontPickerItem) { _, item in
            Task {
                if let item, let d = try? await item.loadTransferable(type: Data.self) {
                    let p = CameraModel.processJpeg(d) ?? d
                    frontBytes = p
                    frontImage = UIImage(data: p)
                }
            }
        }
        .onChange(of: sidePickerItem) { _, item in
            Task {
                if let item, let d = try? await item.loadTransferable(type: Data.self) {
                    let p = CameraModel.processJpeg(d) ?? d
                    sideBytes = p
                    sideImage = UIImage(data: p)
                }
            }
        }
    }

    // MARK: - Tile

    @ViewBuilder
    private func photoTile(image: UIImage?, label: String, required: Bool,
                           onCamera: @escaping () -> Void, onClear: @escaping () -> Void) -> some View {
        Button(action: onCamera) {
            RoundedRectangle(cornerRadius: 18)
                .fill(Palette.card)
                .aspectRatio(0.66, contentMode: .fit)
                .overlay(
                    Group {
                        if let image {
                            Image(uiImage: image).resizable().scaledToFill()
                                .clipShape(RoundedRectangle(cornerRadius: 18))
                                .overlay(alignment: .topTrailing) {
                                    Button(action: onClear) {
                                        Image(systemName: "xmark")
                                            .foregroundStyle(.white)
                                            .padding(6)
                                            .background(Circle().fill(Palette.ink))
                                    }
                                    .padding(8)
                                }
                        } else {
                            VStack(spacing: 6) {
                                Text("+")
                                    .font(Serif.display(30))
                                    .foregroundStyle(Palette.ink)
                                Text(label)
                                    .font(.system(size: 10, weight: .semibold)).tracking(2)
                                    .foregroundStyle(Palette.bronze)
                                if !required {
                                    Text("Optional")
                                        .font(Serif.italic(11))
                                        .foregroundStyle(Palette.muted)
                                }
                            }
                        }
                    }
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 18)
                        .strokeBorder(
                            style: StrokeStyle(lineWidth: 1, dash: image == nil ? [4, 4] : [])
                        )
                        .foregroundStyle(Palette.hairline)
                )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Analyze

    private func analyze() {
        guard let front = frontBytes else { return }
        busy = true
        error = nil
        Task {
            defer { busy = false }
            do {
                let frontPath = try await Repo.shared.uploadOutfitPhoto(bytes: front)
                guard let frontSigned = try await Repo.shared.signedOutfitUrl(frontPath) else {
                    throw RepoError.notFound
                }
                var sideSigned: String?
                var sidePath: String?
                if let side = sideBytes {
                    sidePath = try await Repo.shared.uploadOutfitPhoto(bytes: side)
                    sideSigned = try await Repo.shared.signedOutfitUrl(sidePath!)
                }
                let resp = try await Repo.shared.analyzeBody(frontUrl: frontSigned, sideUrl: sideSigned)
                guard let profile = resp.profile else {
                    throw NSError(domain: "Calibration", code: 1,
                                  userInfo: [NSLocalizedDescriptionKey: resp.error ?? "Analysis failed"])
                }
                try await Repo.shared.saveBodyProfile(profile, frontPath: frontPath, sidePath: sidePath)
                session.bodyProfile = profile
                Haptic.soft()
                onFinished(profile)
            } catch {
                self.error = error.localizedDescription
                toasts.post("Calibration failed: \(error.localizedDescription)")
            }
        }
    }
}
