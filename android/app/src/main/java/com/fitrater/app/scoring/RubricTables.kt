package com.fitrater.app.scoring

/**
 * Scoring v4 — a hand port of the DATA in
 * `supabase/functions/_shared/rubric/rubric.v4.ts`. Every number here is copied
 * verbatim from that file, which remains the only source of truth.
 *
 * The client holds a copy for exactly one reason: to render the brief line and
 * the "grading hardest on …" preview strip BEFORE a credit is spent. It never
 * computes a score — the headline always comes back from the server.
 *
 * If this file and `rubric.v4.ts` ever disagree, the preview strip is wrong and
 * the score is right. Re-port; do not "fix" the server to match.
 */

/** The nine axes, in the server's `AXIS_KEYS` order. Labels are `AXIS_LABELS`. */
enum class AxisKey(val label: String) {
    SIL("Silhouette"),
    FIT("Fit"),
    COL("Colour"),
    TEX("Fabric"),
    CTX("Dress code"),
    DTL("Detail"),
    SHO("Footwear"),
    PRS("Upkeep"),
    POV("Point of view"),
    ;

    companion object {
        /** Declaration order — identical to the server's `AXIS_KEYS`. */
        val ALL: List<AxisKey> = listOf(
            AxisKey.SIL, AxisKey.FIT, AxisKey.COL, AxisKey.TEX, AxisKey.CTX,
            AxisKey.DTL, AxisKey.SHO, AxisKey.PRS, AxisKey.POV,
        )

        /** Tolerant lookup for a key that arrived over the wire. */
        fun from(raw: String?): AxisKey? {
            val v = raw?.trim()?.uppercase() ?: return null
            return ALL.firstOrNull { it.name == v }
        }
    }
}

object RubricTables {

    const val RUBRIC_VERSION: Int = 1

    // ---- occasions -------------------------------------------------------

    const val WORK = "work"
    const val DATE = "date"
    const val WEDDING = "wedding"
    const val CASUAL = "casual"
    const val EVERYDAY = "everyday"

    /** Chip order in the sheet. Wire values are lower case; labels are display. */
    val OCCASIONS: List<String> = listOf(WORK, DATE, WEDDING, CASUAL, EVERYDAY)

    val OCCASION_LABELS: Map<String, String> = mapOf(
        WORK to "Work",
        DATE to "Date",
        WEDDING to "Wedding",
        CASUAL to "Casual",
        EVERYDAY to "Everyday",
    )

    /**
     * Base weight vectors at the neutral dial pair (f=3, p=3). Each column sums
     * to 1.000.
     */
    val BASE_WEIGHTS: Map<String, Map<AxisKey, Double>> = mapOf(
        WORK to mapOf(
            AxisKey.SIL to 0.13, AxisKey.FIT to 0.17, AxisKey.COL to 0.10,
            AxisKey.TEX to 0.09, AxisKey.CTX to 0.17, AxisKey.DTL to 0.12,
            AxisKey.SHO to 0.09, AxisKey.PRS to 0.07, AxisKey.POV to 0.06,
        ),
        DATE to mapOf(
            AxisKey.SIL to 0.15, AxisKey.FIT to 0.15, AxisKey.COL to 0.12,
            AxisKey.TEX to 0.12, AxisKey.CTX to 0.10, AxisKey.DTL to 0.10,
            AxisKey.SHO to 0.07, AxisKey.PRS to 0.05, AxisKey.POV to 0.14,
        ),
        WEDDING to mapOf(
            AxisKey.SIL to 0.13, AxisKey.FIT to 0.15, AxisKey.COL to 0.10,
            AxisKey.TEX to 0.09, AxisKey.CTX to 0.26, AxisKey.DTL to 0.12,
            AxisKey.SHO to 0.08, AxisKey.PRS to 0.05, AxisKey.POV to 0.02,
        ),
        CASUAL to mapOf(
            AxisKey.SIL to 0.17, AxisKey.FIT to 0.12, AxisKey.COL to 0.13,
            AxisKey.TEX to 0.13, AxisKey.CTX to 0.03, AxisKey.DTL to 0.09,
            AxisKey.SHO to 0.11, AxisKey.PRS to 0.06, AxisKey.POV to 0.16,
        ),
        EVERYDAY to mapOf(
            AxisKey.SIL to 0.15, AxisKey.FIT to 0.17, AxisKey.COL to 0.12,
            AxisKey.TEX to 0.11, AxisKey.CTX to 0.05, AxisKey.DTL to 0.09,
            AxisKey.SHO to 0.14, AxisKey.PRS to 0.07, AxisKey.POV to 0.10,
        ),
    )

