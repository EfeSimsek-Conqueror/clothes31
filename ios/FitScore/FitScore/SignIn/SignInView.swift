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
    @State private var password = ""
    @State private var passwordSubmitting = false
    @State private var showPasswordField = false
    @State private var legalURL: URL?

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
                    onRequest: { request in
                        let nonce = AuthNonce.random()
                        apple.setNonce(nonce)
                        request.requestedScopes = [.fullName, .email]
                        request.nonce = AuthNonce.sha256(nonce)
                    },
                    onCompletion: { result in
                        Task {
                            do { try await apple.handle(result: result) }
                            catch AuthError.cancelled { }
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

                    // Password sign-in for demo accounts (App Store review) and
                    // any user who set a password. Collapsed by default so the
                    // primary magic-link path stays uncluttered.
                    DisclosureGroup("Have a password?", isExpanded: $showPasswordField) {
                        VStack(alignment: .leading, spacing: 10) {
                            SecureField("Password", text: $password)
                                .textContentType(.password)
                                .padding(14)
                                .background(Palette.card)
                                .overlay(RoundedRectangle(cornerRadius: 4).stroke(Palette.hairline, lineWidth: 1))
                                .clipShape(RoundedRectangle(cornerRadius: 4))
                            PrimaryButton(
                                title: passwordSubmitting ? "Signing in…" : "Sign In",
                                enabled: !passwordSubmitting && isEmailValid(email) && password.count >= 6
                            ) {
                                signInWithPassword()
                            }
                        }
                        .padding(.top, 10)
                    }
                    .font(Serif.body(14))
                    .foregroundStyle(Palette.muted)
                }

                legalFooter
            }
            .padding(.bottom, 32)
        }
        .padding(.horizontal, 24)
        .sheet(item: Binding(get: { legalURL.map { LegalURL(url: $0) } }, set: { _ in legalURL = nil })) { holder in
            SafariView(url: holder.url)
        }
    }

    /// Compact legal footer. Sign-in is the last screen before body calibration
    /// uploads a full-body photo to a third-party model, so Terms + Privacy have
    /// to be one tap away here — not buried in You → Help.
    private var legalFooter: some View {
        VStack(spacing: 6) {
            Text("By continuing you agree to our")
                .font(Serif.body(12))
                .foregroundStyle(Palette.muted)
            HStack(spacing: 16) {
                Button("Terms of Use") { legalURL = URL(string: "https://fitrater.ai/terms") }
                Button("Privacy Policy") { legalURL = URL(string: "https://fitrater.ai/privacy") }
            }
            .buttonStyle(.plain)
            .font(Serif.body(12, weight: .semibold))
            .underline()
            .foregroundStyle(Palette.ink)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 4)
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
                let msg = (error as? LocalizedError)?.errorDescription
                          ?? String(String(describing: error).prefix(140))
                toasts.post("Magic link failed: \(msg)")
            }
        }
    }

    private func signInWithPassword() {
        passwordSubmitting = true
        Task {
            defer { passwordSubmitting = false }
            do {
                try await EmailAuth.signInWithPassword(email: email, password: password)
                // SessionStore's authStateChanges listener will advance the stage.
            } catch {
                let msg = (error as? LocalizedError)?.errorDescription
                          ?? String(String(describing: error).prefix(140))
                toasts.post("Sign-in failed: \(msg)")
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

/// Sheet identity for the legal links above. `SafariView` (HelpPrivacySheet.swift)
/// is the app-wide SFSafariViewController wrapper.
private struct LegalURL: Identifiable { let id = UUID(); let url: URL }
