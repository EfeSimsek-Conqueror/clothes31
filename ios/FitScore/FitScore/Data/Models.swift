import Foundation
import SwiftUI

// MARK: - Profile

/// Row in `public.profiles`. Every field is optional because we may only
/// select a subset for a given screen — mirroring the Android tolerant model.
struct Profile: Codable, Hashable, Sendable {
    var id: String?
    var display_name: String?
    var email: String?
    var gender: String?
    var style_tags: [String]?
    var honesty: String?
    var onboarded: Bool?
    var avatar_url: String?
    var is_pro: Bool?
    var updated_at: String?
    var first_run_done: Bool?
    var preferred_fabrics: [String]?
    var preferred_colors: [String]?
    var sensitivities: String?
    var rated_ok: Bool?
    var theme: String?
    var text_scale: Double?
    var reduce_motion: Bool?
    var paywall_shown: Bool?
}

/// Upsert payload — `id` is required (the auth uid), everything else optional
/// so screens can write a partial patch without stomping fields they don't own.
struct ProfileUpsert: Codable, Hashable, Sendable {
    var id: String
    var email: String?
    var display_name: String?
    var avatar_url: String?
    var gender: String?
    var style_tags: [String]?
    var honesty: String?
    var onboarded: Bool?
    var preferred_fabrics: [String]?
    var preferred_colors: [String]?
    var sensitivities: String?
}

// MARK: - Outfit + Annotation + Subscores

struct Annotation: Codable, Hashable, Sendable {
    var x_pct: Double = 50.0
    var y_pct: Double = 50.0
    var label: String = ""
    var score: Double = 0.0
    var note: String = ""
}

/// Markup annotation (Sprint 2) — separate shape from piece-scoring `Annotation`.
/// Type ∈ {"arrow","line","focus","swap"}. Coords normalized [0,1] with origin top-left.
///   - arrow/line: from + to
///   - focus:      at + radius
///   - swap:       at
struct MarkupAnnotation: Codable, Hashable, Sendable {
    var type: String
    var coords: MarkupCoords
    var note: String
    var confidence: Double?
}

struct MarkupCoords: Codable, Hashable, Sendable {
    var from: [Double]?    // [x, y]
    var to: [Double]?      // [x, y]
    var at: [Double]?      // [x, y]
    var radius: Double?
}

/// Fit-tension heatmap from `score-outfit` v3. Grid is rows × cols with values
/// in [-1, 1] (-1 pooling/loose, +1 tight/pulling). Resolution is [cols, rows].
struct FitMap: Codable, Hashable, Sendable {
    var resolution: [Int]?          // [cols, rows]
    var grid: [[Double]]?           // rows[cols]
    var hotspots: [FitHotspot]?
}

struct FitHotspot: Codable, Hashable, Sendable {
    var at: [Double]                // [x, y] normalized
    var label: String
    var severity: Double            // [-1, 1]
}

/// Response from `transcribe-intent` — Fal wizper output.
struct TranscribeResponse: Codable, Hashable, Sendable {
    var transcript: String?
    var sanitized: String?
    var engine: String?
    var error: String?
}

/// Convenience payload for the Studio intent box — bundles the sanitized
/// transcript with the raw audio path for later replay/edit.
struct IntentPayload: Codable, Hashable, Sendable {
    var text: String
    var audio_path: String?
}

struct Outfit: Codable, Hashable, Sendable, Identifiable {
    var id: String?
    var user_id: String?
    var name: String?
    var score: Double?
    var notes: String?
    var image_url: String?
    var photo_path: String?
    var verdict: String?
    var occasion: String?
    var weather_c: Int?
    var hem_comment: String?
    var dominant_colors: [String]?
    var subscores: Subscores?
    var swaps: [String]?
    var annotations: [Annotation]?
    var created_at: String?
    var kind: String?
    var linked_piece_id: String?
}

struct OutfitInsert: Codable, Hashable, Sendable {
    var user_id: String
    var photo_path: String
    var score: Double
    var occasion: String
    var hem_comment: String
    var weather_c: Int?
    var verdict: String?
    var subscores: Subscores?
    var swaps: [String]?
    var annotations: [Annotation]?
    var kind: String?
    var linked_piece_id: String?
}

/// Tolerant codable for the outfits.subscores jsonb column. Accepts either:
///   - New flat shape:   `{"fit": 7, "color": 6, "style_match": 8, "seasonal": 7}`
///   - Legacy nested:    `{"fit": {"score": 7, "why": "..."}, "style": {...}, "season": {...}}`
///
/// Also accepts alternate keys `style` (→ style_match) and `season` (→ seasonal),
/// which some early rows on prod still use. Writes only the new flat shape.
struct Subscores: Codable, Hashable, Sendable {
    var color: Double?
    var fit: Double?
    var style_match: Double?
    var seasonal: Double?

