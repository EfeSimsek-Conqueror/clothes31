import Foundation
import SwiftUI

@MainActor
final class SessionStore: ObservableObject {
    enum Stage: Equatable {
        case splash, signIn, onboarding1, onboarding2, home
    }
    @Published var stage: Stage = .splash
    @Published var username: String = "Efe"
    @Published var gender: Gender? = nil
    @Published var pickedChips: Set<String> = []
    @Published var credits: Int = 240
    @Published var isPro: Bool = false

    // Placeholder Supabase config (do not ship management token).
    let supabaseURL = URL(string: "https://ilrzqifdmjvooeyqvexd.supabase.co")!
    let supabasePublishable = "sb_publishable_bz5nOR8qZ3jTaXJMbR4MHA_w2jaMyDS"

    func advance(to s: Stage) { withAnimation(.easeInOut(duration: 0.25)) { stage = s } }

    func greeting() -> String {
        let h = Calendar.current.component(.hour, from: Date())
        let part: String
        switch h {
        case 5..<12:  part = "Morning"
        case 12..<17: part = "Afternoon"
        case 17..<22: part = "Evening"
        default:      part = "Night"
        }
        return "\(part), \(username)."
    }

    func todayEyebrow() -> String {
        let f = DateFormatter()
        f.dateFormat = "EEEE · d MMMM"
        return f.string(from: Date()).uppercased()
    }
}

// MARK: - HemService (mock scoring)
protocol HemService {
    func score(seed: Int) async -> ScoreResult
}

struct MockHemService: HemService {
    func score(seed: Int) async -> ScoreResult {
        let base = 7.4 + Double((seed % 17)) / 20.0
        return ScoreResult(
            overall: min(9.6, base + 0.6),
            fit:     min(9.8, base + 0.4),
            color:   min(9.5, base + 0.2),
            occasion: min(9.7, base + 0.5),
            verdict: [
                "Warm, dry, deliberate.",
                "Quiet backbone — let the shoe speak.",
                "Cream keeps you human in July heat.",
                "Working uniform, elevated."
            ][seed % 4],
            suggestions: [
                "Swap the sneaker for a suede loafer.",
                "Roll the sleeves — show the wrist.",
                "Add a leather belt to break the block."
            ]
        )
    }
}
