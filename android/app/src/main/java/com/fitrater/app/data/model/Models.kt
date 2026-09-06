package com.fitrater.app.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class Profile(
    val id: String? = null,
    val display_name: String? = null,
    val email: String? = null,
    val gender: String? = null,
    val style_tags: List<String>? = null,
    val honesty: String? = null,
    val onboarded: Boolean? = null,
    val avatar_url: String? = null,
    val is_pro: Boolean? = null,
    val updated_at: String? = null,
    val first_run_done: Boolean? = null,
    val preferred_fabrics: List<String>? = null,
    val preferred_colors: List<String>? = null,
    val sensitivities: String? = null,
    val rated_ok: Boolean? = null,
    val theme: String? = null,
    val text_scale: Double? = null,
    val reduce_motion: Boolean? = null,
    val paywall_shown: Boolean? = null,
)

@Serializable
data class ProfileUpsert(
    val id: String,
    val email: String? = null,
    val display_name: String? = null,
    val avatar_url: String? = null,
    val gender: String? = null,
    val style_tags: List<String>? = null,
    val honesty: String? = null,
    val onboarded: Boolean? = null,
    val preferred_fabrics: List<String>? = null,
    val preferred_colors: List<String>? = null,
    val sensitivities: String? = null,
)

@Serializable
data class Annotation(
    val x_pct: Double = 50.0,
    val y_pct: Double = 50.0,
    val label: String = "",
    val score: Double = 0.0,
    val note: String = "",
)

/**
 * Markup annotation from `score-outfit` v3. Type is "arrow" | "line" | "focus" | "swap".
 * Coords normalized [0,1] (origin top-left):
 *  - arrow/line: from + to
 *  - focus:      at + radius
 *  - swap:       at
 */
@Serializable
data class MarkupAnnotation(
    val type: String,
    val coords: MarkupCoords,
    val note: String = "",
    val confidence: Double? = null,
)

@Serializable
data class MarkupCoords(
    val from: List<Double>? = null,
    val to: List<Double>? = null,
    val at: List<Double>? = null,
    val radius: Double? = null,
)

/**
 * Fit-tension heatmap from `score-outfit` v3. Grid rows × cols with values in [-1, 1]
 * (-1 = pooling/loose, +1 = tight/pulling). Resolution is [cols, rows].
 */
@Serializable
data class FitMap(
    val resolution: List<Int>? = null,
    val grid: List<List<Double>>? = null,
    val hotspots: List<FitHotspot>? = null,
)

@Serializable
data class FitHotspot(
    val at: List<Double>,
    val label: String,
    val severity: Double,
)

/** Response from `transcribe-intent` — Fal wizper output. */
@Serializable
data class TranscribeResponse(
    val transcript: String? = null,
    val sanitized: String? = null,
    val engine: String? = null,
    val error: String? = null,
)

/** Local convenience payload — sanitized transcript + optional stored audio path. */
@Serializable
data class IntentPayload(
    val text: String,
    val audio_path: String? = null,
)

@Serializable
data class Outfit(
    val id: String? = null,
    val user_id: String? = null,
    val name: String? = null,
    val score: Double? = null,
    val notes: String? = null,
    val image_url: String? = null,
    val photo_path: String? = null,
    val verdict: String? = null,
    val occasion: String? = null,
    val weather_c: Int? = null,
    val hem_comment: String? = null,
    val dominant_colors: List<String>? = null,
    val subscores: Subscores? = null,
    val swaps: List<String>? = null,
    val annotations: List<Annotation>? = null,
    val created_at: String? = null,
    val kind: String? = null,
    /** Sibling link — e.g. a Studio SIDE view points at its FRONT outfit id. */
    val linked_piece_id: String? = null,
    /** Optional back view uploaded alongside the front — drives R-EVIDENCE. */
    val back_photo_path: String? = null,
    // ---- scoring v4 (migration 0007). All optional; v3 rows have none. ----
    /** The brief exactly as it was sent. */
    val intake: ScoreIntake? = null,
    val rubric_id: String? = null,
    val rubric_version: Int? = null,
    /** "v3_mean" for the unweighted mean of four fixed subscores, "v4" for the
     * weighted, capped nine-axis headline. Averages must only ever be computed
     * WITHIN a version. */
    val scoring_version: String? = null,
    val axes: List<Axis>? = null,
    val score_breakdown: ScoreBreakdown? = null,
    val dress_code: DressCode? = null,
    val presence_check: PresenceCheck? = null,
    val lever: Lever? = null,
    val caveats: List<CaveatRow>? = null,
    val pieces: List<Piece>? = null,
    /** The model's literal observations. Opaque — persist verbatim, never introspect. */
    val signals: JsonObject? = null,
    /** The model's untouched witness statement. Required by /rescore. Opaque. */
    val raw_axes: JsonObject? = null,
    val rescore_count: Int? = null,
)

