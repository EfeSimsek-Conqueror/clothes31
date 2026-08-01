import Foundation
import UIKit

/// Fetches current temperature from open-meteo (no API key, no rate limit for
/// low volumes). Default location: Istanbul. Rounds to nearest integer °C.
enum Weather {
    static func temperatureCelsius() async -> Int? {
        let url = URL(string: "https://api.open-meteo.com/v1/forecast?latitude=41.01&longitude=28.98&current=temperature_2m")!
        var req = URLRequest(url: url)
        req.timeoutInterval = 6
        do {
            let (data, _) = try await URLSession.shared.data(for: req)
            struct Resp: Decodable {
                struct Current: Decodable { let temperature_2m: Double? }
                let current: Current?
            }
            let r = try JSONDecoder().decode(Resp.self, from: data)
            if let t = r.current?.temperature_2m { return Int(t.rounded()) }
            return nil
        } catch {
            return nil
        }
    }
}

/// Editorial-strength haptics wrappers. Every primary tap uses medium; chips
/// use light; the score reveal uses soft.
enum Haptic {
    static func tap()   { UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
    static func chip()  { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    static func soft()  { UIImpactFeedbackGenerator(style: .soft).impactOccurred() }
}
