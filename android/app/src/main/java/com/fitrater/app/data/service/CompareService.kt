package com.fitrater.app.data.service

import com.fitrater.app.data.Supa
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class CompareResponse(
    val winner: String? = null,
    val score_a: Double? = null,
    val score_b: Double? = null,
    val comment: String? = null,
    val reason_a: String? = null,
    val reason_b: String? = null,
    val error: String? = null,
    val detail: String? = null,
)

@Serializable
data class DecodePiece(
    /** v2: 2–4 word natural garment name, sentence case. Empty on v1 payloads. */
    val name: String = "",
    /** v2: 1–3 word crucial construction detail. Empty on v1 payloads. */
    val detail: String = "",
    val type: String = "",
    val silhouette: String = "",
    val colors: List<String> = emptyList(),
    val fabric: String = "",
    val note: String = "",
) {
    /**
     * FROZEN render rule (identical on iOS):
     * name -> "silhouette type" trimmed+capitalised -> type
     */
    val displayName: String
        get() {
            if (name.isNotBlank()) return name.trim()
            val composed = "${silhouette.trim()} ${type.trim()}".trim().replaceFirstChar { it.uppercase() }
            return composed.ifBlank { type.trim().replaceFirstChar { c -> c.uppercase() } }
        }

    /**
     * FROZEN render rule (identical on iOS):
     * [fabric, detail] -> [fabric, colors] -> note
     */
    val displayMeta: String
        get() {
            val primary = listOf(fabric.trim(), detail.trim()).filter { it.isNotBlank() }.joinToString(" · ")
            if (primary.isNotBlank()) return primary
            val joinedColors = colors.map { it.trim() }.filter { it.isNotBlank() }.joinToString(", ")
            val fallback = listOf(fabric.trim(), joinedColors)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (fallback.isNotBlank()) return fallback
            return note.trim()
        }
}

@Serializable
data class DecodeResponse(
    val pieces: List<DecodePiece> = emptyList(),
    val palette_hex: List<String> = emptyList(),
    val style_signature: String = "",
    val error: String? = null,
    val detail: String? = null,
)

object CompareService {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun compare(aUrl: String, bUrl: String, occasion: String): CompareResponse {
        val payload = buildJsonObject {
            put("image_a_url", aUrl)
            put("image_b_url", bUrl)
            put("occasion", occasion)
        }
        val resp: HttpResponse = Supa.client.functions.invoke("compare-outfits", body = payload)
        val text: String = resp.body()
        return runCatching {
            json.decodeFromString(CompareResponse.serializer(), text)
        }.getOrElse { CompareResponse(error = "decode_failed", detail = text.take(200)) }
    }

    suspend fun decode(imageUrl: String): DecodeResponse {
        val payload = buildJsonObject { put("image_url", imageUrl) }
        val resp: HttpResponse = Supa.client.functions.invoke("decode-outfit", body = payload)
        val text: String = resp.body()
        return runCatching {
            json.decodeFromString(DecodeResponse.serializer(), text)
        }.getOrElse { DecodeResponse(error = "decode_failed", detail = text.take(200)) }
    }
}