    init(color: Double? = nil, fit: Double? = nil, style_match: Double? = nil, seasonal: Double? = nil) {
        self.color = color
        self.fit = fit
        self.style_match = style_match
        self.seasonal = seasonal
    }

    private struct ScoreObject: Decodable {
        let score: Double?
    }

    /// Read a numeric field that may be either a raw number or an object with
    /// a `score` key. Silently skips unknown shapes rather than throwing —
    /// legacy rows should never crash the app.
    private static func read(_ container: KeyedDecodingContainer<CodingKeys>, keys: [CodingKeys]) -> Double? {
        for key in keys {
            if let n = try? container.decode(Double.self, forKey: key) { return n }
            if let obj = try? container.decode(ScoreObject.self, forKey: key), let s = obj.score { return s }
        }
        return nil
    }

    enum CodingKeys: String, CodingKey {
        case color, fit, style_match, seasonal, style, season
    }

    init(from decoder: Decoder) throws {
        // Empty object / null → all-nil Subscores rather than throwing.
        guard let c = try? decoder.container(keyedBy: CodingKeys.self) else {
            self.init()
            return
        }
        self.init(
            color: Self.read(c, keys: [.color]),
            fit: Self.read(c, keys: [.fit]),
            style_match: Self.read(c, keys: [.style_match, .style]),
            seasonal: Self.read(c, keys: [.seasonal, .season])
        )
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encodeIfPresent(color, forKey: .color)
        try c.encodeIfPresent(fit, forKey: .fit)
        try c.encodeIfPresent(style_match, forKey: .style_match)
        try c.encodeIfPresent(seasonal, forKey: .seasonal)
    }
}

// MARK: - Closet

struct ClosetItem: Codable, Hashable, Sendable, Identifiable {
    var id: String?
    var user_id: String?
    var category: String?
    var subcategory: String?
    var name: String?
    var image_url: String?
    var image_path: String?
    var color: String?
    var color_hex: String?
    var worn_count: Int?
    var parent_id: String?
    var source: String?
    var created_at: String?
}

struct ClosetItemInsert: Codable, Hashable, Sendable {
    var user_id: String
    var name: String
    var category: String
    var subcategory: String?
    var image_path: String?
    var color_hex: String?
    var parent_id: String?
    var source: String?
}

// MARK: - Credits

struct CreditPack: Codable, Hashable, Sendable, Identifiable {
    var id: String
    var name: String?
    var credits: Int
    var price_cents: Int
    var sort: Int?
}

struct CreditTransaction: Codable, Hashable, Sendable, Identifiable {
    var id: String?
    var user_id: String?
    var amount: Int?
    var kind: String?
    var balance_after: Int?
    var reference_id: String?
    var created_at: String?
}

/// Insert payload for `credit_transactions`. `balance_after` is REQUIRED
/// (server column is NOT NULL) — an early Android bug caused silent spend
/// failures when this was omitted; we enforce it in the type here.
struct CreditTxInsert: Codable, Hashable, Sendable {
    var user_id: String
    var amount: Int
    var kind: String
    var balance_after: Int
    var reference_id: String?
}

// MARK: - Sunday letters & Hem notes

struct SundayLetter: Codable, Hashable, Sendable, Identifiable {
    var id: String?
    var user_id: String?
    var week_start: String?
    var week_end: String?
    var body: String?
    var variant: String?
    var created_at: String?
}

struct HemNote: Codable, Hashable, Sendable, Identifiable {
    var id: String?
    var user_id: String?
    var body: String?
    var created_at: String?
}

// MARK: - Push settings

struct PushSettings: Codable, Hashable, Sendable {
    var user_id: String?
    var enabled: Bool?
    var morning_stylist: Bool?
    var weekly_task: Bool?
    var wrapped: Bool?
}

struct PushSettingsUpsert: Codable, Hashable, Sendable {
    var user_id: String
    var morning_stylist: Bool
    var weekly_task: Bool
    var wrapped: Bool
}

// MARK: - Local (non-DB) models

enum Gender: String, CaseIterable, Identifiable, Sendable {
    case female = "Female", male = "Male", other = "Other"
    var id: String { rawValue }
    var caption: String {
        switch self {
        case .female: return "Feminine silhouettes and palettes."
        case .male:   return "Masculine silhouettes and palettes."
        case .other:  return "Show me everything."
        }
    }
}

/// Response payload from the `generate-piece` / `tryon-outfit` edge functions.
struct GenerateResponse: Codable, Hashable, Sendable {
    var image_url: String?
    var seed: Int64?
    var error: String?
}

