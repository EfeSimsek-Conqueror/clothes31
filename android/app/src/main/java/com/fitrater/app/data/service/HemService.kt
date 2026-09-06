package com.fitrater.app.data.service

import android.util.Log
import com.fitrater.app.data.Supa
import com.fitrater.app.data.model.Annotation
import com.fitrater.app.data.model.Axis
import com.fitrater.app.data.model.BodyProfile
import com.fitrater.app.data.model.BriefVerdict
import com.fitrater.app.data.model.CaveatRow
import com.fitrater.app.data.model.DressCode
import com.fitrater.app.data.model.FitMap
import com.fitrater.app.data.model.Headline
import com.fitrater.app.data.model.IntakeEcho
import com.fitrater.app.data.model.Lever
import com.fitrater.app.data.model.MarkupAnnotation
import com.fitrater.app.data.model.Piece
import com.fitrater.app.data.model.PresenceCheck
import com.fitrater.app.data.model.RubricEcho
import com.fitrater.app.data.model.ScoreBreakdown
import com.fitrater.app.data.model.ScoreIntake
import com.fitrater.app.data.model.ScorePiece
import com.fitrater.app.data.model.ScoreResult
import com.fitrater.app.data.model.Subscores
import com.fitrater.app.data.model.SwapV2
import com.fitrater.app.util.Flags
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.random.Random

/**
 * The `score-outfit` response.
 *
 * The first block is the v3 envelope: those keys are present on every response,
 * their types are frozen forever, and the app must never require anything below
 * them. The second block is the v4 payload, returned only when the request asked
 * for it via `client_features` — every field is nullable so the degraded path
 * (see [HemService.score]) decodes cleanly.
 */
@Serializable
data class ScoreOutfitResponse(
    val score: Double? = null,
    val subscores: Subscores? = null,
    val hem_comment: String? = null,
    val swaps: List<String>? = null,
    val annotations: List<Annotation>? = null,
    val version: String? = null,
    // Sprint 2 additions — all optional so v2 responses still decode.
    val markup_annotations: List<MarkupAnnotation>? = null,
    val fit_map: FitMap? = null,
    val fits_you: Double? = null,
    val raw_model_response: String? = null,
    // ---- v4, gated on client_features: ["axes_v4"] ----
    val headline: Headline? = null,
    val rubric: RubricEcho? = null,
    val brief_verdict: BriefVerdict? = null,
    val axes: List<Axis>? = null,
    val dress_code: DressCode? = null,
    val presence_check: PresenceCheck? = null,
    val score_breakdown: ScoreBreakdown? = null,
    val why_this_number: String? = null,
    val lever: Lever? = null,
    val caveats: List<CaveatRow>? = null,
    val intake_echo: List<IntakeEcho>? = null,
    val pieces: List<Piece>? = null,
    val swaps_v2: List<SwapV2>? = null,
    /** Opaque: the model's literal observations. Persisted verbatim. */
    val signals: JsonObject? = null,
    val palette_hex: List<String>? = null,
    val intent: String? = null,
    val intent_axis: String? = null,
    val intent_note: String? = null,
    val rescores_remaining: Int? = null,
    val scoring_version: String? = null,
    val verdict: String? = null,
    val intake_warnings: List<String>? = null,
    /** Opaque: the witness statement /rescore replays. Persisted verbatim. */
    val raw_axes: JsonObject? = null,
    val transport: String? = null,
    // ---- errors ----
    val error: String? = null,
    val raw: String? = null,
    val detail: String? = null,
)

/**
 * A scored look, flattened for the UI layer.
 *
 * [score] is the v3 headline and is what the row's `score` column stores.
 * [headlineScore] is the v4 weighted, capped number — identical to [score] on a
 * v4 response and null on a v3 one, which is the signal that none of the v4
 * fields below it are populated.
 */
data class HemScored(
    val score: Double,
    val hemComment: String,
    val subscores: Subscores,
    val swaps: List<String>,
    val annotations: List<Annotation>,
    // Sprint 2 additions — safe defaults so existing callers ignore them.
    val markupAnnotations: List<MarkupAnnotation> = emptyList(),
    val fitMap: FitMap? = null,
    val fitsYou: Double? = null,
    // ---- v4 ----
    val headlineScore: Double? = null,
    val headline: Headline? = null,
    val rubric: RubricEcho? = null,
    val briefVerdict: BriefVerdict? = null,
    val axes: List<Axis> = emptyList(),
    val dressCode: DressCode? = null,
    val presenceCheck: PresenceCheck? = null,
    val breakdown: ScoreBreakdown? = null,
    val whyThisNumber: String? = null,
    val lever: Lever? = null,
    val caveats: List<CaveatRow> = emptyList(),
    val intakeEcho: List<IntakeEcho> = emptyList(),
    val pieces: List<Piece> = emptyList(),
    val swapsV2: List<SwapV2> = emptyList(),
    val signals: JsonObject? = null,
    val paletteHex: List<String> = emptyList(),
    val intent: String? = null,
    val intentAxis: String? = null,
    val intentNote: String? = null,
    val rescoresRemaining: Int? = null,
    val scoringVersion: String? = null,
    val verdict: String? = null,
    val intakeWarnings: List<String> = emptyList(),
    val rawAxes: JsonObject? = null,
) {
    /** True when the server graded this look with the v4 engine. */
    val isV4: Boolean get() = scoringVersion == "v4" && axes.isNotEmpty()
}

