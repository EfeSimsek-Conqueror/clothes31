package com.fitrater.app.data.repo

import com.fitrater.app.data.Supa
import com.fitrater.app.data.model.BodyProfile
import com.fitrater.app.data.model.BodyProfileResponse
import com.fitrater.app.data.model.DecodeInvitationResponse
import com.fitrater.app.data.model.InvitationDecoded
import com.fitrater.app.data.model.InvitationRead
import com.fitrater.app.data.model.OutfitCombo
import com.fitrater.app.data.model.SuggestOutfitsResponse
import com.fitrater.app.data.model.ComposeCoverResponse
import com.fitrater.app.data.model.HeadlineResponse
import com.fitrater.app.data.model.MagazineCover
import com.fitrater.app.data.model.FitHotspot
import com.fitrater.app.data.model.FitMap
import com.fitrater.app.data.model.MarkupAnnotation
import com.fitrater.app.data.model.MarkupCoords
import com.fitrater.app.data.model.TranscribeResponse
import com.fitrater.app.data.model.ClosetItem
import com.fitrater.app.data.model.ClosetItemInsert
import com.fitrater.app.data.model.CreditPack
import com.fitrater.app.data.model.CreditTransaction
import com.fitrater.app.data.model.CreditTxInsert
import com.fitrater.app.data.model.HemNote
import com.fitrater.app.data.model.Outfit
import com.fitrater.app.data.model.OutfitInsert
import com.fitrater.app.data.model.Profile
import com.fitrater.app.data.model.ProfileUpsert
import com.fitrater.app.data.model.PushSettings
import com.fitrater.app.data.model.PushSettingsUpsert
import com.fitrater.app.data.model.SundayLetter
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlin.time.Duration.Companion.hours

@Serializable
data class GenerateResponse(val image_url: String? = null, val seed: Long? = null, val error: String? = null)

/** Union used by the Home "Latest" card — combines outfits and closet pieces. */
sealed class LatestActivity {
    data class OutfitItem(val outfit: com.fitrater.app.data.model.Outfit, val signedUrl: String?) : LatestActivity()
    data class Piece(val item: com.fitrater.app.data.model.ClosetItem, val signedUrl: String?) : LatestActivity()
}

data class Stats(
    val pieces: Int,
    val looks: Int,
    val bestScore: Double?,
    val creditsUsed: Int,
    val streakDays: Int,
    val monthDelta: Double?,
)

object Repo {
    private val db get() = Supa.client.postgrest
    private val auth get() = Supa.client.auth
    private val storage get() = Supa.client.storage
    private val functions get() = Supa.client.functions

    val userId: String? get() = auth.currentUserOrNull()?.id
    val userEmail: String? get() = auth.currentUserOrNull()?.email

    suspend fun currentProfile(): Profile? {
        val uid = userId ?: return null
        return db["profiles"].select {
            filter { eq("id", uid) }
            limit(1)
        }.decodeSingleOrNull<Profile>()
    }

    suspend fun upsertProfile(p: ProfileUpsert) {
        db["profiles"].upsert(p) { onConflict = "id" }
    }

    suspend fun markFirstRunDone() {
        val uid = userId ?: return
        runCatching {
            db["profiles"].update({ set("first_run_done", true) }) {
                filter { eq("id", uid) }
            }
        }
    }

    suspend fun closetItemCount(): Int {
        val uid = userId ?: return 0
        return runCatching {
            db["closet_items"].select(Columns.list("id")) {
                filter { eq("user_id", uid) }
            }.decodeList<ClosetItem>().size
        }.getOrDefault(0)
    }

    suspend fun updateHonesty(value: String) {
        val uid = userId ?: return
        db["profiles"].update({
            set("honesty", value)
        }) {
            filter { eq("id", uid) }
        }
    }

    /** JSON decoder tolerant to legacy rows — used for row-by-row outfit decoding. */
    private val tolerantJson: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Decode a raw select result (JSON array text) row-by-row, skipping bad rows. */
    private fun decodeOutfitsSafe(jsonArrayText: String): List<Outfit> {
        val arr = runCatching { tolerantJson.parseToJsonElement(jsonArrayText).jsonArray }
            .getOrElse {
                android.util.Log.w("Repo", "outfits: could not parse response array: ${it.message}")
                return emptyList()
            }
        return arr.mapNotNull { el ->
            runCatching { tolerantJson.decodeFromJsonElement(Outfit.serializer(), el) }
                .onFailure { android.util.Log.w("Repo", "skip outfit due to decode: ${it.message}") }
                .getOrNull()
        }
    }

    suspend fun latestOutfit(): Outfit? {
        val uid = userId ?: return null
        val raw = db["outfits"].select {
            filter { eq("user_id", uid) }
            order("created_at", Order.DESCENDING)
            limit(1)
        }.data
        return decodeOutfitsSafe(raw).firstOrNull()
    }

