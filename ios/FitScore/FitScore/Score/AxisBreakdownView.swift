import SwiftUI

// Scoring v4 — the axis breakdown.
//
// Two visual channels, never one: the BAR LENGTH is the score, the RULE under
// it is the weight. A 9.0 on an axis worth 4% and a 6.0 on an axis worth 33%
// have to look different at a glance, or the intake the user filled in reads as
// decoration. The "·33%" numeral is the literal payoff for having answered.
//
// Nothing here computes. Every number is off the wire.

// MARK: - Bar primitive

/// Length = score. Under-rule = weight, scaled against the heaviest axis in the
/// same set so the rules are comparable across rows.
///
/// When a rule capped the axis, `rawScore` draws the read the model actually
/// gave as a hollow outline extending past the filled bar — the gap between the
/// two is the rule's doing, and it should be visible rather than argued.
struct AxisScoreBar: View {
    let score: Double?
    var rawScore: Double? = nil
    let weight: Double
    let maxWeight: Double
    var tint: Color = Palette.ink
    var barHeight: CGFloat = 7

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            ZStack(alignment: .leading) {
                Capsule().fill(Palette.hairline)
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        if let raw = rawScore, let s = score, raw > s + 0.05 {
                            Capsule()
                                .strokeBorder(tint.opacity(0.35), lineWidth: 1)
                                .frame(width: max(2, g.size.width * frac(raw)))
                        }
                        if let s = score {
                            Capsule()
                                .fill(tint)
                                .frame(width: max(2, g.size.width * frac(s)))
                        }
                    }
                    .frame(height: barHeight)
                }
            }
            .frame(height: barHeight)

            GeometryReader { g in
                Rectangle()
                    .fill(Palette.bronze.opacity(0.6))
                    .frame(width: max(1, g.size.width * weightFrac), height: 2)
            }
            .frame(height: 2)
        }
    }

    private func frac(_ v: Double) -> CGFloat { CGFloat(min(max(v, 0), 10) / 10) }
    private var weightFrac: CGFloat {
        guard maxWeight > 0 else { return 0 }
        return CGFloat(min(weight / maxWeight, 1))
    }
}

// MARK: - Breakdown

struct AxisBreakdownView: View {
    let axes: [Axis]
    /// `R-BT` → "Black tie, absent". Lifted off `score_breakdown.rules_fired`
    /// so a cap can be named rather than shown as an opaque rule id.
    var ruleNames: [String: String] = [:]
    /// How many rows stay open. The brief's primary axes are the first four.
    var expandedCount: Int = 4

    @State private var showRest = false

