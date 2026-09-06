package com.fitrater.app.ui.screens.studio

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitrater.app.data.Supa
import com.fitrater.app.data.billing.RcBilling
import com.fitrater.app.data.model.ClosetItem
import com.fitrater.app.data.model.OutfitInsert
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.ui.components.ReportContentSheet
import com.fitrater.app.ui.components.ReportKind
import com.fitrater.app.ui.screens.StudioCreateScreen
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.PrimaryButton
import com.fitrater.app.ui.theme.SerifDisplay
import com.fitrater.app.util.CreditsBus
import com.fitrater.app.util.CreditsGate
import com.fitrater.app.util.GateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Studio 2.0 — sequential outfit wizard. Android port of the iOS
 * `OutfitWizardView` / `OutfitWizardModel` (Studio/StudioCreateView.swift).
 *
 * Four phases:
 *  1. COMPOSE   — grouped piece picker + "from your closet" strip.
 *  2. ITERATING — one [StudioCreateScreen] run per selected slot.
 *  3. COMBINING — full-screen waiting overlay while front + side render.
 *  4. DONE      — swipeable FRONT / SIDE pager.
 *
 * Credits: each piece is billed by [StudioCreateScreen] itself (studio_gen).
 * The combine step bills [Supa.STUDIO_COMBINE_STANDALONE_COST] under the
 * `studio_outfit_combine` kind — identical to iOS `runCombine()`. Before the
 * run starts we gate-check the *total* [Supa.outfitCost] so the user isn't
 * walked through five design flows only to hit an empty balance at the end.
 */

private enum class WizardPhase { COMPOSE, ITERATING, COMBINING, DONE }

/** A closet item pinned to a slot — the wizard reuses it instead of generating. */
private data class Prefill(val url: String, val itemId: String)