    /**
     * Dial modulation. `m[a] = (1 + a*(f-3)/2) * (1 + b*(p-3)/2)`. Dressier ⇒
     * correctness, tailoring and upkeep matter more and self-expression matters
     * less; louder ⇒ colour, detail and point of view matter more.
     */
    val ALPHA: Map<AxisKey, Double> = mapOf(
        AxisKey.SIL to 0.00, AxisKey.FIT to 0.35, AxisKey.COL to -0.10,
        AxisKey.TEX to 0.10, AxisKey.CTX to 0.60, AxisKey.DTL to 0.25,
        AxisKey.SHO to 0.15, AxisKey.PRS to 0.45, AxisKey.POV to -0.35,
    )

    val BETA: Map<AxisKey, Double> = mapOf(
        AxisKey.SIL to 0.20, AxisKey.FIT to -0.20, AxisKey.COL to 0.45,
        AxisKey.TEX to 0.10, AxisKey.CTX to -0.30, AxisKey.DTL to 0.35,
        AxisKey.SHO to 0.00, AxisKey.PRS to -0.15, AxisKey.POV to 0.70,
    )

    /** Bounds on the modulation multiplier. */
    const val M_MIN: Double = 0.55
    const val M_MAX: Double = 1.75

    /** Bounds on a raw weight, applied AFTER modulation and the band-4 deltas. */
    const val W_MIN: Double = 0.02
    const val W_MAX: Double = 0.40

    /** Seeded dial positions per occasion — tapping a chip snaps both dials. */
    val SEED_FORMALITY: Map<String, Int> = mapOf(
        WORK to 3, DATE to 3, WEDDING to 4, CASUAL to 2, EVERYDAY to 2,
    )

    val SEED_PRESENCE: Map<String, Int> = mapOf(
        WORK to 2, DATE to 4, WEDDING to 3, CASUAL to 3, EVERYDAY to 3,
    )

    /** Band-2 captions, indexed by `formality - 1`. */
    val FORMALITY_CAPTIONS: Map<String, List<String>> = mapOf(
        WORK to listOf(
            "Working from home", "Casual Friday", "Business casual",
            "Client-facing", "Boardroom or interview",
        ),
        DATE to listOf(
            "Coffee or a walk", "A casual bar", "Dinner out",
            "Somewhere nice", "A black-tie event",
        ),
        WEDDING to listOf(
            "Beach or backyard", "Garden party", "Semi-formal",
            "Cocktail", "Black tie",
        ),
        CASUAL to listOf(
            "Errands", "Friends, brunch", "A day out",
            "Dinner or a gig", "A party",
        ),
        EVERYDAY to listOf(
            "Around the house", "Around town", "Out all day",
            "Day into evening", "Something on tonight",
        ),
    )

    /**
     * The dial is occasion-RELATIVE; the model's `formality_read` is ABSOLUTE.
     * This translates a dial position into the absolute rung the brief implies.
     * Client-side it is display only — the gap is always computed on the server.
     */
    val TARGET_FORMALITY: Map<String, List<Int>> = mapOf(
        WORK to listOf(1, 2, 3, 4, 4),
        DATE to listOf(1, 2, 3, 4, 5),
        WEDDING to listOf(2, 3, 4, 4, 5),
        CASUAL to listOf(1, 2, 2, 3, 4),
        EVERYDAY to listOf(1, 2, 2, 3, 4),
    )

    val PRESENCE_CAPTIONS: List<String> = listOf(
        "Invisible", "Quiet", "Balanced", "Noticed", "Be looked at",
    )

    /** Where an outfit ITSELF sits, as reported by the model. Copy only. */
    val FORMALITY_READ_LABELS: List<String> = listOf(
        "Loungewear", "Everyday casual", "Smart casual", "Dressed", "Formal",
    )

    // ---- band 4 ----------------------------------------------------------

    const val ROLE_GUEST = "guest"
    const val ROLE_WEDDING_PARTY = "wedding_party"
    const val ROLE_COUPLE = "couple"

    val ROLES: List<String> = listOf(ROLE_GUEST, ROLE_WEDDING_PARTY, ROLE_COUPLE)

    val ROLE_LABELS: Map<String, String> = mapOf(
        ROLE_GUEST to "Guest",
        ROLE_WEDDING_PARTY to "Wedding party",
        ROLE_COUPLE to "The couple",
    )

    val VENUES: List<String> = listOf("ballroom", "garden", "beach", "registry_or_worship")

    val VENUE_LABELS: Map<String, String> = mapOf(
        "ballroom" to "Ballroom or hotel",
        "garden" to "Garden or outdoors",
        "beach" to "Beach",
        "registry_or_worship" to "Registry or place of worship",
    )

    val ROOMS: List<String> = listOf("creative", "business_casual", "corporate", "client_facing")

