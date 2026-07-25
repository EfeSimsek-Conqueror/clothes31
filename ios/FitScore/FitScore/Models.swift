import Foundation
import SwiftUI

struct Outfit: Identifiable, Hashable {
    let id: UUID
    var title: String
    var score: Double
    var weekday: String
    var swatch: Color
    var quote: String
    var imageName: String?
}

struct ClosetItem: Identifiable, Hashable {
    let id: UUID
    var name: String
    var tint: Color
}

struct SundayLetter: Identifiable, Hashable {
    let id: UUID
    var weekLabel: String
    var body: String
}

struct StyleChip: Identifiable, Hashable {
    let id = UUID()
    var label: String
}

struct ScoreResult: Hashable {
    var overall: Double
    var fit: Double
    var color: Double
    var occasion: Double
    var verdict: String
    var suggestions: [String]
}

enum Gender: String, CaseIterable, Identifiable {
    case female = "Female", male = "Male", other = "Other"
    var id: String { rawValue }
    var caption: String {
        switch self {
        case .female: return "Feminine silhouettes and palettes."
        case .male:   return "Masculine silhouettes and palettes."
        case .other:  return "Show me everything."
        }
    }
}

// Mock seeds
enum Seeds {
    static let styleChips: [StyleChip] = [
        "minimal","streetwear","classic","romantic","sporty","bold prints","earth tones","monochrome"
    ].map { .init(label: $0) }

    static let pieces: [ClosetItem] = [
        .init(id: UUID(), name: "Khaki Oversize Hoodie", tint: Color(red: 0.72, green: 0.62, blue: 0.45)),
        .init(id: UUID(), name: "Rust Boxy Tee",         tint: Color(red: 0.70, green: 0.36, blue: 0.22)),
        .init(id: UUID(), name: "Ink Wide Trousers",     tint: Color(red: 0.16, green: 0.16, blue: 0.20)),
    ]

    static let journal: [Outfit] = [
        .init(id: UUID(), title: "Terracotta linen set", score: 8.4, weekday: "THU",
              swatch: Color(red: 0.75, green: 0.40, blue: 0.28),
              quote: "Warm, dry, deliberate."),
        .init(id: UUID(), title: "Charcoal wide leg",    score: 7.9, weekday: "WED",
              swatch: Color(red: 0.22, green: 0.22, blue: 0.24),
              quote: "Quiet backbone."),
        .init(id: UUID(), title: "Cream knit + denim",   score: 7.6, weekday: "TUE",
              swatch: Color(red: 0.93, green: 0.88, blue: 0.78),
              quote: "Soft, but present."),
        .init(id: UUID(), title: "Olive utility",        score: 8.1, weekday: "MON",
              swatch: Color(red: 0.44, green: 0.46, blue: 0.30),
              quote: "Working uniform, elevated."),
    ]

    static let letter = SundayLetter(
        id: UUID(),
        weekLabel: "Week of 11–18 Jul",
        body: "You leaned into terracotta three times this week — it flatters you. Give the charcoal wide-leg one more spin before Sunday and consider a lighter shoe for humidity."
    )
}