    /**
     * Return whatever the user last created — outfit or Studio piece —
     * whichever has the newer created_at. Signed URL is resolved for both.
     */
    suspend fun latestActivity(): LatestActivity? {
        val outfit = runCatching { latestOutfit() }.getOrNull()
        val piece = runCatching {
            val uid = userId ?: return@runCatching null
            db["closet_items"].select {
                filter { eq("user_id", uid) }
                order("created_at", Order.DESCENDING)
                limit(1)
            }.decodeSingleOrNull<ClosetItem>()
        }.getOrNull()
        val outfitTs = outfit?.created_at
        val pieceTs = piece?.created_at
        val pickPiece = when {
            piece == null -> false
            outfit == null -> true
            outfitTs == null && pieceTs != null -> true
            outfitTs != null && pieceTs == null -> false
            outfitTs != null && pieceTs != null -> pieceTs > outfitTs
            else -> false
        }
        return if (pickPiece) {
            val url = piece!!.image_path?.let { runCatching { signedClosetUrl(it) }.getOrNull() }
                ?: piece.image_url
            LatestActivity.Piece(piece, url)
        } else if (outfit != null) {
            val url = outfit.photo_path?.let { runCatching { signedOutfitUrl(it) }.getOrNull() }
            LatestActivity.OutfitItem(outfit, url)
        } else null
    }

    suspend fun outfits(limit: Long = 20): List<Outfit> {
        val uid = userId ?: return emptyList()
        val raw = db["outfits"].select {
            filter { eq("user_id", uid) }
            order("created_at", Order.DESCENDING)
            limit(limit)
        }.data
        return decodeOutfitsSafe(raw)
    }

    suspend fun outfitById(id: String): Outfit? {
        val raw = db["outfits"].select { filter { eq("id", id) }; limit(1) }.data
        return decodeOutfitsSafe(raw).firstOrNull()
    }

    suspend fun bestOutfit(): Outfit? {
        val uid = userId ?: return null
        val raw = db["outfits"].select {
            filter { eq("user_id", uid) }
            order("score", Order.DESCENDING)
            limit(1)
        }.data
        return decodeOutfitsSafe(raw).firstOrNull()
    }

    suspend fun averageScore(): Double? {
        val list = outfits(50)
        val scored = list.filter { o -> (o.kind == null || o.kind == "score" || o.kind == "user_scan") }
            .mapNotNull { it.score }
            .filter { it > 0.0 }
        if (scored.isEmpty()) return null
        return scored.average()
    }

    suspend fun insertOutfit(o: OutfitInsert): Outfit {
        return db["outfits"].insert(o) { select() }.decodeSingle<Outfit>()
    }

    suspend fun uploadOutfitPhoto(bytes: ByteArray, ext: String = "jpg"): String {
        val uid = userId ?: error("Not signed in")
        val path = "$uid/${java.util.UUID.randomUUID()}.$ext"
        storage.from("outfits").upload(path, bytes) {
            upsert = false
        }
        return path
    }

    suspend fun signedOutfitUrl(path: String): String? = runCatching {
        storage.from("outfits").createSignedUrl(path, 1.hours)
    }.getOrNull()

    suspend fun uploadClosetPhoto(bytes: ByteArray, ext: String = "jpg"): String {
        val uid = userId ?: error("Not signed in")
        val path = "$uid/${java.util.UUID.randomUUID()}.$ext"
        storage.from("closet").upload(path, bytes) { upsert = false }
        return path
    }

    suspend fun signedClosetUrl(path: String): String? = runCatching {
        storage.from("closet").createSignedUrl(path, 1.hours)
    }.getOrNull()

    suspend fun insertClosetItem(item: ClosetItemInsert): ClosetItem {
        return db["closet_items"].insert(item) { select() }.decodeSingle<ClosetItem>()
    }

    suspend fun creditPacks(): List<CreditPack> = runCatching {
        db["credit_packs"].select {
            order("sort", Order.ASCENDING)
        }.decodeList<CreditPack>()
    }.getOrDefault(emptyList())

    suspend fun closetItems(limit: Long = 40): List<ClosetItem> {
        val uid = userId ?: return emptyList()
        return db["closet_items"].select {
            filter { eq("user_id", uid) }
            order("created_at", Order.DESCENDING)
            limit(limit)
        }.decodeList<ClosetItem>()
    }

    suspend fun latestSundayLetter(): SundayLetter? {
        val uid = userId ?: return null
        return db["sunday_letters"].select {
            filter { eq("user_id", uid) }
            order("week_start", Order.DESCENDING)
            limit(1)
        }.decodeSingleOrNull<SundayLetter>()
    }

    /** Latest N Sunday letters, ordered by week_start desc. */
    suspend fun sundayLetters(limit: Long = 12): List<SundayLetter> {
        val uid = userId ?: return emptyList()
        return runCatching {
            db["sunday_letters"].select {
                filter { eq("user_id", uid) }
                order("week_start", Order.DESCENDING)
                limit(limit)
            }.decodeList<SundayLetter>()
        }.getOrDefault(emptyList())
    }

    /** Best-scoring outfits within a date window (device local dates). */
    suspend fun outfitsInWindow(startIso: String, endIso: String, limit: Long = 3): List<Outfit> {
        val uid = userId ?: return emptyList()
        return runCatching {
            val raw = db["outfits"].select {
                filter {
                    eq("user_id", uid)
                    gte("created_at", startIso)
                    lte("created_at", endIso)
                }
                order("score", Order.DESCENDING)
                limit(limit)
            }.data
            decodeOutfitsSafe(raw)
        }.getOrDefault(emptyList())
    }

