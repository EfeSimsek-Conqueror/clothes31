import SwiftUI

/// Sunday letter teaser. Free tier sees a locked bronze eyebrow "PRO UNLOCKS
/// THE FULL LETTER" with a View → pill that routes to paywall. Pro tier sees
/// the letter body (2 lines) with a "Read the full letter →" pill.
struct SundayLetterPreviewCard: View {
    let letter: SundayLetter?
    let isPro: Bool
    var onOpen: () -> Void = {}
    var onOpenPaywall: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Eyebrow(text: "THE SUNDAY LETTER")
            if !isPro {
                Text("This week's letter is waiting…")
                    .font(Serif.italic(19))
                    .foregroundStyle(Palette.ink)
                Text("PRO UNLOCKS THE FULL LETTER")
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(2)
                    .foregroundStyle(Palette.bronze)
                pill("View →") {
                    Haptic.tap()
                    onOpenPaywall()
                }
            } else if let body = letter?.body, !body.isEmpty {
                Text(body)
                    .font(Serif.italic(19))
                    .foregroundStyle(Palette.ink)
                    .lineLimit(2)
                pill("Read the full letter →") {
                    Haptic.tap()
                    onOpen()
                }
            } else {
                Text("Your first Sunday letter will arrive after a week of fits.")
                    .font(Serif.body(14))
                    .foregroundStyle(Palette.muted)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.hairline, lineWidth: 1))
    }

    @ViewBuilder
    private func pill(_ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(Serif.body(13, weight: .medium))
                .foregroundStyle(Palette.ink)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .overlay(Capsule().stroke(Palette.ink.opacity(0.4), lineWidth: 1))
        }
    }
}
