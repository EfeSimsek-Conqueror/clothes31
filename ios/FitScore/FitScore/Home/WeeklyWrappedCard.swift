import SwiftUI

/// Mondays-only wrapped teaser. Given last week's outfits (Mon–Sun of the week
/// just past), pull out top score + count + one-line signature. Ports Android
/// behavior — deterministic, no network.
struct HomeWeeklyWrappedCard: View {
    let lastWeekOutfits: [Outfit]
    var onOpenJournal: () -> Void = {}

    var body: some View {
        // Only render on Mondays.
        let weekday = Calendar.current.component(.weekday, from: Date())
        // Sunday = 1, Monday = 2 in Foundation.
        if weekday != 2 || lastWeekOutfits.isEmpty {
            EmptyView()
        } else {
            let top = lastWeekOutfits.compactMap { $0.score }.max() ?? 0
            let count = lastWeekOutfits.count
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Eyebrow(text: "WEEKLY WRAPPED")
                    Spacer()
                    Button(action: {
                        Haptic.chip()
                        onOpenJournal()
                    }) {
                        Text("OPEN →")
                            .font(.system(size: 11, weight: .semibold))
                            .tracking(1.8)
                            .foregroundStyle(Palette.bronze)
                    }
                }
                VStack(alignment: .leading, spacing: 12) {
                    Text("Last week, in a line.")
                        .font(Serif.display(20))
                        .foregroundStyle(Palette.ink)
                    HStack(spacing: 24) {
                        stat("TOP", String(format: "%.1f", top))
                        stat("FITS", "\(count)")
                    }
                    Text("Tap open to read the full wrap.")
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
    }

    private func stat(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.system(size: 10, weight: .semibold))
                .tracking(1.8)
                .foregroundStyle(Palette.muted)
            Text(value)
                .font(Serif.display(22, weight: .semibold))
                .foregroundStyle(Palette.ink)
        }
    }
}
