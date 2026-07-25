import SwiftUI

struct SplashView: View {
    @EnvironmentObject var session: SessionStore
    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()
            VStack {
                Spacer()
                VStack(alignment: .leading, spacing: 22) {
                    Eyebrow(text: "FITSCORE")
                    Text("Your closet,\nfinally honest.")
                        .font(Serif.display(44, weight: .regular))
                        .foregroundStyle(Palette.ink)
                        .lineSpacing(2)
                    Text("Score your looks, try pieces on in the mirror, and let Hem keep the journal.")
                        .font(Serif.body(16))
                        .foregroundStyle(Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                    PrimaryButton(title: "Continue") { session.advance(to: .signIn) }
                        .padding(.top, 6)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
                .padding(.bottom, 44)
            }
        }
    }
}
