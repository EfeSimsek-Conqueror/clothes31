package com.fitrater.app.ui.screens.camera

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitrater.app.data.Supa
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.data.service.CompareService
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.PrimaryButton
import com.fitrater.app.ui.theme.SerifDisplay
import com.fitrater.app.util.CreditsGate
import com.fitrater.app.util.GateResult
import com.fitrater.app.util.ShareCard
import com.fitrater.app.util.ToastBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Flow 3 — A vs B: two photos, Hem picks. */
@Composable
fun VersusScreen(
    onClose: () -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenCamera: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Hoist bytes into VersusBus so nav'ing to Camera + back preserves the OTHER slot.
    var aUri by remember { mutableStateOf<Uri?>(null) }
    var bUri by remember { mutableStateOf<Uri?>(null) }
    var aBytes by remember { mutableStateOf<ByteArray?>(com.fitrater.app.util.VersusBus.aBytes) }
    var bBytes by remember { mutableStateOf<ByteArray?>(com.fitrater.app.util.VersusBus.bBytes) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<com.fitrater.app.data.service.CompareResponse?>(null) }
    var chooseSlot by remember { mutableStateOf<String?>(null) }

    // On return from CameraCaptureScreen, pull the pending bytes into the correct slot.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val (slot, bytes) = com.fitrater.app.util.CameraBus.consumeWithSlot()
        if (bytes != null && slot != null) {
            if (slot == "A") { aBytes = bytes; aUri = null; com.fitrater.app.util.VersusBus.aBytes = bytes }
            else { bBytes = bytes; bUri = null; com.fitrater.app.util.VersusBus.bBytes = bytes }
        }
    }

    // Persist gallery-picked bytes too. When user picks a URI, materialize + cache in bus.
    androidx.compose.runtime.LaunchedEffect(aUri) {
        val u = aUri ?: return@LaunchedEffect
        val raw = runCatching { withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(u)?.buffered()?.use { it.readBytes() }
        } }.getOrNull() ?: return@LaunchedEffect
        aBytes = raw
        com.fitrater.app.util.VersusBus.aBytes = raw
    }
    androidx.compose.runtime.LaunchedEffect(bUri) {
        val u = bUri ?: return@LaunchedEffect
        val raw = runCatching { withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(u)?.buffered()?.use { it.readBytes() }
        } }.getOrNull() ?: return@LaunchedEffect
        bBytes = raw
        com.fitrater.app.util.VersusBus.bBytes = raw
    }

    // Clear the bus when screen is torn down for good (result shown & user closes).
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { /* keep bus across nav; only cleared on onClose */ }
    }

    val pickA = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) { aUri = u; aBytes = null }
    }
    val pickB = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) { bUri = u; bBytes = null }
    }

    Box(Modifier.fillMaxSize().background(HemColors.Paper)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HemSpace.gutter, vertical = HemSpace.lg),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.padding(end = HemSpace.sm)) {
                    Text("HEM PICKS", style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 2.sp))
                    Spacer(Modifier.height(HemSpace.xs))
                    SerifDisplay("A vs B")
                }
                Spacer(Modifier.fillMaxWidth().weight(1f, fill = false))
                Box(Modifier.size(36.dp).clickable {
                    com.fitrater.app.util.VersusBus.clear()
                    onClose()
                }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            Spacer(Modifier.height(HemSpace.lg))

            val r = result
            if (r != null && aBytes != null && bBytes != null) {
                ResultBlock(a = aBytes!!, b = bBytes!!, res = r, onClose = onClose, onShare = {
                    runCatching {
                        val uri = ShareCard.renderVersus(
                            context = context,
                            photoA = aBytes!!,
                            photoB = bBytes!!,
                            scoreA = r.score_a ?: 0.0,
                            scoreB = r.score_b ?: 0.0,
                            winner = r.winner ?: "tie",
                            comment = r.comment ?: "",
                        )
                        ShareCard.launchShare(context, uri, "Share A vs B")
                    }.onFailure {
                        Log.e("Versus", "share failed", it)
                        ToastBus.post("Share failed: ${it.message ?: "error"}")
                    }
                })
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HemSpace.sm)) {
                    DropZone(
                        label = "A",
                        uri = aUri, bytes = aBytes,
                        onTap = { chooseSlot = "A" },
                        modifier = Modifier.weight(1f),
                    )
                    DropZone(
                        label = "B",
                        uri = bUri, bytes = bBytes,
                        onTap = { chooseSlot = "B" },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (error != null) {
                    Spacer(Modifier.height(HemSpace.sm))
                    Text(error!!, style = HemType.bodyMuted.copy(color = Color(0xFFB0743A)))
                }
                Spacer(Modifier.height(HemSpace.lg))
                PrimaryButton(
                    label = if (busy) "Judging…" else "Let Hem call it · ${Supa.VERSUS_COST} credits",
                    enabled = (aBytes != null || aUri != null) && (bBytes != null || bUri != null) && !busy,
                    onClick = {
                        if (busy) return@PrimaryButton
                        busy = true
                        error = null
                        scope.launch {
                            val gate = CreditsGate.check(Supa.VERSUS_COST)
                            if (gate !is GateResult.Ok) {
                                busy = false
                                if (gate is GateResult.InsufficientBalance) {
                                    ToastBus.post("Not enough credits — ${gate.need} needed.")
                                    onOpenPaywall()
                                } else CreditsGate.explainAndBlock(gate)
                                return@launch
                            }
                            runCatching {
                                // Prefer already-loaded bytes (camera capture or gallery-preload);
                                // fall back to reading the URI stream if only that's set.
                                val ab = aBytes ?: aUri?.let {
                                    withContext(Dispatchers.IO) {
                                        context.contentResolver.openInputStream(it)?.buffered()?.use { s -> s.readBytes() }
                                    }
                                } ?: error("Missing photo A")
                                val bb = bBytes ?: bUri?.let {
                                    withContext(Dispatchers.IO) {
                                        context.contentResolver.openInputStream(it)?.buffered()?.use { s -> s.readBytes() }
                                    }
                                } ?: error("Missing photo B")
                                aBytes = ab
                                bBytes = bb
                                // Identical / near-identical shortcut — no need to spend credits.
                                if (ab.contentEquals(bb) || ab.size == bb.size && ab.take(4096) == bb.take(4096)) {
                                    result = com.fitrater.app.data.service.CompareResponse(
                                        winner = "tie",
                                        score_a = 8.0,
                                        score_b = 8.0,
                                        comment = "Same photo, twice. Call it a draw.",
                                        reason_a = "Same fit as B.",
                                        reason_b = "Same fit as A.",
                                    )
                                    busy = false
                                    return@runCatching
                                }
                                val aPath = Repo.uploadOutfitPhoto(ab)
                                val bPath = Repo.uploadOutfitPhoto(bb)
                                val aSigned = Repo.signedOutfitUrl(aPath) ?: error("Sign failed")
                                val bSigned = Repo.signedOutfitUrl(bPath) ?: error("Sign failed")
                                val res = CompareService.compare(aSigned, bSigned, "everyday")
                                if (res.error != null) error("Hem: ${res.error} — ${res.detail ?: ""}")
                                runCatching { Repo.spendCredits(Supa.VERSUS_COST, "versus") }
                                result = res
                            }.onFailure {
                                Log.e("Versus", "compare failed", it)
                                error = it.message ?: "Something went wrong"
                                ToastBus.post("A vs B failed: ${it.message ?: "error"}")
                            }
                            busy = false
                        }
                    },
                )
                Spacer(Modifier.height(HemSpace.xl))
            }
        }

        // Source picker sheet (camera vs gallery) for the tapped slot.
        val slot = chooseSlot
        if (slot != null) {
            SourcePickerSheet(
                slot = slot,
                onDismiss = { chooseSlot = null },
                onCamera = {
                    com.fitrater.app.util.CameraBus.slot = slot
                    chooseSlot = null
                    onOpenCamera()
                },
                onGallery = {
                    chooseSlot = null
                    if (slot == "A") pickA.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    else pickB.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SourcePickerSheet(
    slot: String,
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HemColors.Paper,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
        ) {
            Text("SOURCE FOR $slot", style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 2.sp))
            Spacer(Modifier.height(HemSpace.sm))
            SerifDisplay("How do you want to add it?")
            Spacer(Modifier.height(HemSpace.lg))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCamera)
                    .padding(vertical = HemSpace.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("📸 Take photo", style = HemType.body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Text("→", style = HemType.body.copy(color = HemColors.Muted))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(HemColors.Hairline))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onGallery)
                    .padding(vertical = HemSpace.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🖼 From gallery", style = HemType.body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Text("→", style = HemType.body.copy(color = HemColors.Muted))
            }
            Spacer(Modifier.height(HemSpace.xl))
        }
    }
}

