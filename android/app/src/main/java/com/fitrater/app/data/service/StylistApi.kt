package com.fitrater.app.data.service

import android.util.Base64
import com.fitrater.app.data.Supa
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One turn of the stylist transcript as the edge function expects it. */
data class StylistTurn(val role: String, val content: String)

@Serializable
data class StylistChatResponse(
    val text: String? = null,
    val error: String? = null,
    val detail: String? = null,
)

/**
 * Thin client for the `stylist-chat` Supabase edge function.
 *
 * Request:  { messages: [{ role, content }], image_base64? }
 * Response: { text } on success, { error, detail? } on failure.
 *
 * Failures always throw — the UI shows a real error with a Retry action.
 * We never fabricate a stylist reply: a made-up answer reads as genuine advice
 * and there is no way for the user to tell it apart from Hem's.
 */
object StylistApi {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Keep the prompt bounded — the last 20 turns are plenty of context. */
    private const val MAX_HISTORY = 20

    suspend fun send(history: List<StylistTurn>, imageBytes: ByteArray?): String {
        val messages = JsonArray(
            history.takeLast(MAX_HISTORY).map { turn ->
                buildJsonObject {
                    put("role", turn.role)
                    put("content", turn.content)
                }
            },
        )
        val payload = buildJsonObject {
            put("messages", messages)
            if (imageBytes != null) {
                put("image_base64", Base64.encodeToString(imageBytes, Base64.NO_WRAP))
            }
        }
        val resp: HttpResponse = Supa.client.functions.invoke("stylist-chat", body = payload)
        val raw: String = resp.body()
        val parsed = runCatching {
            json.decodeFromString(StylistChatResponse.serializer(), raw)
        }.getOrElse {
            throw IllegalStateException("Hem sent something we couldn't read.")
        }
        parsed.error?.let { err ->
            val detail = parsed.detail?.take(140).orEmpty()
            throw IllegalStateException(if (detail.isBlank()) "Hem: $err" else "Hem: $err — $detail")
        }
        val text = parsed.text?.trim()
        if (text.isNullOrEmpty()) throw IllegalStateException("Hem came back empty-handed.")
        return text
    }
}
