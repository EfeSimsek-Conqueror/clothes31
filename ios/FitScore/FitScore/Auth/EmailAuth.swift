import Foundation
import Supabase

/// Magic-link email sign-in. Supabase sends the user an OTP + deep link; the
/// PKCE flow completes when the app is re-opened via the `com.fitrater.app`
/// URL scheme registered in Info.plist and handled by `FitraterApp`.
enum EmailAuth {
    /// Send a magic link to the given email address. Throws on network /
    /// server failure. UI should present a "check your inbox" state on success.
    static func sendMagicLink(to email: String) async throws {
        try await Supa.client.auth.signInWithOTP(
            email: email,
            redirectTo: Supa.authCallbackURL
        )
    }
}
