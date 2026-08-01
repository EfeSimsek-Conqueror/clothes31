import Foundation
import SwiftUI
import Supabase

/// App-wide auth + navigation state. Owns the Supabase auth-state listener,
/// eagerly grants signup credits + loads the profile on sign-in, and drives
/// the top-level `RootView` switch between splash / sign-in / onboarding /
/// home.
@MainActor
final class SessionStore: ObservableObject {
    enum Stage: Equatable {
        case splash, signIn, onboarding1, onboarding2, calibration, home
    }

    @Published var stage: Stage = .splash
    @Published var profile: Profile?
    @Published var bodyProfile: BodyProfile?
    @Published var isPro: Bool = false

    // Onboarding transient state — set by the two onboarding views, consumed
    // on completion when we upsert the profile.
    @Published var gender: Gender?
    @Published var pickedChips: Set<String> = []

    private var authTask: Task<Void, Never>?

    /// User-visible display name. Prefers the profile's display_name, falls
    /// back to the email local-part, then to "there" so the greeting never
    /// reads awkwardly.
    var displayName: String {
        if let n = profile?.display_name, !n.isEmpty { return n }
        if let e = profile?.email ?? Repo.shared.userEmail, !e.isEmpty {
            return String(e.split(separator: "@").first ?? "there")
        }
        return "there"
    }

    /// Kick off the auth-state listener. Called once from `FitraterApp.task`.
    func start() async {
        // Cancel any previous listener (hot-reload safety).
        authTask?.cancel()
        authTask = Task { [weak self] in
            guard let self else { return }
            for await (event, session) in Supa.client.auth.authStateChanges {
                if Task.isCancelled { break }
                await self.handleAuthChange(event, session: session)
            }
        }
    }

    private func handleAuthChange(_ event: AuthChangeEvent, session: Session?) async {
        switch event {
        case .initialSession, .signedIn, .tokenRefreshed, .userUpdated:
            if let uid = session?.user.id.uuidString.lowercased() {
                // Grant signup credits if this is a brand-new user — no-op
                // otherwise (idempotent check on credit_transactions).
                try? await Repo.shared.grantSignupCreditsIfEmpty()
                self.profile = try? await Repo.shared.currentProfile()
                self.bodyProfile = try? await Repo.shared.currentBodyProfile()
                self.isPro = profile?.is_pro ?? false
                await CreditsBus.shared.refresh()
                // Wire RevenueCat to this Supabase user id — safe to call
                // repeatedly; internal `logIn` no-ops when unchanged.
                RcBilling.shared.configure(userId: uid)
                // Feed the server-granted Pro flag into RcBilling so paywall
                // gates (try-on, brutal, letter) unlock without an IAP.
                RcBilling.shared.setServerPro(profile?.is_pro == true)
                let onboarded = profile?.onboarded ?? false
                withAnimation(.easeInOut(duration: 0.25)) {
                    if !onboarded {
                        stage = .onboarding1
                    } else {
                        // Onboarded users still owe us a body calibration —
                        // gate goes on Home once but skips forever after.
                        stage = .home
                    }
                }
            } else if event == .initialSession {
                // Cold start with no persisted session.
                withAnimation(.easeInOut(duration: 0.25)) { stage = .signIn }
            }
        case .signedOut:
            profile = nil
            bodyProfile = nil
            isPro = false
            CreditsBus.shared.clear()
            RcBilling.shared.configure(userId: nil)
            RcBilling.shared.setServerPro(false)
            withAnimation(.easeInOut(duration: 0.25)) { stage = .signIn }
        default:
            break
        }
    }

    /// Explicit stage advance used by splash / onboarding buttons.
    func advance(to s: Stage) {
        withAnimation(.easeInOut(duration: 0.25)) { stage = s }
    }

    // MARK: - Home header helpers

    /// "Morning, Efe." / "Evening, Efe." — device-local hour.
    func greeting() -> String {
        let h = Calendar.current.component(.hour, from: Date())
        let part: String
        switch h {
        case 5..<12:  part = "Morning"
        case 12..<17: part = "Afternoon"
        case 17..<22: part = "Evening"
        default:      part = "Night"
        }
        return "\(part), \(displayName)."
    }

    /// Eyebrow line above the greeting on Home — e.g. "THURSDAY · 24 JULY".
    func todayEyebrow() -> String {
        let f = DateFormatter()
        f.dateFormat = "EEEE · d MMMM"
        return f.string(from: Date()).uppercased()
    }
}
