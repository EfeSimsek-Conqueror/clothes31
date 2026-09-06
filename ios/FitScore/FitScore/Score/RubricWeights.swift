import Foundation

// Scoring v4 — the weight PREVIEW.
//
// The server is authoritative. This is a port of `pass()` and `primaryAxes()`
// from `supabase/functions/_shared/rubric/weights.ts` and nothing else: it
// exists so the brief sheet can say "grading hardest on: dress code, fit,
// detail, silhouette" before a credit is spent. It never computes a score, it
// never sees an axis read, and the number the user is finally shown always
// comes off the wire.
//
// The port keeps the server's exact order of operations — modulate, deltas,
// intent, no-back, clamp, drop nulls, normalise, post-normalisation CTX floor —
// because the steps are not commutative: clamping before modulating collapses a
// cocktail wedding and a black-tie wedding onto the same vector, and applying
// the CTX floor before normalising breaks the guarantee the UI states.
//
// Ties are broken by `AXIS_KEYS` order. V8's sort is stable, Swift's is not, so
// the tie-break has to be written down or the two would disagree on a corner.

enum RubricWeights {

    /// The final, normalised weight vector for a brief. Rounded to 3dp with the
    /// rounding residual pushed onto the heaviest axis, so Σw == 1.000 exactly —
    /// same as `buildWeights`, whose attribution pass the client does not need.
    ///
    /// - Parameters:
    ///   - nullAxes: axes the photograph could not support. Empty before a
    ///     score exists, which is the only time this is used for a preview.
    ///   - intentAxis: the axis the wearer's stated intent maps to.
    static func weights(
        for intake: ScoreIntake,
        nullAxes: [AxisKey] = [],
        intentAxis: AxisKey? = nil
    ) -> [AxisKey: Double] {
        var w = pass(intake, nullAxes: nullAxes, intentAxis: intentAxis)
        for a in AxisKey.allCases { w[a] = round3(w[a] ?? 0) }

        let order = AxisKey.allCases
            .filter { (w[$0] ?? 0) > 0 }
            .sorted { heavier($0, $1, in: w) }
        if let top = order.first {
            let sum = order.reduce(0.0) { $0 + (w[$1] ?? 0) }
            w[top] = round3((w[top] ?? 0) + (1 - sum))
        }
        return w
    }

    /// The axes this brief grades hardest on — the preview strip. Four by
    /// default, matching the server's `primary_axes`.
    static func primaryAxes(
        for intake: ScoreIntake,
        count: Int = 4,
        intentAxis: AxisKey? = nil
    ) -> [AxisKey] {
        let w = weights(for: intake, nullAxes: [], intentAxis: intentAxis)
        return Array(AxisKey.allCases.sorted { heavier($0, $1, in: w) }.prefix(count))
    }

    // MARK: - Internals

    /// Descending by weight, ties broken by declaration order.
    private static func heavier(_ a: AxisKey, _ b: AxisKey, in w: [AxisKey: Double]) -> Bool {
        let wa = w[a] ?? 0, wb = w[b] ?? 0
        return wa == wb ? a.order < b.order : wa > wb
    }

    /// Ascending by weight, ties broken by declaration order.
    private static func lighter(_ a: AxisKey, _ b: AxisKey, in w: [AxisKey: Double]) -> Bool {
        let wa = w[a] ?? 0, wb = w[b] ?? 0
        return wa == wb ? a.order < b.order : wa < wb
    }

    private static func clamp(_ n: Double, _ lo: Double, _ hi: Double) -> Double {
        Swift.max(lo, Swift.min(hi, n))
    }

    private static func round3(_ n: Double) -> Double { (n * 1000).rounded() / 1000 }