/**
 * Real Hem scoring backed by the `score-outfit` Supabase edge function.
 *
 * Two request shapes, and the difference is one field. With
 * `client_features: ["axes_v4","xray_v4"]` the server returns the nine-axis
 * audit trail on top of the v3 envelope. Without it — see [Flags.SCORING_V4] —
 * the same call returns the pure v3 envelope scored on base weights, which is
 * the safe degradation if the Android UI cannot ship in the same release as the
 * server. Everything below the v3 keys is then simply absent and every v4
 * surface hides itself.
 */
object HemService {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Request encoder. A null means "not applicable" and is dropped rather than
     * sent as an explicit null the server would have to interpret — that is what
     * keeps `weather` off the wire when the device could not supply it. Defaults
     * ARE encoded, because `ScoreIntake` carries defaults so old rows decode and
     * an omitted `occasion` would be a different brief.
     */
    private val requestJson = Json { explicitNulls = false; encodeDefaults = true }

    /**
     * Pass an [intake] to be graded against a brief; pass [backUrl] to lift the
     * fit ceiling; pass [bodyProfile] to get `fits_you`. Called with neither —
     * as the try-on flow does, where a look is scored as a side effect and there
     * is no brief to offer — the server seeds the dials from the occasion and
     * replies with the plain v3 envelope.
     *
     * `honesty` is accepted and ignored by the server: every value has been
     * coerced to "honest" for months. It stays on the wire because the field is
     * part of the frozen request shape, not because it does anything.
     */
    suspend fun score(
        imageUrl: String,
        occasion: String,
        honesty: String = "honest",
        intake: ScoreIntake? = null,
        backUrl: String? = null,
        bodyProfile: BodyProfile? = null,
        intent: String? = null,
        locale: String = "en",
    ): HemScored {
        // The v4 payload is requested only when there is both a brief to grade
        // against and a build that can render the result.
        val wantsV4 = intake != null && Flags.SCORING_V4
        val payload: JsonObject = buildJsonObject {
            put("image_url", imageUrl)
            // Legacy mirror. Still written to `outfits.occasion` unchanged.
            put("occasion", occasion)
            put("honesty", honesty)
            put("locale", locale)
            if (!backUrl.isNullOrBlank()) put("back_url", backUrl)
            if (intent != null && intent.isNotBlank()) put("intent", intent.trim())
            if (bodyProfile != null) {
                put("body_profile", requestJson.encodeToJsonElement(BodyProfile.serializer(), bodyProfile))
            }
            if (intake != null) {
                put("intake", requestJson.encodeToJsonElement(ScoreIntake.serializer(), intake))
            }
            if (wantsV4) {
                put(
                    "client_features",
                    buildJsonArray {
                        add("axes_v4")
                        add("xray_v4")
                    },
                )
            }
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
        if (!parsed.intake_warnings.isNullOrEmpty()) {
            Log.w("HemService", "intake warnings: ${parsed.intake_warnings}")
        }
        return HemScored(
            score = String.format(Locale.US, "%.1f", s).toDouble(),
            hemComment = parsed.hem_comment ?: "",
            subscores = parsed.subscores ?: Subscores(),
            swaps = parsed.swaps.orEmpty(),
            annotations = parsed.annotations.orEmpty(),
            markupAnnotations = parsed.markup_annotations.orEmpty(),
            fitMap = parsed.fit_map,
            fitsYou = parsed.fits_you,
            headlineScore = parsed.headline?.value,
            headline = parsed.headline,
            rubric = parsed.rubric,
            briefVerdict = parsed.brief_verdict,
            axes = parsed.axes.orEmpty(),
            dressCode = parsed.dress_code,
            presenceCheck = parsed.presence_check,
            breakdown = parsed.score_breakdown,
            whyThisNumber = parsed.why_this_number,
            lever = parsed.lever,
            caveats = parsed.caveats.orEmpty(),
            intakeEcho = parsed.intake_echo.orEmpty(),
            pieces = parsed.pieces.orEmpty(),
            swapsV2 = parsed.swaps_v2.orEmpty(),
            signals = parsed.signals,
            paletteHex = parsed.palette_hex.orEmpty(),
            intent = parsed.intent,
            intentAxis = parsed.intent_axis,
            intentNote = parsed.intent_note,
            rescoresRemaining = parsed.rescores_remaining,
            scoringVersion = parsed.scoring_version,
            verdict = parsed.verdict,
            intakeWarnings = parsed.intake_warnings.orEmpty(),
            rawAxes = parsed.raw_axes,
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
            score = String.format(Locale.US, "%.1f", score).toDouble(),
            deltaVsAvg = String.format(Locale.US, "%.1f", delta).toDouble(),
            occasion = occasion,
            pullQuote = quote,
            pieces = pieces,
        )
    }
}
