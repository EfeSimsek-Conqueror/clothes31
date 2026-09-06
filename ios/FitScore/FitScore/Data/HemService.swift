import Foundation
import Supabase

/// Decoded shape of the `score-outfit` edge function.
/// Sprint 2 (v3) adds `markup_annotations`, `fit_map`, and `fits_you`. Scoring
/// v4 adds everything from `headline` down — sent only when the request carries
/// `client_features: ["axes_v4","xray_v4"]`. All new fields are optional so v2
/// and v3 responses (and older clients) still decode.
struct ScoreOutfitResponse: Decodable {
    var score: Double?
    var subscores: Subscores?
    var hem_comment: String?
    var swaps: [String]?
    var annotations: [Annotation]?
    // Sprint 2 additions:
    var markup_annotations: [MarkupAnnotation]?
    var fit_map: FitMap?
    var fits_you: Double?
    var error: String?
    var detail: String?
    var raw: String?
    // Scoring v4:
    var headline: Headline?
    var rubric: RubricEcho?
    var brief_verdict: BriefVerdict?
    var axes: [Axis]?
    var dress_code: DressCode?
    var presence_check: PresenceCheck?
    var score_breakdown: ScoreBreakdown?
    var why_this_number: String?
    var lever: Lever?
    var caveats: [CaveatRow]?
    var intake_echo: [IntakeEcho]?
    var pieces: [Piece]?
    var swaps_v2: [SwapV2]?
    /// Opaque; persisted verbatim, never introspected.
    var signals: JSONValue?
    var raw_axes: JSONValue?
    var palette_hex: [String]?
    var intent: String?
    var intent_axis: String?
    var intent_note: String?
    var rescores_remaining: Int?
    var scoring_version: String?
    var verdict: String?
    var intake_warnings: [String]?
    var transport: String?
    /// `/rescore` only.
    var previous_score: Double?
}

struct HemScored {
    let score: Double
    let hemComment: String
    let subscores: Subscores
    let swaps: [String]
    let annotations: [Annotation]
    // Sprint 2 additions — all optional, safe to ignore in existing UI.
    let markupAnnotations: [MarkupAnnotation]
    let fitMap: FitMap?
    let fitsYou: Double?

    // Scoring v4 — every field defaulted so a legacy response (or a call that
    // asked for no v4 features) still builds one of these.
    var headline: Headline? = nil
    var rubric: RubricEcho? = nil
    var briefVerdict: BriefVerdict? = nil
    var axes: [Axis] = []
    var dressCode: DressCode? = nil
    var presenceCheck: PresenceCheck? = nil
    var scoreBreakdown: ScoreBreakdown? = nil
    var whyThisNumber: String? = nil
    var lever: Lever? = nil
    var caveats: [CaveatRow] = []
    var intakeEcho: [IntakeEcho] = []
    var pieces: [Piece] = []
    var swapsV2: [SwapV2] = []
    var signals: JSONValue? = nil
    var rawAxes: JSONValue? = nil
    var paletteHex: [String] = []
    var intent: String? = nil
    var intentAxis: String? = nil
    var intentNote: String? = nil
    var rescoresRemaining: Int? = nil
    var scoringVersion: String? = nil
    var verdict: String? = nil
    var intakeWarnings: [String] = []
    var transport: String? = nil
    /// `/rescore` only — the number this look used to carry.
    var previousScore: Double? = nil

    /// The number to show and to persist.
    ///
    /// v4's headline is already a weighted, capped read over nine axes; the four
    /// legacy subscores are a projection of it, so averaging them would silently
    /// undo every cap the engine applied. Only fall back to the mean for a v3
    /// response that has no headline.
    var headlineScore: Double { headline?.value ?? subscores.averaged ?? score }

    /// Legacy mean. Kept for callers that predate v4 — see `headlineScore`.
    var averagedScore: Double { subscores.averaged ?? score }

    /// Axes heaviest-first, ready to render.
    var orderedAxes: [Axis] { axes.v4Ordered }
}

/// Compare + Decode edge function responses.
/// One row of the A vs B breakdown — both shots scored 0…100 on a single
/// criterion (physique, pose, light, style, vibe).
struct CompareCriterion: Decodable, Hashable {
    var key: String = ""
    var a: Int = 0
    var b: Int = 0
    /// One line per frame explaining what earned that shot its number. Each
    /// note judges its own photo only, so it can never argue against the bar.
    var note_a: String = ""
    var note_b: String = ""

