import Foundation

/// Handoff channel for bytes captured by `CameraCaptureView` back to whichever
/// flow view (`ScoreSheetView`, `TryOnView`, `RoastView`, `VersusView`) opened
/// it. Mirrors the Android `CameraBus` object.
///
/// The `slot` field is used by A vs B (`VersusView`) so we know whether the
/// capture belongs to slot A or B on return.
@MainActor
final class CameraBus {
    static let shared = CameraBus()
    private init() {}

    var pendingBytes: Data?
    var slot: String?

    /// Consume + clear the pending bytes.
    func consume() -> Data? {
        let b = pendingBytes
        pendingBytes = nil
        return b
    }

    /// Consume + clear both bytes and slot (for A vs B routing).
    func consumeWithSlot() -> (slot: String?, bytes: Data?) {
        let b = pendingBytes
        let s = slot
        pendingBytes = nil
        slot = nil
        return (s, b)
    }
}

/// Cross-nav cache for A vs B photos. Persisting bytes here means that if the
/// user leaves to the camera to capture slot B, slot A's photo isn't lost.
@MainActor
final class VersusBus {
    static let shared = VersusBus()
    private init() {}

    var aBytes: Data?
    var bBytes: Data?

    func clear() { aBytes = nil; bBytes = nil }
}
