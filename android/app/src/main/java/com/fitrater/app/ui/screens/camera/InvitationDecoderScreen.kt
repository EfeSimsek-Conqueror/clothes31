package com.fitrater.app.ui.screens.camera

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitrater.app.data.Supa
import com.fitrater.app.data.model.ClosetItem
import com.fitrater.app.data.model.InvitationDecoded
import com.fitrater.app.data.model.OutfitCombo
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.SerifDisplay
import com.fitrater.app.util.CreditsGate
import com.fitrater.app.util.GateResult
import com.fitrater.app.util.ToastBus
import kotlinx.coroutines.launch

private enum class InviteStage { CAPTURE, DECODED, SUGGESTED }

/** Port of iOS `InvitationDecoderView.swift`. */
@Composable
fun InvitationDecoderScreen(onClose: () -> Unit, onOpenPaywall: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf(InviteStage.CAPTURE) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var decoded by remember { mutableStateOf<InvitationDecoded?>(null) }
    var combos by remember { mutableStateOf<List<OutfitCombo>>(emptyList()) }
    var closet by remember { mutableStateOf<List<ClosetItem>>(emptyList()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { scope.launch { bytes = readBytes(context, it) } }
    }

    LaunchedEffect(Unit) {
        closet = runCatching { Repo.closetItems() }.getOrDefault(emptyList())
    }

    fun reset() {
        bytes = null; decoded = null; combos = emptyList(); stage = InviteStage.CAPTURE; errorMsg = null
    }

    fun decode() {
        val b = bytes ?: return
        scope.launch {
            busy = true; errorMsg = null
            try {
                val gate = CreditsGate.check(Supa.INVITATION_DECODE_COST)
                if (gate !is GateResult.Ok) {
                    CreditsGate.explainAndBlock(gate)
                    if (gate is GateResult.InsufficientBalance) onOpenPaywall()
                    busy = false; return@launch
                }
                val path = Repo.uploadOutfitPhoto(b, "jpg")
                val signed = Repo.signedOutfitUrl(path) ?: throw IllegalStateException("Could not sign photo")
                val resp = Repo.decodeInvitation(signed)
                if (!resp.error.isNullOrBlank()) throw IllegalStateException(resp.error)
                runCatching { Repo.spendCredits(Supa.INVITATION_DECODE_COST, "invitation_decode") }
                decoded = resp.decoded
                stage = InviteStage.DECODED
            } catch (e: Throwable) {
                errorMsg = e.message ?: "Unknown error"
                ToastBus.post("Decode failed: ${e.message}")
            } finally { busy = false }
        }
    }

    fun suggest() {
        val d = decoded ?: return
        scope.launch {
            busy = true; errorMsg = null
            try {
                val bp = runCatching { Repo.loadBodyProfile() }.getOrNull()
                val styleTags = runCatching { Repo.currentProfile()?.style_tags }.getOrNull()
                val resp = Repo.suggestOutfits(
                    dressCode = d.dress_code ?: "smart_casual",
                    eventType = d.event_type ?: "other",
                    notes = d.notes,
                    closet = closet,
                    bodyProfile = bp,
                    styleTags = styleTags,
                )
                if (!resp.error.isNullOrBlank()) throw IllegalStateException(resp.error)
                combos = resp.combos ?: emptyList()
                runCatching { Repo.saveInvitationRead(imagePath = null, decoded = d, combos = combos) }
                stage = InviteStage.SUGGESTED
            } catch (e: Throwable) {
                errorMsg = e.message ?: "Unknown error"
                ToastBus.post("Suggest failed: ${e.message}")
            } finally { busy = false }
        }
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
                    Eyebrow("OCCASION"); Spacer(Modifier.height(6.dp))
                    SerifDisplay("Decode the invite.")
                }
                Box(Modifier.size(36.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = HemColors.Ink)
                }
            }
            Spacer(Modifier.height(18.dp))

            when (stage) {
                InviteStage.CAPTURE -> CaptureBlock(
                    bytes = bytes, busy = busy, errorMsg = errorMsg,
                    onPick = { picker.launch("image/*") },
                    onDecode = { decode() },
                )
                InviteStage.DECODED -> DecodedBlock(
                    bytes = bytes, decoded = decoded, busy = busy,
                    canSuggest = decoded != null && closet.isNotEmpty(),
                    errorMsg = errorMsg,
                    onSuggest = { suggest() }, onReset = { reset() },
                    closetEmpty = closet.isEmpty(),
                )
                InviteStage.SUGGESTED -> SuggestedBlock(
                    combos = combos, closet = closet,
                    onReset = { reset() }, onDone = onClose,
                )
            }
            Spacer(Modifier.height(30.dp))
        }
        if (busy) InviteBusyOverlay(capture = stage == InviteStage.CAPTURE)
    }
}

