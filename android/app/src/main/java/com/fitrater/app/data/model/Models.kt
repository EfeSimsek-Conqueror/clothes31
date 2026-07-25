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