@Serializable(with = SubscoresSerializer::class)
data class Subscores(
    val color: Double? = null,
    val fit: Double? = null,
    val style_match: Double? = null,
    val seasonal: Double? = null,
)

/**
 * Tolerant serializer for [Subscores]. Accepts either:
 *   - New shape: {"fit": 7, "color": 6, "style_match": 8, "seasonal": 7}
 *   - Legacy shape: {"fit": {"score": 7, "why": "..."}, "color": {...}, "style": {...}, "season": {...}}
 * Writes the new flat shape.
 */
object SubscoresSerializer : KSerializer<Subscores> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Subscores")

    override fun deserialize(decoder: Decoder): Subscores {
        val jd = decoder as? JsonDecoder ?: error("SubscoresSerializer requires JSON")
        val el = jd.decodeJsonElement()
        if (el !is JsonObject) return Subscores()
        fun read(vararg keys: String): Double? {
            for (k in keys) {
                val v = el[k] ?: continue
                when (v) {
                    is JsonPrimitive -> v.doubleOrNull?.let { return it }
                    is JsonObject -> v["score"]?.let { s ->
                        (s as? JsonPrimitive)?.doubleOrNull?.let { return it }
                    }
                    else -> continue
                }
            }
            return null
        }
        return Subscores(
            color = read("color"),
            fit = read("fit"),
            style_match = read("style_match", "style"),
            seasonal = read("seasonal", "season"),
        )
    }

    override fun serialize(encoder: Encoder, value: Subscores) {
        val je = encoder as? JsonEncoder ?: error("SubscoresSerializer requires JSON")
        val obj = buildJsonObject {
            value.color?.let { put("color", JsonPrimitive(it)) }
            value.fit?.let { put("fit", JsonPrimitive(it)) }
            value.style_match?.let { put("style_match", JsonPrimitive(it)) }
            value.seasonal?.let { put("seasonal", JsonPrimitive(it)) }
        }
        je.encodeJsonElement(obj)
    }
}

/**
 * Score arithmetic shared by every surface that shows a number — VERBATIM port of
 * the iOS `extension Subscores` in Models.swift. Hem returns an overall `score`
 * alongside the four subscores and the two routinely disagree, so the headline
 * number *is* the mean of the reads it is broken down into.
 */
val Subscores.values: List<Double>
    get() = listOfNotNull(color, fit, style_match, seasonal)

/** Mean of the present component reads, rounded to one decimal. Null when there are none. */
val Subscores.averaged: Double?
    get() {
        val vs = values
        if (vs.isEmpty()) return null
        return kotlin.math.round((vs.sum() / vs.size) * 10.0) / 10.0
    }