/// One-time body calibration output from the `analyze-body` edge function.
/// Every field is optional — the model may fail to classify one axis without
/// invalidating the rest. Callers should treat `nil` as "unknown".
struct BodyProfile: Codable, Hashable, Sendable {
    var body_shape: String?          // "triangle" | "inverted_triangle" | "hourglass" | "rectangle" | "apple"
    var shoulder_hip_ratio: Double?
    var torso_leg_ratio: Double?
    var skin_undertone: String?      // "warm" | "cool" | "neutral"
    var coloring_season: String?     // "spring" | "summer" | "autumn" | "winter"
    var palette_hex: [String]?       // up to 6 hex codes, "#RRGGBB"
    var notes: String?
}

/// Envelope returned by `analyze-body`. `error` is populated on failure.
struct BodyProfileResponse: Codable, Hashable, Sendable {
    var profile: BodyProfile?
    var engine: String?
    var error: String?
}

// MARK: - Magazine Cover (Sprint 3)

/// Row in `public.magazine_covers`. All fields optional so tolerant decode
/// never breaks when the schema grows.
struct MagazineCover: Codable, Hashable, Sendable, Identifiable {
    var id: String?
    var outfit_id: String?
    var user_id: String?
    var headline: String?
    var pull_quote: String?
    var vol_number: Int?
    var image_path: String?
    var created_at: String?
}

/// Response from the `compose-cover` edge function.
struct ComposeCoverResponse: Codable, Hashable, Sendable {
    var cover_url: String?
    var headline: String?
    var pull_quote: String?
    var vol_number: Int?
    var cover_id: String?
    var error: String?
}

/// Response from the `regenerate-cover-headline` edge function.
struct HeadlineResponse: Codable, Hashable, Sendable {
    var headline: String?
    var pull_quote: String?
    var error: String?
}

/// Composite stats block for the You sheet. Matches Android `Stats` data class.
struct Stats: Hashable, Sendable {
    var pieces: Int
    var looks: Int
    var bestScore: Double?
    var creditsUsed: Int
    var streakDays: Int
    var monthDelta: Double?
}

/// Union used by the Home "Latest" card — combines outfits and closet pieces.
enum LatestActivity: Hashable, Sendable {
    case outfit(Outfit, signedUrl: String?)
    case piece(ClosetItem, signedUrl: String?)
}

// MARK: - Sprint 5: Invitation decoder + outfit suggestions

/// Structured event fields decoded from an invitation photo by the
/// `decode-invitation` edge function. All fields optional — the model may
/// fail to read one axis without invalidating the rest.
struct InvitationDecoded: Codable, Hashable, Sendable {
    var event_type: String?   // "wedding"|"cocktail"|"dinner"|"gallery"|"work"|"casual"|"other"
    var dress_code: String?   // "black_tie"|"cocktail"|"smart_casual"|"casual"|"business"|"black_tie_optional"|"creative_black_tie"|"business_casual"
    var time_of_day: String?  // "morning"|"afternoon"|"evening"|"night"
    var venue: String?
    var date_text: String?
    var notes: String?
}

/// Envelope returned by `decode-invitation`. `error` populated on failure.
struct DecodeInvitationResponse: Codable, Hashable, Sendable {
    var decoded: InvitationDecoded?
    var engine: String?
    var error: String?
}

/// One outfit suggestion from `suggest-outfits`. `piece_ids` reference the
/// closet items posted in the request. `gap` is non-null if the closet is
/// missing a critical piece for the requested dress code.
struct OutfitCombo: Codable, Hashable, Sendable {
    var score: Int?
    var piece_ids: [String]?
    var rationale: String?
    var gap: String?
}

/// Envelope returned by `suggest-outfits`. `error` populated on failure.
struct SuggestOutfitsResponse: Codable, Hashable, Sendable {
    var combos: [OutfitCombo]?
    var engine: String?
    var error: String?
}

/// Row in `public.invitation_reads`. All fields optional for tolerant decode.
struct InvitationRead: Codable, Hashable, Sendable, Identifiable {
    var id: String?
    var user_id: String?
    var image_path: String?
    var event_type: String?
    var dress_code: String?
    var time_of_day: String?
    var venue: String?
    var notes: String?
    var suggested_combos: [OutfitCombo]?
    var created_at: String?
}

/// Small brochure used in the Score sheet — kept from the original scaffold so
/// subsequent agents can plug real data into the same shape.
struct ScorePiece: Hashable, Sendable {
    var name: String
    var score: Double
    var note: String
}

struct ScoreResult: Hashable, Sendable {
    var score: Double
    var deltaVsAvg: Double
    var occasion: String
    var pullQuote: String
    var pieces: [ScorePiece]
}

// MARK: - Mock seeds (temporary — used by placeholder views until Home/Studio
// agents wire in real Repo data). Keep names stable so the scaffolding
// compiles.
enum Seeds {
    struct StyleChip: Identifiable, Hashable, Sendable {
        let id = UUID()
        var label: String
    }

    static let styleChips: [StyleChip] = [
        "minimal", "streetwear", "classic", "romantic", "sporty",
        "bold prints", "earth tones", "monochrome"
    ].map { .init(label: $0) }
}
