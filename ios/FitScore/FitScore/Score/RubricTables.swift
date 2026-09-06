import Foundation

// Scoring v4 — a hand port of the DATA in
// `supabase/functions/_shared/rubric/rubric.v4.ts`.
//
// The server is authoritative for every number in this file. The client holds a
// copy for one reason only: the brief sheet has to show what a dial does BEFORE
// a credit is spent — the captions, the labels, and (via `RubricWeights`) the
// "grading hardest on …" strip. Nothing here ever produces a score.
//
// If a value drifts from the TypeScript the preview lies to the user, so these
// tables are copied verbatim and ordered exactly as they appear there.

// MARK: - Vocabulary

/// The nine axes, in the server's declaration order (`AXIS_KEYS`). That order is
/// load-bearing: it is the tie-break when two axes carry identical weight, which
/// is how the client reproduces the server's stable sort.
enum AxisKey: String, CaseIterable, Codable, Hashable, Sendable {
    case SIL, FIT, COL, TEX, CTX, DTL, SHO, PRS, POV

    /// Position in `AXIS_KEYS`. See the note above on ties.
    var order: Int { AxisKey.allCases.firstIndex(of: self) ?? 0 }

    var label: String { Rubric.axisLabels[self] ?? rawValue }
}

enum ScoreOccasion: String, CaseIterable, Codable, Hashable, Sendable {
    case work, date, wedding, casual, everyday

    var title: String { rawValue.prefix(1).uppercased() + rawValue.dropFirst() }
}

/// Wedding only. Gates the white rule.
enum WeddingRole: String, CaseIterable, Codable, Hashable, Sendable {
    case guest, wedding_party, couple
    var label: String { Rubric.roleLabels[self] ?? rawValue }
}

/// Wedding only.
enum WeddingVenue: String, CaseIterable, Codable, Hashable, Sendable {
    case ballroom, garden, beach, registry_or_worship
    var label: String { Rubric.venueLabels[self] ?? rawValue }
}

/// Work only.
enum WorkRoom: String, CaseIterable, Codable, Hashable, Sendable {
    case creative, business_casual, corporate, client_facing
    var label: String { Rubric.roomLabels[self] ?? rawValue }
}

/// Everyday only.
enum OnFeet: String, CaseIterable, Codable, Hashable, Sendable {
    case most_of_day, some, seated
    var label: String { Rubric.onFeetLabels[self] ?? rawValue }
}

enum WeatherBand: String, CaseIterable, Codable, Hashable, Sendable {
    case cold, mild, hot
    var label: String { Rubric.weatherLabels[self] ?? rawValue }
}

enum TimeOfDay: String, CaseIterable, Codable, Hashable, Sendable {
    case daytime, evening
    var label: String { Rubric.timeLabels[self] ?? rawValue }
}

// MARK: - Tables

enum Rubric {
    static let version = 1

    static let axisLabels: [AxisKey: String] = [
        .SIL: "Silhouette",
        .FIT: "Fit",
        .COL: "Colour",
        .TEX: "Fabric",
        .CTX: "Dress code",
        .DTL: "Detail",
        .SHO: "Footwear",
        .PRS: "Upkeep",
        .POV: "Point of view",
    ]

    /// Base weight vectors at the neutral dial pair (f=3, p=3). Each column sums
    /// to 1.000.
    static let baseWeights: [ScoreOccasion: [AxisKey: Double]] = [
        .work:     [.SIL: 0.13, .FIT: 0.17, .COL: 0.10, .TEX: 0.09, .CTX: 0.17, .DTL: 0.12, .SHO: 0.09, .PRS: 0.07, .POV: 0.06],
        .date:     [.SIL: 0.15, .FIT: 0.15, .COL: 0.12, .TEX: 0.12, .CTX: 0.10, .DTL: 0.10, .SHO: 0.07, .PRS: 0.05, .POV: 0.14],
        .wedding:  [.SIL: 0.13, .FIT: 0.15, .COL: 0.10, .TEX: 0.09, .CTX: 0.26, .DTL: 0.12, .SHO: 0.08, .PRS: 0.05, .POV: 0.02],
        .casual:   [.SIL: 0.17, .FIT: 0.12, .COL: 0.13, .TEX: 0.13, .CTX: 0.03, .DTL: 0.09, .SHO: 0.11, .PRS: 0.06, .POV: 0.16],
        .everyday: [.SIL: 0.15, .FIT: 0.17, .COL: 0.12, .TEX: 0.11, .CTX: 0.05, .DTL: 0.09, .SHO: 0.14, .PRS: 0.07, .POV: 0.10],
    ]