    val ROOM_LABELS: Map<String, String> = mapOf(
        "creative" to "Creative",
        "business_casual" to "Business casual",
        "corporate" to "Corporate",
        "client_facing" to "Client-facing",
    )

    val ON_FEET: List<String> = listOf("most_of_day", "some", "seated")

    val ON_FEET_LABELS: Map<String, String> = mapOf(
        "most_of_day" to "On my feet most of the day",
        "some" to "Some walking",
        "seated" to "Mostly seated",
    )

    val WEATHER_LABELS: Map<String, String> = mapOf(
        "cold" to "Cold", "mild" to "Mild", "hot" to "Hot",
    )

    val TIME_LABELS: Map<String, String> = mapOf(
        "daytime" to "Daytime", "evening" to "Evening",
    )

    /** Defaults the server applies when the field is absent or unknown. */
    const val DEFAULT_ROLE = ROLE_GUEST
    const val DEFAULT_VENUE = "ballroom"
    const val DEFAULT_ROOM = "business_casual"
    const val DEFAULT_ON_FEET = "some"
    const val DEFAULT_TIME_OF_DAY = "daytime"

    // ---- weight deltas ---------------------------------------------------
    //
    // Additive on the modulated weight, applied before the clamp. The table
    // does not need to sum to zero — the normaliser absorbs it.

    val ROLE_DELTAS: Map<String, Map<AxisKey, Double>> = mapOf(
        ROLE_GUEST to emptyMap(),
        // You are in every photograph for the rest of these people's lives.
        ROLE_WEDDING_PARTY to mapOf(
            AxisKey.DTL to 0.03, AxisKey.PRS to 0.04, AxisKey.POV to -0.02, AxisKey.COL to -0.05,
        ),
        // It IS your day: the code relaxes, self-expression is the point.
        ROLE_COUPLE to mapOf(
            AxisKey.DTL to 0.05, AxisKey.POV to 0.04, AxisKey.CTX to -0.03,
            AxisKey.PRS to 0.02, AxisKey.COL to -0.02,
        ),
    )

    val VENUE_DELTAS: Map<String, Map<AxisKey, Double>> = mapOf(
        "ballroom" to emptyMap(),
        "garden" to mapOf(
            AxisKey.SHO to 0.03, AxisKey.TEX to 0.02, AxisKey.DTL to -0.02, AxisKey.COL to -0.03,
        ),
        "beach" to mapOf(
            AxisKey.SHO to 0.03, AxisKey.TEX to 0.03, AxisKey.DTL to -0.03,
            AxisKey.COL to -0.03, AxisKey.PRS to -0.02,
        ),
        "registry_or_worship" to mapOf(
            AxisKey.CTX to 0.02, AxisKey.PRS to 0.02, AxisKey.POV to -0.02, AxisKey.COL to -0.02,
        ),
    )

    val ROOM_DELTAS: Map<String, Map<AxisKey, Double>> = mapOf(
        "creative" to mapOf(
            AxisKey.POV to 0.05, AxisKey.COL to 0.02, AxisKey.CTX to -0.05, AxisKey.PRS to -0.02,
        ),
        "business_casual" to emptyMap(),
        "corporate" to mapOf(
            AxisKey.CTX to 0.04, AxisKey.FIT to 0.02, AxisKey.POV to -0.03, AxisKey.TEX to -0.03,
        ),
        "client_facing" to mapOf(
            AxisKey.CTX to 0.03, AxisKey.PRS to 0.03, AxisKey.POV to -0.02,
            AxisKey.COL to -0.02, AxisKey.TEX to -0.02,
        ),
    )

    val ON_FEET_DELTAS: Map<String, Map<AxisKey, Double>> = mapOf(
        "most_of_day" to mapOf(AxisKey.SHO to 0.04, AxisKey.COL to -0.02, AxisKey.POV to -0.02),
        "some" to emptyMap(),
        "seated" to mapOf(AxisKey.SHO to -0.05, AxisKey.DTL to 0.03, AxisKey.PRS to 0.02),
    )

    val WEATHER_DELTAS: Map<String, Map<AxisKey, Double>> = mapOf(
        "cold" to mapOf(
            AxisKey.TEX to 0.03, AxisKey.CTX to 0.02, AxisKey.COL to -0.02, AxisKey.POV to -0.03,
        ),
        "mild" to emptyMap(),
        "hot" to mapOf(AxisKey.TEX to 0.03, AxisKey.PRS to -0.02, AxisKey.SIL to -0.01),
    )

    val PRECIP_DELTA: Map<AxisKey, Double> = mapOf(
        AxisKey.SHO to 0.03, AxisKey.TEX to 0.01, AxisKey.COL to -0.02, AxisKey.DTL to -0.02,
    )