    // v4 — the comparison is now weighted by the brief rather than averaged
    // flat, so a row can say how much it counted and what it actually moved.
    /// Display name from the shared rubric ("Dress code"). Falls back to the key.
    var label: String?
    /// This axis's share of the verdict under the chosen brief, 0…1.
    var weight: Double?
    /// Points this row moved the result by, signed towards A.
    var swing: Double?

    /// "fit" -> "Fit". `label` wins when the server sent one.
    var title: String {
        if let l = label, !l.isEmpty { return l }
        return key.isEmpty ? "" : key.prefix(1).uppercased() + key.dropFirst()
    }

    var weightPercent: Int { Int(((weight ?? 0) * 100).rounded()) }
}

// Swift's synthesized `init(from:)` ignores declaration defaults and throws
// `keyNotFound` on any missing key, so every tolerated-missing field is decoded
// with `decodeIfPresent`. Declared in an extension to keep the memberwise init.
extension CompareCriterion {
    enum CodingKeys: String, CodingKey { case key, a, b, note_a, note_b }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        key = try c.decodeIfPresent(String.self, forKey: .key) ?? ""
        a = try c.decodeIfPresent(Int.self, forKey: .a) ?? 0
        b = try c.decodeIfPresent(Int.self, forKey: .b) ?? 0
        note_a = try c.decodeIfPresent(String.self, forKey: .note_a) ?? ""
        note_b = try c.decodeIfPresent(String.self, forKey: .note_b) ?? ""
    }
}

struct CompareResponse: Decodable, Hashable {
    var winner: String?
    var score_a: Double?
    var score_b: Double?
    var comment: String?
    var reason_a: String?
    var reason_b: String?
    var error: String?
    var detail: String?
    // v2 — 0…100 totals, per-criterion breakdown, verdict + reshoot coaching.
    var total_a: Int?
    var total_b: Int?
    var criteria: [CompareCriterion]?

    var verdict: String?
    var next_shot: String?
    // v4
    /// Server-written, from the arithmetic: which axes actually decided it.
    var why: String?
    var axes: [CompareCriterion]?
    var dress_code: CompareDressCode?
    var rubric: CompareRubric?
    /// "high" | "medium" | "low" — low means the two judging passes disagreed
    /// or the totals landed inside the tie band.
    var confidence: String?
    var margin: Int?
    /// Echoed back so the result screen can state what the call was judged for.
    var occasion: String?
    var focus: [String]?
    var intent: String?

    /// 0…100 total for A. Falls back to the v1 0…10 score when the function
    /// still runs the old shape.
    var totalA: Int { total_a ?? Int(((score_a ?? 0) * 10).rounded()) }
    var totalB: Int { total_b ?? Int(((score_b ?? 0) * 10).rounded()) }
    /// Verdict copy, with the v1 `comment` as fallback.
    var verdictText: String { (verdict?.isEmpty == false ? verdict : comment) ?? "" }
}

/// One garment from `decode-outfit`.
///
/// v2 adds `name` (a 2–4 word stylist-spoken garment name, sentence case) and
/// `detail` (1–3 words, the single crucial construction detail). Both default to
/// `""` so v1 responses — which omit them entirely — still decode; the client
/// then falls back through `displayName` / `displayMeta` to the legacy
/// `silhouette` / `type` / `colors` / `note` fields.
/// Where each look sits on the formality ladder, against the rung the brief
/// actually implies. The gap is the dress-code score.
struct CompareDressCode: Decodable, Hashable {
    var target: Int?
    var caption: String?
    var read_a: Int?
    var read_b: Int?
}

/// The brief the two looks were weighed under. Echoed back so the result screen
/// can state what it graded against instead of leaving it implied.
struct CompareRubric: Decodable, Hashable {
    var occasion: String?
    var formality: Int?
    var presence: Int?
}

struct DecodePiece: Decodable, Hashable {
    // v2:
    var name: String = ""
    var detail: String = ""
    // v1 (still emitted):
    var type: String = ""
    var silhouette: String = ""
    var colors: [String] = []
    var fabric: String = ""
    var note: String = ""

    /// Row title. FROZEN render rule, shared with Android:
    /// `name` -> "silhouette type" trimmed + capitalised -> `type` capitalised.
    var displayName: String {
        let n = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if !n.isEmpty { return n }
        let combined = "\(silhouette) \(type)".trimmingCharacters(in: .whitespacesAndNewlines)
        if !combined.isEmpty { return Self.sentenceCased(combined) }
        return Self.sentenceCased(type.trimmingCharacters(in: .whitespacesAndNewlines))
    }

