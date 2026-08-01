import SwiftUI

/// Editorial intro to "Hem" — the AI stylist voice. Shown once, right after
/// sign-in, before the gender/vibe steps. Placeholder — a subsequent agent
/// will decide whether to wire this in before OnboardGenderView.
struct HemIntroView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Spacer()
            HemMonogram(size: 44)
            Text("Meet Hem.")
                .font(Serif.display(40))
                .foregroundStyle(Palette.ink)
            Text("Your quiet stylist. Reads what you own, tells you what works, keeps the journal.")
                .font(Serif.body(16))
                .foregroundStyle(Palette.muted)
            Spacer()
            Text("TODO: HemIntro wiring (currently unused)")
                .font(.caption).foregroundStyle(Palette.muted)
        }
        .padding(.horizontal, 24)
    }
}