    /** Update appearance preferences (theme, text_scale, reduce_motion). */
    suspend fun updateAppearance(theme: String? = null, textScale: Double? = null, reduceMotion: Boolean? = null) {
        val uid = userId ?: return
        db["profiles"].update({
            if (theme != null) set("theme", theme)
            if (textScale != null) set("text_scale", textScale)
            if (reduceMotion != null) set("reduce_motion", reduceMotion)
        }) { filter { eq("id", uid) } }
    }

    suspend fun latestHemNote(): HemNote? {
        val uid = userId ?: return null
        return db["hem_notes"].select {
            filter { eq("user_id", uid) }
            order("created_at", Order.DESCENDING)
            limit(1)
        }.decodeSingleOrNull<HemNote>()
    }

    suspend fun credits(): Int {
        val uid = userId ?: return 0
        val rows = db["credit_transactions"].select(Columns.list("amount")) {
            filter { eq("user_id", uid) }
        }.decodeList<CreditTransaction>()
        return rows.sumOf { it.amount ?: 0 }
    }

    suspend fun grantSignupCreditsIfEmpty() {
        val uid = userId ?: return
        val existing = db["credit_transactions"].select(Columns.list("id")) {
            filter { eq("user_id", uid) }
            limit(1)
        }.decodeList<CreditTransaction>()
        if (existing.isEmpty()) {
            val g = com.fitrater.app.data.Supa.SIGNUP_CREDITS
            db["credit_transactions"].insert(CreditTxInsert(user_id = uid, amount = g, kind = "signup", balance_after = g))
        }
        com.fitrater.app.util.CreditsBus.refreshAsync()
    }

    suspend fun pushSettings(): PushSettings? {
        val uid = userId ?: return null
        return db["push_settings"].select {
            filter { eq("user_id", uid) }
            limit(1)
        }.decodeSingleOrNull<PushSettings>()
    }

    suspend fun spendCredits(amount: Int, kind: String) {
        val uid = userId ?: error("Not signed in")
        val current = credits()
        val next = current - amount
        db["credit_transactions"].insert(CreditTxInsert(user_id = uid, amount = -amount, kind = kind, balance_after = next))
        com.fitrater.app.util.CreditsBus.refreshAsync()
    }

    /**
     * Count of outfits with `kind = 'roast'` created today (device local timezone).
     * Used to enforce Free-tier's 1 roast/day cap so Brutal-mode stays a Pro teaser.
     */
    suspend fun roastsToday(): Int {
        val uid = userId ?: return 0
        val startOfToday = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toOffsetDateTime()
            .toString()
        return runCatching {
            val raw = db["outfits"].select(Columns.list("id,created_at,kind")) {
                filter {
                    eq("user_id", uid)
                    eq("kind", "roast")
                    gte("created_at", startOfToday)
                }
            }.data
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonArray
            arr?.size ?: 0
        }.getOrDefault(0)
    }

    /** Sum of credits spent (positive number) today in the device's local timezone. */
    suspend fun creditsSpentToday(): Int {
        val uid = userId ?: return 0
        val startOfToday = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toOffsetDateTime()
            .toString()
        return runCatching {
            val rows = db["credit_transactions"].select(Columns.list("amount,created_at")) {
                filter {
                    eq("user_id", uid)
                    gte("created_at", startOfToday)
                }
            }.decodeList<CreditTransaction>()
            rows.mapNotNull { it.amount }.filter { it < 0 }.sumOf { -it }
        }.getOrDefault(0)
    }

    /** Sum of credits spent (positive number) since the first of this month, device local. */
    suspend fun creditsSpentThisMonth(): Int {
        val uid = userId ?: return 0
        val today = java.time.LocalDate.now()
        val startOfMonth = today.withDayOfMonth(1)
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toOffsetDateTime()
            .toString()
        return runCatching {
            val rows = db["credit_transactions"].select(Columns.list("amount,created_at")) {
                filter {
                    eq("user_id", uid)
                    gte("created_at", startOfMonth)
                }
            }.decodeList<CreditTransaction>()
            rows.mapNotNull { it.amount }.filter { it < 0 }.sumOf { -it }
        }.getOrDefault(0)
    }

    /** Mark the profile.rated_ok flag so we never grant the Play-rating credits twice. */
    /** Returns true only if the flag was actually persisted — callers gate the reward on it. */
    suspend fun markRatedOk(): Boolean {
        val uid = userId ?: return false
        return runCatching {
            db["profiles"].update({ set("rated_ok", true) }) { filter { eq("id", uid) } }
        }.isSuccess
    }

    /** Credit a positive [amount] to the current user. Used after successful IAP purchase. */
    suspend fun addCredits(amount: Int, kind: String = "iap_purchase") {
        val uid = userId ?: error("Not signed in")
        val current = credits()
        val next = current + amount
        db["credit_transactions"].insert(CreditTxInsert(user_id = uid, amount = amount, kind = kind, balance_after = next))
        com.fitrater.app.util.CreditsBus.refreshAsync()
    }

    suspend fun generatePiece(prompt: String): GenerateResponse = generatePiece(prompt, emptyList())

