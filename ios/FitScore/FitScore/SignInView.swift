import SwiftUI
import AuthenticationServices

struct SignInView: View {
    @EnvironmentObject var session: SessionStore

    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            Spacer(minLength: 40)
            Text("Sign in.")
                .font(Serif.display(46))
                .foregroundStyle(Palette.ink)
            Text("Your closet and journal follow you across devices.")
                .font(Serif.body(16))
                .foregroundStyle(Palette.muted)

            Spacer()

            VStack(spacing: 12) {
                SignInWithAppleButton(.continue) { _ in } onCompletion: { _ in
                    session.advance(to: .onboarding1)
                }
                .signInWithAppleButtonStyle(.black)
                .frame(height: 56)
                .clipShape(RoundedRectangle(cornerRadius: 4))

                SecondaryButton(title: "Continue with Google", icon: "G") {
                    session.advance(to: .onboarding1)
                }
                SecondaryButton(title: "Continue with email", icon: "✉︎") {
                    session.advance(to: .onboarding1)
                }
            }
            .padding(.bottom, 32)
        }
        .padding(.horizontal, 24)
    }
}
