import SwiftUI

struct PaywallView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var plan: Plan = .annual
    enum Plan { case annual, monthly }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                HStack {
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .foregroundStyle(Palette.ink)
                            .font(.system(size: 16, weight: .semibold))
                    }
                }
                Eyebrow(text: "FITSCORE PRO")
                Text("Every feature.\nNothing held back.")
                    .font(Serif.display(38))
                    .foregroundStyle(Palette.ink)

                VStack(alignment: .leading, spacing: 14) {
                    romanRow("I", "Unlimited look scoring with Hem's notes")
                    romanRow("II", "The Mirror — try any piece on your photo")
                    romanRow("III", "Closet auto-tagging & the Sunday letter")
                }
                .padding(.top, 6)

                planCard(
                    plan: .annual,
                    title: "Annual",
                    price: "7 days free, then $59.99 / year",
                    badge: "SAVE 50%"
                )
                planCard(
                    plan: .monthly,
                    title: "Monthly",
                    price: "$9.99 / month",
                    badge: nil
                )

                PrimaryButton(title: "Start 7-Day Free Trial") { dismiss() }
                    .padding(.top, 6)

                Button("Restore purchases") { }
                    .font(Serif.body(13))
                    .foregroundStyle(Palette.muted)
                    .frame(maxWidth: .infinity)
                    .padding(.bottom, 24)
            }
            .padding(24)
        }
        .background(Palette.paper.ignoresSafeArea())
    }

    @ViewBuilder
    private func romanRow(_ numeral: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Text(numeral)
                .font(Serif.display(18))
                .foregroundStyle(Palette.bronze)
                .frame(width: 28, alignment: .leading)
            Text(text)
                .font(Serif.body(16))
                .foregroundStyle(Palette.ink)
        }
    }

    @ViewBuilder
    private func planCard(plan p: Plan, title: String, price: String, badge: String?) -> some View {
        Button {
            plan = p
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text(title).font(Serif.display(20)).foregroundStyle(Palette.ink)
                        if let b = badge {
                            Text(b)
                                .font(.system(size: 10, weight: .bold))
                                .tracking(1.5)
                                .foregroundStyle(.white)
                                .padding(.horizontal, 8).padding(.vertical, 4)
                                .background(LinearGradient.gold)
                                .clipShape(Capsule())
                        }
                    }
                    Text(price).font(Serif.body(14)).foregroundStyle(Palette.muted)
                }
                Spacer()
                Circle()
                    .strokeBorder(plan == p ? Palette.bronze : Palette.hairline, lineWidth: 1.5)
                    .background(Circle().fill(plan == p ? Palette.bronze : .clear))
                    .frame(width: 18, height: 18)
            }
            .padding(18)
            .overlay(
                RoundedRectangle(cornerRadius: 4)
                    .stroke(plan == p ? Palette.ink : Palette.hairline, lineWidth: plan == p ? 1.5 : 1)
            )
        }
    }
}
