import Foundation

// Scoring v4 — the wire types.
//
// These mirror the `score-outfit` v4 envelope, which is additive on top of the
// frozen v3 keys and only ever sent when the request carries
// `client_features: ["axes_v4","xray_v4"]`. Everything except an identity field
// is optional: the server omits a section rather than nulling it (an
// unjudgeable axis has no `score`, a look with no dress code has no markers),
// and rows written by an older build have none of it at all.
//
// Nothing here computes. The server is the judge; these are its findings.

// MARK: - JSON passthrough

/// A verbatim slice of JSON.
///
/// `signals` and `raw_axes` are the model's untouched witness statement. The
/// client never introspects either — it decodes them, hands them straight back
/// to the `outfits` insert, and `/rescore` replays the engine over them with no
/// model call. Losing a key here would silently cost the user their free
/// re-scores, so the round-trip is lossless by construction.
enum JSONValue: Codable, Hashable, Sendable {
    case null
    case bool(Bool)
    case number(Double)
    case string(String)
    case array([JSONValue])
    case object([String: JSONValue])

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() { self = .null; return }
        if let b = try? c.decode(Bool.self) { self = .bool(b); return }
        if let d = try? c.decode(Double.self) { self = .number(d); return }
        if let s = try? c.decode(String.self) { self = .string(s); return }
        if let a = try? c.decode([JSONValue].self) { self = .array(a); return }
        if let o = try? c.decode([String: JSONValue].self) { self = .object(o); return }
        throw DecodingError.dataCorruptedError(in: c, debugDescription: "Unrepresentable JSON value")
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        switch self {
        case .null: try c.encodeNil()
        case .bool(let b): try c.encode(b)
        case .number(let d): try c.encode(d)
        case .string(let s): try c.encode(s)
        case .array(let a): try c.encode(a)
        case .object(let o): try c.encode(o)
        }
    }

    // Readers, for the rare surface that wants one known key out of `signals`
    // without giving the whole blob a schema it would then have to maintain.
    var objectValue: [String: JSONValue]? { if case .object(let o) = self { return o } else { return nil } }
    var arrayValue: [JSONValue]? { if case .array(let a) = self { return a } else { return nil } }
    var stringValue: String? { if case .string(let s) = self { return s } else { return nil } }
    var doubleValue: Double? { if case .number(let d) = self { return d } else { return nil } }
    var boolValue: Bool? { if case .bool(let b) = self { return b } else { return nil } }
    subscript(key: String) -> JSONValue? { objectValue?[key] }
}

// MARK: - Request

struct ScoreWeather: Codable, Hashable, Sendable {
    var band: WeatherBand
    var precip: Bool

    init(band: WeatherBand, precip: Bool = false) {
        self.band = band
        self.precip = precip
    }

    enum CodingKeys: String, CodingKey { case band, precip }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        band = (try? c.decodeIfPresent(WeatherBand.self, forKey: .band)) ?? .mild
        precip = (try? c.decodeIfPresent(Bool.self, forKey: .precip)) ?? false
    }
}

/// The `intake` object on the request — the brief the wearer set.
///
/// Also the decoded shape of `outfits.intake`, which is why it decodes
/// tolerantly: the column defaults to `{}` and every row written before v4 has
/// exactly that. A throw here would take the whole outfit row down with it.
///
/// Nil fields are omitted on encode, never nulled. That matters most for
/// `weather`: the server treats an absent weather block as "unknown" and
/// disarms the weather rules, but a present block with a guessed band arms
/// them. Location denied means omit.
struct ScoreIntake: Codable, Hashable, Sendable {
    var occasion: ScoreOccasion
    var formality: Int
    var presence: Int
    var role: WeddingRole?
    var venue: WeddingVenue?
    var room: WorkRoom?
    var on_feet: OnFeet?
    var intent: String?
    var weather: ScoreWeather?
    var time_of_day: TimeOfDay
    /// Server-derived (it is `back_url != ""`), so it is never sent on a score
    /// request. Present when this intake was read back off a stored row.
    var has_back: Bool?

