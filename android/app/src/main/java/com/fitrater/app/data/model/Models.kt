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
