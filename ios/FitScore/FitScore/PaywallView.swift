import SwiftUI

/// Backwards-compatible shim — callers that were previously showing
/// `PaywallView()` in a `.sheet` now get the real `CreditsSheet` (packs +
/// plans). Optional `context` picks the hero card at the top.
struct PaywallView: View {
    var context: PaywallContext? = nil

    var body: some View {
        CreditsSheet(paywallContext: context?.rawValue)
    }
}
