import Foundation
import SwiftUI

/// Single source of truth for the user's current credit balance. Every screen
/// that shows credits observes `balance`. Every spend/grant path in `Repo`
/// calls `refreshAsync()` so all live UIs update immediately.
@MainActor
final class CreditsBus: ObservableObject {
    static let shared = CreditsBus()
    private init() {}

    @Published private(set) var balance: Int?

    /// Re-read credits from the DB and publish. Idempotent.
    func refresh() async {
        balance = try? await Repo.shared.credits()
    }

    /// Fire-and-forget refresh from any callback / view scope. Suitable for
    /// call sites that don't want to hold a Task handle.
    nonisolated func refreshAsync() {
        Task { @MainActor in await self.refresh() }
    }

    /// Called on sign-out — drop the cached balance so the paywall/roast
    /// gates don't accidentally read a stale value.
    func clear() { balance = nil }
}
