import Foundation
import UIKit

// The weather chip is gone as of Sep 2026. It called open-meteo with a
// hardcoded Istanbul latitude/longitude (41.01 / 28.98) and showed that number
// to every user on earth as if it were their own weather. Making it truthful
// needs CoreLocation — a new purpose string and a location entry in App
// Privacy — which the feature does not earn. Removed rather than faked.
// See `HomeView.load()` and `HomeHeader.tempC`.

/// Editorial-strength haptics wrappers. Every primary tap uses medium; chips
/// use light; the score reveal uses soft.
enum Haptic {
    static func tap()   { UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
    static func chip()  { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    static func soft()  { UIImpactFeedbackGenerator(style: .soft).impactOccurred() }
}