    var body: some View {
        let ordered = axes.v4Ordered
        let head = Array(ordered.prefix(expandedCount))
        let tail = Array(ordered.dropFirst(expandedCount))
        let maxWeight = ordered.map(\.weight).max() ?? 0

        VStack(alignment: .leading, spacing: 0) {
            ForEach(head) { axis in
                AxisRow(axis: axis, maxWeight: maxWeight, ruleNames: ruleNames)
                Rectangle().fill(Palette.hairline).frame(height: 0.5)
            }

            if !tail.isEmpty {
                if showRest {
                    ForEach(tail) { axis in
                        AxisRow(axis: axis, maxWeight: maxWeight, ruleNames: ruleNames)
                        Rectangle().fill(Palette.hairline).frame(height: 0.5)
                    }
                }
                Button {
                    Haptic.chip()
                    withAnimation(.easeInOut(duration: 0.2)) { showRest.toggle() }
                } label: {
                    HStack(spacing: 6) {
                        Text(showRest ? "hide the rest" : "the rest of the read")
                            .font(Serif.body(13))
                        Image(systemName: showRest ? "chevron.up" : "chevron.down")
                            .font(.system(size: 10, weight: .semibold))
                    }
                    .foregroundStyle(Palette.bronze)
                    .padding(.vertical, 12)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
    }
}

/// One axis. Label, weight numeral, score, the two bars, the evidence sentence,
/// then whatever the engine did to it.
private struct AxisRow: View {
    let axis: Axis
    let maxWeight: Double
    let ruleNames: [String: String]

    var body: some View {
        let dead = axis.isUnjudgeable
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(axis.label)
                    .font(Serif.body(15, weight: .medium))
                    .foregroundStyle(dead ? Palette.muted : Palette.ink)
                if axis.isIntentAxis {
                    Text("← YOUR ASK")
                        .font(.system(size: 9, weight: .semibold))
                        .tracking(1.2)
                        .foregroundStyle(Palette.bronze)
                        .padding(.horizontal, 6).padding(.vertical, 3)
                        .overlay(RoundedRectangle(cornerRadius: 3).stroke(Palette.bronze, lineWidth: 1))
                }
                Spacer(minLength: 8)
                Text("·\(axis.weightPercent)%")
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(1)
                    .foregroundStyle(Palette.bronze)
                Text(dead ? "—" : String(format: "%.1f", axis.score ?? 0))
                    .font(Serif.display(17, weight: .semibold))
                    .foregroundStyle(dead ? Palette.muted : Palette.ink)
                    .frame(minWidth: 34, alignment: .trailing)
            }

            AxisScoreBar(
                score: dead ? nil : axis.score,
                rawScore: axis.raw_score,
                weight: axis.weight,
                maxWeight: maxWeight,
                tint: dead ? Palette.muted.opacity(0.4) : Palette.ink
            )

            if dead {
                Text("I couldn't grade this from the photo. Its share went to the rest — I didn't guess, and I didn't hold it against you.")
                    .font(Serif.body(13))
                    .foregroundStyle(Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            } else if !axis.evidence.isEmpty {
                Text(axis.evidence)
                    .font(Serif.body(13))
                    .foregroundStyle(Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }

            // A cap is the rulebook's doing, not the model's opinion — say so,
            // and show the read it overwrote.
            if let rule = axis.capped_by, let raw = axis.raw_score, let s = axis.score, raw > s + 0.05 {
                note("\(named(rule)) pulled this from \(fmt(raw)) to \(fmt(s)).")
            } else if let rule = axis.capped_by {
                note("Capped by \(named(rule)).")
            }
            if let ceiling = axis.ceiling {
                note("Held under \(fmt(ceiling)) by \(named(axis.ceiling_rule ?? "the evidence rule")) — the photo couldn't support more.")
            }
            if let floor = axis.floored_by {
                note("Floored by \(named(floor)) — it wasn't allowed to fall further.")
            }
            if axis.source == "engine" && !dead {
                note("Measured by the rulebook, not read off the photo.")
            }
        }
        .padding(.vertical, 14)
    }

    private func note(_ text: String) -> some View {
        HStack(alignment: .top, spacing: 6) {
            Rectangle().fill(Palette.bronze.opacity(0.5)).frame(width: 2)
            Text(text)
                .font(Serif.body(12))
                .foregroundStyle(Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .fixedSize(horizontal: false, vertical: true)
    }

    private func named(_ ruleId: String) -> String {
        if let n = ruleNames[ruleId], !n.isEmpty { return n }
        return ruleId
    }

    private func fmt(_ v: Double) -> String { String(format: "%.1f", v) }
}

// MARK: - The literal weight table

/// "How is this calculated?" — the nine weights as sent, and the fact that they
/// sum to one. No prose: the point of this disclosure is that it is checkable.
struct AxisWeightTable: View {
    let axes: [Axis]
    var source: String? = nil

    var body: some View {
        let ordered = axes.v4Ordered
        let total = ordered.reduce(0.0) { $0 + $1.weight }
        VStack(alignment: .leading, spacing: 0) {
            Text("Your answers set nine weights. Each axis is scored out of ten, multiplied by its weight, and added up. Caps and floors are applied after.")
                .font(Serif.body(13))
                .foregroundStyle(Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.bottom, 12)

            ForEach(ordered) { a in
                HStack {
                    Text(a.label).font(Serif.body(14)).foregroundStyle(Palette.ink)
                    if a.isUnjudgeable {
                        Text("· not graded")
                            .font(Serif.body(12))
                            .foregroundStyle(Palette.muted)
                    }
                    Spacer()
                    Text(String(format: "%.1f%%", a.weight * 100))
                        .font(Serif.body(13, weight: .medium))
                        .foregroundStyle(Palette.muted)
                }
                .padding(.vertical, 7)
                Rectangle().fill(Palette.hairline).frame(height: 0.5)
            }

            HStack {
                Text("Total").font(Serif.body(14, weight: .medium)).foregroundStyle(Palette.ink)
                Spacer()
                Text(String(format: "%.0f%%", total * 100))
                    .font(Serif.body(13, weight: .medium))
                    .foregroundStyle(Palette.bronze)
            }
            .padding(.vertical, 8)

            if let source, !source.isEmpty {
                Text(source)
                    .font(Serif.body(12))
                    .foregroundStyle(Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}
