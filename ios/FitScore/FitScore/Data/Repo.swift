import Foundation
import Supabase

/// Singleton data-access facade. Ports Android's `Repo` object 1:1 — same
/// method names, same behavior, same tolerant decode fallbacks. Screens
/// should never talk to `Supa.client` directly; go through `Repo.shared`.
///
/// Everything is `async throws`. Callers wrap in `Task {}` and surface errors
/// through `ToastBus` or a local `@State var error: Error?`.
@MainActor
final class Repo {
    static let shared = Repo()
    private init() {}

    // MARK: - Client accessors (small aliases keep the call sites clean)
    private var auth: AuthClient { Supa.client.auth }
    private var storage: SupabaseStorageClient { Supa.client.storage }
    private var functions: FunctionsClient { Supa.client.functions }

    /// Row-tolerant JSON decoder used for "row-by-row" outfit decoding — one
    /// malformed row (legacy shape, missing column, etc.) must NOT nuke the
    /// entire list. Matches the Android `decodeOutfitsSafe` behavior.
    private let tolerantDecoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        return d
    }()

    // MARK: - Current user

    var userId: String? { auth.currentUser?.id.uuidString.lowercased() }
    var userEmail: String? { auth.currentUser?.email }

    // MARK: - Profile

    func currentProfile() async throws -> Profile? {
        guard let uid = userId else { return nil }
        let rows: [Profile] = try await Supa.client
            .from("profiles")
            .select()
            .eq("id", value: uid)
            .limit(1)
            .execute()
            .value
        return rows.first
    }

    func upsertProfile(_ p: ProfileUpsert) async throws {
        try await Supa.client
            .from("profiles")
            .upsert(p, onConflict: "id")
            .execute()
    }

    func markFirstRunDone() async throws {
        guard let uid = userId else { return }
        struct Patch: Encodable { let first_run_done: Bool }
        try? await Supa.client
            .from("profiles")
            .update(Patch(first_run_done: true))
            .eq("id", value: uid)
            .execute()
    }

    func closetItemCount() async throws -> Int {
        guard let uid = userId else { return 0 }
        struct IdRow: Decodable { let id: String? }
        let rows: [IdRow] = (try? await Supa.client
            .from("closet_items")
            .select("id")
            .eq("user_id", value: uid)
            .execute()
            .value) ?? []
        return rows.count
    }

    func updateHonesty(_ value: String) async throws {
        guard let uid = userId else { return }
        struct Patch: Encodable { let honesty: String }
        try await Supa.client
            .from("profiles")
            .update(Patch(honesty: value))
            .eq("id", value: uid)
            .execute()
    }

    // MARK: - Outfits (tolerant decode)

    /// Fetch outfits as raw `Data`, then attempt row-by-row decode. Failed
    /// rows are logged and skipped — see Android `decodeOutfitsSafe`.
    private func decodeOutfitsSafe(_ data: Data) -> [Outfit] {
        guard let arr = try? JSONSerialization.jsonObject(with: data) as? [Any] else {
            return []
        }
        var out: [Outfit] = []
        out.reserveCapacity(arr.count)
        for el in arr {
            guard let rowData = try? JSONSerialization.data(withJSONObject: el, options: []) else { continue }
            if let outfit = try? tolerantDecoder.decode(Outfit.self, from: rowData) {
                out.append(outfit)
            } else {
                // Legacy row we can't decode — silently skip; matches Android.
                #if DEBUG
                print("Repo: skipping outfit row (legacy shape)")
                #endif
            }
        }
        return out
    }

    func latestOutfit() async throws -> Outfit? {
        guard let uid = userId else { return nil }
        let resp = try await Supa.client
            .from("outfits")
            .select()
            .eq("user_id", value: uid)
            .neq("kind", value: "occasion_plan")
            .order("created_at", ascending: false)
            .limit(1)
            .execute()
        return decodeOutfitsSafe(resp.data).first
    }

    /// Return whatever the user last created — outfit or Studio piece — whichever
    /// has the newer `created_at`. Signed URL is resolved for both.
    func latestActivity() async throws -> LatestActivity? {
        let outfit = try? await latestOutfit()
        var piece: ClosetItem? = nil
        if let uid = userId {
            let rows: [ClosetItem] = (try? await Supa.client
                .from("closet_items")
                .select()
                .eq("user_id", value: uid)
                .order("created_at", ascending: false)
                .limit(1)
                .execute()
                .value) ?? []
            piece = rows.first
        }
        let outfitTs = outfit?.created_at
        let pieceTs = piece?.created_at
        let pickPiece: Bool = {
            if piece == nil { return false }
            if outfit == nil { return true }
            switch (outfitTs, pieceTs) {
            case (nil, _?): return true
            case (_?, nil): return false
            case let (o?, p?): return p > o
            default: return false
            }
        }()
        if pickPiece, let piece {
            var signed: String? = piece.image_url
            if let p = piece.image_path {
                signed = (try? await signedClosetUrl(p)) ?? piece.image_url
            }
            return .piece(piece, signedUrl: signed)
        } else if let outfit {
            var signed: String? = outfit.image_url
            if let p = outfit.photo_path {
                signed = try? await signedOutfitUrl(p)
            }
            return .outfit(outfit, signedUrl: signed)
        }
        return nil
    }

    func outfits(limit: Int = 20) async throws -> [Outfit] {
        guard let uid = userId else { return [] }
        let resp = try await Supa.client
            .from("outfits")
            .select()
            .eq("user_id", value: uid)
            .order("created_at", ascending: false)
            .limit(limit)
            .execute()
        // Studio outfit side views are siblings of the front — hide them from
        // the general list so the Journal doesn't show duplicates. They're
        // fetched explicitly via `linkedOutfit(...)` when opening detail.
        return decodeOutfitsSafe(resp.data).filter { $0.kind != "outfit_studio_side" }
    }

    func outfitById(_ id: String) async throws -> Outfit? {
        let resp = try await Supa.client
            .from("outfits")
            .select()
            .eq("id", value: id)
            .limit(1)
            .execute()
        return decodeOutfitsSafe(resp.data).first
    }

    /// Find a sibling outfit linked to `id` (via linked_piece_id) with the given
    /// `kind`. Used to fetch the SIDE view for a Studio outfit's front view.
    func linkedOutfit(linkedTo id: String, kind: String) async throws -> Outfit? {
        let resp = try await Supa.client
            .from("outfits")
            .select()
            .eq("linked_piece_id", value: id)
            .eq("kind", value: kind)
            .limit(1)
            .execute()
        return decodeOutfitsSafe(resp.data).first
    }

    func bestOutfit() async throws -> Outfit? {
        guard let uid = userId else { return nil }
        let resp = try await Supa.client
            .from("outfits")
            .select()
            .eq("user_id", value: uid)
            .order("score", ascending: false)
            .limit(1)
            .execute()
        return decodeOutfitsSafe(resp.data).first
    }

    /// The user's running average, computed WITHIN one scoring family.
    ///
    /// v4 caps only ever subtract, so a v4 headline and a v3 mean are not the
    /// same measurement and averaging them together is a category error: the
    /// first v4 score a user ever gets would read "below your average" for a
    /// look that is completely fine. The family of the most recent scored look
    /// wins, and everything from the other engine is excluded.
    func averageScore() async throws -> Double? {
        let list = try await outfits(limit: 50)
        let scored = list.filter { $0.kind == nil || $0.kind == "score" || $0.kind == "user_scan" }
        guard let family = scored.first(where: { ($0.displayScore ?? 0) > 0 })?.scoringFamily else { return nil }
        let values = scored
            .filter { $0.scoringFamily == family }
            .compactMap { $0.displayScore }
            .filter { $0 > 0 }
        guard !values.isEmpty else { return nil }
        return values.reduce(0, +) / Double(values.count)
    }

    func outfitsInWindow(startIso: String, endIso: String, limit: Int = 3) async throws -> [Outfit] {
        guard let uid = userId else { return [] }
        let resp = try await Supa.client
            .from("outfits")
            .select()
            .eq("user_id", value: uid)
            .gte("created_at", value: startIso)
            .lte("created_at", value: endIso)
            .order("score", ascending: false)
            .limit(limit)
            .execute()
        return decodeOutfitsSafe(resp.data)
    }

    /// Most recent A vs B row. The winner's photo is the row's `photo_path`;
    /// the pair, the totals and the breakdown live in `signals.versus`.
    func latestVersus() async throws -> Outfit? {
        guard let uid = userId else { return nil }
        let resp = try await Supa.client
            .from("outfits")
            .select()
            .eq("user_id", value: uid)
            .eq("kind", value: "versus")
            .order("created_at", ascending: false)
            .limit(1)
            .execute()
        return decodeOutfitsSafe(resp.data).first
    }

    /// Every outfit read in this file uses a bare `.select()`, so the v4 columns
    /// (intake, axes, score_breakdown, dress_code, presence_check, lever,
    /// caveats, pieces, rubric_id, scoring_version, rescore_count) come back
    /// without a column list to maintain. `OutfitInsert` carries the same set on
    /// the way in — a row saved without `scoring_version` would later be read as
    /// v3 and have its headline re-averaged out of the legacy subscores.
    func insertOutfit(_ o: OutfitInsert) async throws -> Outfit {
        let inserted: [Outfit] = try await Supa.client
            .from("outfits")
            .insert(o, returning: .representation)
            .select()
            .execute()
            .value
        guard let first = inserted.first else { throw RepoError.notFound }
        return first
    }

    // MARK: - Storage — outfits + closet + avatars

    func uploadOutfitPhoto(bytes: Data, ext: String = "jpg") async throws -> String {
        guard let uid = userId else { throw RepoError.notSignedIn }
        let path = "\(uid)/\(UUID().uuidString.lowercased()).\(ext)"
        _ = try await storage.from("outfits").upload(
            path, data: bytes,
            options: FileOptions(contentType: mime(for: ext), upsert: false)
        )
        return path
    }

    func signedOutfitUrl(_ path: String) async throws -> String? {
        (try? await storage.from("outfits").createSignedURL(path: path, expiresIn: 3600))?.absoluteString
    }

    /// Signs many outfit paths in one request instead of one round-trip each.
    ///
    /// Journal used to await `signedOutfitUrl` inside a loop, so a 200-entry
    /// history cost 200 sequential round-trips before a single card could be
    /// drawn. Storage signs a whole batch at once; chunking keeps any single
    /// request from carrying an unreasonable body.
    ///
    /// Returns a path-keyed map. Paths that cannot be signed are simply absent
    /// rather than throwing, so one deleted file cannot blank the whole screen.
    func signedOutfitUrls(_ paths: [String], expiresIn: Int = 3600) async -> [String: String] {
        let unique = Array(Set(paths.filter { !$0.isEmpty }))
        guard !unique.isEmpty else { return [:] }
        var out: [String: String] = [:]
        for chunk in stride(from: 0, to: unique.count, by: 100).map({
            Array(unique[$0 ..< min($0 + 100, unique.count)])
        }) {
            guard let results = try? await storage.from("outfits")
                .createSignedURLs(paths: chunk, expiresIn: expiresIn) else { continue }
            for r in results {
                if case let .success(path, url) = r { out[path] = url.absoluteString }
            }
        }
        return out
    }

    func uploadClosetPhoto(bytes: Data, ext: String = "jpg") async throws -> String {
        guard let uid = userId else { throw RepoError.notSignedIn }
        let path = "\(uid)/\(UUID().uuidString.lowercased()).\(ext)"
        _ = try await storage.from("closet").upload(
            path, data: bytes,
            options: FileOptions(contentType: mime(for: ext), upsert: false)
        )
        return path
    }

    func signedClosetUrl(_ path: String) async throws -> String? {
        (try? await storage.from("closet").createSignedURL(path: path, expiresIn: 3600))?.absoluteString
    }

    /// Batch counterpart to `signedClosetUrl`. See `signedOutfitUrls` — the
    /// Studio grid and the Try On strip both walk a 200-piece closet, and doing
    /// that one signature at a time is what made pieces trickle in.
    func signedClosetUrls(_ paths: [String], expiresIn: Int = 3600) async -> [String: String] {
        let unique = Array(Set(paths.filter { !$0.isEmpty }))
        guard !unique.isEmpty else { return [:] }
        var out: [String: String] = [:]
        for chunk in stride(from: 0, to: unique.count, by: 100).map({
            Array(unique[$0 ..< min($0 + 100, unique.count)])
        }) {
            guard let results = try? await storage.from("closet")
                .createSignedURLs(paths: chunk, expiresIn: expiresIn) else { continue }
            for r in results {
                if case let .success(path, url) = r { out[path] = url.absoluteString }
            }
        }
        return out
    }

    func insertClosetItem(_ item: ClosetItemInsert) async throws -> ClosetItem {
        let inserted: [ClosetItem] = try await Supa.client
            .from("closet_items")
            .insert(item, returning: .representation)
            .select()
            .execute()
            .value
        guard let first = inserted.first else { throw RepoError.notFound }
        // Announced here, not at the call sites: five screens write pieces and
        // any of them could forget. Anything showing a closet list reloads.
        await MainActor.run { ClosetBus.shared.changed() }
        return first
    }

    func updateClosetItem(id: String, imagePath: String? = nil, name: String? = nil) async throws {
        guard imagePath != nil || name != nil else { return }
        struct Patch: Encodable {
            let image_path: String?
            let name: String?
        }
        try await Supa.client
            .from("closet_items")
            .update(Patch(image_path: imagePath, name: name))
            .eq("id", value: id)
            .execute()
    }

    func closetItemById(_ id: String) async throws -> ClosetItem? {
        let rows: [ClosetItem] = try await Supa.client
            .from("closet_items")
            .select()
            .eq("id", value: id)
            .limit(1)
            .execute()
            .value
        return rows.first
    }

    func closetChildren(parentId: String) async throws -> [ClosetItem] {
        (try? await Supa.client
            .from("closet_items")
            .select()
            .eq("parent_id", value: parentId)
            .order("created_at", ascending: true)
            .execute()
            .value) ?? []
    }

    func closetItems(limit: Int = 40) async throws -> [ClosetItem] {
        guard let uid = userId else { return [] }
        return try await Supa.client
            .from("closet_items")
            .select()
            .eq("user_id", value: uid)
            .order("created_at", ascending: false)
            .limit(limit)
            .execute()
            .value
    }

    func creditPacks() async throws -> [CreditPack] {
        (try? await Supa.client
            .from("credit_packs")
            .select()
            .order("sort", ascending: true)
            .execute()
            .value) ?? []
    }

    // MARK: - Sunday letters & Hem notes

    func latestSundayLetter() async throws -> SundayLetter? {
        guard let uid = userId else { return nil }
        let rows: [SundayLetter] = try await Supa.client
            .from("sunday_letters")
            .select()
            .eq("user_id", value: uid)
            .order("week_start", ascending: false)
            .limit(1)
            .execute()
            .value
        return rows.first
    }

    func sundayLetters(limit: Int = 12) async throws -> [SundayLetter] {
        guard let uid = userId else { return [] }
        return (try? await Supa.client
            .from("sunday_letters")
            .select()
            .eq("user_id", value: uid)
            .order("week_start", ascending: false)
            .limit(limit)
            .execute()
            .value) ?? []
    }

    func latestHemNote() async throws -> HemNote? {
        guard let uid = userId else { return nil }
        let rows: [HemNote] = try await Supa.client
            .from("hem_notes")
            .select()
            .eq("user_id", value: uid)
            .order("created_at", ascending: false)
            .limit(1)
            .execute()
            .value
        return rows.first
    }

    // MARK: - Credits

    func credits() async throws -> Int {
        guard let uid = userId else { return 0 }
        let rows: [CreditTransaction] = try await Supa.client
            .from("credit_transactions")
            .select("amount")
            .eq("user_id", value: uid)
            .execute()
            .value
        return rows.reduce(0) { $0 + ($1.amount ?? 0) }
    }

    /// Grant the initial `SIGNUP_CREDITS` if the user has NO credit transactions
    /// yet. Idempotent — safe to call on every sign-in.
    func grantSignupCreditsIfEmpty() async throws {
        guard let uid = userId else { return }
        struct IdRow: Decodable { let id: String? }
        let existing: [IdRow] = (try? await Supa.client
            .from("credit_transactions")
            .select("id")
            .eq("user_id", value: uid)
            .limit(1)
            .execute()
            .value) ?? []
        if existing.isEmpty {
            let grant = Supa.signupCredits
            try await Supa.client
                .from("credit_transactions")
                .insert(CreditTxInsert(user_id: uid, amount: grant, kind: "signup", balance_after: grant))
                .execute()
        }
        CreditsBus.shared.refreshAsync()
    }

    /// Spend credits. `amount` is the positive number to deduct; we record a
    /// negative row and always populate `balance_after` (NOT NULL server column).
    func spendCredits(amount: Int, kind: String) async throws {
        guard let uid = userId else { throw RepoError.notSignedIn }
        let current = try await credits()
        let next = current - amount
        try await Supa.client
            .from("credit_transactions")
            .insert(CreditTxInsert(user_id: uid, amount: -amount, kind: kind, balance_after: next))
            .execute()
        CreditsBus.shared.refreshAsync()
    }

    /// Positive credit grant (IAP fulfilment, rating reward, etc). Always
    /// includes `balance_after` — mirror of Android bugfix.
    func addCredits(amount: Int, kind: String = "iap_purchase") async throws {
        guard let uid = userId else { throw RepoError.notSignedIn }
        let current = try await credits()
        let next = current + amount
        try await Supa.client
            .from("credit_transactions")
            .insert(CreditTxInsert(user_id: uid, amount: amount, kind: kind, balance_after: next))
            .execute()
        CreditsBus.shared.refreshAsync()
    }

    /// Sum of credits spent (positive number) today in device-local tz.
    func creditsSpentToday() async throws -> Int {
        guard let uid = userId else { return 0 }
        let startOfToday = Self.iso8601StartOfDay(daysAgo: 0)
        let rows: [CreditTransaction] = (try? await Supa.client
            .from("credit_transactions")
            .select("amount,created_at")
            .eq("user_id", value: uid)
            .gte("created_at", value: startOfToday)
            .execute()
            .value) ?? []
        return rows.compactMap { $0.amount }.filter { $0 < 0 }.reduce(0) { $0 - $1 }
    }

    /// Sum of credits spent (positive number) since first of this month, local tz.
    func creditsSpentThisMonth() async throws -> Int {
        guard let uid = userId else { return 0 }
        let startOfMonth = Self.iso8601StartOfMonth()
        let rows: [CreditTransaction] = (try? await Supa.client
            .from("credit_transactions")
            .select("amount,created_at")
            .eq("user_id", value: uid)
            .gte("created_at", value: startOfMonth)
            .execute()
            .value) ?? []
        return rows.compactMap { $0.amount }.filter { $0 < 0 }.reduce(0) { $0 - $1 }
    }

    /// True if user has any positive purchase transaction on record.
    func hasEverPurchased() async throws -> Bool {
        guard let uid = userId else { return false }
        let rows: [CreditTransaction] = (try? await Supa.client
            .from("credit_transactions")
            .select("kind,amount")
            .eq("user_id", value: uid)
            .gt("amount", value: 0)
            .execute()
            .value) ?? []
        return rows.contains { ($0.kind ?? "").hasPrefix("iap_") || $0.kind == "purchase" }
    }

    /// Count of outfits with `kind = 'roast'` created today (local tz). Used
    /// to enforce the Free tier's 1-roast/day cap.
    func roastsToday() async throws -> Int {
        guard let uid = userId else { return 0 }
        let startOfToday = Self.iso8601StartOfDay(daysAgo: 0)
        struct IdRow: Decodable { let id: String? }
        let rows: [IdRow] = (try? await Supa.client
            .from("outfits")
            .select("id,created_at,kind")
            .eq("user_id", value: uid)
            .eq("kind", value: "roast")
            .gte("created_at", value: startOfToday)
            .execute()
            .value) ?? []
        return rows.count
    }

    // MARK: - Profile flags

    /// Mark that the first-run paywall has been shown once.
    func markPaywallShown() async throws {
        guard let uid = userId else { return }
        struct Patch: Encodable { let paywall_shown: Bool }
        try? await Supa.client
            .from("profiles")
            .update(Patch(paywall_shown: true))
            .eq("id", value: uid)
            .execute()
    }

    /// Read the paywall_shown flag defensively — returns false if the column
    /// doesn't exist yet on legacy prod.
    func paywallShown() async throws -> Bool {
        guard let uid = userId else { return true }
        struct Row: Decodable { let paywall_shown: Bool? }
        let rows: [Row] = (try? await Supa.client
            .from("profiles")
            .select("paywall_shown")
            .eq("id", value: uid)
            .limit(1)
            .execute()
            .value) ?? []
        return rows.first?.paywall_shown ?? false
    }

    func markRatedOk() async throws {
        guard let uid = userId else { return }
        struct Patch: Encodable { let rated_ok: Bool }
        try? await Supa.client
            .from("profiles")
            .update(Patch(rated_ok: true))
            .eq("id", value: uid)
            .execute()
    }

    // MARK: - Push settings

    func pushSettings() async throws -> PushSettings? {
        guard let uid = userId else { return nil }
        let rows: [PushSettings] = try await Supa.client
            .from("push_settings")
            .select()
            .eq("user_id", value: uid)
            .limit(1)
            .execute()
            .value
        return rows.first
    }

    func upsertPushSettings(morning: Bool, weekly: Bool, wrapped: Bool) async throws {
        guard let uid = userId else { return }
        try await Supa.client
            .from("push_settings")
            .upsert(
                PushSettingsUpsert(user_id: uid, morning_stylist: morning, weekly_task: weekly, wrapped: wrapped),
                onConflict: "user_id"
            )
            .execute()
    }

    // MARK: - Edge functions

    func generatePiece(prompt: String, imageUrls: [String] = []) async throws -> GenerateResponse {
        struct Payload: Encodable {
            let prompt: String
            let image_urls: [String]?
        }
        let payload = Payload(prompt: prompt, image_urls: imageUrls.isEmpty ? nil : imageUrls)
        return try await invokeJSON("generate-piece", body: payload)
    }

    /// Persist a completed BodyProfile to Supabase. Idempotent — upserts on
    /// user_id primary key. Photo paths are the storage paths (not signed URLs).
    func saveBodyProfile(_ profile: BodyProfile, frontPath: String?, sidePath: String?) async throws {
        guard let uid = userId else { throw RepoError.notSignedIn }
        struct Row: Encodable {
            let user_id: String
            let body_shape: String?
            let shoulder_hip_ratio: Double?
            let torso_leg_ratio: Double?
            let skin_undertone: String?
            let coloring_season: String?
            let palette_hex: [String]?
            let notes: String?
            let front_photo_path: String?
            let side_photo_path: String?
        }
        let row = Row(
            user_id: uid,
            body_shape: profile.body_shape,
            shoulder_hip_ratio: profile.shoulder_hip_ratio,
            torso_leg_ratio: profile.torso_leg_ratio,
            skin_undertone: profile.skin_undertone,
            coloring_season: profile.coloring_season,
            palette_hex: profile.palette_hex,
            notes: profile.notes,
            front_photo_path: frontPath,
            side_photo_path: sidePath
        )
        _ = try await Supa.client.from("body_profiles").upsert(row, onConflict: "user_id").execute()
    }

    /// Fetch the current user's BodyProfile row if one exists.
    func currentBodyProfile() async throws -> BodyProfile? {
        guard let uid = userId else { return nil }
        struct Row: Decodable {
            let body_shape: String?
            let shoulder_hip_ratio: Double?
            let torso_leg_ratio: Double?
            let skin_undertone: String?
            let coloring_season: String?
            let palette_hex: [String]?
            let notes: String?
        }
        let rows: [Row] = try await Supa.client.from("body_profiles")
            .select("body_shape,shoulder_hip_ratio,torso_leg_ratio,skin_undertone,coloring_season,palette_hex,notes")
            .eq("user_id", value: uid)
            .limit(1)
            .execute().value
        guard let r = rows.first else { return nil }
        return BodyProfile(
            body_shape: r.body_shape,
            shoulder_hip_ratio: r.shoulder_hip_ratio,
            torso_leg_ratio: r.torso_leg_ratio,
            skin_undertone: r.skin_undertone,
            coloring_season: r.coloring_season,
            palette_hex: r.palette_hex,
            notes: r.notes
        )
    }

    /// One-time body calibration. Sends the front (and optional side) photo
    /// URLs to the `analyze-body` edge function and returns a `BodyProfile`
    /// used to personalize future ratings.
    func analyzeBody(frontUrl: String, sideUrl: String?) async throws -> BodyProfileResponse {
        struct Payload: Encodable {
            let front_url: String
            let side_url: String?
        }
        let payload = Payload(front_url: frontUrl, side_url: sideUrl)
        let decoder = JSONDecoder()
        do {
            return try await functions.invoke(
                "analyze-body",
                options: FunctionInvokeOptions(body: payload),
                decoder: decoder
            )
        } catch {
            if case FunctionsError.httpError(_, let data) = error,
               let text = String(data: data, encoding: .utf8) {
                return BodyProfileResponse(profile: nil, engine: nil, error: String(text.prefix(200)))
            }
            throw error
        }
    }

    func tryOnPiece(
        personUrl: String,
        garmentUrl: String,
        category: String = "auto",
        /// One piece off a multi-item plate, by name. Empty means the whole look.
        only: String = ""
    ) async throws -> GenerateResponse {
        struct Payload: Encodable {
            let person_url: String
            let garment_url: String
            let only: String
            let category: String
            let mode: String
        }
        let payload = Payload(
            person_url: personUrl,
            garment_url: garmentUrl,
            only: only,
            category: category,
            mode: "quality"
        )
        return try await invokeJSON("tryon-outfit", body: payload)
    }

    /// Invoke an edge function and decode the response as `GenerateResponse`,
    /// falling back to a truncated error string on decode failure. Matches
    /// Android's tolerant `.getOrElse { GenerateResponse(error = text.take(200)) }`.
    private func invokeJSON(_ name: String, body: some Encodable) async throws -> GenerateResponse {
        let decoder = JSONDecoder()
        do {
            return try await functions.invoke(
                name,
                options: FunctionInvokeOptions(body: body),
                decoder: decoder
            )
        } catch {
            // If we got back a body we couldn't decode, present the truncated
            // string so the caller can display it. Otherwise rethrow.
            if case FunctionsError.httpError(_, let data) = error,
               let text = String(data: data, encoding: .utf8) {
                return GenerateResponse(image_url: nil, seed: nil, error: String(text.prefix(200)))
            }
            throw error
        }
    }

    // MARK: - Sprint 2: annotation/fit-map persistence
    //
    // The `transcribeIntent` wrapper used to live here. It was never called by
    // any screen, and shipping it would have obliged the app to declare
    // NSMicrophoneUsageDescription, which it does not. Removed Sep 2026.

    /// Batch-insert markup annotations for an outfit. Deletes existing rows
    /// for the outfit first so this is a full replace (matches the batch semantics
    /// on Android). Row-level policy `own_annotations` enforces ownership.
    func saveAnnotations(outfitId: String, _ annotations: [MarkupAnnotation]) async throws {
        try? await Supa.client
            .from("outfit_annotations")
            .delete()
            .eq("outfit_id", value: outfitId)
            .execute()
        guard !annotations.isEmpty else { return }
        struct Row: Encodable {
            let outfit_id: String
            let type: String
            let coords: MarkupCoords
            let note: String?
            let confidence: Double?
            let idx: Int
        }
        let rows = annotations.enumerated().map { (i, a) in
            Row(outfit_id: outfitId, type: a.type, coords: a.coords, note: a.note, confidence: a.confidence, idx: i)
        }
        try await Supa.client
            .from("outfit_annotations")
            .insert(rows)
            .execute()
    }

    /// Upsert the fit-tension heatmap for an outfit. One row per outfit
    /// (outfit_id is the primary key of `outfit_fit_maps`).
    func saveFitMap(outfitId: String, _ map: FitMap) async throws {
        struct Row: Encodable {
            let outfit_id: String
            let resolution: [Int]?
            let grid: [[Double]]?
            let hotspots: [FitHotspot]?
        }
        let row = Row(outfit_id: outfitId, resolution: map.resolution, grid: map.grid, hotspots: map.hotspots)
        try await Supa.client
            .from("outfit_fit_maps")
            .upsert(row, onConflict: "outfit_id")
            .execute()
    }

    /// Load ordered markup annotations for an outfit.
    func loadAnnotations(outfitId: String) async throws -> [MarkupAnnotation] {
        struct Row: Decodable {
            let type: String
            let coords: MarkupCoords
            let note: String?
            let confidence: Double?
            let idx: Int?
        }
        let rows: [Row] = (try? await Supa.client
            .from("outfit_annotations")
            .select("type,coords,note,confidence,idx")
            .eq("outfit_id", value: outfitId)
            .order("idx", ascending: true)
            .execute()
            .value) ?? []
        return rows.map { MarkupAnnotation(type: $0.type, coords: $0.coords, note: $0.note ?? "", confidence: $0.confidence) }
    }

    /// Load a single fit-map row for an outfit, or nil if none.
    func loadFitMap(outfitId: String) async throws -> FitMap? {
        struct Row: Decodable {
            let resolution: [Int]?
            let grid: [[Double]]?
            let hotspots: [FitHotspot]?
        }
        let rows: [Row] = (try? await Supa.client
            .from("outfit_fit_maps")
            .select("resolution,grid,hotspots")
            .eq("outfit_id", value: outfitId)
            .limit(1)
            .execute()
            .value) ?? []
        guard let r = rows.first else { return nil }
        return FitMap(resolution: r.resolution, grid: r.grid, hotspots: r.hotspots)
    }

    // MARK: - Sprint 3: Magazine Covers

    /// Compose a magazine-cover render for an outfit photo. Delegates to the
    /// `compose-cover` edge function (Fal any-llm/vision for headline + pull-quote,
    /// Fal nano-banana/edit for the final 9:16 cover). Row is persisted server-side.
    func composeCover(
        sourceImageUrl: String,
        outfitId: String? = nil,
        userName: String? = nil,
        seedHeadline: String? = nil,
        referenceCoverUrl: String? = nil,
        referenceTemplateId: String? = nil,
        masthead: String? = nil,
        mood: String? = nil,
        layout: String? = nil,
        customHeadline: String? = nil,
        customPullQuote: String? = nil,
        coverLines: [String]? = nil,
        price: String? = nil,
        includeBarcode: Bool? = nil,
        userPrompt: String? = nil,
        customMasthead: String? = nil
    ) async throws -> ComposeCoverResponse {
        struct Payload: Encodable {
            let source_image_url: String
            let outfit_id: String?
            let user_name: String?
            let seed_headline: String?
            let reference_cover_url: String?
            let reference_template_id: String?
            let masthead: String?
            let mood: String?
            let layout: String?
            let custom_headline: String?
            let custom_pull_quote: String?
            let cover_lines: [String]?
            let price: String?
            let include_barcode: Bool?
            let user_prompt: String?
            let custom_masthead: String?
        }
        let payload = Payload(
            source_image_url: sourceImageUrl,
            outfit_id: outfitId,
            user_name: userName,
            seed_headline: seedHeadline,
            reference_cover_url: referenceCoverUrl,
            reference_template_id: referenceTemplateId,
            masthead: masthead,
            mood: mood,
            layout: layout,
            custom_headline: customHeadline,
            custom_pull_quote: customPullQuote,
            cover_lines: coverLines,
            price: price,
            include_barcode: includeBarcode,
            user_prompt: userPrompt,
            custom_masthead: customMasthead
        )
        let decoder = JSONDecoder()
        do {
            return try await functions.invoke(
                "compose-cover",
                options: FunctionInvokeOptions(body: payload),
                decoder: decoder
            )
        } catch {
            if case FunctionsError.httpError(_, let data) = error,
               let text = String(data: data, encoding: .utf8) {
                return ComposeCoverResponse(
                    cover_url: nil, headline: nil, pull_quote: nil,
                    vol_number: nil, cover_id: nil, error: String(text.prefix(200))
                )
            }
            throw error
        }
    }

    /// Create a style-only cover TEMPLATE (no subject photo). Delegates to the
    /// `create-cover-template` edge function — pure typography + color, no Fal.
    /// The saved template can later be fed to `composeCover` as a
    /// `referenceCoverUrl` to transfer style DNA onto a real photo.
    func createCoverTemplate(
        masthead: String? = nil,
        headline: String,
        pullQuote: String? = nil,
        mood: String? = nil,
        color: String? = nil,
        layout: String? = nil,
        coverLines: [String]? = nil,
        pose: String? = nil,
        price: String? = nil,
        includeBarcode: Bool? = nil
    ) async throws -> ComposeCoverResponse {
        struct Payload: Encodable {
            let masthead: String?
            let headline: String
            let pull_quote: String?
            let mood: String?
            let color: String?
            let layout: String?
            let cover_lines: [String]?
            let pose: String?
            let price: String?
            let include_barcode: Bool?
        }
        let payload = Payload(
            masthead: masthead,
            headline: headline,
            pull_quote: pullQuote,
            mood: mood,
            color: color,
            layout: layout,
            cover_lines: coverLines,
            pose: pose,
            price: price,
            include_barcode: includeBarcode
        )
        let decoder = JSONDecoder()
        do {
            return try await functions.invoke(
                "create-cover-template",
                options: FunctionInvokeOptions(body: payload),
                decoder: decoder
            )
        } catch {
            if case FunctionsError.httpError(_, let data) = error,
               let text = String(data: data, encoding: .utf8) {
                return ComposeCoverResponse(
                    cover_url: nil, headline: nil, pull_quote: nil,
                    vol_number: nil, cover_id: nil, error: String(text.prefix(200))
                )
            }
            throw error
        }
    }

    /// Regenerate just the headline + pull-quote for an existing cover row.
    /// If `coverId` is provided the row is updated server-side.
    func regenerateCoverHeadline(coverId: String) async throws -> HeadlineResponse {
        struct Payload: Encodable { let cover_id: String }
        return try await invokeHeadline(payload: Payload(cover_id: coverId))
    }

    /// Variant of `regenerateCoverHeadline` for cases where we don't have a
    /// cover row yet (e.g., regenerating before the compose step commits).
    func regenerateCoverHeadline(outfitId: String?, sourceImageUrl: String) async throws -> HeadlineResponse {
        struct Payload: Encodable {
            let outfit_id: String?
            let source_image_url: String
        }
        return try await invokeHeadline(payload: Payload(outfit_id: outfitId, source_image_url: sourceImageUrl))
    }

    private func invokeHeadline(payload: some Encodable) async throws -> HeadlineResponse {
        let decoder = JSONDecoder()
        do {
            return try await functions.invoke(
                "regenerate-cover-headline",
                options: FunctionInvokeOptions(body: payload),
                decoder: decoder
            )
        } catch {
            if case FunctionsError.httpError(_, let data) = error,
               let text = String(data: data, encoding: .utf8) {
                return HeadlineResponse(headline: nil, pull_quote: nil, error: String(text.prefix(200)))
            }
            throw error
        }
    }

    /// Load the current user's magazine covers, most recent first.
    func loadCovers(limit: Int = 40) async throws -> [MagazineCover] {
        guard let uid = userId else { return [] }
        return (try? await Supa.client
            .from("magazine_covers")
            .select()
            .eq("user_id", value: uid)
            .order("created_at", ascending: false)
            .limit(limit)
            .execute()
            .value) ?? []
    }

    /// Load the current user's cover TEMPLATES (rows with `outfit_id IS NULL`),
    /// most recent first. Templates are style-only covers created via
    /// `createCoverTemplate` — no subject photo attached.
    func loadCoverTemplates(limit: Int = 40) async throws -> [MagazineCover] {
        guard let uid = userId else { return [] }
        return (try? await Supa.client
            .from("magazine_covers")
            .select()
            .eq("user_id", value: uid)
            .is("outfit_id", value: nil)
            .order("created_at", ascending: false)
            .limit(limit)
            .execute()
            .value) ?? []
    }

    /// Latest cover for a specific outfit, if one exists.
    func loadCoverForOutfit(_ outfitId: String) async throws -> MagazineCover? {
        let rows: [MagazineCover] = (try? await Supa.client
            .from("magazine_covers")
            .select()
            .eq("outfit_id", value: outfitId)
            .order("created_at", ascending: false)
            .limit(1)
            .execute()
            .value) ?? []
        return rows.first
    }

    func downloadBytes(_ url: String) async throws -> Data {
        guard let u = URL(string: url) else { throw RepoError.badURL }
        var req = URLRequest(url: u)
        req.timeoutInterval = 30
        let (data, _) = try await URLSession.shared.data(for: req)
        return data
    }

    // MARK: - Style profile + avatar

    func updateStyleProfile(
        gender: String?,
        styleTags: [String]?,
        preferredFabrics: [String]?,
        preferredColors: [String]?,
        sensitivities: String?
    ) async throws {
        guard let uid = userId else { return }
        struct Patch: Encodable {
            let gender: String?
            let style_tags: [String]?
            let preferred_fabrics: [String]?
            let preferred_colors: [String]?
            let sensitivities: String?
        }
        try await Supa.client
            .from("profiles")
            .update(Patch(
                gender: gender,
                style_tags: styleTags,
                preferred_fabrics: preferredFabrics,
                preferred_colors: preferredColors,
                sensitivities: sensitivities
            ))
            .eq("id", value: uid)
            .execute()
    }

    func updateAppearance(theme: String? = nil, textScale: Double? = nil, reduceMotion: Bool? = nil) async throws {
        guard let uid = userId else { return }
        struct Patch: Encodable {
            let theme: String?
            let text_scale: Double?
            let reduce_motion: Bool?
        }
        try await Supa.client
            .from("profiles")
            .update(Patch(theme: theme, text_scale: textScale, reduce_motion: reduceMotion))
            .eq("id", value: uid)
            .execute()
    }

    func uploadAvatar(_ bytes: Data) async throws -> String {
        guard let uid = userId else { throw RepoError.notSignedIn }
        let path = "\(uid)/avatar.jpg"
        _ = try await storage.from("avatars").upload(
            path, data: bytes,
            options: FileOptions(contentType: "image/jpeg", upsert: true)
        )
        // Bucket is public — prefer public URL, fall back to signed.
        if let url = try? storage.from("avatars").getPublicURL(path: path) {
            return url.absoluteString
        }
        if let signed = try? await storage.from("avatars").createSignedURL(path: path, expiresIn: 60 * 60 * 24) {
            return signed.absoluteString
        }
        throw RepoError.notFound
    }

    func updateAvatarUrl(_ url: String) async throws {
        guard let uid = userId else { return }
        struct Patch: Encodable { let avatar_url: String }
        try await Supa.client
            .from("profiles")
            .update(Patch(avatar_url: url))
            .eq("id", value: uid)
            .execute()
    }

    // MARK: - Stats

    func stats() async throws -> Stats {
        let pieces = (try? await closetItems(limit: 500)) ?? []
        let piecesCount = pieces.count
        let outfits = (try? await self.outfits(limit: 500)) ?? []
        let looksCount = outfits.count
        let best = outfits.compactMap { $0.displayScore }.max()

        var creditsUsedReal = 0
        if let uid = userId {
            let rows: [CreditTransaction] = (try? await Supa.client
                .from("credit_transactions")
                .select("amount")
                .eq("user_id", value: uid)
                .execute()
                .value) ?? []
            creditsUsedReal = rows.compactMap { $0.amount }.filter { $0 < 0 }.reduce(0) { $0 - $1 }
        }

        // Streak: distinct scan dates, walking backward from today (local tz).
        let cal = Calendar.current
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let dates: Set<DateComponents> = Set(outfits.compactMap { o -> DateComponents? in
            guard let raw = o.created_at else { return nil }
            let d = iso.date(from: raw) ?? ISO8601DateFormatter().date(from: raw)
            guard let d else { return nil }
            return cal.dateComponents([.year, .month, .day], from: d)
        })
        var streak = 0
        var cursor = cal.dateComponents([.year, .month, .day], from: Date())
        while dates.contains(cursor) {
            streak += 1
            guard let prev = cal.date(from: cursor).flatMap({ cal.date(byAdding: .day, value: -1, to: $0) }) else { break }
            cursor = cal.dateComponents([.year, .month, .day], from: prev)
        }

        // Month delta: this month's avg score vs personal all-time avg. Both
        // sides are restricted to a single scoring family — see `averageScore`;
        // a v4 headline and a v3 mean are not the same measurement.
        let today = Date()
        let todayComps = cal.dateComponents([.year, .month], from: today)
        let family = outfits.first(where: { ($0.displayScore ?? 0) > 0 })?.scoringFamily
        let sameEngine = outfits.filter { $0.scoringFamily == family }
        let thisMonth: [Double] = sameEngine.compactMap { o -> Double? in
            guard let raw = o.created_at, let d = iso.date(from: raw) ?? ISO8601DateFormatter().date(from: raw) else { return nil }
            let c = cal.dateComponents([.year, .month], from: d)
            guard c.year == todayComps.year && c.month == todayComps.month else { return nil }
            return o.displayScore
        }
        let allTime = sameEngine.compactMap { $0.displayScore }
        let monthDelta: Double? = (thisMonth.isEmpty || allTime.isEmpty)
            ? nil
            : (thisMonth.reduce(0, +) / Double(thisMonth.count)) - (allTime.reduce(0, +) / Double(allTime.count))

        return Stats(
            pieces: piecesCount,
            looks: looksCount,
            bestScore: best,
            creditsUsed: creditsUsedReal,
            streakDays: streak,
            monthDelta: monthDelta
        )
    }

    /// How many try-on outfits reference the given studio piece.
    func tryOnCountForPiece(_ pieceId: String) async throws -> Int {
        guard let uid = userId else { return 0 }
        struct IdRow: Decodable { let id: String? }
        let rows: [IdRow] = (try? await Supa.client
            .from("outfits")
            .select("id")
            .eq("user_id", value: uid)
            .eq("kind", value: "tryon")
            .eq("linked_piece_id", value: pieceId)
            .execute()
            .value) ?? []
        return rows.count
    }

    // MARK: - Sprint 5: Invitation decoder + outfit suggestions

    /// Call `decode-invitation` with a signed image URL. Errors are flattened
    /// into `DecodeInvitationResponse.error` rather than thrown, matching the
    /// other edge-function wrappers.
    func decodeInvitation(imageUrl: String) async throws -> DecodeInvitationResponse {
        struct Payload: Encodable { let image_url: String }
        let payload = Payload(image_url: imageUrl)
        let decoder = JSONDecoder()
        do {
            return try await functions.invoke(
                "decode-invitation",
                options: FunctionInvokeOptions(body: payload),
                decoder: decoder
            )
        } catch {
            if case FunctionsError.httpError(_, let data) = error,
               let text = String(data: data, encoding: .utf8) {
                return DecodeInvitationResponse(decoded: nil, engine: nil, error: String(text.prefix(200)))
            }
            throw error
        }
    }

    /// Ask `suggest-outfits` for 3 combos from the given closet inventory
    /// (caller should trim to top 30) plus optional body profile + style tags.
    func suggestOutfits(
        dressCode: String,
        eventType: String,
        notes: String? = nil,
        closet: [ClosetItem],
        bodyProfile: BodyProfile? = nil,
        styleTags: [String]? = nil
    ) async throws -> SuggestOutfitsResponse {
        struct Piece: Encodable {
            let id: String
            let name: String
            let category: String
            let subcategory: String?
            let image_url: String?
        }
        struct Payload: Encodable {
            let dress_code: String
            let event_type: String
            let notes: String?
            let body_profile: BodyProfile?
            let closet_items: [Piece]
            let style_tags: [String]?
        }
        let items = closet.prefix(30).compactMap { c -> Piece? in
            guard let id = c.id else { return nil }
            return Piece(
                id: id,
                name: c.name ?? "",
                category: c.category ?? "",
                subcategory: c.subcategory,
                image_url: c.image_url
            )
        }
        let payload = Payload(
            dress_code: dressCode,
            event_type: eventType,
            notes: notes,
            body_profile: bodyProfile,
            closet_items: Array(items),
            style_tags: styleTags
        )
        let decoder = JSONDecoder()
        do {
            return try await functions.invoke(
                "suggest-outfits",
                options: FunctionInvokeOptions(body: payload),
                decoder: decoder
            )
        } catch {
            if case FunctionsError.httpError(_, let data) = error,
               let text = String(data: data, encoding: .utf8) {
                return SuggestOutfitsResponse(combos: nil, engine: nil, error: String(text.prefix(200)))
            }
            throw error
        }
    }

    /// Persist a decoded invitation + suggested combos to `invitation_reads`.
    /// RLS policy `own_invitation_reads` enforces ownership.
    func saveInvitationRead(
        imagePath: String?,
        decoded: InvitationDecoded,
        combos: [OutfitCombo]
    ) async throws {
        guard let uid = userId else { throw RepoError.notSignedIn }
        struct Row: Encodable {
            let user_id: String
            let image_path: String?
            let event_type: String?
            let dress_code: String?
            let time_of_day: String?
            let venue: String?
            let notes: String?
            let suggested_combos: [OutfitCombo]
        }
        let row = Row(
            user_id: uid,
            image_path: imagePath,
            event_type: decoded.event_type,
            dress_code: decoded.dress_code,
            time_of_day: decoded.time_of_day,
            venue: decoded.venue,
            notes: decoded.notes,
            suggested_combos: combos
        )
        try await Supa.client
            .from("invitation_reads")
            .insert(row)
            .execute()
    }

    /// Load invitation reads for the current user, newest first.
    func loadInvitationReads() async throws -> [InvitationRead] {
        guard let uid = userId else { return [] }
        let rows: [InvitationRead] = (try? await Supa.client
            .from("invitation_reads")
            .select()
            .eq("user_id", value: uid)
            .order("created_at", ascending: false)
            .limit(50)
            .execute()
            .value) ?? []
        return rows
    }

    // MARK: - GDPR export + hard delete

    /// Serialize all user-owned rows across tables. Returns raw JSON `Data`.
    func exportUserData() async throws -> Data {
        guard let uid = userId else { throw RepoError.notSignedIn }
        func raw(_ table: String, uidCol: String = "user_id") async -> Any {
            let resp = try? await Supa.client.from(table).select().eq(uidCol, value: uid).execute()
            if let d = resp?.data,
               let obj = try? JSONSerialization.jsonObject(with: d) {
                return obj
            }
            return []
        }
        var out: [String: Any] = [:]
        out["exported_at"] = ISO8601DateFormatter().string(from: Date())
        out["user_id"] = uid
        out["email"] = userEmail ?? ""
        out["profile"] = await raw("profiles", uidCol: "id")
        out["outfits"] = await raw("outfits")
        out["closet_items"] = await raw("closet_items")
        out["credit_transactions"] = await raw("credit_transactions")
        out["hem_notes"] = await raw("hem_notes")
        out["sunday_letters"] = await raw("sunday_letters")
        return try JSONSerialization.data(withJSONObject: out, options: [.prettyPrinted, .sortedKeys])
    }

    /// Hard-delete the account. The `delete-account` edge function owns the whole
    /// wipe now — rows, storage prefixes, Sign in with Apple revocation, and the
    /// `auth.users` row itself, which a client can never delete for itself. The
    /// old client-side sweep left the identity alive, so the same Apple/Google/
    /// email login walked straight back in.
    ///
    /// Throws on any failure. Callers must not claim success unless this returns.
    /// Signs out only once the server has confirmed deletion.
    func deleteAllUserData() async throws {
        guard userId != nil else { throw RepoError.notSignedIn }
        struct Response: Decodable {
            let deleted: Bool?
            let error: String?
            let detail: String?
        }
        let payload: [String: String] = [:]
        let response: Response
        do {
            response = try await functions.invoke(
                "delete-account",
                options: FunctionInvokeOptions(body: payload),
                decoder: JSONDecoder()
            )
        } catch {
            if case FunctionsError.httpError(_, let data) = error,
               let text = String(data: data, encoding: .utf8) {
                throw RepoError.deleteFailed(String(text.prefix(200)))
            }
            throw error
        }
        guard response.deleted == true else {
            throw RepoError.deleteFailed(response.error ?? response.detail ?? "Account deletion did not complete")
        }
        // Local session teardown only — the server already removed the account.
        try? await auth.signOut()
    }

    // MARK: - Helpers

    private func mime(for ext: String) -> String {
        switch ext.lowercased() {
        case "jpg", "jpeg": return "image/jpeg"
        case "png": return "image/png"
        case "webp": return "image/webp"
        case "heic": return "image/heic"
        default: return "application/octet-stream"
        }
    }

    /// Midnight (start of day) in device-local tz, formatted as ISO-8601 with
    /// offset. Matches Android `LocalDate.now().atStartOfDay(...).toOffsetDateTime()`.
    private static func iso8601StartOfDay(daysAgo: Int) -> String {
        let cal = Calendar.current
        let today = cal.startOfDay(for: Date())
        let d = cal.date(byAdding: .day, value: -daysAgo, to: today) ?? today
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f.string(from: d)
    }

    private static func iso8601StartOfMonth() -> String {
        let cal = Calendar.current
        let comps = cal.dateComponents([.year, .month], from: Date())
        let d = cal.date(from: comps) ?? Date()
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f.string(from: d)
    }
}

enum RepoError: Error, LocalizedError {
    case notSignedIn
    case notFound
    case badURL
    case deleteFailed(String)

    var errorDescription: String? {
        switch self {
        case .notSignedIn: return "Not signed in"
        case .notFound: return "Not found"
        case .badURL: return "Invalid URL"
        case .deleteFailed(let detail): return detail
        }
    }
}