private const val FREE_PIECE_LIMIT = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitWizardScreen(
    onClose: () -> Unit,
    onOpenPaywall: () -> Unit = onClose,
) {
    val scope = rememberCoroutineScope()
    val credits by CreditsBus.balance.collectAsState()

    var phase by remember { mutableStateOf(WizardPhase.COMPOSE) }
    val selection = remember { mutableStateListOf<OutfitPiece>() }
    val prefilled = remember { mutableStateMapOf<OutfitPiece, Prefill>() }
    var autoCombine by remember { mutableStateOf(true) }

    // Iteration runtime
    var currentStep by remember { mutableStateOf(0) }
    // Bumped on every advance so StudioCreateScreen re-inits with fresh state.
    var stepKey by remember { mutableStateOf(0) }

    // Combine results
    var combining by remember { mutableStateOf(false) }
    var frontUrl by remember { mutableStateOf<String?>(null) }
    var sideUrl by remember { mutableStateOf<String?>(null) }
    var combineError by remember { mutableStateOf<String?>(null) }
    // Journal row id of the persisted FRONT view — the id we report against.
    var combinedOutfitId by remember { mutableStateOf<String?>(null) }
    var reporting by remember { mutableStateOf(false) }

    var showProUpsell by remember { mutableStateOf(false) }

    // Closet strip (compose phase)
    var stripItems by remember { mutableStateOf<List<ClosetItem>>(emptyList()) }
    var stripUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var stripLoaded by remember { mutableStateOf(false) }

    // Per-slot closet picker sheet
    var pickerForPiece by remember { mutableStateOf<OutfitPiece?>(null) }
    var pickerOptions by remember { mutableStateOf<List<ClosetItem>>(emptyList()) }
    var pickerUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var pickerLoading by remember { mutableStateOf(false) }

    val currentPiece: OutfitPiece? = selection.getOrNull(currentStep)
    val isPro = RcBilling.isPro() || RcBilling.hasServerPro()

    LaunchedEffect(Unit) {
        CreditsBus.refresh()
        val list = runCatching { Repo.closetItems(20L) }.getOrDefault(emptyList())
        val urls = mutableMapOf<String, String>()
        list.forEach { item ->
            val id = item.id ?: return@forEach
            val direct = item.image_url
            if (!direct.isNullOrBlank()) { urls[id] = direct; return@forEach }
            val path = item.image_path ?: return@forEach
            runCatching { Repo.signedClosetUrl(path) }.getOrNull()?.let { urls[id] = it }
        }
        stripItems = list
        stripUrls = urls
        stripLoaded = true
    }

    /**
     * Build the combine input in the order the user selected. Prefilled slots
     * use their closet URL directly; the rest come from the freshly generated
     * closet items sitting at the top of the closet (newest first).
     * Mirrors iOS `hydrateSavedUrls()`.
     */
    suspend fun hydrateSavedUrls(): List<String> {
        val freshCount = selection.count { prefilled[it] == null }
        val freshUrls = mutableListOf<String>()
        if (freshCount > 0) {
            runCatching {
                val items = Repo.closetItems(freshCount.toLong())
                items.forEach { item ->
                    val direct = item.image_url
                    if (!direct.isNullOrBlank()) { freshUrls.add(direct); return@forEach }
                    val path = item.image_path ?: return@forEach
                    Repo.signedClosetUrl(path)?.let { freshUrls.add(it) }
                }
            }.onFailure { Log.w("OutfitWizard", "hydrate failed", it) }
        }
        val urls = mutableListOf<String>()
        var freshIdx = 0
        for (piece in selection) {
            val pre = prefilled[piece]
            if (pre != null) {
                if (pre.url.isNotBlank()) urls.add(pre.url)
            } else if (freshIdx < freshUrls.size) {
                urls.add(freshUrls[freshIdx])
                freshIdx += 1
            }
        }
        return urls
    }

    /**
     * True multi-piece combine — two parallel `generate-piece` calls, one front
     * view and one side view. Prompt copied verbatim from iOS `combineCall()`;
     * changing the wording here would make the two platforms diverge.
     */
    suspend fun runCombine(refs: List<String>) {
        combining = true
        combineError = null
        frontUrl = null
        sideUrl = null
        combinedOutfitId = null
        try {
            if (refs.size < 2) return
            val pieces = selection.toList()
            val results = coroutineScope {
                listOf("front", "side").map { view ->
                    async(Dispatchers.IO) {
                        val prompt = buildCombinePrompt(pieces, view)
                        Log.i("OutfitWizard", "combine[$view] refs=${refs.size}")
                        val r = runCatching { Repo.generatePiece(prompt, refs) }
                        r.getOrNull()?.image_url?.takeIf { it.isNotBlank() }
                    }
                }.awaitAll()
            }
            val f = results.getOrNull(0)
            val s = results.getOrNull(1)
            frontUrl = f
            sideUrl = s

            if (f == null && s == null) {
                combineError = "no image returned"
                return
            }
            // Charge only if at least one view succeeded — same rule as iOS.
            runCatching { Repo.spendCredits(Supa.STUDIO_COMBINE_STANDALONE_COST, "studio_outfit_combine") }
                .onFailure { Log.w("OutfitWizard", "spend failed", it) }
            CreditsBus.refresh()

            val uid = Repo.userId ?: return
            val label = pieces.joinToString(" + ") { it.displayName }
            var frontId: String? = null
            if (f != null) {
                runCatching {
                    val bytes = Repo.downloadBytes(f)
                    val path = withContext(Dispatchers.IO) { Repo.uploadOutfitPhoto(bytes, ext = "png") }
                    val inserted = Repo.insertOutfit(
                        OutfitInsert(
                            user_id = uid,
                            photo_path = path,
                            score = 0.0,
                            occasion = "Studio",
                            hem_comment = "Studio outfit · $label",
                            kind = "outfit_studio",
                        ),
                    )
                    frontId = inserted.id
                    combinedOutfitId = inserted.id
                }.onFailure { Log.w("OutfitWizard", "front persist failed", it) }
            }
            if (s != null) {
                runCatching {
                    val bytes = Repo.downloadBytes(s)
                    val path = withContext(Dispatchers.IO) { Repo.uploadOutfitPhoto(bytes, ext = "png") }
                    Repo.insertOutfit(
                        OutfitInsert(
                            user_id = uid,
                            photo_path = path,
                            score = 0.0,
                            occasion = "Studio",
                            hem_comment = "Studio outfit (side view) · $label",
                            kind = "outfit_studio_side",
                            linked_piece_id = frontId,
                        ),
                    )
                }.onFailure { Log.w("OutfitWizard", "side persist failed", it) }
            }
        } catch (t: Throwable) {
            Log.e("OutfitWizard", "combine crash", t)
            combineError = t.message ?: "Combine failed"
        } finally {
            combining = false
        }
    }

    suspend fun onAllStepsComplete() {
        val refs = hydrateSavedUrls()
        if (autoCombine && refs.size >= 2) {
            phase = WizardPhase.COMBINING
            runCombine(refs)
        }
        phase = WizardPhase.DONE
    }

    /**
     * Walk `currentStep` past any consecutive prefilled slots so we never mount
     * StudioCreateScreen for a slot that already has an image. Returns the new
     * step index (iOS `skipPrefilled()`).
     */
    fun skipPrefilled(from: Int): Int {
        var i = from
        while (i < selection.size && prefilled[selection[i]] != null) i += 1
        return i
    }

    fun advanceStep() {
        val next = skipPrefilled(currentStep + 1)
        currentStep = next
        stepKey += 1
        if (next >= selection.size) {
            scope.launch { onAllStepsComplete() }
        }
    }

    fun togglePiece(piece: OutfitPiece) {
        val idx = selection.indexOf(piece)
        if (idx >= 0) {
            // Re-tap deselects, and drops any closet prefill for that slot.
            selection.removeAt(idx)
            prefilled.remove(piece)
            return
        }
        if (!isPro && selection.size >= FREE_PIECE_LIMIT) {
            showProUpsell = true
            return
        }
        selection.add(piece)
    }

    /** Given a closet item, figure out which OutfitPiece slot it fits. */
    fun inferPiece(item: ClosetItem): OutfitPiece? {
        val sub = item.subcategory?.lowercase().orEmpty()
        // Exact subtypeHint match first, then fall back on closetCategory.
        val byHint = OutfitPiece.entries.firstOrNull { it.subtypeHint.lowercase() == sub }
        return byHint ?: OutfitPiece.entries.firstOrNull { it.closetCategory == item.category }
    }

    suspend fun loadPickerOptions(piece: OutfitPiece) {
        pickerLoading = true
        val all = runCatching { Repo.closetItems() }.getOrDefault(emptyList())
        val hint = piece.subtypeHint.lowercase()
        val filtered = all.filter { item ->
            if (item.category != piece.closetCategory) return@filter false
            // Accessories MUST match by subcategory since bag/glasses/belt all
            // live under "accessory". Coarser categories are tolerant.
            if (item.category == "accessory") {
                val sub = item.subcategory?.lowercase() ?: return@filter false
                return@filter sub.contains(hint) || hint.contains(sub)
            }
            val sub = item.subcategory?.lowercase()
            if (sub.isNullOrBlank()) return@filter true
            sub.contains(hint) || hint.contains(sub)
        }
        val urls = mutableMapOf<String, String>()
        filtered.forEach { item ->
            val id = item.id ?: return@forEach
            val direct = item.image_url
            if (!direct.isNullOrBlank()) { urls[id] = direct; return@forEach }
            val path = item.image_path ?: return@forEach
            runCatching { Repo.signedClosetUrl(path) }.getOrNull()?.let { urls[id] = it }
        }
        pickerOptions = filtered
        pickerUrls = urls
        pickerLoading = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(HemColors.Paper),
    ) {
        // ---------- Header ----------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = HemSpace.md, vertical = HemSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "✕",
                style = HemType.body.copy(fontSize = 18.sp),
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onClose() }
                    .padding(HemSpace.xs),
            )
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val eyebrowText = when (phase) {
                    WizardPhase.COMPOSE -> "OUTFIT · PIECES"
                    WizardPhase.ITERATING ->
                        if (currentPiece != null) "STEP ${currentStep + 1} OF ${selection.size}"
                        else "OUTFIT · WRAPPING UP"
                    WizardPhase.COMBINING -> "OUTFIT · COMPOSING"
                    WizardPhase.DONE -> "OUTFIT · READY"
                }
                Eyebrow(eyebrowText)
                if (phase == WizardPhase.ITERATING && currentPiece != null) {
                    Text(currentPiece.displayName, style = HemType.serifSection.copy(fontSize = 16.sp))
                }
            }
            Box(Modifier.width(60.dp), contentAlignment = Alignment.CenterEnd) {
                credits?.let {
                    Text("✦ $it", style = HemType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(HemColors.Hairline))

        // ---------- Phase router ----------
        when (phase) {
            WizardPhase.COMPOSE -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(HemSpace.gutter),
            ) {
                SerifDisplay("Which pieces?", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(HemSpace.xs))
                Text(
                    "Tap in the order you want to design them. Each piece walks through the same design questions — one at a time.",
                    style = HemType.bodyMuted,
                )
                Spacer(Modifier.height(HemSpace.lg))

                // FROM YOUR CLOSET strip
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("FROM YOUR CLOSET", modifier = Modifier.weight(1f))
                    Text("Tap to add to the outfit", style = HemType.bodyMuted.copy(fontSize = 11.sp))
                }
                Spacer(Modifier.height(HemSpace.xs))
                if (!stripLoaded) {
                    Row(horizontalArrangement = Arrangement.spacedBy(HemSpace.xs)) {
                        repeat(3) {
                            Box(
                                Modifier
                                    .width(90.dp)
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(HemColors.CardCream),
                            )
                        }
                    }
                } else if (stripItems.isEmpty()) {
                    Text(
                        "Nothing here yet — generate a piece and it'll show up.",
                        style = HemType.bodyMuted.copy(fontSize = 12.sp),
                    )
                } else {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(HemSpace.xs),
                    ) {
                        stripItems.forEach { item ->
                            val piece = inferPiece(item)
                            val picked = piece != null && prefilled[piece]?.itemId == item.id
                            Column(
                                Modifier
                                    .width(90.dp)
                                    .clickable {
                                        val p = piece ?: return@clickable
                                        val id = item.id ?: return@clickable
                                        // Tap again to unselect: drop prefill + slot.
                                        if (picked) {
                                            prefilled.remove(p)
                                            selection.remove(p)
                                            return@clickable
                                        }
                                        if (!selection.contains(p)) {
                                            if (!isPro && selection.size >= FREE_PIECE_LIMIT) {
                                                showProUpsell = true
                                                return@clickable
                                            }
                                            selection.add(p)
                                        }
                                        prefilled[p] = Prefill(url = stripUrls[id].orEmpty(), itemId = id)
                                    },
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(HemColors.CardCream)
                                        .border(
                                            if (picked) 2.dp else 1.dp,
                                            if (picked) HemColors.Ink else HemColors.Hairline,
                                            RoundedCornerShape(10.dp),
                                        ),
                                ) {
                                    val u = item.id?.let { stripUrls[it] }
                                    if (!u.isNullOrBlank()) {
                                        AsyncImage(
                                            model = u,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                    if (picked) {
                                        Text(
                                            "✓",
                                            style = HemType.label.copy(color = Color.White),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .clip(CircleShape)
                                                .background(HemColors.Ink)
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(HemSpace.xxs))
                                Text(
                                    piece?.displayName ?: "Piece",
                                    style = HemType.bodyMuted.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(HemSpace.lg))

                // Grouped 3-column picker
                OUTFIT_PIECE_GROUPS.forEach { group ->
                    Eyebrow(group.name)
                    Spacer(Modifier.height(HemSpace.xs))
                    group.pieces.chunked(3).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(HemSpace.xs),
                        ) {
                            row.forEach { piece ->
                                val selected = selection.contains(piece)
                                val ordinal = selection.indexOf(piece).takeIf { it >= 0 }?.plus(1)
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) HemColors.CardCream else HemColors.Paper)
                                        .border(
                                            if (selected) 1.4.dp else 1.dp,
                                            if (selected) HemColors.Ink else HemColors.Hairline,
                                            RoundedCornerShape(12.dp),
                                        )
                                        .clickable { togglePiece(piece) }
                                        .padding(HemSpace.sm),
                                ) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(piece.emoji, style = HemType.body.copy(fontSize = 26.sp))
                                        Spacer(Modifier.weight(1f))
                                        if (ordinal != null) {
                                            Text(
                                                "$ordinal",
                                                style = HemType.label.copy(fontSize = 11.sp),
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(HemColors.Ink)
                                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(HemSpace.xxs))
                                    Text(
                                        piece.displayName,
                                        style = HemType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                                        maxLines = 1,
                                    )
                                }
                            }
                            // Keep the grid aligned when a row is short.
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(HemSpace.xs))
                    }
                    Spacer(Modifier.height(HemSpace.sm))
                }

                // ORDER list — tap a slot to pull an existing closet piece.
                if (selection.isNotEmpty()) {
                    Eyebrow("ORDER · ${selection.size} ${if (selection.size == 1) "piece" else "pieces"}")
                    Spacer(Modifier.height(HemSpace.xs))
                    selection.forEachIndexed { idx, piece ->
                        val pre = prefilled[piece]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(HemColors.CardCream)
                                .border(1.dp, HemColors.Hairline, RoundedCornerShape(10.dp))
                                .clickable {
                                    pickerForPiece = piece
                                    scope.launch { loadPickerOptions(piece) }
                                }
                                .padding(horizontal = HemSpace.sm, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${idx + 1}.",
                                style = HemType.smallLabel.copy(color = HemColors.Bronze, fontSize = 12.sp),
                                modifier = Modifier.width(20.dp),
                            )
                            Text(
                                piece.displayName,
                                style = HemType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                            )
                            Spacer(Modifier.weight(1f))
                            if (pre != null) {
                                Text(
                                    "USING EXISTING",
                                    style = HemType.label.copy(fontSize = 10.sp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(HemColors.Ink)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            } else {
                                Text(
                                    "GENERATE NEW",
                                    style = HemType.smallLabel.copy(color = HemColors.Muted, fontSize = 10.sp),
                                )
                            }
                            Spacer(Modifier.width(HemSpace.xs))
                            Text("›", style = HemType.body.copy(color = HemColors.Bronze))
                        }
                        Spacer(Modifier.height(HemSpace.xs))
                    }
                    Text(
                        "Tap a slot to pull an existing piece from your closet instead of generating a new one.",
                        style = HemType.bodyMuted.copy(fontSize = 11.sp),
                    )
                    Spacer(Modifier.height(HemSpace.md))
                }

                // Auto-combine toggle
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(HemColors.CardCream)
                        .border(1.dp, HemColors.Hairline, RoundedCornerShape(12.dp))
                        .padding(HemSpace.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Combine on one model at the end",
                            style = HemType.body.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            "Stitch every piece into a single editorial render " +
                                "(+${Supa.STUDIO_COMBINE_STANDALONE_COST} credits).",
                            style = HemType.bodyMuted.copy(fontSize = 12.sp),
                        )
                    }
                    Switch(
                        checked = autoCombine,
                        onCheckedChange = { autoCombine = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = HemColors.Ink),
                    )
                }
                Spacer(Modifier.height(HemSpace.md))

                if (showProUpsell) {
                    Text(
                        "Free plan is capped at $FREE_PIECE_LIMIT pieces per outfit. Go Pro for 3 or more.",
                        style = HemType.bodyMuted.copy(color = HemColors.Bronze, fontSize = 13.sp),
                    )
                    Spacer(Modifier.height(HemSpace.xs))
                    PrimaryButton(label = "Go Pro", onClick = { showProUpsell = false; onOpenPaywall() })
                    Spacer(Modifier.height(HemSpace.md))
                }

                PrimaryButton(
                    label = if (selection.size == 1) "Start" else "Start · ${selection.size} pieces",
                    enabled = selection.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            // Affordability pre-check for the whole run (pieces + combine).
                            val cost = Supa.outfitCost(selection.size)
                            when (val gate = CreditsGate.check(cost)) {
                                is GateResult.Ok -> Unit
                                is GateResult.InsufficientBalance -> { onOpenPaywall(); return@launch }
                                else -> { CreditsGate.explainAndBlock(gate); return@launch }
                            }
                            val start = skipPrefilled(0)
                            currentStep = start
                            stepKey = 0
                            if (start >= selection.size) {
                                // Everything prefilled — jump straight to combine.
                                phase = WizardPhase.COMBINING
                                onAllStepsComplete()
                            } else {
                                phase = WizardPhase.ITERATING
                            }
                        }
                    },
                )
                Spacer(Modifier.height(HemSpace.xl))
            }

            WizardPhase.ITERATING -> {
                val piece = currentPiece
                if (piece != null) {
                    // `stepKey` in the remember key forces a fresh wizard per slot.
                    key(stepKey) {
                        StudioCreateScreen(
                            onBack = onClose,
                            onOpenPaywall = onOpenPaywall,
                            // Completion callback for one slot → advance the wizard.
                            onAddedGoHome = { advanceStep() },
                            presetType = piece.presetType,
                            // Contract with the StudioCreateScreen agent: Step 1's
                            // Type + Subcategory chips lock when both presets are set.
                            presetSubtype = piece.subtypeHint,
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = HemColors.Ink)
                    }
                    LaunchedEffect(Unit) { onAllStepsComplete() }
                }
            }

            WizardPhase.COMBINING -> CombiningOverlay()

            WizardPhase.DONE -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(HemSpace.gutter),
            ) {
                SerifDisplay("Outfit ready.", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(HemSpace.xs))
                Text(
                    "All ${selection.size} pieces are saved to your Studio closet.",
                    style = HemType.bodyMuted,
                )
                Spacer(Modifier.height(HemSpace.md))

                if (combining) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = HemColors.Ink,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(HemSpace.xs))
                        Text("Combining pieces on one mannequin…", style = HemType.bodyMuted)
                    }
                } else if (frontUrl != null || sideUrl != null) {
                    Eyebrow("THE COMPOSED LOOK · SWIPE FOR SIDE")
                    Spacer(Modifier.height(HemSpace.xs))
                    val pagerState = rememberPagerState(initialPage = 0) { 2 }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f),
                    ) { page ->
                        val url = if (page == 0) frontUrl else sideUrl
                        val caption = if (page == 0) "FRONT VIEW" else "SIDE VIEW"
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(HemColors.CardCream)
                                .border(1.dp, HemColors.Hairline, RoundedCornerShape(16.dp)),
                        ) {
                            if (!url.isNullOrBlank()) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = caption,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text(
                                    "Not available",
                                    style = HemType.bodyMuted,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                            Text(
                                caption,
                                style = HemType.label.copy(fontSize = 10.sp),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(HemColors.Ink.copy(alpha = 0.7f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                            if (!url.isNullOrBlank()) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp)
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(HemColors.Paper.copy(alpha = 0.9f))
                                        .clickable { reporting = true },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Outlined.Flag,
                                        contentDescription = "Report this outfit",
                                        tint = HemColors.Ink,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (reporting) {
                        ReportContentSheet(
                            contentKind = ReportKind.OUTFIT,
                            // Falls back to the render URL if the Journal write failed.
                            contentId = combinedOutfitId ?: frontUrl ?: sideUrl.orEmpty(),
                            onDismiss = { reporting = false },
                        )
                    }
                    Spacer(Modifier.height(HemSpace.xs))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        repeat(2) { i ->
                            Box(
                                Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (pagerState.currentPage == i) HemColors.Ink else HemColors.Hairline,
                                    ),
                            )
                        }
                    }
                } else if (combineError != null) {
                    Text(
                        "Combine failed: $combineError",
                        style = HemType.bodyMuted.copy(color = HemColors.Bronze),
                    )
                }

                Spacer(Modifier.height(HemSpace.lg))
                PrimaryButton(label = "Done", onClick = onClose)
                Spacer(Modifier.height(HemSpace.xl))
            }
        }
    }

    // ---------- Per-slot closet picker sheet ----------
    val target = pickerForPiece
    if (target != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { pickerForPiece = null },
            sheetState = sheetState,
            containerColor = HemColors.Paper,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = HemSpace.gutter)
                    .padding(bottom = HemSpace.xl),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Pick from your closet",
                        style = HemType.serifSection,
                        modifier = Modifier.weight(1f),
                    )
                    if (prefilled[target] != null) {
                        Text(
                            "CLEAR",
                            style = HemType.smallLabel.copy(color = HemColors.Bronze),
                            modifier = Modifier.clickable {
                                prefilled.remove(target)
                                pickerForPiece = null
                            },
                        )
                    }
                }
                Spacer(Modifier.height(HemSpace.md))
                when {
                    pickerLoading -> Box(
                        Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = HemColors.Ink) }

                    pickerOptions.isEmpty() -> Column {
                        Text("Nothing in your Studio closet for this slot yet.", style = HemType.bodyMuted)
                        Spacer(Modifier.height(HemSpace.xxs))
                        Text(
                            "Generate one and it'll appear here next time.",
                            style = HemType.bodyMuted.copy(fontSize = 12.sp),
                        )
                    }

                    else -> Column {
                        pickerOptions.chunked(3).forEach { row ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(HemSpace.xs),
                            ) {
                                row.forEach { item ->
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .clickable {
                                                val id = item.id ?: return@clickable
                                                prefilled[target] = Prefill(
                                                    url = pickerUrls[id].orEmpty(),
                                                    itemId = id,
                                                )
                                                pickerForPiece = null
                                            },
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(120.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(HemColors.CardCream),
                                        ) {
                                            val u = item.id?.let { pickerUrls[it] }
                                            if (!u.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = u,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(HemSpace.xxs))
                                        Text(
                                            item.name ?: item.subcategory ?: "Piece",
                                            style = HemType.body.copy(fontSize = 11.sp),
                                            maxLines = 1,
                                        )
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(HemSpace.xs))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-screen editorial waiting state for the combine step — front + side
 * render in parallel takes ~10-20s. Android equivalent of the iOS
 * `WaitingOverlay`; tip copy is taken verbatim from `combiningView`.
 */
@Composable
private fun CombiningOverlay() {
    val tips = listOf(
        "Placing each piece exactly as you designed it…",
        "No extras, no substitutes — just what you chose.",
        "Front view and side view side-by-side.",
        "Editorial catalog lighting, clean cream backdrop.",
    )
    var tipIndex by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2600)
            tipIndex = (tipIndex + 1) % tips.size
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(HemColors.Paper),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(HemSpace.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Eyebrow("COMPOSING THE OUTFIT")
            Spacer(Modifier.height(HemSpace.sm))
            Text("Dressing the mannequin", style = HemType.serifTitle)
            Spacer(Modifier.height(HemSpace.lg))
            CircularProgressIndicator(color = HemColors.Ink)
            Spacer(Modifier.height(HemSpace.lg))
            Text(
                tips[tipIndex],
                style = HemType.bodyMuted,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Combine prompt — VERBATIM port of iOS `combineCall(view:refs:)`. If you edit
 * the wording here, edit `StudioCreateView.swift` in lockstep, otherwise the
 * two platforms start producing different renders from identical inputs.
 */
internal fun buildCombinePrompt(pieces: List<OutfitPiece>, view: String): String {
    val pieceNarrative = pieces.mapIndexed { i, p ->
        "image ${i + 1} is the user's chosen ${p.displayName.lowercase()}"
    }.joinToString("; ")
    val providedList = pieces.joinToString(", ") { it.displayName.lowercase() }
    val viewClause = if (view == "front") {
        "Straight-on front view, mannequin facing the camera."
    } else {
        "90-degree side profile view, mannequin turned to their left, showing the silhouette."
    }
    // Per-piece placement instructions — accessories need explicit handling on
    // a HEADLESS mannequin (no face → no glasses/hats on head).
    val placementLines = pieces.mapNotNull { it.placementInstruction }.map { "- $it" }
    val placementBlock = if (placementLines.isEmpty()) {
        ""
    } else {
        "\nPLACEMENT:\n" + placementLines.joinToString("\n")
    }

    // NOTE: trimMargin (not trimIndent) — the interpolated $placementBlock is
    // itself multi-line and unindented, which would defeat trimIndent.
    return """
        |Studio product photograph. Dress a SINGLE headless matte-white androgynous-form MANNEQUIN with EXACTLY these ${pieces.size} piece(s) and nothing else: $providedList.
        |
        |MANNEQUIN REQUIREMENTS:
        |- Full body visible from shoulders to feet, both arms attached, standing upright, front/side facing per the view instruction below.
        |- The mannequin has NO head, matte-white plastic surface, standard fashion catalog form.
        |
        |HARD RULES:
        |- Use ONLY the items shown in the reference images. Do NOT add, invent, substitute, or infer any additional clothing.
        |- If no top was provided, leave the torso bare (mannequin surface visible). Do NOT invent a t-shirt.
        |- If no bottom was provided, leave the legs bare. Do NOT invent trousers, shorts, or a skirt.
        |- If no shoes were provided, leave the feet bare. Do NOT invent shoes.
        |- Preserve each item's exact colour, cut, buttons, texture, and details as they appear in the reference images. $pieceNarrative.
        |- Layer the provided garments naturally (baselayers under overlayers, bottoms on legs).
        |$placementBlock
        |
        |SCENE:
        |Clean cream studio backdrop. Soft diffused studio lighting. Editorial catalog aesthetic. Sharp fabric, hardware, and stitching detail. $viewClause.
        |
        |ABSOLUTELY FORBIDDEN:
        |No magazine cover. No masthead. No text overlay. No headline. No pull-quote. No price tag. No barcode. No logo. No watermark. No accompanying items beyond the ones listed above.
    """.trimMargin()
}
