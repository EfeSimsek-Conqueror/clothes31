import SwiftUI

// MARK: - Date parsing helpers (shared with JournalView)

func parseIsoDate(_ iso: String?) -> Date? {
    guard let iso else { return nil }
    let f = ISO8601DateFormatter()
    f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    if let d = f.date(from: iso.replacingOccurrences(of: " ", with: "T")) { return d }
    f.formatOptions = [.withInternetDateTime]
    return f.date(from: iso.replacingOccurrences(of: " ", with: "T"))
}

func parseHexColor(_ hex: String) -> Color? {
    var h = hex
    if h.hasPrefix("#") { h.removeFirst() }
    while h.count < 6 { h = "0" + h }
    guard h.count >= 6, let v = UInt64(h.prefix(6), radix: 16) else { return nil }
    let r = Double((v >> 16) & 0xFF) / 255.0
    let g = Double((v >> 8) & 0xFF) / 255.0
    let b = Double(v & 0xFF) / 255.0
    return Color(red: r, green: g, blue: b)
}

let EN_MONTHS = ["JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"]

func enMonthName(_ m: Int) -> String {
    guard m >= 1, m <= 12 else { return "" }
    return EN_MONTHS[m - 1]
}

// MARK: - Dominant tone detection

private struct ToneEntry {
    let hexes: [String]
    let label: String
}

private let DOM_TONE_TABLE: [ToneEntry] = [
    .init(hexes: ["B0743A","9C5A2E","A0522D","8B4513","C4661F"], label: "rust"),
    .init(hexes: ["F5EBD0","F7F2E8","E8DCC0","EFE7D2"], label: "cream"),
    .init(hexes: ["000000","141210","1A1A1A","222222"], label: "black"),
    .init(hexes: ["1F2A44","1A2238","0F1E3D"], label: "navy"),
    .init(hexes: ["6B7A3A","556B2F","7A8450"], label: "olive"),
    .init(hexes: ["A9A18C","7A6A55","8B8378"], label: "stone"),
]

private func colorClose(_ a: String, _ b: String) -> Bool {
    guard a.count >= 6, b.count >= 6,
          let ar = Int(a.prefix(2), radix: 16),
          let ag = Int(a.dropFirst(2).prefix(2), radix: 16),
          let ab = Int(a.dropFirst(4).prefix(2), radix: 16),
          let br = Int(b.prefix(2), radix: 16),
          let bg = Int(b.dropFirst(2).prefix(2), radix: 16),
          let bb = Int(b.dropFirst(4).prefix(2), radix: 16) else { return false }
    return abs(ar - br) + abs(ag - bg) + abs(ab - bb) < 120
}

func dominantToneLabel(_ outfits: [Outfit]) -> String? {
    let hexes: [String] = outfits.flatMap { $0.dominant_colors ?? [] }
        .compactMap { raw in
            var h = raw
            if h.hasPrefix("#") { h.removeFirst() }
            while h.count < 6 { h = "0" + h }
            let up = h.uppercased()
            return up.count >= 6 ? String(up.prefix(6)) : nil
        }
    guard !hexes.isEmpty else { return nil }
    var tally: [String: Int] = [:]
    for hex in hexes {
        let key = DOM_TONE_TABLE.first(where: { entry in
            entry.hexes.contains(where: { colorClose(hex, $0) })
        })?.label ?? "mixed"
        tally[key, default: 0] += 1
    }
    return tally.filter { $0.key != "mixed" }.max(by: { $0.value < $1.value })?.key
}