@Serializable
data class OutfitInsert(
    val user_id: String,
    val photo_path: String,
    val score: Double,
    val occasion: String,
    val hem_comment: String,
    val weather_c: Int? = null,
    val verdict: String? = null,
    val subscores: Subscores? = null,
    val swaps: List<String>? = null,
    val annotations: List<Annotation>? = null,
    val kind: String? = null,
    val linked_piece_id: String? = null,
    val back_photo_path: String? = null,
    // ---- scoring v4 (migration 0007). Written only when the response carried
    // the v4 payload; a v3 envelope leaves every one of these null and the row
    // is labelled "v3_mean". ----
    val intake: ScoreIntake? = null,
    val rubric_id: String? = null,
    val rubric_version: Int? = null,
    val scoring_version: String? = null,
    val axes: List<Axis>? = null,
    val score_breakdown: ScoreBreakdown? = null,
    val dress_code: DressCode? = null,
    val presence_check: PresenceCheck? = null,
    val lever: Lever? = null,
    val caveats: List<CaveatRow>? = null,
    val pieces: List<Piece>? = null,
    val signals: JsonObject? = null,
    val raw_axes: JsonObject? = null,
)

@Serializable
data class ClosetItem(
    val id: String? = null,
    val user_id: String? = null,
    val category: String? = null,
    val subcategory: String? = null,
    val name: String? = null,
    val image_url: String? = null,
    val image_path: String? = null,
    val color: String? = null,
    val color_hex: String? = null,
    val worn_count: Int? = null,
    val parent_id: String? = null,
    val created_at: String? = null,
)

@Serializable
data class ClosetItemInsert(
    val user_id: String,
    val name: String,
    val category: String,
    val subcategory: String? = null,
    val image_path: String? = null,
    val color_hex: String? = null,
    val parent_id: String? = null,
    val source: String? = null,
)

@Serializable
data class CreditPack(
    val id: String,
    val name: String? = null,
    val credits: Int,
    val price_cents: Int,
    val sort: Int? = null,
)

@Serializable
data class SundayLetter(
    val id: String? = null,
    val user_id: String? = null,
    val week_start: String? = null,
    val week_end: String? = null,
    val body: String? = null,
    val variant: String? = null,
    val created_at: String? = null,
)

@Serializable
data class CreditTransaction(
    val id: String? = null,
    val user_id: String? = null,
    val amount: Int? = null,
    val kind: String? = null,
    val balance_after: Int? = null,
    val created_at: String? = null,
)

@Serializable
data class CreditTxInsert(
    val user_id: String,
    val amount: Int,
    val kind: String,
    val balance_after: Int,
    val reference_id: String? = null,
)

/** Insert shape for `public.content_reports` — write-only, never read back. */
@Serializable
data class ContentReportInsert(
    val user_id: String,
    val content_kind: String,
    val content_id: String,
    val reason: String,
    val note: String? = null,
)

@Serializable
data class PushSettings(
    val user_id: String? = null,
    val enabled: Boolean? = null,
    val morning_stylist: Boolean? = null,
    val weekly_task: Boolean? = null,
    val wrapped: Boolean? = null,
)

@Serializable
data class PushSettingsUpsert(
    val user_id: String,
    val morning_stylist: Boolean,
    val weekly_task: Boolean,
    val wrapped: Boolean,
)

@Serializable
data class HemNote(
    val id: String? = null,
    val user_id: String? = null,
    val body: String? = null,
    val created_at: String? = null,
)

/**
 * One-time body calibration output from the `analyze-body` edge function.
 * All fields optional — the model may fail to classify one axis without
 * invalidating the rest. Callers should treat `null` as "unknown".
 */
@Serializable
data class BodyProfile(
    val body_shape: String? = null,          // triangle | inverted_triangle | hourglass | rectangle | apple
    val shoulder_hip_ratio: Double? = null,
    val torso_leg_ratio: Double? = null,
    val skin_undertone: String? = null,      // warm | cool | neutral
    val coloring_season: String? = null,     // spring | summer | autumn | winter
    val palette_hex: List<String>? = null,   // up to 6 hex codes, "#RRGGBB"
    val notes: String? = null,
)

/** Envelope returned by `analyze-body`. `error` is populated on failure. */
@Serializable
data class BodyProfileResponse(
    val profile: BodyProfile? = null,
    val engine: String? = null,
    val error: String? = null,
)

// --- Sprint 3: Magazine Cover ---

