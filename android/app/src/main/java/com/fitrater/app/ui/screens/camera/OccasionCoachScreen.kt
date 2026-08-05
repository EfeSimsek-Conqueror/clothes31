package com.fitrater.app.ui.screens.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitrater.app.data.Supa
import com.fitrater.app.data.model.CreditTxInsert
import com.fitrater.app.data.model.OutfitInsert
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.Hairline
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.SerifDisplay
import com.fitrater.app.util.CreditsGate
import com.fitrater.app.util.GateResult
import com.fitrater.app.util.ToastBus
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

// ============================================================================
// Models
// ============================================================================

@Serializable
data class OccasionPiece(
    val name: String = "",
    val category: String = "",
    val color: String = "",
    val matched_closet_id: String? = null,
    val generate_prompt: String? = null,
)

@Serializable
data class OccasionCombo(
    val title: String = "",
    val rationale: String = "",
    val pieces: List<OccasionPiece> = emptyList(),
)

@Serializable
private data class OccasionResponse(
    val combos: List<OccasionCombo>? = null,
    val error: String? = null,
    val detail: String? = null,
)

// ============================================================================
// Service
// ============================================================================

object OccasionCoachService {
    private val client get() = Supa.client
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun plan(prompt: String): List<OccasionCombo> {
        val closet = runCatching { Repo.closetItems(limit = 60) }.getOrDefault(emptyList())
        val profile = runCatching { Repo.currentProfile() }.getOrNull()
        val body = buildJsonObject {
            put("prompt", prompt)
            putJsonArray("closet_items") {
                closet.forEach { c ->
                    val id = c.id ?: return@forEach
                    add(buildJsonObject {
                        put("id", id)
                        c.name?.let { put("name", it) }
                        c.category?.let { put("category", it) }
                        c.subcategory?.let { put("subcategory", it) }
                        c.color?.let { put("color", it) }
                        c.color_hex?.let { put("color_hex", it) }
                    })
                }
            }
            putJsonObject("style_profile") {
                profile?.gender?.let { put("gender", it) }
                profile?.style_tags?.let { tags ->
                    putJsonArray("style_tags") { tags.forEach { add(it) } }
                }
            }
        }
        val resp: HttpResponse = client.functions.invoke(function = "occasion-coach", body = body)
        val text: String = resp.body()
        val parsed = runCatching { json.decodeFromString(OccasionResponse.serializer(), text) }
            .getOrElse { OccasionResponse(error = text.take(200)) }
        if (parsed.error != null) throw IllegalStateException("Hem: ${parsed.error} — ${parsed.detail ?: ""}")
        return parsed.combos ?: emptyList()
    }

    suspend fun weeklyFreeAvailable(): Boolean {
        val uid = Repo.userId ?: return false
        val startIso = isoStartOfWeek()
        val rows = runCatching {
            client.postgrest["credit_transactions"].select {
                filter {
                    eq("user_id", uid)
                    eq("kind", "occasion_coach_free")
                    gte("created_at", startIso)
                }
                limit(1)
            }.data
        }.getOrNull() ?: return false
        // If the JSON body is `[]` treat as empty
        return rows.trim() == "[]" || rows.isBlank()
    }

    suspend fun recordWeeklyFreeUse() {
        val uid = Repo.userId ?: return
        val current = runCatching { Repo.credits() }.getOrDefault(0)
        runCatching {
            client.postgrest["credit_transactions"].insert(
                CreditTxInsert(user_id = uid, amount = 0, kind = "occasion_coach_free", balance_after = current)
            )
        }
    }

    private fun isoStartOfWeek(): String {
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val start = monday.atStartOfDay(ZoneOffset.UTC)
        return start.format(DateTimeFormatter.ISO_INSTANT)
    }
}

// ============================================================================
// Screen
// ============================================================================

