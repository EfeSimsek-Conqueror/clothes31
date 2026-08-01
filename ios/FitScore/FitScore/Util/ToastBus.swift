import Foundation
import SwiftUI

/// Simple transient toast bus. Any code (including detached tasks) can post a
/// message; the root view observes `message` and shows a bronze pill.
@MainActor
final class ToastBus: ObservableObject {
    static let shared = ToastBus()
    private init() {}

    @Published var message: String?
    private var clearTask: Task<Void, Never>?

    /// Post a message. Auto-clears after 3 seconds unless replaced.
    func post(_ text: String) {
        message = text
        clearTask?.cancel()
        clearTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            guard !Task.isCancelled else { return }
            self?.message = nil
        }
    }

    /// Non-isolated posting helper for callback contexts.
    nonisolated func postAsync(_ text: String) {
        Task { @MainActor in self.post(text) }
    }
}

/// In-memory dismissal tracker for the Low-Credits nudge banner. Force-quit
/// re-shows the nudge on next launch — intentional (soft nudge, not a hard
/// silence).
@MainActor
final class NudgeBus: ObservableObject {
    static let shared = NudgeBus()
    private init() {}

    @Published private(set) var dismissedOn: Date?

    var isDismissedToday: Bool {
        guard let d = dismissedOn else { return false }
        return Calendar.current.isDateInToday(d)
    }

    func dismissForToday() { dismissedOn = Date() }
}
