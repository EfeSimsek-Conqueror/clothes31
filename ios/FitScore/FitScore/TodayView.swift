import SwiftUI

struct TodayView: View {
    @EnvironmentObject var session: SessionStore
    @Binding var showPaywall: Bool
    @Binding var showScoreSheet: Bool
    @State private var goDetail = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    header
                    hemCard
                    todaysFit
                    twoUp
                }
                .padding(.horizontal, 22)
                .padding(.top, 8)
                .padding(.bottom, 40)
            }
            .background(Palette.paper.ignoresSafeArea())
            .navigationDestination(isPresented: $goDetail) { ScoreDetailView() }
        }
    }

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 8) {
                Eyebrow(text: session.todayEyebrow())
                Text(session.greeting())
                    .font(Serif.display(34))
                    .foregroundStyle(Palette.ink)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 6) {
                HStack(spacing: 6) {
                    Image(systemName: "sun.max.fill").foregroundStyle(Palette.bronze)
                    Text("28°").font(Serif.body(14, weight: .medium)).foregroundStyle(Palette.ink)
                }
                Circle().fill(Palette.bronze.opacity(0.25))
                    .frame(width: 36, height: 36)
                    .overlay(Text(String(session.username.prefix(1))).font(Serif.display(16)).foregroundStyle(Palette.ink))
            }
        }
    }

    private var hemCard: some View {
        TanCard {
            VStack(alignment: .leading, spacing: 12) {
                Eyebrow(text: "HEM · THIS MORNING")
                Text("The linen shirt hasn't seen the sun in twelve days — try it with the wide-leg trousers before the heat breaks.")
                    .font(Serif.italic(19))
                    .foregroundStyle(Palette.ink)
                    .fixedSize(horizontal: false, vertical: true)
                HStack {
                    Spacer()
                    Text("Try it on →")
                        .font(Serif.body(14, weight: .medium))
                        .foregroundStyle(Palette.bronze)
                }
            }
        }
    }

    private var todaysFit: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Eyebrow(text: "TODAY'S FIT")
                Spacer()
                Text("SHARE ↗")
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(1.6)
                    .foregroundStyle(Palette.bronze)
            }
            ZStack(alignment: .topTrailing) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(LinearGradient(colors: [
                        Color(red: 0.35, green: 0.28, blue: 0.22),
                        Color(red: 0.55, green: 0.44, blue: 0.30)
                    ], startPoint: .top, endPoint: .bottom))
                    .frame(height: 420)
                    .overlay(
                        VStack {
                            Spacer()
                            Image(systemName: "figure.stand")
                                .font(.system(size: 180, weight: .ultraLight))
                                .foregroundStyle(.white.opacity(0.25))
                            Spacer()
                        }
                    )
                ScoreChip(score: 8.2).padding(14)
            }
            Text("0.6 above your average · DAILY")
                .font(.system(size: 11, weight: .semibold))
                .tracking(1.6)
                .foregroundStyle(Palette.muted)
            PullQuote(quote: "Warm, dry, deliberate — the terracotta finally meets the linen it deserves.")

            PrimaryButton(title: "✦  Score a Look") { goDetail = true }
        }
    }

    private var twoUp: some View {
        VStack(spacing: 0) {
            Hairline()
            HStack(spacing: 0) {
                twoUpItem(title: "The Mirror", subtitle: "Try any piece on your photo")
                Rectangle().fill(Palette.hairline).frame(width: 0.5, height: 76)
                twoUpItem(title: "Ask Hem", subtitle: "Talk to your stylist")
            }
            Hairline()
        }
    }
    @ViewBuilder
    private func twoUpItem(title: String, subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(Serif.display(18)).foregroundStyle(Palette.ink)
            Text(subtitle).font(Serif.body(12)).foregroundStyle(Palette.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 18)
        .padding(.horizontal, 4)
    }
}