    /// Both dials snap to the occasion's seed unless the caller overrides them —
    /// the same defaults the server applies when a dial is missing.
    init(occasion: ScoreOccasion,
         formality: Int? = nil,
         presence: Int? = nil,
         role: WeddingRole? = nil,
         venue: WeddingVenue? = nil,
         room: WorkRoom? = nil,
         on_feet: OnFeet? = nil,
         intent: String? = nil,
         weather: ScoreWeather? = nil,
         time_of_day: TimeOfDay = .daytime,
         has_back: Bool? = nil) {
        self.occasion = occasion
        self.formality = ScoreIntake.dial(formality ?? Rubric.seedFormality[occasion] ?? 3)
        self.presence = ScoreIntake.dial(presence ?? Rubric.seedPresence[occasion] ?? 3)
        // A field that belongs to another occasion is dropped server-side; drop
        // it here too so the preview weights match what the server will build.
        self.role = occasion == .wedding ? role : nil
        self.venue = occasion == .wedding ? venue : nil
        self.room = occasion == .work ? room : nil
        self.on_feet = occasion == .everyday ? on_feet : nil
        let trimmed = intent?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        self.intent = trimmed.isEmpty ? nil : String(trimmed.prefix(140))
        self.weather = weather
        self.time_of_day = time_of_day
        self.has_back = has_back
    }

    private static func dial(_ n: Int) -> Int { max(1, min(5, n)) }

    /// Reset both dials to this occasion's seeds and drop the band-4 answers the
    /// new occasion has no use for. What an occasion chip tap does.
    mutating func snapToOccasion(_ occasion: ScoreOccasion) {
        self = ScoreIntake(
            occasion: occasion,
            role: role, venue: venue, room: room, on_feet: on_feet,
            intent: intent, weather: weather, time_of_day: time_of_day, has_back: has_back
        )
    }

    /// The axis a tapped intent chip maps to. Free text is mapped by the model,
    /// which answers on `intent_axis`.
    var intentChipAxis: AxisKey? {
        guard let intent else { return nil }
        return Rubric.intentChips.first { $0.label.lowercased() == intent.lowercased() }?.axis
    }

    var formalityCaption: String { Rubric.formalityCaption(occasion, formality) }
    var presenceCaption: String { Rubric.presenceCaption(presence) }

    /// Capitalised legacy mirror for the `occasion` request field and the
    /// `outfits.occasion` column. Those semantics are frozen — new vocabulary
    /// lives in the intake, never here.
    var legacyOccasion: String { occasion.title }

    enum CodingKeys: String, CodingKey {
        case occasion, formality, presence, role, venue, room, on_feet
        case intent, weather, time_of_day, has_back
    }

    init(from decoder: Decoder) throws {
        guard let c = try? decoder.container(keyedBy: CodingKeys.self) else {
            self.init(occasion: .everyday)
            return
        }
        let occasion = (try? c.decodeIfPresent(ScoreOccasion.self, forKey: .occasion)) ?? .everyday
        self.init(
            occasion: occasion,
            formality: try? c.decodeIfPresent(Int.self, forKey: .formality),
            presence: try? c.decodeIfPresent(Int.self, forKey: .presence),
            role: try? c.decodeIfPresent(WeddingRole.self, forKey: .role),
            venue: try? c.decodeIfPresent(WeddingVenue.self, forKey: .venue),
            room: try? c.decodeIfPresent(WorkRoom.self, forKey: .room),
            on_feet: try? c.decodeIfPresent(OnFeet.self, forKey: .on_feet),
            intent: try? c.decodeIfPresent(String.self, forKey: .intent),
            weather: try? c.decodeIfPresent(ScoreWeather.self, forKey: .weather),
            time_of_day: (try? c.decodeIfPresent(TimeOfDay.self, forKey: .time_of_day)) ?? .daytime,
            has_back: try? c.decodeIfPresent(Bool.self, forKey: .has_back)
        )
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(occasion, forKey: .occasion)
        try c.encode(formality, forKey: .formality)
        try c.encode(presence, forKey: .presence)
        try c.encodeIfPresent(role, forKey: .role)
        try c.encodeIfPresent(venue, forKey: .venue)
        try c.encodeIfPresent(room, forKey: .room)
        try c.encodeIfPresent(on_feet, forKey: .on_feet)
        try c.encodeIfPresent(intent, forKey: .intent)
        try c.encodeIfPresent(weather, forKey: .weather)
        try c.encode(time_of_day, forKey: .time_of_day)
        try c.encodeIfPresent(has_back, forKey: .has_back)
    }
}

// MARK: - Response

struct Headline: Codable, Hashable, Sendable {
    var value: Double?
    /// What Σ w·score came to before any cap or floor. `value` is what the user
    /// is told; the difference between the two is the rules' waterfall.
    var weighted_mean: Double?
    var band: String?
    var reason: String?
}