    val TIME_DELTAS: Map<String, Map<AxisKey, Double>> = mapOf(
        "daytime" to mapOf(
            AxisKey.TEX to 0.02, AxisKey.COL to 0.02, AxisKey.DTL to -0.02, AxisKey.POV to -0.02,
        ),
        "evening" to emptyMap(),
    )

    /**
     * An explicit intent buys its axis five points of weight, taken from the two
     * lightest OTHER axes.
     */
    const val INTENT_WEIGHT_BONUS: Double = 0.05

    /** Without a back view the fit read is a partial one. */
    const val NO_BACK_FIT_MULTIPLIER: Double = 0.80

    /** Tappable intent chips, and the axis each maps to. */
    data class IntentChip(val label: String, val axis: AxisKey)

    val INTENT_CHIPS: List<IntentChip> = listOf(
        IntentChip("Look taller", AxisKey.SIL),
        IntentChip("Look expensive", AxisKey.TEX),
        IntentChip("Look like I didn't try", AxisKey.POV),
        IntentChip("Hide my arms", AxisKey.FIT),
    )

    /** Free text longer than this is truncated server-side. */
    const val INTENT_MAX_CHARS: Int = 140

    /** Dress-code gap coefficients. Display only on the client. */
    val UNDER_COEFF: Map<String, Double> = mapOf(
        WORK to 1.00, DATE to 1.00, WEDDING to 1.25, CASUAL to 0.50, EVERYDAY to 0.50,
    )

    val OVER_COEFF: Map<String, Double> = mapOf(
        WORK to 0.75, DATE to 0.90, WEDDING to 1.00, CASUAL to 1.00, EVERYDAY to 1.00,
    )

    // ---- the brief line --------------------------------------------------
    //
    // Port of `_shared/rubric/brief.ts`. Composed from a locale format string
    // with named slots, never by concatenation, so word order survives
    // translation.

    private val BRIEF_FORMATS: Map<String, String> = mapOf(
        "en" to "{occasion} look{role}{qualifier}, meant to read {presence}.",
        "tr" to "{presence} durmasını istediğin{qualifier} bir {occasion} görünümü{role}.",
    )

    private val OCCASION_NP: Map<String, String> = mapOf(
        WORK to "A work",
        DATE to "A date",
        WEDDING to "A wedding",
        CASUAL to "A casual",
        EVERYDAY to "An everyday",
    )

    private val ROLE_SLOT: Map<String, String> = mapOf(
        ROLE_GUEST to ", as a guest",
        ROLE_WEDDING_PARTY to ", in the wedding party",
        ROLE_COUPLE to ", as the couple",
    )

    /** "meant to read ___" — adjectives, not the dial captions. */
    private val PRESENCE_ADJ: List<String> =
        listOf("invisible", "quiet", "balanced", "noticed", "unmissable")

    private val SLOT = Regex("\\{(\\w+)}")

    private fun fill(template: String, slots: Map<String, String>): String =
        SLOT.replace(template) { m -> slots[m.groupValues[1]] ?: "" }

    fun formalityCaption(occasion: String, formality: Int): String =
        FORMALITY_CAPTIONS[occasion]?.getOrNull(formality - 1) ?: ""

    fun presenceCaption(presence: Int): String =
        PRESENCE_CAPTIONS.getOrNull(presence - 1) ?: ""

    /** The one-line restatement of the brief shown live above the score button. */
    fun briefLine(intake: PreviewIntake, locale: String = "en"): String {
        val caption = formalityCaption(intake.occasion, intake.formality)
        val slots = mapOf(
            "occasion" to (OCCASION_NP[intake.occasion] ?: "A"),
            "role" to if (intake.occasion == WEDDING && intake.role != null) {
                ROLE_SLOT[intake.role] ?: ""
            } else "",
            "qualifier" to if (caption.isNotEmpty()) ", for ${caption.lowercase()}" else "",
            "presence" to (PRESENCE_ADJ.getOrNull(intake.presence - 1) ?: "balanced"),
        )
        val template = BRIEF_FORMATS[locale] ?: BRIEF_FORMATS.getValue("en")
        return fill(template, slots).replace(Regex("\\s+"), " ").trim()
    }

    /** "Wedding · black tie · guest" — the label under the score. */
    fun rubricLabel(intake: PreviewIntake): String {
        val parts = mutableListOf(
            intake.occasion.replaceFirstChar { it.uppercase() },
            formalityCaption(intake.occasion, intake.formality).lowercase(),
        )
        if (intake.occasion == WEDDING && intake.role != null) {
            parts.add(intake.role.replace('_', ' '))
        }
        if (intake.occasion == WORK && intake.room != null) {
            parts.add(intake.room.replace('_', ' '))
        }
        return parts.filter { it.isNotBlank() }.joinToString(" · ")
    }
}
