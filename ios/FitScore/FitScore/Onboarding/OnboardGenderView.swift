import SwiftUI

struct OnboardGenderView: View {
    @EnvironmentObject var session: SessionStore
    @State private var pick: Gender?

    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            Spacer(minLength: 24)
            Eyebrow(text: "STEP 1 OF 2")
            Text("Who's dressing?")
                .font(Serif.display(40))
                .foregroundStyle(Palette.ink)
            Text("We tune fits and palettes to what you actually wear.")
                .font(Serif.body(15)).foregroundStyle(Palette.muted)

            VStack(spacing: 14) {
                ForEach(Gender.allCases) { g in
                    Button { pick = g } label: { row(g) }
                }
            }
            .padding(.top, 8)

            Spacer()

            PrimaryButton(title: "Continue", enabled: pick != nil) {
                session.gender = pick
                session.advance(to: .onboarding2)
            }
            .padding(.bottom, 24)
        }
        .padding(.horizontal, 24)
    }

    @ViewBuilder
    private func row(_ g: Gender) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(g.rawValue).font(Serif.display(22)).foregroundStyle(Palette.ink)
                Text(g.caption).font(Serif.body(13)).foregroundStyle(Palette.muted)
            }
            Spacer()
            Circle()
                .strokeBorder(pick == g ? Palette.bronze : Palette.hairline, lineWidth: 1.5)
                .background(Circle().fill(pick == g ? Palette.bronze : .clear))
                .frame(width: 18, height: 18)
        }
        .padding(20)
        .frame(maxWidth: .infinity)
        .frame(minHeight: 96)
        .overlay(RoundedRectangle(cornerRadius: 4).stroke(pick == g ? Palette.ink : Palette.hairline, lineWidth: pick == g ? 1.5 : 1))
    }
}
