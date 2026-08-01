import SwiftUI

/// BREAKDOWN grid rendered under the hero photo in `ScoreDetailView`. Each row
/// is `Label ...... 7.5 / 10` with a hairline divider. Only shows entries with
/// non-nil values (matches Android behavior).
struct SubscoresView: View {
    let subscores: Subscores

    var body: some View {
        let entries = self.entries()
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(entries.enumerated()), id: \.offset) { _, e in
                HStack {
                    Text(e.label)
                        .font(Serif.body(15))
                        .foregroundStyle(Palette.ink)
                    Spacer()
                    Text(String(format: "%.1f / 10", e.value))
                        .font(Serif.body(14))
                        .foregroundStyle(Palette.ink)
                }
                .padding(.vertical, 10)
                Rectangle().fill(Palette.hairline).frame(height: 0.5)
            }
        }
    }

    private func entries() -> [(label: String, value: Double)] {
        var out: [(String, Double)] = []
        if let v = subscores.color       { out.append(("Color", v)) }
        if let v = subscores.fit         { out.append(("Fit", v)) }
        if let v = subscores.style_match { out.append(("Style match", v)) }
        if let v = subscores.seasonal    { out.append(("Seasonal", v)) }
        return out
    }
}
