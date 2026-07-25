package com.fitrater.app.data.service

import android.util.Log
import com.fitrater.app.data.Supa
import com.fitrater.app.data.model.Annotation
import com.fitrater.app.data.model.ScorePiece
import com.fitrater.app.data.model.ScoreResult
import com.fitrater.app.data.model.Subscores
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.random.Random

@Serializable
data class ScoreOutfitResponse(
    val score: Double? = null,
    val subscores: Subscores? = null,
    val hem_comment: String? = null,
    val swaps: List<String>? = null,
    val annotations: List<Annotation>? = null,
    val raw_model_response: String? = null,
    val error: String? = null,
    val raw: String? = null,
    val detail: String? = null,
)

data class HemScored(
    val score: Double,
    val hemComment: String,
    val subscores: Subscores,
    val swaps: List<String>,
    val annotations: List<Annotation>,
)

/**
 * Real Hem scoring backed by the `score-outfit` Supabase edge function
 * (Fal.ai `any-llm/vision` → Gemini Pro 1.5). Falls back to a deterministic
 * local stub on network failure so the app still functions offline.
 */
object HemService {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Real scoring path. Throws on non-recoverable model/parse errors. */
    suspend fun score(imageUrl: String, occasion: String, honesty: String): HemScored {
        val payload: JsonObject = buildJsonObject {
            put("image_url", imageUrl)
            put("occasion", occasion)
            put("honesty", honesty)
        }
        val resp: HttpResponse = Supa.client.functions.invoke(
            function = "score-outfit",
            body = payload,
        )
        val text: String = resp.body()
        val parsed = runCatching {
            json.decodeFromString(ScoreOutfitResponse.serializer(), text)
        }.getOrElse {
            Log.e("HemService", "decode fail: ${text.take(300)}", it)
            throw IllegalStateException("Hem could not read the response.")
        }
        if (parsed.error != null) {
            val detail = parsed.detail ?: parsed.raw ?: parsed.error
            throw IllegalStateException("Hem: ${parsed.error} — ${detail?.take(140) ?: ""}")
        }
        val s = parsed.score ?: throw IllegalStateException("Hem returned no score.")
        val comment = parsed.hem_comment ?: ""
        val subs = parsed.subscores ?: Subscores()
        val swaps = parsed.swaps.orEmpty()
        val annotations = parsed.annotations.orEmpty()
        return HemScored(
            score = String.format("%.1f", s).toDouble(),
            hemComment = comment,
            subscores = subs,
            swaps = swaps,
            annotations = annotations,
        )
    }

    /** Legacy deterministic stub kept for fallback / previews. */
    fun scoreStub(photoPath: String, occasion: String): ScoreResult {
        val seed = photoPath.hashCode().toLong()
        val rand = Random(seed)
        val score = (70 + rand.nextInt(0, 26)) / 10.0
        val delta = rand.nextInt(-10, 12) / 10.0
        val quotes = listOf(
            "Sleek and street-honest — the print does the talking.",
            "Warm palette, quiet confidence. Trust the wide leg.",
            "Silhouette carries you — accessories can rest today.",
            "Contrast reads editorial. Keep the shoes plain.",
            "Nothing fights for attention. That's the win.",
        )
        val quote = quotes[abs(seed % quotes.size).toInt()]
        val pieces = listOf(
            ScorePiece("Top layer", score - 0.2, "Cut sits clean at the shoulder."),
            ScorePiece("Bottom", score + 0.1, "Break lands where it should."),
            ScorePiece("Footwear", score - 0.4, "Palette-friendly, low-drama."),
        )
        return ScoreResult(
            score = String.format("%.1f", score).toDouble(),
            deltaVsAvg = String.format("%.1f", delta).toDouble(),
            occasion = occasion,
            pullQuote = quote,
            pieces = pieces,
        )
    }
}