/** Row in `public.magazine_covers`. All fields optional for tolerant decode. */
@Serializable
data class MagazineCover(
    val id: String? = null,
    val outfit_id: String? = null,
    val user_id: String? = null,
    val headline: String? = null,
    val pull_quote: String? = null,
    val vol_number: Int? = null,
    val image_path: String? = null,
    val created_at: String? = null,
)

/** Response from the `compose-cover` edge function. */
@Serializable
data class ComposeCoverResponse(
    val cover_url: String? = null,
    val headline: String? = null,
    val pull_quote: String? = null,
    val vol_number: Int? = null,
    val cover_id: String? = null,
    val error: String? = null,
)

/** Response from the `regenerate-cover-headline` edge function. */
@Serializable
data class HeadlineResponse(
    val headline: String? = null,
    val pull_quote: String? = null,
    val error: String? = null,
)

// --- Sprint 5: Invitation decoder + outfit suggestions ---

/**
 * Structured event fields decoded from an invitation photo by the
 * `decode-invitation` edge function. All fields optional — the model may
 * fail to read one axis without invalidating the rest.
 */
@Serializable
data class InvitationDecoded(
    val event_type: String? = null,   // wedding|cocktail|dinner|gallery|work|casual|other
    val dress_code: String? = null,   // black_tie|cocktail|smart_casual|casual|business|black_tie_optional|creative_black_tie|business_casual
    val time_of_day: String? = null,  // morning|afternoon|evening|night
    val venue: String? = null,
    val date_text: String? = null,
    val notes: String? = null,
)

/** Envelope returned by `decode-invitation`. `error` populated on failure. */
@Serializable
data class DecodeInvitationResponse(
    val decoded: InvitationDecoded? = null,
    val engine: String? = null,
    val error: String? = null,
)

/**
 * One outfit suggestion from `suggest-outfits`. `piece_ids` reference the
 * closet items posted in the request. `gap` is non-null if the closet is
 * missing a critical piece for the requested dress code.
 */
@Serializable
data class OutfitCombo(
    val score: Int? = null,
    val piece_ids: List<String>? = null,
    val rationale: String? = null,
    val gap: String? = null,
)

/** Envelope returned by `suggest-outfits`. `error` populated on failure. */
@Serializable
data class SuggestOutfitsResponse(
    val combos: List<OutfitCombo>? = null,
    val engine: String? = null,
    val error: String? = null,
)

/** Row in `public.invitation_reads`. All fields optional for tolerant decode. */
@Serializable
data class InvitationRead(
    val id: String? = null,
    val user_id: String? = null,
    val image_path: String? = null,
    val event_type: String? = null,
    val dress_code: String? = null,
    val time_of_day: String? = null,
    val venue: String? = null,
    val notes: String? = null,
    val suggested_combos: List<OutfitCombo>? = null,
    val created_at: String? = null,
)

// Local models
data class ScorePiece(
    val name: String,
    val score: Double,
    val note: String,
)

data class ScoreResult(
    val score: Double,
    val deltaVsAvg: Double,
    val occasion: String,
    val pullQuote: String,
    val pieces: List<ScorePiece>,
)

// ---------------------------------------------------------------------------
// Scoring v4 — "the brief dial".
//
// The server contract lives in `supabase/functions/score-outfit/`. These are the
// wire shapes of the additive v4 payload, which is returned only when the
// request carries `client_features: ["axes_v4","xray_v4"]`. Every field is
// optional so a v3 envelope — and every outfit row written before v4 — still
// decodes. Nothing here replaces the v3 keys; `score`, `subscores`,
// `hem_comment`, `swaps` and `annotations` keep their types forever.
// ---------------------------------------------------------------------------

/** The brief, exactly as it goes on the wire. Nulls are dropped by the encoder:
 * a field belonging to another occasion is never sent, and `weather` is omitted
 * entirely rather than guessed. */
