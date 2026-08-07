import Foundation
import SwiftUI

enum CameraFlow: String, Identifiable {
    case score, tryon, versus, roast, decode, occasion, paywall
    // Sprint 3 + 5 additions
    case cover       // manual magazine-cover composer
    case invitation  // decode a printed invite → 3 closet combos
    // Stylist Chat (Aug 2026): moderate AI chat, replaces user-facing brutal roast entry.
    case chat
    var id: String { rawValue }
}

@MainActor
final class CameraMenuBus: ObservableObject {
    static let shared = CameraMenuBus()
    @Published var pending: CameraFlow? = nil
    @Published var paywallContext: PaywallContext? = nil

    func request(_ flow: CameraFlow, context: PaywallContext? = nil) {
        paywallContext = context
        pending = flow
    }
}
