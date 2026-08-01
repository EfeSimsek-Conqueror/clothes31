import Foundation
import SwiftUI

/// Style DNA — deterministic snapshot computed from the user's outfits +
/// style profile. Cheap enough to recompute on demand (no server, no cache).
struct StyleDna {
    let month: String                 // "2026-07" or "" for all-time
    let fitCount: Int
    let studioCount: Int
    let avgScore: Double?
    let bestScore: Double?
    let paletteHex: [String]          // up to 5 hex swatches
    let topOccasion: String?
    let topKind: String?              // most-used feature (score/tryon/…)
    let moodWord: String              // one-word month mood
    let archetypeDeclared: String?
    let archetypeObserved: String
    let driftScore: Double            // 0 = perfectly aligned, 1 = totally drifted
    let bestFitPath: String?

    var subline: String {
        // Editorial one-liner Hem might write about the month
        let mood = moodWord.lowercased()
        if let a = archetypeDeclared, a.lowercased() != archetypeObserved.lowercased() {
            return "A declared \(a.lowercased()) leaning \(archetypeObserved.lowercased()) — \(mood)."
        }
        return "A \(archetypeObserved.lowercased()) month — \(mood)."
    }
}

enum StyleDnaBuilder {

    /// Build a Style DNA for a specific month key ("YYYY-MM"). Pass `nil` for
    /// all-time. Palette + observed archetype are derived from scored fits +
    /// closet items.
    static func build(
        outfits: [Outfit],
        closet: [ClosetItem],
        profile: Profile?,
        monthKey: String? = nil
    ) -> StyleDna {
        let scoped: [Outfit] = {
            guard let m = monthKey else { return outfits }
            return outfits.filter { ($0.created_at ?? "").hasPrefix(m) }
        }()

        let scored = scoped.compactMap { $0.score }.filter { $0 > 0 }
        let studioKinds: Set<String> = ["studio_gen", "tryon", "roast", "decode"]
        let studioCount = scoped.filter { studioKinds.contains($0.kind ?? "") }.count
        let fitCount = scoped.count - studioCount

        let avg = scored.isEmpty ? nil : scored.reduce(0, +) / Double(scored.count)
        let best = scored.max()
        let bestOutfit = scoped.max { ($0.score ?? -1) < ($1.score ?? -1) }

        // Top occasion (weighted by score presence)
        let occFreq = scoped
            .compactMap { $0.occasion?.lowercased() }
            .reduce(into: [String: Int]()) { $0[$1, default: 0] += 1 }
        let topOcc: String? = occFreq.max(by: { $0.value < $1.value }).map { entry in
            let s = entry.key
            return s.prefix(1).uppercased() + s.dropFirst()
        }

        // Top kind
        let kindFreq = scoped
            .compactMap { $0.kind }
            .reduce(into: [String: Int]()) { $0[$1, default: 0] += 1 }
        let topKind = kindFreq.max { $0.value < $1.value }?.key

        // Palette — start with dominant_colors on scored fits, fallback to
        // closet piece color hexes.
        var seen = Set<String>()
        var palette: [String] = []
        for o in scoped where !palette.contains(where: { $0 == "cap" }) {
            let cols = colorsFromOutfit(o)
            for hex in cols where palette.count < 5 && seen.insert(hex).inserted {
                palette.append(hex)
            }
            if palette.count >= 5 { break }
        }
        if palette.count < 5 {
            for c in closet {
                if let h = c.color_hex, seen.insert(h).inserted { palette.append(h) }
                if palette.count >= 5 { break }
            }
        }

        // Observed archetype — very rough heuristic
        let observed = observeArchetype(closet: closet, palette: palette, avg: avg)

        // Declared archetype from profile.style_tags (first tag if any)
        let declared: String? = profile?.style_tags?.first.map { s in
            s.prefix(1).uppercased() + s.dropFirst()
        }

        let drift: Double = {
            guard let d = declared else { return 0 }
            return d.lowercased() == observed.lowercased() ? 0.0 : 0.55
        }()

        let mood: String = {
            guard let a = avg else { return "quiet" }
            switch a {
            case 8.5...: return "confident"
            case 7.5..<8.5: return "sharp"
            case 6.5..<7.5: return "steady"
            default: return "exploratory"
            }
        }()

        return StyleDna(
            month: monthKey ?? "",
            fitCount: fitCount,
            studioCount: studioCount,
            avgScore: avg,
            bestScore: best,
            paletteHex: palette,
            topOccasion: topOcc,
            topKind: topKind,
            moodWord: mood,
            archetypeDeclared: declared,
            archetypeObserved: observed,
            driftScore: drift,
            bestFitPath: bestOutfit?.photo_path
        )
    }

    // Extracts up to 5 hex colors from an Outfit's dominant_colors JSON blob if present.
    private static func colorsFromOutfit(_ o: Outfit) -> [String] {
        // Outfit model may or may not have a dominant_colors field parsed. Best-effort:
        // walk through common places where colors could show up.
        if let hex = o.hem_comment, hex.hasPrefix("#") { return [String(hex.prefix(7))] }
        return []
    }

    private static func observeArchetype(closet: [ClosetItem], palette: [String], avg: Double?) -> String {
        // Rough archetype inference — based on the mix of closet categories +
        // palette warmth. Deterministic, easily replaceable later.
        let cats = closet.compactMap { $0.category?.lowercased() }
        let hasStreet = cats.filter { $0 == "outerwear" || $0 == "shoes" }.count > 3
        let dressy = cats.filter { $0 == "dress" }.count > 2
        let earthy = palette.filter { isEarthy($0) }.count >= 2

        if dressy && earthy { return "Romantic" }
        if hasStreet { return "Streetwear" }
        if earthy { return "Minimalist" }
        if let a = avg, a >= 8 { return "Classic" }
        return "Modern"
    }

    private static func isEarthy(_ hex: String) -> Bool {
        var s = hex; if s.hasPrefix("#") { s.removeFirst() }
        guard s.count >= 6, let v = Int(s.prefix(6), radix: 16) else { return false }
        let r = (v >> 16) & 0xFF, g = (v >> 8) & 0xFF, b = v & 0xFF
        // warm, muted: red > blue, moderate saturation
        return r > b && max(r, g, b) < 220 && abs(r - g) < 60
    }
}
