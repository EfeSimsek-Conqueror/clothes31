package com.fitrater.app.scoring

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Scoring v4 — a port of `pass()` and `primaryAxes()` from
 * `supabase/functions/_shared/rubric/weights.ts`.
 *
 * This exists for the preview strip ONLY: "grading hardest on: dress code, fit,
 * detail, silhouette", rendered live as the user moves the dials and before a
 * credit is spent. The headline is always the server's — nothing here is ever
 * multiplied by an axis score.
 *
 * The leave-one-out attribution half of `buildWeights()` is deliberately not
 * ported: the server sends its own `weight_deltas` on the response, and a second
 * implementation of it would be a second thing to keep in sync for no gain.
 */

/** The subset of the intake that the weight pipeline actually reads. */
data class PreviewIntake(
    val occasion: String = RubricTables.EVERYDAY,
    /** 1..5 — how dressed up the wearer intended to be. */
    val formality: Int = 2,
    /** 1..5 — how much the wearer wanted to be looked at. */
    val presence: Int = 3,
    val role: String? = null,
    val venue: String? = null,
    val room: String? = null,
    val onFeet: String? = null,
    /** Axis the wearer's stated intent maps to, when a chip resolves it. */
    val intentAxis: AxisKey? = null,
    /** Omitted entirely when the device could not supply it — never guessed. */
    val weatherBand: String? = null,
    val precip: Boolean = false,
    val timeOfDay: String = RubricTables.DEFAULT_TIME_OF_DAY,
    /** True when a back view was supplied. */
    val hasBack: Boolean = false,
)

object RubricWeights {

    private fun clamp(n: Double, lo: Double, hi: Double): Double = max(lo, min(hi, n))

    private fun round3(n: Double): Double = (n * 1000.0).roundToInt() / 1000.0

    /**
     * Every band-4 and cross-cutting delta that applies to this intake. Fields
     * belonging to another occasion are dropped, exactly as the server drops
     * them, so a `room` left over from a work brief cannot arm a wedding rule.
     */
    private fun sourcesFor(intake: PreviewIntake): List<Map<AxisKey, Double>> {
        val out = mutableListOf<Map<AxisKey, Double>>()
        if (intake.occasion == RubricTables.WEDDING && intake.role != null) {
            RubricTables.ROLE_DELTAS[intake.role]?.let { out.add(it) }
        }
        if (intake.occasion == RubricTables.WEDDING && intake.venue != null) {
            RubricTables.VENUE_DELTAS[intake.venue]?.let { out.add(it) }
        }
        if (intake.occasion == RubricTables.WORK && intake.room != null) {
            RubricTables.ROOM_DELTAS[intake.room]?.let { out.add(it) }
        }
        if (intake.occasion == RubricTables.EVERYDAY && intake.onFeet != null) {
            RubricTables.ON_FEET_DELTAS[intake.onFeet]?.let { out.add(it) }
        }
        if (intake.weatherBand != null) {
            RubricTables.WEATHER_DELTAS[intake.weatherBand]?.let { out.add(it) }
            if (intake.precip) out.add(RubricTables.PRECIP_DELTA)
        }
        RubricTables.TIME_DELTAS[intake.timeOfDay]?.let { out.add(it) }
        return out
    }

