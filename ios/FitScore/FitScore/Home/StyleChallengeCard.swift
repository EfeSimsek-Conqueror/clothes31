import SwiftUI

/// Deterministic ISO-week-driven challenge from a fixed 12-item list. Ports
/// Android `StyleChallenges`.
enum StyleChallenges {
    static let list: [String] = [
        "Try monochrome for a day.",
        "Add one accessory you never wear.",
        "Layer two tones from the same family.",
        "Ground your fit with brown shoes.",
        "One vintage piece, one modern.",
        "Wear a color you rarely reach for.",
        "Cuff your sleeves.",
        "Skip black for a week.",
        "Match your bag to your belt.",
        "Try a shirt tucked in properly.",
        "Wear texture you can feel from across the room.",
        "Fewer buttons open, more discipline.",
    ]

    static func current(_ date: Date = Date()) -> String {
        var cal = Calendar(identifier: .iso8601)
        cal.minimumDaysInFirstWeek = 4
        let week = cal.component(.weekOfYear, from: date)
        let idx = ((week % list.count) + list.count) % list.count
        return list[idx]
    }
}

struct StyleChallengeCard: View {
    let title: String
    var onSeeProgress: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Eyebrow(text: "THIS WEEK'S CHALLENGE")
                Spacer()
                Button(action: {
                    Haptic.chip()
                    onSeeProgress()
                }) {
                    Text("SEE PROGRESS →")
                        .font(.system(size: 11, weight: .semibold))
                        .tracking(1.8)
                        .foregroundStyle(Palette.bronze)
                }
            }
            Text(title)
                .font(Serif.display(20))
                .foregroundStyle(Palette.ink)
            Text("Score a fit that matches — Hem will note it in your Journal.")
                .font(Serif.body(13))
                .foregroundStyle(Palette.muted)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.hairline, lineWidth: 1))
    }
}