    /// Every band-4 and cross-cutting delta that applies to this brief, in the
    /// order they are disclosed to the user.
    private static func sources(_ intake: ScoreIntake) -> [[AxisKey: Double]] {
        var out: [[AxisKey: Double]] = []
        if intake.occasion == .wedding, let role = intake.role {
            out.append(Rubric.roleDeltas[role] ?? [:])
        }
        if intake.occasion == .wedding, let venue = intake.venue {
            out.append(Rubric.venueDeltas[venue] ?? [:])
        }
        if intake.occasion == .work, let room = intake.room {
            out.append(Rubric.roomDeltas[room] ?? [:])
        }
        if intake.occasion == .everyday, let onFeet = intake.on_feet {
            out.append(Rubric.onFeetDeltas[onFeet] ?? [:])
        }
        if let weather = intake.weather {
            out.append(Rubric.weatherDeltas[weather.band] ?? [:])
            if weather.precip { out.append(Rubric.precipDelta) }
        }
        out.append(Rubric.timeDeltas[intake.time_of_day] ?? [:])
        return out
    }

    /// One full pass of the weight pipeline. Deterministic.
    private static func pass(
        _ intake: ScoreIntake,
        nullAxes: [AxisKey],
        intentAxis: AxisKey?
    ) -> [AxisKey: Double] {
        let base = Rubric.baseWeights[intake.occasion] ?? [:]
        var w: [AxisKey: Double] = [:]

        // 1 — dial modulation.
        for a in AxisKey.allCases {
            let alpha = Rubric.alpha[a] ?? 0
            let beta = Rubric.beta[a] ?? 0
            var m = (1 + alpha * Double(intake.formality - 3) / 2)
                  * (1 + beta * Double(intake.presence - 3) / 2)
            m = clamp(m, Rubric.mMin, Rubric.mMax)
            w[a] = (base[a] ?? 0) * m
        }

        // 2 — band-4 and cross-cutting deltas.
        for source in sources(intake) {
            for (axis, d) in source { w[axis] = (w[axis] ?? 0) + d }
        }

        // 3 — intent buys its axis weight from the two lightest OTHER axes, so an
        //     intent that lands on an already-light axis cannot donate to itself.
        if let intentAxis, !nullAxes.contains(intentAxis) {
            let donors = AxisKey.allCases
                .filter { $0 != intentAxis && !nullAxes.contains($0) }
                .sorted { lighter($0, $1, in: w) }
                .prefix(2)
            let donorTotal = donors.reduce(0.0) { $0 + (w[$1] ?? 0) }
            if donorTotal > 0 {
                w[intentAxis] = (w[intentAxis] ?? 0) + Rubric.intentWeightBonus
                for d in donors {
                    w[d] = (w[d] ?? 0) - Rubric.intentWeightBonus * ((w[d] ?? 0) / donorTotal)
                }
            }
        }

        // 4 — a fit read without a back view is a partial one.
        if intake.has_back != true { w[.FIT] = (w[.FIT] ?? 0) * Rubric.noBackFitMultiplier }

        // 5 — clamp AFTER modulation, not before.
        for a in AxisKey.allCases { w[a] = clamp(w[a] ?? 0, Rubric.wMin, Rubric.wMax) }

        // 6 — an axis the photo cannot support is dropped, never guessed at 5.
        for a in nullAxes { w[a] = 0 }

        // 7 — normalise over what is left.
        let total = AxisKey.allCases.reduce(0.0) { $0 + (w[$1] ?? 0) }
        if total > 0 { for a in AxisKey.allCases { w[a] = (w[a] ?? 0) / total } }

        // 8 — the dress-code floor, applied AFTER normalisation so the guarantee
        //     is the one the UI states: CTX never falls below half its occasion
        //     share, which is what stops presence=5 silently cancelling
        //     formality=5.
        if !nullAxes.contains(.CTX) {
            let floor = 0.5 * (base[.CTX] ?? 0)
            if (w[.CTX] ?? 0) < floor {
                let rest = AxisKey.allCases.filter { $0 != .CTX }
                let restTotal = rest.reduce(0.0) { $0 + (w[$1] ?? 0) }
                if restTotal > 0 {
                    let scale = (1 - floor) / restTotal
                    for a in rest { w[a] = (w[a] ?? 0) * scale }
                    w[.CTX] = floor
                }
            }
        }

        return w
    }
}