// ============================================================================
// Stages
// ============================================================================

@Composable
private fun CaptureBlock(
    bytes: ByteArray?, busy: Boolean, errorMsg: String?,
    onPick: () -> Unit, onDecode: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Snap the invitation, sign, or venue photo. Hem parses the dress code and pulls combinations from your Studio closet.",
            style = HemType.body.copy(fontSize = 15.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(20.dp))
                .background(HemColors.CardCream)
                .border(1.dp, HemColors.Hairline, RoundedCornerShape(20.dp))
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            if (bytes != null) {
                val bmp = remember(bytes) { runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() }
                bmp?.let {
                    androidx.compose.foundation.Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Text("+ Add invitation photo", style = HemType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Ink))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineBtn("From gallery", modifier = Modifier.weight(1f), onClick = onPick)
        }
        errorMsg?.let { Text(it, style = HemType.body.copy(fontSize = 13.sp, color = HemColors.Bronze)) }
        PrimaryBtn(
            text = if (busy) "Decoding…" else "Decode · ${Supa.INVITATION_DECODE_COST} credits",
            enabled = bytes != null && !busy,
            onClick = onDecode,
        )
    }
}

@Composable
private fun DecodedBlock(
    bytes: ByteArray?, decoded: InvitationDecoded?, busy: Boolean,
    canSuggest: Boolean, errorMsg: String?, closetEmpty: Boolean,
    onSuggest: () -> Unit, onReset: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (bytes != null) {
            val bmp = remember(bytes) { runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() }
            bmp?.let {
                Box(
                    Modifier
                        .widthIn(max = 200.dp)
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(HemColors.CardCream),
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Eyebrow("DECODED")
        decoded?.let { d ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HemColors.CardCream)
                    .border(1.dp, HemColors.Hairline, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                d.event_type?.let { LabeledRow("EVENT", pretty(it)) }
                d.dress_code?.let { LabeledRow("DRESS", pretty(it), highlight = true) }
                d.venue?.let { LabeledRow("VENUE", it) }
                d.date_text?.let { LabeledRow("WHEN", it) }
                d.notes?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = HemType.body.copy(fontSize = 14.sp, fontStyle = FontStyle.Italic, color = HemColors.Ink.copy(alpha = 0.8f)))
                }
            }
        }
        errorMsg?.let { Text(it, style = HemType.body.copy(fontSize = 13.sp, color = HemColors.Bronze)) }
        PrimaryBtn(
            text = if (busy) "Building combos…" else "See combinations",
            enabled = !busy && canSuggest,
            onClick = onSuggest,
        )
        if (closetEmpty) {
            Text(
                "Your Studio closet is empty — add a few pieces first so Hem has something to work with.",
                style = HemType.body.copy(fontSize = 13.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted),
            )
        }
        Text(
            "Scan a different invite",
            style = HemType.body.copy(fontSize = 14.sp, color = HemColors.Muted),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onReset),
        )
    }
}

