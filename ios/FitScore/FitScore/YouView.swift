import SwiftUI

struct YouView: View {
    @EnvironmentObject var session: SessionStore
    @Binding var showPaywall: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                Text("You")
                    .font(Serif.display(40))
                    .foregroundStyle(Palette.ink)

                HStack(spacing: 16) {
                    Circle().fill(Palette.bronze.opacity(0.25))
                        .frame(width: 64, height: 64)
                        .overlay(Text(String(session.username.prefix(1))).font(Serif.display(26)).foregroundStyle(Palette.ink))
                    VStack(alignment: .leading, spacing: 4) {
                        Text(session.username).font(Serif.display(22)).foregroundStyle(Palette.ink)
                        Text("Since July 2026").font(Serif.body(13)).foregroundStyle(Palette.muted)
                    }
                    Spacer()
                }

                Button { showPaywall = true } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Eyebrow(text: "FITSCORE PRO", color: .white)
                            Text("Every feature. Nothing held back.")
                                .font(Serif.italic(15)).foregroundStyle(.white)
                        }
                        Spacer()
                        Text("→").foregroundStyle(.white)
                    }
                    .padding(18)
                    .background(LinearGradient.gold)
                    .clipShape(RoundedRectangle(cornerRadius: 4))
                }

                VStack(spacing: 0) {
                    Hairline()
                    SectionRow(title: "Style profile", subtitle: "Vibe, palette, silhouette", trailing: "→"); Hairline()
                    SectionRow(title: "Notifications", subtitle: "Daily nudge · 8:00", trailing: "→"); Hairline()
                    SectionRow(title: "Credits", subtitle: "\(session.credits) remaining", trailing: "→"); Hairline()
                    SectionRow(title: "Sign out", trailing: "→"); Hairline()
                }
            }
            .padding(22)
        }
        .background(Palette.paper.ignoresSafeArea())
    }
}