@Serializable
data class ScoreIntake(
    // Defaults are the server's own defaults, and they exist so the `'{}'::jsonb`
    // this column carries on every pre-v4 row still decodes. The request encoder
    // runs with `encodeDefaults = true`, so a default here is still sent.
    val occasion: String = "everyday",
    /** 1..5 — how dressed up the wearer intended to be. */
    val formality: Int = 2,
    /** 1..5 — how much the wearer wanted to be looked at. */
    val presence: Int = 3,
    val time_of_day: String = "daytime",
    val role: String? = null,
    val venue: String? = null,
    val room: String? = null,
    val on_feet: String? = null,
    val intent: String? = null,
    val weather: ScoreWeather? = null,
)

@Serializable
data class ScoreWeather(val band: String = "", val precip: Boolean = false)

/** One of the nine axes, as scored. `score` is null when the axis was
 * unjudgeable — then `weight` is 0 and the row is greyed rather than hidden. */
@Serializable
data class Axis(
    // Defaulted, like every field on every v4 shape here: these are decoded from
    // jsonb columns row-by-row, and one missing key would drop the whole outfit
    // out of the Journal rather than just blanking a line.
    val key: String = "",
    val label: String? = null,
    val weight: Double = 0.0,
    val raw_score: Double? = null,
    val score: Double? = null,
    val contribution: Double? = null,
    val evidence: String? = null,
    /** "engine" for the dress-code axis the server computes itself, else "model". */
    val source: String? = null,
    val rank: Int? = null,
    val capped_by: String? = null,
    val ceiling: Double? = null,
    val ceiling_rule: String? = null,
    val floored_by: String? = null,
    val unjudgeable: Boolean? = null,
    val is_intent_axis: Boolean? = null,
)

@Serializable
data class Headline(
    val value: Double? = null,
    val weighted_mean: Double? = null,
    val band: String? = null,
    val reason: String? = null,
)

/** What the look was graded against — the rubric the intake resolved to. */
@Serializable
data class RubricEcho(
    val id: String? = null,
    val version: Int? = null,
    val occasion: String? = null,
    val role: String? = null,
    val venue: String? = null,
    val room: String? = null,
    val on_feet: String? = null,
    val formality: Int? = null,
    val formality_caption: String? = null,
    val presence: Int? = null,
    val presence_caption: String? = null,
    val label: String? = null,
    val brief_line: String? = null,
    val primary_axes: List<String>? = null,
)

/** "on_brief" | "nearly" | "off_brief". */
@Serializable
data class BriefVerdict(val state: String? = null, val copy: String? = null)

/** One line of the dress-code checklist. `present` is null — never a cross —
 * when the photograph could not show it. */
@Serializable
data class CodeMarker(
    val name: String = "",
    val required: Boolean = true,
    val present: Boolean? = null,
    val note: String? = null,
)

@Serializable
data class DressCode(
    val read: Int? = null,
    val claimed: Int? = null,
    val target: Int? = null,
    val gap: Int? = null,
    /** "met" | "interpreted" | "near" | "miss". */
    val severity: String? = null,
    /** "under" | "over" | "none". */
    val direction: String? = null,
    val line: String? = null,
    val evidence: String? = null,
    val markers: List<CodeMarker>? = null,
    /** False ⇒ the brief carries no code worth drawing a section for. */
    val render: Boolean = false,
)

@Serializable
data class PresenceCheck(
    val read: Int? = null,
    val claimed: Int? = null,
    val gap: Int? = null,
    val line: String? = null,
    val evidence: String? = null,
    val render: Boolean = false,
    val mechanism: String? = null,
)

/** One rule that fired, with what it cost the headline. `rules_fired` is an
 * ordered waterfall: `raw_weighted + Σ headline_cost == final`. */
@Serializable
data class RuleFired(
    val id: String? = null,
    val name: String? = null,
    val trigger: String? = null,
    val effect: String? = null,
    val axis: String? = null,
    val before: Double? = null,
    val after: Double? = null,
    val headline_cost: Double = 0.0,
    /** "cap" | "caveat_only" | "off". */
    val mode: String? = null,
    val confidence: Double? = null,
)

