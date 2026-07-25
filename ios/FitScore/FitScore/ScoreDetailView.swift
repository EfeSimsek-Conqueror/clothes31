import SwiftUI

struct ScoreDetailView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Eyebrow(text: "TODAY'S SCORE")
                Text("Your look, honestly.")
                    .font(Serif.display(32))
                    .foregroundStyle(Palette.ink)

                ZStack(alignment: .topTrailing) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(LinearGradient(colors: [
                            Color(red: 0.35, green: 0.28, blue: 0.22),
                            Color(red: 0.55, green: 0.44, blue: 0.30)
                        ], startPoint: .top, endPoint: .bottom))
                        .frame(height: 440)
                    ScoreChip(score: 8.2).padding(14)
                }

                Text("0.6 above your average · DAILY")
                    .font(.system(size: 11, weight: .semibold)).tracking(1.6)
                    .foregroundStyle(Palette.muted)

                PullQuote(quote: "Warm, dry, deliberate — the terracotta finally meets the linen it deserves.")

                PrimaryButton(title: "✦  Score a Look") { }

                VStack(spacing: 0) {
                    Hairline()
                    HStack(spacing: 0) {
                        cell(title: "The Mirror", subtitle: "Try any piece")
                        Rectangle().fill(Palette.hairline).frame(width: 0.5, height: 76)
                        cell(title: "Ask Hem", subtitle: "Talk it out")
                    }
                    Hairline()
                }
            }
            .padding(22)
        }
        .background(Palette.paper.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
    }

    @ViewBuilder
    private func cell(title: String, subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(Serif.display(18)).foregroundStyle(Palette.ink)
            Text(subtitle).font(Serif.body(12)).foregroundStyle(Palette.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 18).padding(.horizontal, 4)
    }
}
