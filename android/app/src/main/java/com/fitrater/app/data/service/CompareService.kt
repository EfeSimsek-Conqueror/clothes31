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
    val type: String = "",
    val silhouette: String = "",
    val colors: List<String> = emptyList(),
    val fabric: String = "",
    val note: String = "",
)

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