/** What one intake answer did to one axis weight, post-normalisation. */
@Serializable
data class WeightDeltaRow(
    val reason: String? = null,
    val axis: String? = null,
    val delta: Double = 0.0,
    /** "dial" | "band4" | "cross" | "intent" | "evidence" | "null_redistribution". */
    val kind: String? = null,
)

@Serializable
data class CapApplied(
    val id: String? = null,
    val scope: String? = null,
    val to: Double? = null,
    val binding: Boolean = false,
)

/** A rule running in `caveat_only` — it says its piece and costs nothing. */
@Serializable
data class DowngradedRule(
    val id: String? = null,
    val name: String? = null,
    val copy: String? = null,
    val confidence: Double? = null,
)

/** One entry of the re-score ledger, written by `/score-outfit/rescore`. */
@Serializable
data class ScoreHistoryRow(
    val from: Double? = null,
    val to: Double? = null,
    val rubric_id: String? = null,
)

@Serializable
data class ScoreBreakdown(
    val weights_source: String? = null,
    val null_axes: List<String>? = null,
    val discarded_axes: List<String>? = null,
    val weight_deltas: List<WeightDeltaRow>? = null,
    val raw_weighted: Double? = null,
    val rules_fired: List<RuleFired>? = null,
    val caps_applied: List<CapApplied>? = null,
    val downgraded_to_caveat: List<DowngradedRule>? = null,
    /**
     * Additive, client-written. `swaps_v2` has no column of its own and the flat
     * `swaps` array is frozen at plain strings, so the score path folds the rich
     * swaps into this jsonb blob on insert. `/score-outfit/rescore` rewrites
     * `score_breakdown` wholesale and will drop them again — the detail screen
     * falls back to `swaps` when they are absent, which is why that is safe.
     */
    val swaps_v2: List<SwapV2>? = null,
    // `final` is a Kotlin modifier keyword; the wire name is preserved.
    @kotlinx.serialization.SerialName("final")
    val finalScore: Double? = null,
    val history: List<ScoreHistoryRow>? = null,
)

/** What the caveat cost, and which axis it cost it on. `kind` is one of
 * "no_back_view" | "null_axis" | "discarded_axis" | "no_weather" | "rule:R-…"
 * | "model". */
@Serializable
data class CaveatRow(
    val kind: String? = null,
    val copy: String? = null,
    val cost: String? = null,
    val axis: String? = null,
)

/** "You picked X → here is what X did." */
@Serializable
data class IntakeEcho(val label: String? = null, val effect: String? = null)

/** The single highest-value change, and where it would land the score. */
@Serializable
data class Lever(
    val axis: String? = null,
    val label: String? = null,
    val from: Double? = null,
    val to: Double? = null,
    val projected: Double? = null,
    val delta: Double? = null,
    val copy: String? = null,
    val action: String? = null,
)

/** A swap with its reasoning and how much work it is. `effort` is
 * "swap" | "tailor" | "buy_or_rent". */
@Serializable
data class SwapV2(
    val from: String? = null,
    val to: String? = null,
    val axis: String? = null,
    val why: String? = null,
    val effort: String? = null,
)

/** One garment as read off the photo. `verdict` is
 * "works" | "works_elsewhere" | "drags". */
@Serializable
data class Piece(
    val label: String? = null,
    val x_pct: Double? = null,
    val y_pct: Double? = null,
    val score: Double? = null,
    val verdict: String? = null,
    val note: String? = null,
)

/**
 * The number a surface should print for an outfit.
 *
 * v4 rows carry a weighted, capped headline in `score` and their four
 * `subscores` are a projection of it, so re-averaging them would undo every cap
 * the engine applied. v3 rows are the other way round: the row's `score` and its
 * subscores routinely disagreed, and the mean of the component reads is the
 * number every shipped surface has always shown. Mirrors the iOS rule.
 */
val Outfit.displayScore: Double?
    get() = if (scoring_version == "v4") score else (subscores?.averaged ?: score)

/** True when this row was graded by the v4 engine and carries the audit trail. */
val Outfit.isV4: Boolean
    get() = scoring_version == "v4"