/// The rubric the look was actually graded against, echoed back so two scores
/// are only ever compared like for like.
struct RubricEcho: Codable, Hashable, Sendable {
    var id: String?
    var version: Int?
    var occasion: String?
    var role: String?
    var venue: String?
    var room: String?
    var on_feet: String?
    var formality: Int?
    var formality_caption: String?
    var presence: Int?
    var presence_caption: String?
    var label: String?
    var brief_line: String?
    var primary_axes: [String]?

    /// The four axes the brief graded hardest on, typed. Unknown keys are
    /// dropped rather than guessed.
    var primaryAxisKeys: [AxisKey] { (primary_axes ?? []).compactMap(AxisKey.init(rawValue:)) }
}

/// "on_brief" | "nearly" | "off_brief".
struct BriefVerdict: Codable, Hashable, Sendable {
    var state: String?
    var copy: String?
}

/// One axis of nine. `score` is nil exactly when the photograph could not
/// support a read — the axis is then dropped from the mean rather than guessed
/// at 5, and `unjudgeable` is true.
struct Axis: Codable, Hashable, Sendable, Identifiable {
    var key: String = ""
    var label: String = ""
    var weight: Double = 0
    var raw_score: Double?
    var score: Double?
    var contribution: Double?
    var evidence: String = ""
    /// "engine" for CTX — the one axis the model never scores — "model" for the
    /// other eight.
    var source: String = "model"
    var rank: Int = 0
    var capped_by: String?
    var ceiling: Double?
    var ceiling_rule: String?
    var floored_by: String?
    var unjudgeable: Bool?
    var is_intent_axis: Bool?

    var id: String { key }
    var axisKey: AxisKey? { AxisKey(rawValue: key) }
    var isUnjudgeable: Bool { unjudgeable == true || score == nil }
    var isIntentAxis: Bool { is_intent_axis == true }
    /// Weight as whole percent, the way every v4 surface writes it.
    var weightPercent: Int { Int((weight * 100).rounded()) }

    enum CodingKeys: String, CodingKey {
        case key, label, weight, raw_score, score, contribution, evidence, source, rank
        case capped_by, ceiling, ceiling_rule, floored_by, unjudgeable, is_intent_axis
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        key = try c.decodeIfPresent(String.self, forKey: .key) ?? ""
        label = try c.decodeIfPresent(String.self, forKey: .label) ?? AxisKey(rawValue: key)?.label ?? key
        weight = try c.decodeIfPresent(Double.self, forKey: .weight) ?? 0
        raw_score = try c.decodeIfPresent(Double.self, forKey: .raw_score)
        score = try c.decodeIfPresent(Double.self, forKey: .score)
        contribution = try c.decodeIfPresent(Double.self, forKey: .contribution)
        evidence = try c.decodeIfPresent(String.self, forKey: .evidence) ?? ""
        source = try c.decodeIfPresent(String.self, forKey: .source) ?? "model"
        rank = try c.decodeIfPresent(Int.self, forKey: .rank) ?? 0
        capped_by = try c.decodeIfPresent(String.self, forKey: .capped_by)
        ceiling = try c.decodeIfPresent(Double.self, forKey: .ceiling)
        ceiling_rule = try c.decodeIfPresent(String.self, forKey: .ceiling_rule)
        floored_by = try c.decodeIfPresent(String.self, forKey: .floored_by)
        unjudgeable = try c.decodeIfPresent(Bool.self, forKey: .unjudgeable)
        is_intent_axis = try c.decodeIfPresent(Bool.self, forKey: .is_intent_axis)
    }

    init(key: String, label: String = "", weight: Double = 0, score: Double? = nil,
         evidence: String = "", source: String = "model", rank: Int = 0) {
        self.key = key
        self.label = label.isEmpty ? (AxisKey(rawValue: key)?.label ?? key) : label
        self.weight = weight
        self.score = score
        self.evidence = evidence
        self.source = source
        self.rank = rank
    }
}

extension Array where Element == Axis {
    /// Display order. The server already sends the array heaviest-first with a
    /// 1-based `rank`, so trust `rank` when it is populated and only fall back
    /// to weight for a row written before `rank` existed.
    var v4Ordered: [Axis] {
        let ranked = allSatisfy { $0.rank > 0 }
        if ranked { return sorted { $0.rank < $1.rank } }
        return sorted {
            $0.weight == $1.weight
                ? ($0.axisKey?.order ?? 99) < ($1.axisKey?.order ?? 99)
                : $0.weight > $1.weight
        }
    }
}

