import Foundation

/// Application-wide task launcher that survives view lifecycles. Mirrors the
/// Android `AppScope` object — used for fire-and-forget writes that shouldn't
/// be tied to a specific SwiftUI view's lifetime (analytics, credits refresh,
/// etc).
///
/// Prefer `Task {}` in view code when the work is scoped to that view; reach
/// for `AppScope.launch { ... }` only when the caller might disappear before
/// the work completes.
enum AppScope {
    @discardableResult
    static func launch(_ body: @escaping @Sendable () async -> Void) -> Task<Void, Never> {
        Task.detached(priority: .userInitiated) { await body() }
    }
}
