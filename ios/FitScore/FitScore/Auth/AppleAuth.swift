import Foundation
import AuthenticationServices
import UIKit
import Supabase

/// Sign in with Apple → Supabase id-token bridge. This is the App Store's
/// preferred / required OAuth flow when the app offers third-party sign-in,
/// so it lives as first-class citizen alongside Google.
///
/// The SwiftUI `SignInWithAppleButton` owns the `ASAuthorizationController`
/// lifecycle (presentation + delegate) — this class only vends the nonce and
/// completes the Supabase exchange from the credential the button returns.
@MainActor
final class AppleAuth: ObservableObject {
    /// Published error surface — the sign-in view can bind to this and show a
    /// toast without needing to manage the delegate handshake.
    @Published var lastError: String?

    private var currentNonce: String?

    /// Store the raw nonce the SwiftUI button's `onRequest` closure generated
    /// so we can pass it to Supabase alongside the returned identity token.
    func setNonce(_ nonce: String) {
        currentNonce = nonce
    }

    /// Complete the Supabase exchange from the result the SwiftUI
    /// `SignInWithAppleButton` handed us. Throws `AuthError.cancelled` when
    /// the user tapped Cancel so callers can silently ignore it.
    func handle(result: Result<ASAuthorization, Error>) async throws {
        switch result {
        case .failure(let error):
            if let ae = error as? ASAuthorizationError, ae.code == .canceled {
                throw AuthError.cancelled
            }
            throw error

        case .success(let authorization):
            guard
                let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
                let identityTokenData = credential.identityToken,
                let idToken = String(data: identityTokenData, encoding: .utf8),
                let nonce = currentNonce
            else {
                throw AuthError.noIdToken
            }
            try await Supa.client.auth.signInWithIdToken(
                credentials: OpenIDConnectCredentials(
                    provider: .apple,
                    idToken: idToken,
                    nonce: nonce
                )
            )
        }
    }
}