/// One line of the dress-code checklist. `present == nil` means the photograph
/// could not show it — never a cross.
struct CodeMarker: Codable, Hashable, Sendable, Identifiable {
    var name: String = ""
    var required: Bool?
    var present: Bool?
    var note: String?

    var id: String { name }

    enum CodingKeys: String, CodingKey { case name, required, present, note }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        required = try c.decodeIfPresent(Bool.self, forKey: .required)
        present = try c.decodeIfPresent(Bool.self, forKey: .present)
        note = try c.decodeIfPresent(String.self, forKey: .note)
    }
}

/// The distance between the code the wearer dialled and the code the photograph
/// reads. `render: false` means the brief carries no code worth drawing.
struct DressCode: Codable, Hashable, Sendable {
    var read: Int?
    var claimed: Int?
    var target: Int?
    var gap: Int?
    /// "met" | "interpreted" | "near" | "miss".
    var severity: String?
    /// "under" | "over" | "none".
    var direction: String?
    var line: String?
    var evidence: String?
    var markers: [CodeMarker]?
    var render: Bool?

    var shouldRender: Bool { render == true }
}

struct PresenceCheck: Codable, Hashable, Sendable {
    var read: Int?
    var claimed: Int?
    var gap: Int?
    var line: String?
    var evidence: String?
    var render: Bool?
    /// "Colour carries 18% of this score at that setting."
    var mechanism: String?

    var shouldRender: Bool { render == true }
}

/// One rule's entry in the waterfall. Fire order is array order, and
/// `raw_weighted + Σ headline_cost == final` is an identity.
struct RuleFired: Codable, Hashable, Sendable, Identifiable {
    var id: String = ""
    var name: String?
    var trigger: String?
    var effect: String?
    var axis: String?
    var before: Double?
    var after: Double?
    var headline_cost: Double?
    /// "cap" | "caveat_only" | "off".
    var mode: String?
    var confidence: Double?

    enum CodingKeys: String, CodingKey {
        case id, name, trigger, effect, axis, before, after, headline_cost, mode, confidence
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? ""
        name = try c.decodeIfPresent(String.self, forKey: .name)
        trigger = try c.decodeIfPresent(String.self, forKey: .trigger)
        effect = try c.decodeIfPresent(String.self, forKey: .effect)
        axis = try c.decodeIfPresent(String.self, forKey: .axis)
        before = try c.decodeIfPresent(Double.self, forKey: .before)
        after = try c.decodeIfPresent(Double.self, forKey: .after)
        headline_cost = try c.decodeIfPresent(Double.self, forKey: .headline_cost)
        mode = try c.decodeIfPresent(String.self, forKey: .mode)
        confidence = try c.decodeIfPresent(Double.self, forKey: .confidence)
    }
}

/// What one intake answer did to one axis's weight, measured post-normalisation.
struct WeightDelta: Codable, Hashable, Sendable, Identifiable {
    var reason: String = ""
    var axis: String = ""
    var delta: Double = 0
    /// "dial" | "band4" | "cross" | "intent" | "evidence" | "null_redistribution".
    var kind: String?

    var id: String { "\(reason)|\(axis)" }

    enum CodingKeys: String, CodingKey { case reason, axis, delta, kind }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        reason = try c.decodeIfPresent(String.self, forKey: .reason) ?? ""
        axis = try c.decodeIfPresent(String.self, forKey: .axis) ?? ""
        delta = try c.decodeIfPresent(Double.self, forKey: .delta) ?? 0
        kind = try c.decodeIfPresent(String.self, forKey: .kind)
    }
}

/// An overall ceiling a rule armed. `binding` is true only for the one that
/// actually held the number down.
struct CapApplied: Codable, Hashable, Sendable, Identifiable {
    var id: String = ""
    var scope: String?
    var to: Double?
    var binding: Bool?

    enum CodingKeys: String, CodingKey { case id, scope, to, binding }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? ""
        scope = try c.decodeIfPresent(String.self, forKey: .scope)
        to = try c.decodeIfPresent(Double.self, forKey: .to)
        binding = try c.decodeIfPresent(Bool.self, forKey: .binding)
    }
}

/// A rule that ran in `caveat_only` — a line of copy with zero numeric effect.
struct DowngradedRule: Codable, Hashable, Sendable, Identifiable {
    var id: String = ""
    var name: String?
    var copy: String?
    var confidence: Double?

