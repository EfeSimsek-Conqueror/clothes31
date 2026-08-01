import SwiftUI
import AVFoundation
import UIKit
import PhotosUI

/// AVFoundation-backed live camera capture with a 4:5 portrait framing guide.
/// On successful capture, bytes are handed off via `CameraBus.pendingBytes`
/// and the view dismisses. Skips the review screen (crop/resize applied
/// immediately on shutter).
struct CameraCaptureView: View {
    @Environment(\.dismiss) private var dismiss

    @StateObject private var model = CameraModel()
    @State private var showPicker = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if model.permissionGranted {
                CameraPreview(session: model.session)
                    .ignoresSafeArea()

                FramingOverlay()
                    .allowsHitTesting(false)

                VStack {
                    HStack {
                        roundBtn("xmark") { dismiss() }
                        Spacer()
                        roundBtn("arrow.triangle.2.circlepath.camera") {
                            model.flipCamera()
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 40)

                    Spacer()

                    HStack {
                        roundBtn(model.flashOn ? "bolt.fill" : "bolt.slash.fill") {
                            model.toggleFlash()
                        }
                        Spacer()
                        ShutterButton {
                            Haptic.tap()
                            model.capture { data in
                                guard let data else { return }
                                CameraBus.shared.pendingBytes = data
                                dismiss()
                            }
                        }
                        Spacer()
                        roundBtn("photo.on.rectangle") {
                            showPicker = true
                        }
                    }
                    .padding(.horizontal, 32)
                    .padding(.bottom, 48)
                }
            } else if model.deniedTwice {
                VStack(spacing: 12) {
                    Text("Camera unavailable")
                        .font(Serif.display(22))
                        .foregroundStyle(.white)
                    Text("Pick from your library instead.")
                        .font(Serif.body(14))
                        .foregroundStyle(.white.opacity(0.7))
                    Button("Open library") { showPicker = true }
                        .foregroundStyle(.white)
                        .padding(.top, 10)
                    Button("Cancel") { dismiss() }
                        .foregroundStyle(.white.opacity(0.7))
                }
            } else {
                ProgressView()
                    .tint(.white)
            }
        }
        .onAppear { model.start() }
        .onDisappear { model.stop() }
        .sheet(isPresented: $showPicker) {
            LibraryPicker(onPicked: { data in
                if let d = data {
                    CameraBus.shared.pendingBytes = d
                    dismiss()
                }
                showPicker = false
            })
        }
    }

    @ViewBuilder
    private func roundBtn(_ icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 44, height: 44)
                .background(Color.black.opacity(0.35))
                .clipShape(Circle())
        }
    }
}

// MARK: - Shutter

private struct ShutterButton: View {
    var action: () -> Void
    @State private var pressed = false
    var body: some View {
        Button(action: action) {
            ZStack {
                Circle()
                    .stroke(Palette.card, lineWidth: 3)
                    .frame(width: 72, height: 72)
                Circle()
                    .fill(Palette.ink)
                    .frame(width: 56, height: 56)
            }
            .scaleEffect(pressed ? 0.9 : 1)
        }
        .buttonStyle(PressStyle(pressed: $pressed))
    }
}

private struct PressStyle: ButtonStyle {
    @Binding var pressed: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .onChange(of: configuration.isPressed) { _, v in pressed = v }
    }
}

// MARK: - 4:5 framing guide overlay