    /// Row meta. FROZEN render rule, shared with Android:
    /// "fabric · detail" -> "fabric · colors" -> `note` -> "".
    /// Empty components are dropped before joining so there is never a dangling separator.
    var displayMeta: String {
        let fabric = self.fabric.trimmingCharacters(in: .whitespacesAndNewlines)
        let detail = self.detail.trimmingCharacters(in: .whitespacesAndNewlines)
        let primary = [fabric, detail].filter { !$0.isEmpty }.joined(separator: " · ")
        if !primary.isEmpty { return primary }

        let colorText = colors
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: ", ")
        let secondary = [fabric, colorText].filter { !$0.isEmpty }.joined(separator: " · ")
        if !secondary.isEmpty { return secondary }

        return note.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Uppercases only the first character — the palette copy is sentence case,
    /// not Title Case.
    private static func sentenceCased(_ s: String) -> String {
        s.isEmpty ? "" : s.prefix(1).uppercased() + s.dropFirst()
    }
}

// See the note on `CompareCriterion`: declaration defaults do not survive the
// synthesized decoder, so a v1 body (no `name` / no `detail`, or a piece with no
// `colors`) would throw. Decode every field with `decodeIfPresent`.
extension DecodePiece {
    enum CodingKeys: String, CodingKey { case name, detail, type, silhouette, colors, fabric, note }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        detail = try c.decodeIfPresent(String.self, forKey: .detail) ?? ""
        type = try c.decodeIfPresent(String.self, forKey: .type) ?? ""
        silhouette = try c.decodeIfPresent(String.self, forKey: .silhouette) ?? ""
        colors = try c.decodeIfPresent([String].self, forKey: .colors) ?? []
        fabric = try c.decodeIfPresent(String.self, forKey: .fabric) ?? ""
        note = try c.decodeIfPresent(String.self, forKey: .note) ?? ""
    }
}

/// One concrete alternative the wearer could put in, and what it changes.
struct DecodeSwap: Decodable, Hashable {
    var swap: String = ""
    var why: String = ""
}

struct DecodeResponse: Decodable, Hashable {
    var pieces: [DecodePiece] = []
    var palette_hex: [String] = []
    var style_signature: String = ""
    // v3 read. Every field is optional and defaults empty, so a client that
    // gets a v2 response — or a v3 response the model only half filled — still
    // renders everything it did before.
    var palette_names: [String] = []
    var signature_note: String = ""
    var dress_code: String = ""
    var mood_tags: [String] = []
    var styling_notes: [String] = []
    var make_it_yours: [DecodeSwap] = []
    var where_it_works: [String] = []
    var error: String?
    var detail: String?

    /// Colour name for a swatch, when the server sent names that line up with
    /// the hexes. Positional by design — the server drops mismatched lists.
    func paletteName(at index: Int) -> String? {
        guard index < palette_names.count else { return nil }
        let n = palette_names[index].trimmingCharacters(in: .whitespacesAndNewlines)
        return n.isEmpty ? nil : n
    }
}

extension DecodeResponse {
    enum CodingKeys: String, CodingKey {
        case pieces, palette_hex, style_signature, palette_names, signature_note,
             dress_code, mood_tags, styling_notes, make_it_yours, where_it_works,
             error, detail
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        pieces = try c.decodeIfPresent([DecodePiece].self, forKey: .pieces) ?? []
        palette_hex = try c.decodeIfPresent([String].self, forKey: .palette_hex) ?? []
        style_signature = try c.decodeIfPresent(String.self, forKey: .style_signature) ?? ""
        palette_names = try c.decodeIfPresent([String].self, forKey: .palette_names) ?? []
        signature_note = try c.decodeIfPresent(String.self, forKey: .signature_note) ?? ""
        dress_code = try c.decodeIfPresent(String.self, forKey: .dress_code) ?? ""
        mood_tags = try c.decodeIfPresent([String].self, forKey: .mood_tags) ?? []
        styling_notes = try c.decodeIfPresent([String].self, forKey: .styling_notes) ?? []
        make_it_yours = try c.decodeIfPresent([DecodeSwap].self, forKey: .make_it_yours) ?? []
        where_it_works = try c.decodeIfPresent([String].self, forKey: .where_it_works) ?? []
        error = try c.decodeIfPresent(String.self, forKey: .error)
        detail = try c.decodeIfPresent(String.self, forKey: .detail)
    }
}

