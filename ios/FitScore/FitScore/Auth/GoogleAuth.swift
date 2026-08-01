import Foundation
import UIKit
import AuthenticationServices
import GoogleSignIn
import Supabase

final class PresentationAnchor: NSObject, ASWebAuthenticationPresentationContextProviding {
    static let shared = PresentationAnchor()
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first(where: { $0.isKeyWindow }) ?? ASPresentationAnchor()
    }
}

/// Google Sign-In → Supabase id-token bridge. We use the Web (server) client
/// id so Supabase can verify the token issuer; the iOS client id lives in
/// GoogleSignIn's own config plist (or is derived from the reversed web id).
@MainActor
enum GoogleAuth {
    /// Call once from `App.init()` — configures the GID singleton with the
    /// server client id. The iOS-side client id (reversed) is auto-derived by
    /// GoogleSignIn from the URL scheme in Info.plist.
    static func configure() {
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(
            clientID: Supa.googleIOSClientId
        )
    }

    /// Present the Google sign-in sheet and, on success, exchange the id token
    /// with Supabase. GoogleSignIn 7 (iOS) doesn't surface a nonce parameter,
    /// so Supabase relies on `audience` validation (the web client id set on
    /// `GIDConfiguration`) plus PKCE inside the sign-in library itself.
    /// Web-based OAuth via Supabase — opens Google in an ASWebAuthenticationSession,
    /// callback returns via https://<project>.supabase.co/auth/v1/callback →
    /// Supabase redirects to our custom scheme with the session.
    /// Avoids the id_token+nonce mismatch that plagues GoogleSignIn 7 iOS.
    static func signIn(presenting: UIViewController) async throws {
        try await Supa.client.auth.signInWithOAuth(
            provider: .google,
            redirectTo: Supa.authCallbackURL,
            launchFlow: { url in
                try await withCheckedThrowingContinuation { cont in
                    let session = ASWebAuthenticationSession(
                        url: url,
                        callbackURLScheme: Supa.authCallbackURL.scheme
                    ) { callback, error in
                        if let error {
                            cont.resume(throwing: error); return
                        }
                        guard let callback else {
                            cont.resume(throwing: AuthError.invalidState); return
                        }
                        cont.resume(returning: callback)
                    }
                    session.presentationContextProvider = PresentationAnchor.shared
                    session.prefersEphemeralWebBrowserSession = false
                    session.start()
                }
            }
        )
    }

    private static func decodeJWTNonce(_ token: String) -> String? {
        let parts = token.split(separator: ".")
        guard parts.count == 3 else { return nil }
        var payload = String(parts[1]).replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        while payload.count % 4 != 0 { payload += "=" }
        guard let data = Data(base64Encoded: payload),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        return json["nonce"] as? String
    }

    /// URL handler for the Google OAuth callback. Return `true` if handled.
    static func handle(url: URL) -> Bool {
        GIDSignIn.sharedInstance.handle(url)
    }
}