    suspend fun generatePiece(prompt: String, imageUrls: List<String>): GenerateResponse {
        val payload: JsonObject = buildJsonObject {
            put("prompt", prompt)
            if (imageUrls.isNotEmpty()) {
                put(
                    "image_urls",
                    kotlinx.serialization.json.buildJsonArray {
                        imageUrls.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                    },
                )
            }
        }
        val resp: HttpResponse = functions.invoke(
            function = "generate-piece",
            body = payload,
        )
        val text: String = resp.body()
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        return runCatching { json.decodeFromString(GenerateResponse.serializer(), text) }
            .getOrElse { GenerateResponse(error = text.take(200)) }
    }

    /**
     * One-time body calibration. Sends the front (and optional side) photo
     * URLs to the `analyze-body` edge function and returns a [BodyProfileResponse]
     * used to personalize future ratings.
     */
    suspend fun analyzeBody(frontUrl: String, sideUrl: String?): BodyProfileResponse {
        val payload: JsonObject = buildJsonObject {
            put("front_url", frontUrl)
            if (sideUrl != null) put("side_url", sideUrl)
        }
        val resp: HttpResponse = functions.invoke(function = "analyze-body", body = payload)
        val text: String = resp.body()
        val json = Json { ignoreUnknownKeys = true }
        return runCatching { json.decodeFromString(BodyProfileResponse.serializer(), text) }
            .getOrElse { BodyProfileResponse(error = text.take(200)) }
    }

    // ---- Sprint 2: transcribe-intent + annotation/fit-map persistence ----

    /**
     * Call the `transcribe-intent` edge function with a signed audio URL and
     * return the raw + sanitized transcript from Fal wizper. Errors are
     * flattened into [TranscribeResponse.error] rather than thrown, matching
     * the other edge-function wrappers.
     */
    suspend fun transcribeIntent(audioUrl: String): TranscribeResponse {
        val payload: JsonObject = buildJsonObject { put("audio_url", audioUrl) }
        val resp: HttpResponse = functions.invoke(function = "transcribe-intent", body = payload)
        val text: String = resp.body()
        val json = Json { ignoreUnknownKeys = true }
        return runCatching { json.decodeFromString(TranscribeResponse.serializer(), text) }
            .getOrElse { TranscribeResponse(error = text.take(200)) }
    }

    /**
     * Batch-replace markup annotations for an outfit. Existing rows are
     * deleted first so this is a full replace. RLS policy `own_annotations`
     * enforces ownership.
     */
    suspend fun saveAnnotations(outfitId: String, annotations: List<MarkupAnnotation>) {
        runCatching {
            db["outfit_annotations"].delete { filter { eq("outfit_id", outfitId) } }
        }
        if (annotations.isEmpty()) return
        @Serializable
        data class Row(
            val outfit_id: String,
            val type: String,
            val coords: MarkupCoords,
            val note: String? = null,
            val confidence: Double? = null,
            val idx: Int,
        )
        val rows = annotations.mapIndexed { i, a ->
            Row(outfit_id = outfitId, type = a.type, coords = a.coords, note = a.note, confidence = a.confidence, idx = i)
        }
        db["outfit_annotations"].insert(rows)
    }

    /** Upsert the fit-tension heatmap for an outfit (one row per outfit). */
    suspend fun saveFitMap(outfitId: String, map: FitMap) {
        @Serializable
        data class Row(
            val outfit_id: String,
            val resolution: List<Int>? = null,
            val grid: List<List<Double>>? = null,
            val hotspots: List<FitHotspot>? = null,
        )
        val row = Row(outfit_id = outfitId, resolution = map.resolution, grid = map.grid, hotspots = map.hotspots)
        db["outfit_fit_maps"].upsert(row) { onConflict = "outfit_id" }
    }

    /** Load ordered markup annotations for an outfit. */
    suspend fun loadAnnotations(outfitId: String): List<MarkupAnnotation> {
        @Serializable
        data class Row(
            val type: String,
            val coords: MarkupCoords,
            val note: String? = null,
            val confidence: Double? = null,
            val idx: Int? = null,
        )
        return runCatching {
            db["outfit_annotations"].select(Columns.list("type", "coords", "note", "confidence", "idx")) {
                filter { eq("outfit_id", outfitId) }
                order("idx", Order.ASCENDING)
            }.decodeList<Row>().map {
                MarkupAnnotation(type = it.type, coords = it.coords, note = it.note ?: "", confidence = it.confidence)
            }
        }.getOrDefault(emptyList())
    }

    /** Load a single fit-map row for an outfit, or null if none. */
    suspend fun loadFitMap(outfitId: String): FitMap? {
        @Serializable
        data class Row(
            val resolution: List<Int>? = null,
            val grid: List<List<Double>>? = null,
            val hotspots: List<FitHotspot>? = null,
        )
        val row = runCatching {
            db["outfit_fit_maps"].select(Columns.list("resolution", "grid", "hotspots")) {
                filter { eq("outfit_id", outfitId) }
                limit(1)
            }.decodeSingleOrNull<Row>()
        }.getOrNull() ?: return null
        return FitMap(resolution = row.resolution, grid = row.grid, hotspots = row.hotspots)
    }