@Composable
private fun DropZone(
    label: String,
    uri: Uri?,
    bytes: ByteArray?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(18.dp))
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bytes != null -> AsyncImage(model = bytes, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            uri != null -> AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = HemType.serifSection.copy(fontSize = 34.sp))
                Spacer(Modifier.height(HemSpace.xs))
                Text("tap to add", style = HemType.bodyMuted.copy(fontSize = 12.sp))
            }
        }
    }
}

@Composable
private fun ResultBlock(
    a: ByteArray,
    b: ByteArray,
    res: com.fitrater.app.data.service.CompareResponse,
    onClose: () -> Unit,
    onShare: () -> Unit,
) {
    val winner = res.winner ?: "tie"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HemSpace.sm)) {
        WinnerCard(bytes = a, label = "A", score = res.score_a, reason = res.reason_a, isWinner = winner == "A", modifier = Modifier.weight(1f))
        WinnerCard(bytes = b, label = "B", score = res.score_b, reason = res.reason_b, isWinner = winner == "B", modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(HemSpace.lg))
    val q = res.comment
    if (!q.isNullOrBlank()) {
        Text(
            "“$q”",
            style = HemType.serifQuote.copy(fontStyle = FontStyle.Italic, fontSize = 22.sp, lineHeight = 30.sp),
        )
        Spacer(Modifier.height(HemSpace.xs))
        Text("— Hem", style = HemType.bodyMuted.copy(fontStyle = FontStyle.Italic))
    }
    Spacer(Modifier.height(HemSpace.xl))
    PrimaryButton(label = "Share the verdict", onClick = onShare)
    Spacer(Modifier.height(HemSpace.sm))
    OutlinedRow(label = "Done", onClick = onClose)
    Spacer(Modifier.height(HemSpace.xl))
}

@Composable
private fun WinnerCard(
    bytes: ByteArray,
    label: String,
    score: Double?,
    reason: String?,
    isWinner: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(HemColors.CardCream)
                .border(
                    if (isWinner) 2.dp else 1.dp,
                    if (isWinner) HemColors.Bronze else HemColors.Hairline,
                    RoundedCornerShape(18.dp),
                ),
        ) {
            AsyncImage(model = bytes, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (isWinner) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(HemColors.Bronze)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text("WINNER", style = HemType.smallLabel.copy(color = Color.White, letterSpacing = 2.sp))
                }
            }
        }
        Spacer(Modifier.height(HemSpace.xs))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = HemType.smallLabel.copy(color = HemColors.Muted, letterSpacing = 2.sp))
            Spacer(Modifier.fillMaxWidth().weight(1f, fill = false))
            Text(
                score?.let { String.format("%.1f", it) } ?: "–",
                style = HemType.serifSection.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium),
            )
        }
        if (!reason.isNullOrBlank()) {
            Text(reason, style = HemType.bodyMuted.copy(fontSize = 12.sp))
        }
    }
}