private struct FramingOverlay: View {
    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            let frameW = w * 0.82
            let frameHRaw = frameW * 5 / 4
            let maxH = h * 0.72
            let frameH = min(frameHRaw, maxH)
            let actualW = frameHRaw > maxH ? maxH * 4 / 5 : frameW
            let left = (w - actualW) / 2
            let top = (h - frameH) / 2 - h * 0.02
            ZStack {
                // Darken outside frame using an even-odd mask
                Rectangle()
                    .fill(Color.black.opacity(0.4))
                    .mask(
                        Rectangle()
                            .overlay(
                                Rectangle()
                                    .frame(width: actualW, height: frameH)
                                    .position(x: left + actualW / 2, y: top + frameH / 2)
                                    .blendMode(.destinationOut)
                            )
                            .compositingGroup()
                    )
                // Frame border
                Path { p in
                    p.addRect(CGRect(x: left, y: top, width: actualW, height: frameH))
                }
                .stroke(Color.black.opacity(0.6), lineWidth: 2)
                // Corner brackets
                Path { p in
                    let b: CGFloat = 24
                    let x2 = left + actualW
                    let y2 = top + frameH
                    p.move(to: CGPoint(x: left, y: top + b));   p.addLine(to: CGPoint(x: left, y: top));   p.addLine(to: CGPoint(x: left + b, y: top))
                    p.move(to: CGPoint(x: x2 - b, y: top));      p.addLine(to: CGPoint(x: x2, y: top));     p.addLine(to: CGPoint(x: x2, y: top + b))
                    p.move(to: CGPoint(x: left, y: y2 - b));     p.addLine(to: CGPoint(x: left, y: y2));    p.addLine(to: CGPoint(x: left + b, y: y2))
                    p.move(to: CGPoint(x: x2 - b, y: y2));       p.addLine(to: CGPoint(x: x2, y: y2));      p.addLine(to: CGPoint(x: x2, y: y2 - b))
                }
                .stroke(Color.white.opacity(0.9), lineWidth: 3.5)
                VStack {
                    Spacer().frame(height: top + frameH + 12)
                    Text("FRAME YOUR LOOK")
                        .font(.system(size: 11, weight: .semibold))
                        .tracking(3)
                        .foregroundStyle(.white.opacity(0.85))
                    Spacer()
                }
            }
        }
    }
}

// MARK: - AVFoundation model

@MainActor
final class CameraModel: NSObject, ObservableObject, AVCapturePhotoCaptureDelegate {
    let session = AVCaptureSession()
    private let output = AVCapturePhotoOutput()
    private var currentInput: AVCaptureDeviceInput?
    private let sessionQueue = DispatchQueue(label: "cam.session")

    @Published var permissionGranted = false
    @Published var deniedTwice = false
    @Published var flashOn = false

    private var lensBack = true
    private var captureContinuation: ((Data?) -> Void)?

    func start() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            permissionGranted = true
            configure()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                Task { @MainActor in
                    self?.permissionGranted = granted
                    if granted { self?.configure() } else { self?.deniedTwice = true }
                }
            }
        default:
            deniedTwice = true
        }
    }

    private func configure() {
        sessionQueue.async { [weak self] in
            guard let self else { return }
            self.session.beginConfiguration()
            self.session.sessionPreset = .photo
            self.session.inputs.forEach { self.session.removeInput($0) }
            if let dev = self.device(back: self.lensBack),
               let input = try? AVCaptureDeviceInput(device: dev),
               self.session.canAddInput(input) {
                self.session.addInput(input)
                self.currentInput = input
            }
            if self.session.outputs.isEmpty, self.session.canAddOutput(self.output) {
                self.session.addOutput(self.output)
            }
            self.session.commitConfiguration()
            if !self.session.isRunning { self.session.startRunning() }
        }
    }

    private func device(back: Bool) -> AVCaptureDevice? {
        let pos: AVCaptureDevice.Position = back ? .back : .front
        return AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: pos)
    }

    func stop() {
        sessionQueue.async { [weak self] in
            self?.session.stopRunning()
        }
    }

    func flipCamera() {
        lensBack.toggle()
        configure()
    }

    func toggleFlash() { flashOn.toggle() }

    func capture(_ completion: @escaping (Data?) -> Void) {
        captureContinuation = completion
        let settings = AVCapturePhotoSettings()
        if let dev = currentInput?.device, dev.hasFlash {
            settings.flashMode = flashOn ? .on : .off
        }
        sessionQueue.async { [weak self] in
            guard let self else { return }
            self.output.capturePhoto(with: settings, delegate: self)
        }
    }

    // Delegate — bounces back to main to invoke completion.
    nonisolated func photoOutput(_ output: AVCapturePhotoOutput,
                                 didFinishProcessingPhoto photo: AVCapturePhoto,
                                 error: Error?) {
        let data = photo.fileDataRepresentation()
        Task { @MainActor in
            let processed = data.flatMap { Self.processJpeg($0) }
            self.captureContinuation?(processed)
            self.captureContinuation = nil
        }
    }

    /// Decode → center-crop 4:5 → resize longest edge to 2048px → JPEG q=85.
    static func processJpeg(_ bytes: Data) -> Data? {
        guard let img = UIImage(data: bytes) else { return nil }
        let normalized = img.normalizedUp()
        let cropped = normalized.centerCrop45()
        let resized = cropped.resizeLongestEdge(2048)
        return resized.jpegData(compressionQuality: 0.85)
    }
}