    /**
     * One full pass of the weight pipeline. Deterministic — the same intake
     * always produces the same vector here and on the server.
     */
    private fun pass(intake: PreviewIntake, nullAxes: Set<AxisKey>): MutableMap<AxisKey, Double> {
        val base = RubricTables.BASE_WEIGHTS[intake.occasion]
            ?: RubricTables.BASE_WEIGHTS.getValue(RubricTables.EVERYDAY)
        val w = mutableMapOf<AxisKey, Double>()

        // 1 — dial modulation.
        for (a in AxisKey.ALL) {
            val alpha = RubricTables.ALPHA.getValue(a)
            val beta = RubricTables.BETA.getValue(a)
            var m = (1.0 + alpha * (intake.formality - 3) / 2.0) *
                (1.0 + beta * (intake.presence - 3) / 2.0)
            m = clamp(m, RubricTables.M_MIN, RubricTables.M_MAX)
            w[a] = base.getValue(a) * m
        }

        // 2 — band-4 and cross-cutting deltas.
        for (source in sourcesFor(intake)) {
            for ((axis, d) in source) {
                w[axis] = w.getValue(axis) + d
            }
        }

        // 3 — intent buys its axis weight from the two lightest OTHER axes, so
        //     an intent that lands on an already-light axis cannot donate to
        //     itself.
        val intentAxis = intake.intentAxis
        if (intentAxis != null && intentAxis !in nullAxes) {
            val donors = AxisKey.ALL
                .filter { it != intentAxis && it !in nullAxes }
                .sortedBy { w.getValue(it) }
                .take(2)
            val donorTotal = donors.sumOf { w.getValue(it) }
            if (donorTotal > 0.0) {
                w[intentAxis] = w.getValue(intentAxis) + RubricTables.INTENT_WEIGHT_BONUS
                for (d in donors) {
                    w[d] = w.getValue(d) -
                        RubricTables.INTENT_WEIGHT_BONUS * (w.getValue(d) / donorTotal)
                }
            }
        }

        // 4 — a fit read without a back view is a partial one.
        if (!intake.hasBack) {
            w[AxisKey.FIT] = w.getValue(AxisKey.FIT) * RubricTables.NO_BACK_FIT_MULTIPLIER
        }

        // 5 — clamp AFTER modulation, not before.
        for (a in AxisKey.ALL) {
            w[a] = clamp(w.getValue(a), RubricTables.W_MIN, RubricTables.W_MAX)
        }

        // 6 — an axis the photo cannot support is dropped, never guessed at 5.
        for (a in nullAxes) w[a] = 0.0

        // 7 — normalise over what is left.
        val total = AxisKey.ALL.sumOf { w.getValue(it) }
        if (total > 0.0) for (a in AxisKey.ALL) w[a] = w.getValue(a) / total

        // 8 — the dress-code floor, applied AFTER normalisation: CTX never falls
        //     below half its occasion share, which is what stops presence=5
        //     silently cancelling formality=5.
        if (AxisKey.CTX !in nullAxes) {
            val floor = 0.5 * base.getValue(AxisKey.CTX)
            if (w.getValue(AxisKey.CTX) < floor) {
                val rest = AxisKey.ALL.filter { it != AxisKey.CTX }
                val restTotal = rest.sumOf { w.getValue(it) }
                if (restTotal > 0.0) {
                    val scale = (1.0 - floor) / restTotal
                    for (a in rest) w[a] = w.getValue(a) * scale
                    w[AxisKey.CTX] = floor
                }
            }
        }

        return w
    }

    /**
     * The final weight vector, rounded to 3dp with the rounding residual pushed
     * onto the heaviest axis so the displayed percentages sum to 100 — the same
     * fixup the server applies before it sends them.
     */
    fun weights(intake: PreviewIntake, nullAxes: Set<AxisKey> = emptySet()): Map<AxisKey, Double> {
        val full = pass(intake, nullAxes)
        val w = LinkedHashMap<AxisKey, Double>(AxisKey.ALL.size)
        for (a in AxisKey.ALL) w[a] = round3(full.getValue(a))
        val order = AxisKey.ALL.filter { w.getValue(it) > 0.0 }.sortedByDescending { w.getValue(it) }
        if (order.isNotEmpty()) {
            val sum = order.sumOf { w.getValue(it) }
            val heaviest = order.first()
            w[heaviest] = round3(w.getValue(heaviest) + (1.0 - sum))
        }
        return w
    }

    /** The axes this intake grades hardest on — the client's preview strip. */
    fun primaryAxes(intake: PreviewIntake, count: Int = 4): List<AxisKey> {
        val w = weights(intake)
        return AxisKey.ALL.sortedByDescending { w[it] ?: 0.0 }.take(count)
    }

    /**
     * "Grading hardest on: dress code, fit, detail." Lower-cased labels, joined
     * the way a sentence joins them.
     */
    fun primaryAxesLine(intake: PreviewIntake, count: Int = 3): String =
        primaryAxes(intake, count).joinToString(" · ") { it.label.lowercase() }
}