    enum CodingKeys: String, CodingKey { case id, name, copy, confidence }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? ""
        name = try c.decodeIfPresent(String.self, forKey: .name)
        copy = try c.decodeIfPresent(String.self, forKey: .copy)
        confidence = try c.decodeIfPresent(Double.self, forKey: .confidence)
    }
}

/// One row of `score_breakdown.history`, appended by `/rescore`.
struct RescoreHistoryEntry: Codable, Hashable, Sendable {
    var from: Double?
    var to: Double?
    var rubric_id: String?
}

struct ScoreBreakdown: Codable, Hashable, Sendable {
    var weights_source: String?
    var null_axes: [String]?
    var discarded_axes: [String]?
    var weight_deltas: [WeightDelta]?
    var raw_weighted: Double?
    var rules_fired: [RuleFired]?
    var caps_applied: [CapApplied]?
    var downgraded_to_caveat: [DowngradedRule]?
    var final: Double?
    /// Present only on rows that have been re-scored.
    var history: [RescoreHistoryEntry]?

    /// The cap that actually held the number down, if any.
    var bindingCap: CapApplied? { (caps_applied ?? []).first { $0.binding == true } }
}

/// "no_back_view" | "null_axis" | "discarded_axis" | "no_weather" |
/// "rule:R-WHITE" | "model".
struct CaveatRow: Codable, Hashable, Sendable, Identifiable {
    var kind: String = ""
    var copy: String = ""
    var cost: String?
    var axis: String?

    var id: String { "\(kind)|\(copy)" }

    enum CodingKeys: String, CodingKey { case kind, copy, cost, axis }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        kind = try c.decodeIfPresent(String.self, forKey: .kind) ?? ""
        copy = try c.decodeIfPresent(String.self, forKey: .copy) ?? ""
        cost = try c.decodeIfPresent(String.self, forKey: .cost)
        axis = try c.decodeIfPresent(String.self, forKey: .axis)
    }
}

/// "You picked X → here is what X did." The section that exists so a user can
/// never say the app ignored their answer.
struct IntakeEcho: Codable, Hashable, Sendable, Identifiable {
    var label: String = ""
    var effect: String = ""

    var id: String { "\(label)|\(effect)" }

    enum CodingKeys: String, CodingKey { case label, effect }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        label = try c.decodeIfPresent(String.self, forKey: .label) ?? ""
        effect = try c.decodeIfPresent(String.self, forKey: .effect) ?? ""
    }
}

/// The single change with the largest projected effect on the headline.
struct Lever: Codable, Hashable, Sendable {
    var axis: String?
    var label: String?
    var from: Double?
    var to: Double?
    var projected: Double?
    var delta: Double?
    var copy: String?
    /// The concrete thing to do — "A black bow tie".
    var action: String?
}

struct SwapV2: Codable, Hashable, Sendable, Identifiable {
    var from: String = ""
    var to: String = ""
    var axis: String = ""
    var why: String = ""
    /// "swap" | "tailor" | "buy_or_rent".
    var effort: String = "swap"

    var id: String { "\(from)→\(to)" }

    enum CodingKeys: String, CodingKey { case from, to, axis, why, effort }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.from = try c.decodeIfPresent(String.self, forKey: .from) ?? ""
        to = try c.decodeIfPresent(String.self, forKey: .to) ?? ""
        axis = try c.decodeIfPresent(String.self, forKey: .axis) ?? ""
        why = try c.decodeIfPresent(String.self, forKey: .why) ?? ""
        effort = try c.decodeIfPresent(String.self, forKey: .effort) ?? "swap"
    }
}

/// One garment, placed on the photograph.
/// `verdict` ∈ "works" | "works_elsewhere" | "drags".
struct Piece: Codable, Hashable, Sendable, Identifiable {
    var label: String = ""
    var x_pct: Double = 50
    var y_pct: Double = 50
    var score: Double = 5
    var verdict: String = "works"
    var note: String = ""

    var id: String { "\(label)|\(x_pct)|\(y_pct)" }

    enum CodingKeys: String, CodingKey { case label, x_pct, y_pct, score, verdict, note }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        label = try c.decodeIfPresent(String.self, forKey: .label) ?? ""
        x_pct = try c.decodeIfPresent(Double.self, forKey: .x_pct) ?? 50
        y_pct = try c.decodeIfPresent(Double.self, forKey: .y_pct) ?? 50
        score = try c.decodeIfPresent(Double.self, forKey: .score) ?? 5
        verdict = try c.decodeIfPresent(String.self, forKey: .verdict) ?? "works"
        note = try c.decodeIfPresent(String.self, forKey: .note) ?? ""
    }
}
