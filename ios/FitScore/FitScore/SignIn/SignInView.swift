import SwiftUI
import AuthenticationServices
import UIKit

/// Real sign-in screen. Wires Google + Apple + email magic-link flows into
/// Supabase. All three converge on `Supa.client.auth.signInWithIdToken` (or
/// magic-link callback) which fires `authStateChanges` and the SessionStore
/// advances the stage automatically.
struct SignInView: View {
    @EnvironmentObject var session: SessionStore
    @EnvironmentObject var toasts: ToastBus

    @StateObject private var apple = AppleAuth()
    @State private var email = ""
    @State private var sending = false
    @State private var linkSent = false

    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            Spacer(minLength: 40)

            VStack(alignment: .leading, spacing: 10) {
                Eyebrow(text: "FITRATER")
                Text("Sign in.")
                    .font(Serif.display(46))
                    .foregroundStyle(Palette.ink)
                Text("Your closet and journal follow you across devices.")
                    .font(Serif.body(16))
                    .foregroundStyle(Palette.muted)
            }

            Spacer()

            VStack(spacing: 12) {
                // Apple — required by App Store when offering other providers.
                SignInWithAppleButton(.continue,
                    onRequest: { _ in },
                    onCompletion: { _ in
                        Task {
                            do { try await apple.signIn() }
                            catch AuthError.cancelled { /* silent */ }
                            catch { toasts.post("Apple sign-in failed.") }
                        }
                    }
                )
                .signInWithAppleButtonStyle(.black)
                .frame(height: 56)
                .clipShape(RoundedRectangle(cornerRadius: 4))

                SecondaryButton(title: "Continue with Google", icon: "G") {
                    Task {
                        guard let vc = Self.topViewController() else {
                            toasts.post("Couldn't open Google sign-in.")
                            return
                        }
                        do { try await GoogleAuth.signIn(presenting: vc) }
                        catch { toasts.post("Google: \(String(error.localizedDescription).prefix(120))") }
                    }
                }

                Hairline().padding(.vertical, 8)

                // Email magic-link — simplest form, single field + button.
                VStack(alignment: .leading, spacing: 10) {
                    Eyebrow(text: "OR EMAIL A MAGIC LINK")
                    TextField("you@domain.com", text: $email)
                        .textContentType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.emailAddress)
                        .padding(14)
                        .background(Palette.card)
                        .overlay(RoundedRectangle(cornerRadius: 4).stroke(Palette.hairline, lineWidth: 1))
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                    PrimaryButton(
                        title: linkSent ? "Link Sent — Check Inbox" : (sending ? "Sending…" : "Send Magic Link"),
                        enabled: !sending && isEmailValid(email)
                    ) {
                        sendLink()
                    }
                }
            }
            .padding(.bottom, 32)
        }
        .padding(.horizontal, 24)
    }

    private func sendLink() {
        sending = true
        linkSent = false
        Task {
            defer { sending = false }
            do {
                try await EmailAuth.sendMagicLink(to: email)
                linkSent = true
                toasts.post("Magic link sent to \(email).")
            } catch {
                toasts.post("Couldn't send magic link.")
            }
        }
    }

    private func isEmailValid(_ s: String) -> Bool {
        s.contains("@") && s.contains(".") && s.count >= 5
    }

    /// Best-effort top-view-controller lookup for the Google sheet.
    private static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes
        guard let window = scenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows })
            .first(where: { $0.isKeyWindow }) else { return nil }
        var vc = window.rootViewController
        while let presented = vc?.presentedViewController { vc = presented }
        return vc
    }
}