    /** Dedicated try-on endpoint (Fashn) — much better than generic image-edit for garment try-on. */
    suspend fun tryOnPiece(personUrl: String, garmentUrl: String, category: String = "auto"): GenerateResponse {
        val payload: JsonObject = buildJsonObject {
            put("person_url", personUrl)
            put("garment_url", garmentUrl)
            put("category", category)
            put("mode", "quality")
        }
        val resp: HttpResponse = functions.invoke(function = "tryon-outfit", body = payload)
        val text: String = resp.body()
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        return runCatching { json.decodeFromString(GenerateResponse.serializer(), text) }
            .getOrElse { GenerateResponse(error = text.take(200)) }
    }

    suspend fun updateClosetItem(id: String, image_path: String? = null, name: String? = null) {
        if (image_path == null && name == null) return
        db["closet_items"].update({
            if (image_path != null) set("image_path", image_path)
            if (name != null) set("name", name)
        }) {
            filter { eq("id", id) }
        }
    }

    /** Fetch a single closet item by id (used by history navigation). */
    suspend fun closetItemById(id: String): ClosetItem? =
        db["closet_items"].select { filter { eq("id", id) }; limit(1) }.decodeSingleOrNull<ClosetItem>()

    /** Fetch all children (variations) of a given piece. */
    suspend fun closetChildren(parentId: String): List<ClosetItem> {
        return runCatching {
            db["closet_items"].select {
                filter { eq("parent_id", parentId) }
                order("created_at", Order.ASCENDING)
            }.decodeList<ClosetItem>()
        }.getOrDefault(emptyList())
    }

    // ---- Sprint 3: Magazine Covers ----

    /**
     * Compose a magazine-cover render for an outfit photo. Delegates to the
     * `compose-cover` edge function (Fal any-llm/vision for headline + pull-quote,
     * Fal nano-banana/edit for the final 9:16 cover). Row is persisted server-side.
     */
    suspend fun composeCover(
        sourceImageUrl: String,
        outfitId: String? = null,
        userName: String? = null,
        seedHeadline: String? = null,
        referenceCoverUrl: String? = null,
        referenceTemplateId: String? = null,
        masthead: String? = null,
        mood: String? = null,
        layout: String? = null,
        customHeadline: String? = null,
        customPullQuote: String? = null,
        coverLines: List<String>? = null,
        price: String? = null,
        includeBarcode: Boolean? = null,
        userPrompt: String? = null,
        customMasthead: String? = null,
    ): ComposeCoverResponse {
        val payload: JsonObject = buildJsonObject {
            put("source_image_url", sourceImageUrl)
            if (outfitId != null) put("outfit_id", outfitId)
            if (userName != null) put("user_name", userName)
            if (seedHeadline != null) put("seed_headline", seedHeadline)
            if (referenceCoverUrl != null) put("reference_cover_url", referenceCoverUrl)
            if (referenceTemplateId != null) put("reference_template_id", referenceTemplateId)
            if (masthead != null) put("masthead", masthead)
            if (mood != null) put("mood", mood)
            if (layout != null) put("layout", layout)
            if (customHeadline != null) put("custom_headline", customHeadline)
            if (customPullQuote != null) put("custom_pull_quote", customPullQuote)
            if (coverLines != null) {
                putJsonArray("cover_lines") { coverLines.forEach { add(it) } }
            }
            if (price != null) put("price", price)
            if (includeBarcode != null) put("include_barcode", includeBarcode)
            if (userPrompt != null) put("user_prompt", userPrompt)
            if (customMasthead != null) put("custom_masthead", customMasthead)
        }
        val resp: HttpResponse = functions.invoke(function = "compose-cover", body = payload)
        val text: String = resp.body()
        val json = Json { ignoreUnknownKeys = true }
        return runCatching { json.decodeFromString(ComposeCoverResponse.serializer(), text) }
            .getOrElse { ComposeCoverResponse(error = text.take(200)) }
    }

    /**
     * Create a style-only cover TEMPLATE (no subject photo). Delegates to the
     * `create-cover-template` edge function — pure typography + color, no Fal.
     * The saved template can later be fed to `composeCover` as a
     * `referenceCoverUrl` to transfer style DNA onto a real photo.
     */
    suspend fun createCoverTemplate(
        masthead: String? = null,
        headline: String,
        pullQuote: String? = null,
        mood: String? = null,
        color: String? = null,
        layout: String? = null,
        coverLines: List<String>? = null,
        pose: String? = null,
        price: String? = null,
        includeBarcode: Boolean? = null,
    ): ComposeCoverResponse {
        val payload: JsonObject = buildJsonObject {
            if (masthead != null) put("masthead", masthead)
            put("headline", headline)
            if (pullQuote != null) put("pull_quote", pullQuote)
            if (mood != null) put("mood", mood)
            if (color != null) put("color", color)
            if (layout != null) put("layout", layout)
            if (coverLines != null) {
                putJsonArray("cover_lines") {
                    coverLines.forEach { add(it) }
                }
            }
            if (pose != null) put("pose", pose)
            if (price != null) put("price", price)
            if (includeBarcode != null) put("include_barcode", includeBarcode)
        }
        val resp: HttpResponse = functions.invoke(function = "create-cover-template", body = payload)
        val text: String = resp.body()
        val json = Json { ignoreUnknownKeys = true }
        return runCatching { json.decodeFromString(ComposeCoverResponse.serializer(), text) }
            .getOrElse { ComposeCoverResponse(error = text.take(200)) }
    }