@Composable
fun OccasionCoachScreen(onClose: () -> Unit, onOpenPaywall: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val clipboard: ClipboardManager = LocalClipboardManager.current

    var prompt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var combos by remember { mutableStateOf<List<OccasionCombo>>(emptyList()) }
    var freeAvailable by remember { mutableStateOf(false) }
    var checkedFree by remember { mutableStateOf(false) }

    val presets = listOf("Wedding", "Interview", "Date", "Casual", "Party", "Trip", "Weekend")

    LaunchedEffect(Unit) {
        freeAvailable = runCatching { OccasionCoachService.weeklyFreeAvailable() }.getOrDefault(false)
        checkedFree = true
    }

    fun run() {
        if (busy) return
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            busy = true; errorMsg = null
            val useFree = freeAvailable
            if (!useFree) {
                val gate = CreditsGate.check(Supa.OCCASION_COST)
                if (gate !is GateResult.Ok) {
                    busy = false
                    CreditsGate.explainAndBlock(gate)
                    if (gate is GateResult.InsufficientBalance) onOpenPaywall()
                    return@launch
                }
            }
            try {
                val result = OccasionCoachService.plan(trimmed)
                if (result.isEmpty()) {
                    errorMsg = "Hem couldn't compose 3 combos — try rewording."
                    busy = false; return@launch
                }
                if (useFree) {
                    OccasionCoachService.recordWeeklyFreeUse()
                    freeAvailable = false
                } else {
                    runCatching { Repo.spendCredits(Supa.OCCASION_COST, "occasion_coach") }
                }
                combos = result
            } catch (e: Throwable) {
                errorMsg = e.message ?: "Unknown error"
                ToastBus.post("Occasion Coach failed: ${e.message}")
            } finally { busy = false }
        }
    }

    suspend fun saveCombo(c: OccasionCombo) {
        val uid = Repo.userId ?: run { ToastBus.post("Not signed in."); return }
        var photoPath = ""
        for (p in c.pieces) {
            val cid = p.matched_closet_id ?: continue
            val item = runCatching { Repo.closetItemById(cid) }.getOrNull()
            val path = item?.image_path
            if (!path.isNullOrBlank()) { photoPath = path; break }
        }
        val hem = c.rationale.ifBlank { c.title }
        runCatching {
            Repo.insertOutfit(
                OutfitInsert(
                    user_id = uid,
                    photo_path = photoPath,
                    score = 0.0,
                    occasion = prompt.trim(),
                    hem_comment = hem,
                    verdict = c.title,
                    kind = "occasion_plan",
                )
            )
        }
        ToastBus.post("Saved to journal.")
    }

    val buttonTitle = when {
        !checkedFree -> "Get 3 combos · ${Supa.OCCASION_COST} credits"
        freeAvailable -> "Get 3 combos · free this week"
        else -> "Get 3 combos · ${Supa.OCCASION_COST} credits"
    }

    Box(Modifier.fillMaxSize().background(HemColors.Paper)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("OCCASION COACH")
                    Spacer(Modifier.height(6.dp))
                    Text("What's the occasion?", style = HemType.serifDisplay.copy(fontSize = 28.sp, color = HemColors.Ink))
                }
                Box(Modifier.size(36.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = HemColors.Ink)
                }
            }
            Spacer(Modifier.height(20.dp))
            if (combos.isEmpty()) {
                // Composer
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(HemColors.CardCream)
                        .border(1.dp, HemColors.Hairline, RoundedCornerShape(8.dp))
                        .padding(14.dp),
                ) {
                    if (prompt.isEmpty()) {
                        Text(
                            "Wedding · outdoor · summer, boho…",
                            style = HemType.body.copy(fontSize = 16.sp, color = HemColors.Muted),
                        )
                    }
                    BasicTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        textStyle = HemType.body.copy(fontSize = 16.sp, color = HemColors.Ink),
                        cursorBrush = SolidColor(HemColors.Bronze),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { p ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(HemColors.CardCream)
                                .border(1.dp, HemColors.Hairline, RoundedCornerShape(999.dp))
                                .clickable { prompt = p }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(p.uppercase(), style = HemType.smallLabel.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Ink, letterSpacing = 1.4.sp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                errorMsg?.let { Text(it, style = HemType.body.copy(fontSize = 13.sp, color = HemColors.Bronze)); Spacer(Modifier.height(8.dp)) }
                PrimaryBtn(text = buttonTitle, enabled = prompt.trim().isNotEmpty() && !busy, onClick = { run() })
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    combos.forEach { c ->
                        ComboCardV2(
                            combo = c,
                            onCopyPrompt = { text ->
                                clipboard.setText(AnnotatedString(text))
                                ToastBus.post("Prompt copied — paste in Studio.")
                            },
                            onSave = { scope.launch { saveCombo(c) } },
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable { combos = emptyList(); errorMsg = null }
                            .border(1.dp, HemColors.Ink, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "TRY ANOTHER OCCASION",
                            style = HemType.smallLabel.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Ink, letterSpacing = 1.6.sp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
        if (busy) OccasionBusyOverlay()
    }
}

@Composable
private fun OccasionBusyOverlay() {
    Box(
        Modifier.fillMaxSize().background(HemColors.Paper.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow("STYLING")
            Text("Hem is writing", style = HemType.serifDisplay.copy(fontSize = 22.sp, color = HemColors.Ink))
            listOf(
                "Reading your closet…",
                "Weighting your style tags…",
                "Composing three plays…",
                "Usually 15-30 seconds.",
            ).forEach {
                Text(it, style = HemType.body.copy(fontSize = 13.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted))
            }
        }
    }
}

// ============================================================================
// Combo card
// ============================================================================

@Composable
private fun ComboCardV2(combo: OccasionCombo, onCopyPrompt: (String) -> Unit, onSave: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Bronze.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(combo.title, style = HemType.serifDisplay.copy(fontSize = 18.sp, color = HemColors.Ink))
        if (combo.rationale.isNotBlank()) {
            Text(combo.rationale, style = HemType.body.copy(fontSize = 14.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted))
        }
        Hairline()
        Column {
            combo.pieces.forEachIndexed { idx, p ->
                if (idx > 0) Hairline()
                PieceRow(p, onCopyPrompt)
            }
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, HemColors.Bronze, RoundedCornerShape(999.dp))
                .clickable(onClick = onSave)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                "SAVE COMBO",
                style = HemType.smallLabel.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Bronze, letterSpacing = 1.5.sp),
            )
        }
    }
}

