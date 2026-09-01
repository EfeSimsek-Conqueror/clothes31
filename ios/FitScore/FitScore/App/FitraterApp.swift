import SwiftUI
import Supabase
import GoogleSignIn
#if canImport(FirebaseCore)
import FirebaseCore
#endif

@main
struct FitraterApp: App {
    @StateObject private var session = SessionStore()
    @StateObject private var credits = CreditsBus.shared
    @StateObject private var toasts = ToastBus.shared
    @StateObject private var nudges = NudgeBus.shared

    init() {
        #if canImport(FirebaseCore)
        FirebaseApp.configure()
        #endif
        // Configure Google Sign-In once at process start — cheap, must happen
        // before any sign-in button is tapped.
        GoogleAuth.configure()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(session)
                .environmentObject(credits)
                .environmentObject(toasts)
                .environmentObject(nudges)
                .preferredColorScheme(.light)
                .tint(Palette.ink)
                .task { await session.start() }
                .onOpenURL { url in
                    // 1) Google callback — GoogleSignIn owns its URLs.
                    if GoogleAuth.handle(url: url) { return }
                    // 2) Supabase magic-link / OAuth callback — exchange for a
                    //    session. Anything else (a universal link to a marketing
                    //    or legal page, a stray share URL) is not a sign-in and
                    //    must not raise a sign-in error: just open the app.
                    guard Self.isAuthCallback(url) else { return }
                    Task {
                        do {
                            try await Supa.client.auth.session(from: url)
                        } catch {
                            ToastBus.shared.post("Couldn't finish sign-in.")
                        }
                    }
                }
        }
    }

    /// True only for URLs that actually carry a Supabase auth callback.
    ///
    /// Two shapes qualify. The one the app asks for is the custom scheme in
    /// `Supa.authCallbackURL` (`com.fitrater.app://auth-callback`) — every
    /// magic link and OAuth redirect comes back on it. The https form is
    /// accepted defensively so an `applinks:fitrater.ai` `/auth/callback`
    /// link still completes sign-in; see the AASA route in
    /// `web/app/.well-known/apple-app-site-association/route.ts`.
    private static func isAuthCallback(_ url: URL) -> Bool {
        guard let scheme = url.scheme?.lowercased() else { return false }
        if scheme == Supa.authCallbackURL.scheme?.lowercased() {
            return true
        }
        if scheme == "https", url.host?.lowercased().hasSuffix("fitrater.ai") == true {
            return url.path.hasPrefix("/auth/callback")
        }
        return false
    }
}
