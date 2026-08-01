import Foundation
import CryptoKit

/// PKCE-style nonce helpers shared by Apple + Google sign-in. Both providers
/// require sending the SHA-256 hash of a nonce; the raw nonce is then passed
/// back to Supabase's `signInWithIdToken` so it can verify the `nonce` claim.
enum AuthNonce {
    /// Generate a URL-safe random nonce of the given length.
    static func random(length: Int = 32) -> String {
        let chars: [Character] = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        result.reserveCapacity(length)
        var remaining = length
        while remaining > 0 {
            var randoms = [UInt8](repeating: 0, count: 16)
            _ = SecRandomCopyBytes(kSecRandomDefault, randoms.count, &randoms)
            for byte in randoms where remaining > 0 {
                let idx = Int(byte) % chars.count
                result.append(chars[idx])
                remaining -= 1
            }
        }
        return result
    }

    /// SHA-256 hash of the input, hex-encoded (lowercase). Used for the
    /// `nonce` field sent to the identity provider.
    static func sha256(_ input: String) -> String {
        let data = Data(input.utf8)
        let hash = SHA256.hash(data: data)
        return hash.map { String(format: "%02x", $0) }.joined()
    }
}

enum AuthError: Error, LocalizedError {
    case noIdToken
    case cancelled
    case invalidState

    var errorDescription: String? {
        switch self {
        case .noIdToken: return "Provider did not return an ID token."
        case .cancelled: return "Sign-in cancelled."
        case .invalidState: return "Sign-in state was invalid."
        }
    }
}