@Composable
private fun PieceRow(p: OccasionPiece, onCopyPrompt: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(colorFromName(p.color) ?: HemColors.Muted.copy(alpha = 0.4f))
                .border(1.dp, HemColors.Hairline, CircleShape),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                p.category.uppercase(),
                style = HemType.smallLabel.copy(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Bronze, letterSpacing = 1.4.sp),
            )
            Text(p.name, style = HemType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = HemColors.Ink))
        }
        val cid = p.matched_closet_id
        val gp = p.generate_prompt
        if (!cid.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Check, contentDescription = null, tint = HemColors.Success, modifier = Modifier.size(11.dp))
                Text("IN CLOSET", style = HemType.smallLabel.copy(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Success, letterSpacing = 1.4.sp))
            }
        } else if (!gp.isNullOrBlank()) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, HemColors.Bronze, RoundedCornerShape(999.dp))
                    .clickable { onCopyPrompt(gp) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text(
                    "GENERATE → ${Supa.GENERATE_COST}C",
                    style = HemType.smallLabel.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Bronze, letterSpacing = 1.4.sp),
                )
            }
        }
    }
}

@Composable
private fun PrimaryBtn(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) HemColors.Ink else HemColors.Muted)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HemType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HemColors.OnInk))
    }
}

// ============================================================================
// Colors
// ============================================================================

private fun colorFromName(name: String): Color? {
    val n = name.lowercase().trim()
    if (n.startsWith("#")) return colorFromHex(n)
    return when (n) {
        "black", "ink" -> HemColors.Ink
        "white", "cream", "ivory", "bone" -> Color(0xFFF5F0E3)
        "beige", "sand", "tan", "khaki" -> Color(0xFFD6C4A3)
        "brown", "chocolate", "cocoa" -> Color(0xFF5C3B21)
        "bronze", "camel" -> HemColors.Bronze
        "gray", "grey", "charcoal", "slate" -> Color(0xFF5C5C5C)
        "navy", "midnight" -> Color(0xFF192652)
        "blue", "powder-blue", "sky" -> Color(0xFF5985BF)
        "green", "olive", "sage", "forest" -> Color(0xFF52734D)
        "red", "wine", "rust", "terracotta" -> Color(0xFFB33F33)
        "pink", "peach", "blush" -> Color(0xFFF2B8AE)
        "yellow", "butter", "mustard" -> Color(0xFFE5BF4D)
        "purple", "lavender", "violet" -> Color(0xFF8C66AD)
        "gold" -> HemColors.GoldStart
        "silver" -> Color(0xFFBFBFC7)
        else -> null
    }
}

private fun colorFromHex(hex: String): Color? {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6) return null
    val v = clean.toLongOrNull(16) ?: return null
    val r = ((v shr 16) and 0xff).toInt() / 255f
    val g = ((v shr 8) and 0xff).toInt() / 255f
    val b = (v and 0xff).toInt() / 255f
    return Color(r, g, b, 1f)
}