    /** Regenerate the headline + pull-quote for an existing cover row (server updates it). */
    suspend fun regenerateCoverHeadline(coverId: String): HeadlineResponse {
        val payload: JsonObject = buildJsonObject { put("cover_id", coverId) }
        return invokeHeadline(payload)
    }

    /** Regenerate a headline+quote for an ad-hoc outfit image (no persisted cover). */
    suspend fun regenerateCoverHeadline(outfitId: String?, sourceImageUrl: String): HeadlineResponse {
        val payload: JsonObject = buildJsonObject {
            if (outfitId != null) put("outfit_id", outfitId)
            put("source_image_url", sourceImageUrl)
        }
        return invokeHeadline(payload)
    }

    private suspend fun invokeHeadline(payload: JsonObject): HeadlineResponse {
        val resp: HttpResponse = functions.invoke(function = "regenerate-cover-headline", body = payload)
        val text: String = resp.body()
        val json = Json { ignoreUnknownKeys = true }
        return runCatching { json.decodeFromString(HeadlineResponse.serializer(), text) }
            .getOrElse { HeadlineResponse(error = text.take(200)) }
    }

    /** Load the current user's magazine covers, most recent first. */
    suspend fun loadCovers(limit: Long = 40): List<MagazineCover> {
        val uid = userId ?: return emptyList()
        return runCatching {
            db["magazine_covers"].select {
                filter { eq("user_id", uid) }
                order("created_at", Order.DESCENDING)
                limit(limit)
            }.decodeList<MagazineCover>()
        }.getOrDefault(emptyList())
    }

    /**
     * Load the current user's cover TEMPLATES (rows with `outfit_id IS NULL`),
     * most recent first. Templates are style-only covers created via
     * `createCoverTemplate` — no subject photo attached.
     */
    suspend fun loadCoverTemplates(limit: Long = 40): List<MagazineCover> {
        val uid = userId ?: return emptyList()
        return runCatching {
            db["magazine_covers"].select {
                filter {
                    eq("user_id", uid)
                    isExact("outfit_id", null)
                }
                order("created_at", Order.DESCENDING)
                limit(limit)
            }.decodeList<MagazineCover>()
        }.getOrDefault(emptyList())
    }

    /** Latest magazine cover for a specific outfit, or null if none. */
    suspend fun loadCoverForOutfit(outfitId: String): MagazineCover? {
        return runCatching {
            db["magazine_covers"].select {
                filter { eq("outfit_id", outfitId) }
                order("created_at", Order.DESCENDING)
                limit(1)
            }.decodeSingleOrNull<MagazineCover>()
        }.getOrNull()
    }

