import Foundation
import AuthenticationServices
import UIKit
import Supabase

/// Sign in with Apple → Supabase id-token bridge. This is the App Store's
/// preferred / required OAuth flow when the app offers third-party sign-in,
/// so it lives as first-class citizen alongside Google.
@MainActor
final class AppleAuth: NSObject, ObservableObject {
    /// Published error surface — the sign-in view can bind to this and show a
    /// toast without needing to manage the delegate handshake.
    @Published var lastError: String?

    private var currentNonce: String?
    private var continuation: CheckedContinuation<Void, Error>?

    /// Start the Apple sign-in flow. Returns when the user completes or cancels.
    func signIn() async throws {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
            let nonce = AuthNonce.random()
            currentNonce = nonce
            continuation = cont

            let provider = ASAuthorizationAppleIDProvider()
            let request = provider.createRequest()
            request.requestedScopes = [.fullName, .email]
            request.nonce = AuthNonce.sha256(nonce)

            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.presentationContextProvider = self
            controller.performRequests()
        }
    }
}

extension AppleAuth: ASAuthorizationControllerDelegate {
    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        guard
            let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
            let identityTokenData = credential.identityToken,
            let idToken = String(data: identityTokenData, encoding: .utf8),
            let nonce = currentNonce
        else {
            continuation?.resume(throwing: AuthError.noIdToken)
            continuation = nil
            return
        }
        let cont = continuation
        continuation = nil
        Task {
            do {
                try await Supa.client.auth.signInWithIdToken(
                    credentials: OpenIDConnectCredentials(
                        provider: .apple,
                        idToken: idToken,
                        nonce: nonce
                    )
                )
                cont?.resume()
            } catch {
                cont?.resume(throwing: error)
            }
        }
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError error: Error
    ) {
        let cont = continuation
        continuation = nil
        // The user tapping "Cancel" surfaces as an ASAuthorizationError.canceled.
        if let ae = error as? ASAuthorizationError, ae.code == .canceled {
            cont?.resume(throwing: AuthError.cancelled)
        } else {
            cont?.resume(throwing: error)
        }
    }
}

extension AppleAuth: ASAuthorizationControllerPresentationContextProviding {
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        // Grab the currently active foreground window — works on iOS 15+.
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first(where: { $0.isKeyWindow })
            ?? ASPresentationAnchor()
    }
}