/// Edge-function wrappers. Mirror Android `HemService` + `CompareService`.
enum HemService {
    /// Score a look against a brief.
    ///
    /// `intake` is the brief and it is what moves the number — occasion, both
    /// dials, the band-4 answer, weather (omitted entirely when unknown, never
    /// guessed). `occasion` and `honesty` are the frozen v3 mirrors: the server
    /// writes `occasion` to the outfits column unchanged and ignores `honesty`
    /// forever, and both keep older server builds working.
    static func score(imageUrl: String,
                      occasion: String,
                      honesty: String,
                      intake: ScoreIntake,
                      intent: String? = nil,
                      backUrl: String? = nil,
                      bodyProfile: BodyProfile? = nil,
                      clientFeatures: [String] = ["axes_v4", "xray_v4"],
                      locale: String = "en") async throws -> HemScored {
        struct Payload: Encodable {
            let image_url: String
            let occasion: String
            let honesty: String
            let intent: String?
            let back_url: String?
            let body_profile: BodyProfile?
            let intake: ScoreIntake
            let client_features: [String]
            let locale: String
        }
        let payload = Payload(
            image_url: imageUrl,
            occasion: occasion,
            honesty: honesty,
            intent: intent?.isEmpty == false ? intent : nil,
            back_url: backUrl,
            body_profile: bodyProfile,
            intake: intake,
            client_features: clientFeatures,
            locale: locale
        )
        let parsed: ScoreOutfitResponse
        do {
            parsed = try await invoke("score-outfit", body: payload)
        } catch {
            if let env: ScoreOutfitResponse = errorEnvelope(error), let err = env.error {
                throw hemError(err, env.detail ?? env.raw)
            }
            throw error
        }
        return try scored(from: parsed)
    }