// MARK: - AVCaptureVideoPreviewLayer wrapper

private struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession
    func makeUIView(context: Context) -> PreviewUIView {
        let v = PreviewUIView()
        v.videoLayer.session = session
        v.videoLayer.videoGravity = .resizeAspectFill
        return v
    }
    func updateUIView(_ uiView: PreviewUIView, context: Context) {}
}

final class PreviewUIView: UIView {
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
    var videoLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
}

// MARK: - PhotosPicker fallback

struct LibraryPicker: UIViewControllerRepresentable {
    var onPicked: (Data?) -> Void

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var cfg = PHPickerConfiguration(photoLibrary: .shared())
        cfg.filter = .images
        cfg.selectionLimit = 1
        let vc = PHPickerViewController(configuration: cfg)
        vc.delegate = context.coordinator
        return vc
    }
    func updateUIViewController(_ uiViewController: PHPickerViewController, context: Context) {}
    func makeCoordinator() -> Coordinator { Coordinator(onPicked: onPicked) }

    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        let onPicked: (Data?) -> Void
        init(onPicked: @escaping (Data?) -> Void) { self.onPicked = onPicked }
        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            guard let item = results.first?.itemProvider,
                  item.canLoadObject(ofClass: UIImage.self) else {
                onPicked(nil)
                return
            }
            item.loadObject(ofClass: UIImage.self) { obj, _ in
                let img = obj as? UIImage
                let data = img?.normalizedUp().resizeLongestEdge(2048).jpegData(compressionQuality: 0.85)
                Task { @MainActor in self.onPicked(data) }
            }
        }
    }
}

// MARK: - UIImage helpers

extension UIImage {
    func normalizedUp() -> UIImage {
        if imageOrientation == .up { return self }
        UIGraphicsBeginImageContextWithOptions(size, false, scale)
        draw(in: CGRect(origin: .zero, size: size))
        let out = UIGraphicsGetImageFromCurrentImageContext() ?? self
        UIGraphicsEndImageContext()
        return out
    }

    /// Center-crop to 4:5 portrait.
    func centerCrop45() -> UIImage {
        guard let cg = cgImage else { return self }
        let w = CGFloat(cg.width)
        let h = CGFloat(cg.height)
        let target: CGFloat = 4.0 / 5.0
        let srcRatio = w / h
        let rect: CGRect
        if srcRatio > target {
            // too wide → crop sides
            let newW = h * target
            let x = (w - newW) / 2
            rect = CGRect(x: x, y: 0, width: newW, height: h)
        } else {
            let newH = w / target
            let y = (h - newH) / 2
            rect = CGRect(x: 0, y: y, width: w, height: newH)
        }
        guard let cropped = cg.cropping(to: rect) else { return self }
        return UIImage(cgImage: cropped, scale: scale, orientation: .up)
    }

    func resizeLongestEdge(_ max: CGFloat) -> UIImage {
        let longest = Swift.max(size.width, size.height)
        if longest <= max { return self }
        let scale = max / longest
        let ns = CGSize(width: size.width * scale, height: size.height * scale)
        let fmt = UIGraphicsImageRendererFormat.default()
        fmt.scale = 1
        let renderer = UIGraphicsImageRenderer(size: ns, format: fmt)
        return renderer.image { _ in draw(in: CGRect(origin: .zero, size: ns)) }
    }
}