    /// Dial modulation: `m[a] = (1 + α·(f−3)/2) · (1 + β·(p−3)/2)`.
    /// Dressier ⇒ correctness, tailoring and upkeep matter more and
    /// self-expression matters less.
    static let alpha: [AxisKey: Double] = [
        .SIL: 0.00, .FIT: 0.35, .COL: -0.10, .TEX: 0.10, .CTX: 0.60,
        .DTL: 0.25, .SHO: 0.15, .PRS: 0.45, .POV: -0.35,
    ]

    /// Louder ⇒ colour, detail and point of view matter more and correctness and
    /// upkeep matter less.
    static let beta: [AxisKey: Double] = [
        .SIL: 0.20, .FIT: -0.20, .COL: 0.45, .TEX: 0.10, .CTX: -0.30,
        .DTL: 0.35, .SHO: 0.00, .PRS: -0.15, .POV: 0.70,
    ]

    /// Bounds on the modulation multiplier.
    static let mMin = 0.55
    static let mMax = 1.75

    /// Bounds on a raw weight, applied AFTER modulation and the band-4 deltas.
    static let wMin = 0.02
    static let wMax = 0.40

    /// Tapping an occasion chip snaps both dials — the first visible proof that
    /// the chip does something.
    static let seedFormality: [ScoreOccasion: Int] = [
        .work: 3, .date: 3, .wedding: 4, .casual: 2, .everyday: 2,
    ]

    static let seedPresence: [ScoreOccasion: Int] = [
        .work: 2, .date: 4, .wedding: 3, .casual: 3, .everyday: 3,
    ]

    /// Band-2 captions, indexed by `formality - 1`.
    static let formalityCaptions: [ScoreOccasion: [String]] = [
        .work:     ["Working from home", "Casual Friday", "Business casual", "Client-facing", "Boardroom or interview"],
        .date:     ["Coffee or a walk", "A casual bar", "Dinner out", "Somewhere nice", "A black-tie event"],
        .wedding:  ["Beach or backyard", "Garden party", "Semi-formal", "Cocktail", "Black tie"],
        .casual:   ["Errands", "Friends, brunch", "A day out", "Dinner or a gig", "A party"],
        .everyday: ["Around the house", "Around town", "Out all day", "Day into evening", "Something on tonight"],
    ]

    /// The dial is occasion-RELATIVE; the model's `formality_read` is ABSOLUTE.
    /// This table translates a dial position into the absolute rung the brief
    /// implies, and it is what the dress-code gap is measured against.
    static let targetFormality: [ScoreOccasion: [Int]] = [
        .work:     [1, 2, 3, 4, 4],
        .date:     [1, 2, 3, 4, 5],
        .wedding:  [2, 3, 4, 4, 5],
        .casual:   [1, 2, 2, 3, 4],
        .everyday: [1, 2, 2, 3, 4],
    ]

    static let presenceCaptions = [
        "Invisible", "Quiet", "Balanced", "Noticed", "Be looked at",
    ]

    /// Where an outfit ITSELF sits, as reported by the model. Copy only.
    static let formalityReadLabels = [
        "Loungewear", "Everyday casual", "Smart casual", "Dressed", "Formal",
    ]

    static let roleLabels: [WeddingRole: String] = [
        .guest: "Guest",
        .wedding_party: "Wedding party",
        .couple: "The couple",
    ]

    static let venueLabels: [WeddingVenue: String] = [
        .ballroom: "Ballroom or hotel",
        .garden: "Garden or outdoors",
        .beach: "Beach",
        .registry_or_worship: "Registry or place of worship",
    ]

    static let roomLabels: [WorkRoom: String] = [
        .creative: "Creative",
        .business_casual: "Business casual",
        .corporate: "Corporate",
        .client_facing: "Client-facing",
    ]

    static let onFeetLabels: [OnFeet: String] = [
        .most_of_day: "On my feet most of the day",
        .some: "Some walking",
        .seated: "Mostly seated",
    ]

    static let weatherLabels: [WeatherBand: String] = [
        .cold: "Cold", .mild: "Mild", .hot: "Hot",
    ]

    static let timeLabels: [TimeOfDay: String] = [
        .daytime: "Daytime", .evening: "Evening",
    ]

    // MARK: Deltas
    //
    // Band-4 and cross-cutting weight deltas, additive on the modulated weight
    // and applied before the clamp. They do not need to sum to zero — the
    // normaliser absorbs it.