    /// Re-run the engine over the stored witness with a corrected brief.
    ///
    /// Free, no model call, at most three per outfit. Called on its own URL
    /// rather than through `functions.invoke` for two reasons: the sub-path has
    /// to survive intact, and supabase-swift throws away the response body on
    /// every non-2xx (see `errorEnvelope`) — but here the body IS the product.
    /// `rescore_limit` and `no_witness` are things the user has to be told, not
    /// generic failures.
    static func rescore(outfitId: String, intake: ScoreIntake) async throws -> HemScored {
        struct Body: Encodable {
            let outfit_id: String
            let intake: ScoreIntake
        }
        var req = URLRequest(url: Supa.url.appendingPathComponent("functions/v1/score-outfit/rescore"))
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue(Supa.anonKey, forHTTPHeaderField: "apikey")
        // The route loads the outfit scoped to the caller's own user_id, so it
        // needs the USER's token, not the anon key.
        let token = try await Supa.client.auth.session.accessToken
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        req.httpBody = try JSONEncoder().encode(Body(outfit_id: outfitId, intake: intake))

        let (data, response) = try await URLSession.shared.data(for: req)
        let parsed = (try? JSONDecoder().decode(ScoreOutfitResponse.self, from: data))
            ?? ScoreOutfitResponse()
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            throw hemError(parsed.error ?? "rescore_failed", parsed.detail ?? parsed.raw)
        }
        return try scored(from: parsed)
    }

    /// Shared tail of `score` and `rescore`: surface the error envelope, then
    /// carry every v4 section across untouched.
    private static func scored(from parsed: ScoreOutfitResponse) throws -> HemScored {
        if let err = parsed.error {
            throw hemError(err, parsed.detail ?? parsed.raw)
        }
        guard let s = parsed.score else {
            throw NSError(domain: "Hem", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hem returned no score."])
        }
        let rounded = (s * 10).rounded() / 10
        return HemScored(
            score: rounded,
            hemComment: parsed.hem_comment ?? "",
            subscores: parsed.subscores ?? Subscores(),
            swaps: parsed.swaps ?? [],
            annotations: parsed.annotations ?? [],
            markupAnnotations: parsed.markup_annotations ?? [],
            fitMap: parsed.fit_map,
            fitsYou: parsed.fits_you,
            headline: parsed.headline,
            rubric: parsed.rubric,
            briefVerdict: parsed.brief_verdict,
            axes: parsed.axes ?? [],
            dressCode: parsed.dress_code,
            presenceCheck: parsed.presence_check,
            scoreBreakdown: parsed.score_breakdown,
            whyThisNumber: parsed.why_this_number,
            lever: parsed.lever,
            caveats: parsed.caveats ?? [],
            intakeEcho: parsed.intake_echo ?? [],
            pieces: parsed.pieces ?? [],
            swapsV2: parsed.swaps_v2 ?? [],
            signals: parsed.signals,
            rawAxes: parsed.raw_axes,
            paletteHex: parsed.palette_hex ?? [],
            intent: parsed.intent,
            intentAxis: parsed.intent_axis,
            intentNote: parsed.intent_note,
            rescoresRemaining: parsed.rescores_remaining,
            scoringVersion: parsed.scoring_version,
            verdict: parsed.verdict,
            intakeWarnings: parsed.intake_warnings ?? [],
            transport: parsed.transport,
            previousScore: parsed.previous_score
        )
    }

    private static func hemError(_ code: String, _ detail: String?) -> NSError {
        let text = detail ?? code
        return NSError(domain: "Hem", code: 1, userInfo: [
            NSLocalizedDescriptionKey: "Hem: \(code) — \(String(text.prefix(140)))"
        ])
    }

    /// `focus` picks which criteria get scored at all; `sceneA` / `sceneB` are
    /// the on-device scene tags, passed so the judge knows the two shots were
    /// taken under different conditions.
    static func compare(
        aUrl: String, bUrl: String, occasion: String,
        focus: [String] = [], sceneA: String? = nil, sceneB: String? = nil,
        intent: String = "", sheetAB: String = "", sheetBA: String = "",
        /// The brief both looks are weighed under. Without it the comparison is
        /// unanswerable — the tailored look wins a wedding and loses a Saturday.
        intake: ScoreIntake = ScoreIntake(occasion: .everyday)
    ) async -> CompareResponse {
        struct Payload: Encodable {
            let image_a_url: String
            let image_b_url: String
            let occasion: String
            let intake: ScoreIntake
            let focus: [String]
            let scene_a: String
            let scene_b: String
            let intent: String
            /// Both frames rendered into one side-by-side sheet. The vision
            /// endpoint reads a single `image_url`, so this is the only way
            /// both photos actually reach the judge.
            let sheet_ab_url: String
            let sheet_ba_url: String
        }
        let p = Payload(
            image_a_url: aUrl, image_b_url: bUrl, occasion: occasion, intake: intake, focus: focus,
            scene_a: sceneA ?? "", scene_b: sceneB ?? "", intent: intent,
            sheet_ab_url: sheetAB, sheet_ba_url: sheetBA
        )
        do {
            return try await invoke("compare-outfits", body: p)
        } catch {
            return CompareResponse(error: "decode_failed", detail: String(describing: error).prefix(200).description)
        }
    }

    /// Decode an outfit. Never throws — failures come back as `error` on the response.
    ///
    /// Carries its own 45s timeout: the decode UI is non-dismissible while a
    /// request is in flight, so a hung invoke would trap the user. The network
    /// call races a sleep in a task group; the first finisher wins, the loser is
    /// cancelled, and exactly one value is ever returned.
    static func decode(imageUrl: String) async -> DecodeResponse {
        struct Payload: Encodable, Sendable { let image_url: String }
        let payload = Payload(image_url: imageUrl)
        let timedOut = DecodeResponse(error: "timeout", detail: "Hem took too long to answer.")

        return await withTaskGroup(of: DecodeResponse.self) { group in
            group.addTask {
                do {
                    return try await invoke("decode-outfit", body: payload)
                } catch {
                    if let env: DecodeResponse = errorEnvelope(error), env.error != nil { return env }
                    return DecodeResponse(error: "decode_failed", detail: String(describing: error).prefix(200).description)
                }
            }
            group.addTask {
                // A cancelled sleep throws; the value is discarded either way
                // because the group is already returning the winner.
                try? await Task.sleep(for: .seconds(45))
                return timedOut
            }
            // `next()` returns as soon as either child finishes. It can only be
            // nil if the group were empty, which it is not.
            let winner = await group.next() ?? timedOut
            group.cancelAll()
            return winner
        }
    }

    /// supabase-swift throws `FunctionsError.httpError(code:data:)` for every
    /// non-2xx instead of decoding the body, so the edge function's JSON error
    /// envelope (`{"error":…,"detail":…}`) never reaches the caller. Recover it
    /// here so iOS surfaces the same text Android does.
    private static func errorEnvelope<T: Decodable>(_ error: Error) -> T? {
        guard let fe = error as? FunctionsError, case .httpError(_, let data) = fe else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    private static func invoke<T: Decodable, B: Encodable>(_ name: String, body: B) async throws -> T {
        let decoder = JSONDecoder()
        return try await Supa.client.functions.invoke(
            name,
            options: FunctionInvokeOptions(body: body),
            decoder: decoder
        )
    }
}
