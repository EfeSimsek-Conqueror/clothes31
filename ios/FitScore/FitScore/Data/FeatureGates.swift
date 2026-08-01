import Foundation

/// Which flow triggered the paywall — used to pick the right hero copy in
/// `CreditsSheet` (see its `heroIfNeeded`).
enum PaywallContext: String {
    case tryon
    case brutal
    case letter

    var headline: String {
        switch self {
        case .tryon:  return "Try-on is Pro."
        case .brutal: return "Brutal mode is Pro."
        case .letter: return "The Sunday Letter is Pro."
        }
    }

    var subline: String {
        switch self {
        case .tryon:  return "Wear any Studio piece on your own photo. Unlimited on Pro."
        case .brutal: return "Turn Hem's kid gloves off. Brutal, honest, one-line verdicts."
        case .letter: return "A short Sunday letter — what you wore, what worked, what to try."
        }
    }
}

/// Pre-flight checks for Pro-locked flows. Return `nil` if the user may
/// proceed, else the `PaywallContext` that should be shown. Callers pass the
/// returned context into `CreditsSheet(paywallContext:)` to render the
/// matching hero card.
@MainActor
enum FeatureGates {
    static func requireTryon() -> PaywallContext? {
        RcBilling.shared.isPro ? nil : .tryon
    }
    static func requireBrutal() -> PaywallContext? {
        RcBilling.shared.isPro ? nil : .brutal
    }
    static func requireLetter() -> PaywallContext? {
        RcBilling.shared.isPro ? nil : .letter
    }
}
