import Foundation
import Supabase

/// Central Supabase configuration + client singleton. Mirrors the Android
/// `Supa` object in `data/SupabaseClient.kt` — credit economics, URLs, and
/// timeouts are kept in one place so subsequent agents don't hunt for magic
/// numbers.
enum Supa {
    // MARK: - Endpoints

    static let url = URL(string: "https://ilrzqifdmjvooeyqvexd.supabase.co")!

    /// Publishable anon key (new-style). Safe to ship in the app binary — RLS
    /// enforces per-user access on the server. The legacy JWT is kept as a
    /// fallback in case the publishable format is ever revoked.
    static let anonKey = "sb_publishable_bz5nOR8qZ3jTaXJMbR4MHA_w2jaMyDS"
    static let legacyAnonJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImlscnpxaWZkbWp2b29leXF2ZXhkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODQzODAzNzUsImV4cCI6MjA5OTk1NjM3NX0.BfoEiMusmWwO4PAj7bD3aFDYNGdxCuXkqVSCySvRwvQ"

    /// Google Web (server) OAuth client ID — required for the id-token flow
    /// that Supabase accepts. NOT the iOS client id.
    static let googleWebClientId = "1062757466057-vngm8cbvrdjrra81sunt5ebi2rsf5u82.apps.googleusercontent.com"
    static let googleIOSClientId = "1062757466057-gl4jq09mvu812ufk992umhlh639vgmpr.apps.googleusercontent.com"

    /// URL that Supabase should redirect to on OAuth completion. Must match
    /// `CFBundleURLTypes` in Info.plist.
    static let authCallbackURL = URL(string: "com.fitrater.app://auth-callback")!

    /// RevenueCat iOS public API key (safe to bundle). Get from
    /// RevenueCat dashboard → Fitrater iOS app → App configuration.
    static let revenueCatIOSKey = "appl_XcRnJzmGqEityyPyHuMWRdaSWvV"

    // MARK: - Credit economics (must match Android + RevenueCat grants)

    static let signupCredits = 25
    static let scoreCost = 5
    static let generateCost = 15
    static let tryonCost = 20
    static let versusCost = 8
    static let roastCost = 5
    static let decodeCost = 10
    static let occasionCost = 15
    // Sprint 1-5 additions — set to keep 40%+ margin over Fal/Nano-banana costs.
    static let bodyCalibrationCost = 0       // one-time, foundational
    static let magazineCoverCost = 10        // nano-banana render + headline
    static let mirrorCleanerCost = 0         // bundled as loyalty perk
    static let invitationDecodeCost = 6      // OCR + combo generation
    static let coverTemplateCost = 2         // Fal flux/schnell placeholder + SVG chrome
    static let tailorTicketCost = 2          // text-only prompt after heatmap
    static let frontBackExtraCost = 2        // added on top of scoreCost for dual shot
    static let magazineCoverThreshold = 8.5  // auto-trigger cover if score ≥ this

    /// Free-trial abuse guard: annual sub in its 7-day intro period is capped
    /// to this many credits/day.
    static let trialDailyCap = 20
    /// Play/App Store rating one-shot reward.
    static let playRatingReward = 20
    /// Standard monthly sub credit cap (fair-use).
    static let subMonthlyCap = 1200
    /// Annual sub gives 3000/yr total (~250/mo) — enforced via monthly reset.
    static let subAnnualMonthlyCap = 750

    /// SKU id → credits granted mapping (client-side source of truth). Keys
    /// mirror App Store product ids configured in RevenueCat.
    static let creditPackGrants: [String: Int] = [
        "fitrater_v2_credits_100": 150,     // Starter — $1.99 → 150cr
        "fitrater_v2_credits_500": 500,     // Popular — $6.99 → 500cr
        "fitrater_v2_credits_1500": 1200,   // Pro Pack — $14.99 → 1200cr
        "fitrater_v2_credits_5000": 3000    // Mega — $39.99 → 3000cr
    ]

    // MARK: - Client

    /// Shared Supabase client. `SupabaseClient` internally persists the session
    /// to the iOS Keychain, so cold-start restoration is automatic.
    static let client: SupabaseClient = {
        SupabaseClient(
            supabaseURL: url,
            supabaseKey: anonKey,
            options: SupabaseClientOptions(
                db: .init(schema: "public"),
                auth: .init(
                    redirectToURL: authCallbackURL,
                    flowType: .pkce,
                    autoRefreshToken: true
                ),
                global: .init(
                    // Fal endpoints (nano-banana image gen, gemini vision) can
                    // take 20–40s. Default URLSession timeout is fine at 60s,
                    // but bump the resource timeout to 120s to be safe.
                    session: {
                        let cfg = URLSessionConfiguration.default
                        cfg.timeoutIntervalForRequest = 120
                        cfg.timeoutIntervalForResource = 180
                        return URLSession(configuration: cfg)
                    }()
                )
            )
        )
    }()
}