    suspend fun downloadBytes(url: String): ByteArray {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.inputStream.use { it.readBytes() }
        }
    }

    /** Upload avatar image to `avatars/{uid}/avatar.jpg`, return signed URL. */
    suspend fun uploadAvatar(bytes: ByteArray): String {
        val uid = userId ?: error("Not signed in")
        val path = "$uid/avatar.jpg"
        storage.from("avatars").upload(path, bytes) { upsert = true }
        // Bucket is public — build the public URL directly. Fallback to signed if that fails.
        return runCatching { storage.from("avatars").publicUrl(path) }
            .getOrNull()
            ?: runCatching { storage.from("avatars").createSignedUrl(path, 24.hours) }.getOrNull()
            ?: error("Could not resolve avatar URL")
    }

    suspend fun updateAvatarUrl(url: String) {
        val uid = userId ?: return
        db["profiles"].update({ set("avatar_url", url) }) { filter { eq("id", uid) } }
    }

    /** Partial profile write for the Style Profile screen. */
    suspend fun updateStyleProfile(
        gender: String?,
        style_tags: List<String>?,
        preferred_fabrics: List<String>?,
        preferred_colors: List<String>?,
        sensitivities: String?,
    ) {
        val uid = userId ?: return
        db["profiles"].update({
            if (gender != null) set("gender", gender)
            if (style_tags != null) set("style_tags", style_tags)
            if (preferred_fabrics != null) set("preferred_fabrics", preferred_fabrics)
            if (preferred_colors != null) set("preferred_colors", preferred_colors)
            if (sensitivities != null) set("sensitivities", sensitivities)
        }) { filter { eq("id", uid) } }
    }

    /** Composite stats block for the You sheet. */
    suspend fun stats(): Stats {
        val piecesCount = runCatching { closetItems(500).size }.getOrDefault(0)
        val outfits = runCatching { outfits(500) }.getOrDefault(emptyList())
        val looksCount = outfits.size
        val best = outfits.mapNotNull { it.score }.maxOrNull()
        val uid = userId
        val creditsUsed = if (uid == null) 0 else runCatching {
            val rows = db["credit_transactions"].select(Columns.list("amount")) {
                filter { eq("user_id", uid) }
            }.decodeList<CreditTransaction>()
            rows.mapNotNull { it.amount }.filter { it < 0 }.sumOf { -it }
        }.getOrDefault(0)

        // Distinct scan dates, walking backward from today.
        val dates: Set<java.time.LocalDate> = outfits.mapNotNull { o ->
            runCatching {
                val raw = o.created_at ?: return@mapNotNull null
                java.time.OffsetDateTime.parse(raw).toLocalDate()
            }.getOrNull()
        }.toSet()
        var streak = 0
        var cursor = java.time.LocalDate.now()
        while (cursor in dates) {
            streak += 1
            cursor = cursor.minusDays(1)
        }

        // Month delta: this month's avg vs personal all-time avg.
        val today = java.time.LocalDate.now()
        val thisMonth = outfits.filter {
            runCatching {
                val d = java.time.OffsetDateTime.parse(it.created_at ?: return@runCatching false).toLocalDate()
                d.year == today.year && d.monthValue == today.monthValue
            }.getOrDefault(false)
        }.mapNotNull { it.score }
        val allTime = outfits.mapNotNull { it.score }
        val monthDelta: Double? = if (thisMonth.isNotEmpty() && allTime.isNotEmpty()) {
            thisMonth.average() - allTime.average()
        } else null

        return Stats(piecesCount, looksCount, best, creditsUsed, streak, monthDelta)
    }

    /** Serialize all user-owned rows across tables for GDPR export. */
    suspend fun exportUserData(): String {
        val uid = userId ?: error("Not signed in")
        val json = kotlinx.serialization.json.Json { prettyPrint = true; encodeDefaults = false }
        val outfitsRaw = runCatching {
            db["outfits"].select { filter { eq("user_id", uid) } }.data
        }.getOrDefault("[]")
        val closetRaw = runCatching {
            db["closet_items"].select { filter { eq("user_id", uid) } }.data
        }.getOrDefault("[]")
        val creditsRaw = runCatching {
            db["credit_transactions"].select { filter { eq("user_id", uid) } }.data
        }.getOrDefault("[]")
        val profileRaw = runCatching {
            db["profiles"].select { filter { eq("id", uid) } }.data
        }.getOrDefault("[]")
        val notesRaw = runCatching {
            db["hem_notes"].select { filter { eq("user_id", uid) } }.data
        }.getOrDefault("[]")
        val lettersRaw = runCatching {
            db["sunday_letters"].select { filter { eq("user_id", uid) } }.data
        }.getOrDefault("[]")
        val obj = buildJsonObject {
            put("exported_at", kotlinx.serialization.json.JsonPrimitive(java.time.Instant.now().toString()))
            put("user_id", kotlinx.serialization.json.JsonPrimitive(uid))
            put("email", kotlinx.serialization.json.JsonPrimitive(userEmail ?: ""))
            put("profile", json.parseToJsonElement(profileRaw))
            put("outfits", json.parseToJsonElement(outfitsRaw))
            put("closet_items", json.parseToJsonElement(closetRaw))
            put("credit_transactions", json.parseToJsonElement(creditsRaw))
            put("hem_notes", json.parseToJsonElement(notesRaw))
            put("sunday_letters", json.parseToJsonElement(lettersRaw))
        }
        return json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), obj)
    }

    /** Wipe user-owned rows and storage folders. Signs the user out at the end. */
    suspend fun deleteAllUserData() {
        val uid = userId ?: return
        // Rows first — storage deletions can partial-fail without blocking sign-out.
        listOf("outfits", "closet_items", "credit_transactions", "hem_notes", "sunday_letters", "push_settings")
            .forEach { table ->
                runCatching {
                    db[table].delete { filter { eq(if (table == "profiles") "id" else "user_id", uid) } }
                }
            }
        runCatching { db["profiles"].delete { filter { eq("id", uid) } } }
        // Storage — best effort. Supabase kt has no recursive delete; list + remove.
        listOf("avatars", "closet", "outfits", "references").forEach { bucket ->
            runCatching {
                val prefix = "$uid/"
                val items = storage.from(bucket).list(prefix)
                if (items.isNotEmpty()) {
                    storage.from(bucket).delete(items.map { "$prefix${it.name}" })
                }
            }
        }
        runCatching { auth.signOut() }
    }

    /** Persist that the first-run paywall has been shown once (won't auto-show again). */
    suspend fun markPaywallShown() {
        val uid = userId ?: return
        runCatching {
            db["profiles"].update({ set("paywall_shown", true) }) { filter { eq("id", uid) } }
        }
    }

    /** Read the paywall_shown flag defensively — returns false if the column doesn't exist yet. */
    suspend fun paywallShown(): Boolean {
        val uid = userId ?: return true
        return runCatching {
            val raw = db["profiles"].select(Columns.list("paywall_shown")) {
                filter { eq("id", uid) }
                limit(1)
            }.data
            val el = kotlinx.serialization.json.Json.parseToJsonElement(raw)
            val arr = (el as? kotlinx.serialization.json.JsonArray) ?: return@runCatching false
            val first = arr.firstOrNull() as? kotlinx.serialization.json.JsonObject
            (first?.get("paywall_shown") as? kotlinx.serialization.json.JsonPrimitive)
                ?.content?.equals("true", ignoreCase = true) == true
        }.getOrDefault(false)
    }

    /** True if the user has any positive purchase transaction on record. */
    suspend fun hasEverPurchased(): Boolean {
        val uid = userId ?: return false
        val rows = runCatching {
            db["credit_transactions"].select(Columns.list("kind,amount")) {
                filter {
                    eq("user_id", uid)
                    gt("amount", 0)
                }
            }.decodeList<CreditTransaction>()
        }.getOrDefault(emptyList())
        return rows.any { (it.kind ?: "").startsWith("iap_") || it.kind == "purchase" }
    }

    suspend fun upsertPushSettings(morning: Boolean, weekly: Boolean, wrapped: Boolean) {
        val uid = userId ?: return
        db["push_settings"].upsert(
            PushSettingsUpsert(user_id = uid, morning_stylist = morning, weekly_task = weekly, wrapped = wrapped),
        ) { onConflict = "user_id" }
    }

    /**
     * How many try-on outfits reference a given studio piece. Queries the
     * `outfits.linked_piece_id` column (added in the 2026-07 migration). Returns 0
     * on failure — the piece detail merely omits the "Worn N times" line.
     */
    // ---- Sprint 5: Invitation decoder + outfit suggestions ----

    /**
     * Call `decode-invitation` with a signed image URL. Errors are flattened
     * into [DecodeInvitationResponse.error] rather than thrown.
     */
    suspend fun decodeInvitation(imageUrl: String): DecodeInvitationResponse {
        val payload: JsonObject = buildJsonObject { put("image_url", imageUrl) }
        val resp: HttpResponse = functions.invoke(function = "decode-invitation", body = payload)
        val text: String = resp.body()
        val json = Json { ignoreUnknownKeys = true }
        return runCatching { json.decodeFromString(DecodeInvitationResponse.serializer(), text) }
            .getOrElse { DecodeInvitationResponse(error = text.take(200)) }
    }

    /**
     * Ask `suggest-outfits` for 3 combos from the given closet inventory
     * (trimmed to top 30) plus optional body profile + style tags.
     */
    suspend fun suggestOutfits(
        dressCode: String,
        eventType: String,
        notes: String? = null,
        closet: List<ClosetItem>,
        bodyProfile: BodyProfile? = null,
        styleTags: List<String>? = null,
    ): SuggestOutfitsResponse {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        val payload: JsonObject = buildJsonObject {
            put("dress_code", dressCode)
            put("event_type", eventType)
            if (notes != null) put("notes", notes)
            if (bodyProfile != null) {
                put("body_profile", json.encodeToJsonElement(BodyProfile.serializer(), bodyProfile))
            }
            if (styleTags != null) {
                putJsonArray("style_tags") { styleTags.forEach { add(it) } }
            }
            putJsonArray("closet_items") {
                closet.take(30).forEach { c ->
                    val id = c.id ?: return@forEach
                    addJsonObject {
                        put("id", id)
                        put("name", c.name ?: "")
                        put("category", c.category ?: "")
                        if (c.subcategory != null) put("subcategory", c.subcategory)
                        if (c.image_url != null) put("image_url", c.image_url)
                    }
                }
            }
        }
        val resp: HttpResponse = functions.invoke(function = "suggest-outfits", body = payload)
        val text: String = resp.body()
        return runCatching { json.decodeFromString(SuggestOutfitsResponse.serializer(), text) }
            .getOrElse { SuggestOutfitsResponse(error = text.take(200)) }
    }

    /**
     * Persist a decoded invitation + suggested combos to `invitation_reads`.
     * RLS policy `own_invitation_reads` enforces ownership.
     */
    suspend fun saveInvitationRead(
        imagePath: String?,
        decoded: InvitationDecoded,
        combos: List<OutfitCombo>,
    ) {
        val uid = userId ?: return
        @Serializable
        data class Row(
            val user_id: String,
            val image_path: String? = null,
            val event_type: String? = null,
            val dress_code: String? = null,
            val time_of_day: String? = null,
            val venue: String? = null,
            val notes: String? = null,
            val suggested_combos: List<OutfitCombo>? = null,
        )
        val row = Row(
            user_id = uid,
            image_path = imagePath,
            event_type = decoded.event_type,
            dress_code = decoded.dress_code,
            time_of_day = decoded.time_of_day,
            venue = decoded.venue,
            notes = decoded.notes,
            suggested_combos = combos,
        )
        db["invitation_reads"].insert(row)
    }

    /** Load invitation reads for the current user, newest first. */
    suspend fun loadInvitationReads(): List<InvitationRead> {
        val uid = userId ?: return emptyList()
        return runCatching {
            db["invitation_reads"].select {
                filter { eq("user_id", uid) }
                order("created_at", Order.DESCENDING)
                limit(50)
            }.decodeList<InvitationRead>()
        }.getOrDefault(emptyList())
    }

    suspend fun tryOnCountForPiece(pieceId: String): Int {
        val uid = userId ?: return 0
        return runCatching {
            val raw = db["outfits"].select(Columns.list("id")) {
                filter {
                    eq("user_id", uid)
                    eq("kind", "tryon")
                    eq("linked_piece_id", pieceId)
                }
            }.data
            // decode a lightweight shape rather than pull the full Outfit graph.
            val trimmed = Json { ignoreUnknownKeys = true; isLenient = true }
                .parseToJsonElement(raw).jsonArray
            trimmed.size
        }.getOrDefault(0)
    }
}