    static let roleDeltas: [WeddingRole: [AxisKey: Double]] = [
        .guest: [:],
        // You are in every photograph for the rest of these people's lives.
        .wedding_party: [.DTL: 0.03, .PRS: 0.04, .POV: -0.02, .COL: -0.05],
        // It IS your day: the code relaxes, self-expression is the point.
        .couple: [.DTL: 0.05, .POV: 0.04, .CTX: -0.03, .PRS: 0.02, .COL: -0.02],
    ]

    static let venueDeltas: [WeddingVenue: [AxisKey: Double]] = [
        .ballroom: [:],
        .garden: [.SHO: 0.03, .TEX: 0.02, .DTL: -0.02, .COL: -0.03],
        .beach: [.SHO: 0.03, .TEX: 0.03, .DTL: -0.03, .COL: -0.03, .PRS: -0.02],
        .registry_or_worship: [.CTX: 0.02, .PRS: 0.02, .POV: -0.02, .COL: -0.02],
    ]

    static let roomDeltas: [WorkRoom: [AxisKey: Double]] = [
        .creative: [.POV: 0.05, .COL: 0.02, .CTX: -0.05, .PRS: -0.02],
        .business_casual: [:],
        .corporate: [.CTX: 0.04, .FIT: 0.02, .POV: -0.03, .TEX: -0.03],
        .client_facing: [.CTX: 0.03, .PRS: 0.03, .POV: -0.02, .COL: -0.02, .TEX: -0.02],
    ]

    static let onFeetDeltas: [OnFeet: [AxisKey: Double]] = [
        .most_of_day: [.SHO: 0.04, .COL: -0.02, .POV: -0.02],
        .some: [:],
        .seated: [.SHO: -0.05, .DTL: 0.03, .PRS: 0.02],
    ]

    static let weatherDeltas: [WeatherBand: [AxisKey: Double]] = [
        .cold: [.TEX: 0.03, .CTX: 0.02, .COL: -0.02, .POV: -0.03],
        .mild: [:],
        .hot: [.TEX: 0.03, .PRS: -0.02, .SIL: -0.01],
    ]

    static let precipDelta: [AxisKey: Double] = [.SHO: 0.03, .TEX: 0.01, .COL: -0.02, .DTL: -0.02]

    static let timeDeltas: [TimeOfDay: [AxisKey: Double]] = [
        .daytime: [.TEX: 0.02, .COL: 0.02, .DTL: -0.02, .POV: -0.02],
        .evening: [:],
    ]

    /// An explicit intent buys its axis five points of weight, taken from the two
    /// lightest OTHER axes.
    static let intentWeightBonus = 0.05

    /// Without a back view the fit read is a partial one, and it is weighted as
    /// such. R-EVIDENCE also ceilings the axis at 8.5.
    static let noBackFitMultiplier = 0.80

    /// Tappable intent chips, and the axis each maps to. Free text is mapped by
    /// the model, which returns `intent_axis`.
    static let intentChips: [IntentChip] = [
        IntentChip(label: "Look taller", axis: .SIL),
        IntentChip(label: "Look expensive", axis: .TEX),
        IntentChip(label: "Look like I didn't try", axis: .POV),
        IntentChip(label: "Hide my arms", axis: .FIT),
    ]

    /// Dress-code gap coefficients. Server-side arithmetic; carried here so the
    /// brief sheet can explain the asymmetry honestly.
    static let underCoeff: [ScoreOccasion: Double] = [
        .work: 1.00, .date: 1.00, .wedding: 1.25, .casual: 0.50, .everyday: 0.50,
    ]

    static let overCoeff: [ScoreOccasion: Double] = [
        .work: 0.75, .date: 0.90, .wedding: 1.00, .casual: 1.00, .everyday: 1.00,
    ]

    // MARK: Lookups

    /// Caption for a dial position, 1…5. Out-of-range returns "".
    static func formalityCaption(_ occasion: ScoreOccasion, _ formality: Int) -> String {
        guard let row = formalityCaptions[occasion], (1...row.count).contains(formality) else { return "" }
        return row[formality - 1]
    }

    static func presenceCaption(_ presence: Int) -> String {
        guard (1...presenceCaptions.count).contains(presence) else { return "" }
        return presenceCaptions[presence - 1]
    }

    static func formalityReadLabel(_ read: Int) -> String {
        guard (1...formalityReadLabels.count).contains(read) else { return "" }
        return formalityReadLabels[read - 1]
    }

    /// The absolute formality rung a dial position implies.
    static func target(_ occasion: ScoreOccasion, _ formality: Int) -> Int {
        guard let row = targetFormality[occasion], (1...row.count).contains(formality) else { return formality }
        return row[formality - 1]
    }
}

struct IntentChip: Identifiable, Hashable, Sendable {
    var label: String
    var axis: AxisKey
    var id: String { label }
}