@Composable
private fun SuggestedBlock(
    combos: List<OutfitCombo>, closet: List<ClosetItem>,
    onReset: () -> Unit, onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow("YOUR PLAYS")
        if (combos.isEmpty()) {
            Text(
                "No combinations came back — try scanning again or expand your closet.",
                style = HemType.body.copy(fontSize = 14.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted),
            )
        }
        combos.forEach { c -> ComboCard(c, closet) }
        Text(
            "Scan another",
            style = HemType.body.copy(fontSize = 14.sp, color = HemColors.Muted),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable(onClick = onReset),
        )
        OutlineBtn("Done", modifier = Modifier.fillMaxWidth(), onClick = onDone)
    }
}

// ============================================================================
// Combo card
// ============================================================================

@Composable
private fun ComboCard(combo: OutfitCombo, closet: List<ClosetItem>) {
    val pieces = (combo.piece_ids ?: emptyList()).mapNotNull { pid -> closet.firstOrNull { it.id == pid } }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "MATCH ${combo.score ?: 0}%",
                style = HemType.smallLabel.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Bronze, letterSpacing = 1.5.sp),
                modifier = Modifier.weight(1f),
            )
            val gap = combo.gap
            if (!gap.isNullOrBlank()) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(HemColors.Bronze)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("GAP", style = HemType.smallLabel.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = HemColors.OnInk, letterSpacing = 1.5.sp))
                }
            }
        }
        if (pieces.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pieces.forEach { PieceThumb(it) }
            }
        }
        combo.rationale?.let { Text(it, style = HemType.body.copy(fontSize = 14.sp, color = HemColors.Ink)) }
        val gap = combo.gap
        if (!gap.isNullOrBlank()) {
            Text("Missing: $gap", style = HemType.body.copy(fontSize = 13.sp, fontStyle = FontStyle.Italic, color = HemColors.Bronze))
        }
    }
}

@Composable
private fun PieceThumb(piece: ClosetItem) {
    var url by remember(piece.id) { mutableStateOf(piece.image_url) }
    LaunchedEffect(piece.id) {
        if (url.isNullOrBlank()) {
            piece.image_path?.let { p ->
                url = runCatching { Repo.signedClosetUrl(p) }.getOrNull()
            }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = 84.dp, height = 100.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(HemColors.Paper)
                .border(1.dp, HemColors.Hairline, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val u = url
            if (u != null) {
                AsyncImage(model = u, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text("…", style = HemType.body.copy(fontSize = 12.sp, color = HemColors.Muted))
            }
        }
        Text(
            ((piece.name ?: piece.category ?: "").take(14)),
            style = HemType.body.copy(fontSize = 11.sp, color = HemColors.Muted),
            maxLines = 1,
        )
    }
}

// ============================================================================
// Shared bits
// ============================================================================

@Composable
private fun LabeledRow(label: String, value: String, highlight: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = HemType.smallLabel.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Bronze, letterSpacing = 1.5.sp),
            modifier = Modifier.width(54.dp),
        )
        Text(
            value,
            style = HemType.body.copy(
                fontSize = 14.sp,
                fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
                color = if (highlight) HemColors.Bronze else HemColors.Ink,
            ),
        )
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

@Composable
private fun OutlineBtn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, HemColors.Ink.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HemType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = HemColors.Ink))
    }
}

@Composable
private fun InviteBusyOverlay(capture: Boolean) {
    Box(Modifier.fillMaxSize().background(HemColors.Paper.copy(alpha = 0.94f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow(if (capture) "READING" else "MATCHING")
            Text(
                if (capture) "Decoding the invite" else "Pulling from your closet",
                style = HemType.serifDisplay.copy(fontSize = 22.sp, color = HemColors.Ink),
            )
            listOf("Reading dress code…", "Weighing your palette…", "Ranking combinations…", "Flagging any closet gaps.")
                .forEach { Text(it, style = HemType.body.copy(fontSize = 13.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted)) }
        }
    }
}

private fun pretty(raw: String): String =
    raw.replace('_', ' ').split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

private suspend fun readBytes(context: Context, uri: Uri): ByteArray? =
    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
