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
                    // 2) Supabase magic-link / OAuth callback — exchange for a session.
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
}
