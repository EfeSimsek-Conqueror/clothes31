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
import com.fitrater.app.data.model.ClosetItemInsert
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.data.service.CompareService
import com.fitrater.app.data.service.DecodeResponse
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.PrimaryButton
import com.fitrater.app.ui.theme.SerifDisplay
import com.fitrater.app.util.CreditsGate
import com.fitrater.app.util.GateResult
import com.fitrater.app.util.ToastBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Flow 5 — Decode style: reference photo → pieces + palette + signature. */
@Composable
fun DecodeScreen(
    onClose: () -> Unit,
    onOpenPaywall: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<DecodeResponse?>(null) }
    var saving by remember { mutableStateOf(false) }
    var savedOk by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) { pickedUri = u; result = null; savedOk = false }
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
                    Text("READ THE ROOM", style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 2.sp))
                    Spacer(Modifier.height(HemSpace.xs))
                    SerifDisplay("Decode style")
                }
                Spacer(Modifier.fillMaxWidth().weight(1f, fill = false))
                Box(Modifier.size(36.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            Spacer(Modifier.height(HemSpace.lg))

            // Reference tile
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(HemColors.CardCream)
                    .border(1.dp, HemColors.Hairline, RoundedCornerShape(18.dp))
                    .clickable {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center,
            ) {
                val u = pickedUri
                if (u != null) AsyncImage(model = u, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text("Tap to pick a reference photo", style = HemType.bodyMuted)
            }

            val res = result
            if (res != null) {
                Spacer(Modifier.height(HemSpace.lg))
                Text("STYLE SIGNATURE", style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 2.sp))
                Spacer(Modifier.height(HemSpace.xs))
                Text(
                    res.style_signature.ifBlank { "—" },
                    style = HemType.serifQuote.copy(fontStyle = FontStyle.Italic, fontSize = 22.sp, lineHeight = 30.sp),
                )

                Spacer(Modifier.height(HemSpace.lg))
                Text("PIECES", style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 2.sp))
                Spacer(Modifier.height(HemSpace.xs))
                res.pieces.forEachIndexed { idx, p ->
                    if (idx > 0) Hairline()
                    Column(Modifier.fillMaxWidth().padding(vertical = HemSpace.sm)) {
                        Text(
                            "${p.silhouette.ifBlank { "" }} ${p.type}".trim().replaceFirstChar { it.uppercase() },
                            style = HemType.body.copy(fontWeight = FontWeight.SemiBold),
                        )
                        if (p.note.isNotBlank()) {
                            Text(p.note, style = HemType.bodyMuted.copy(fontSize = 13.sp))
                        }
                        val meta = listOfNotNull(
                            p.fabric.takeIf { it.isNotBlank() },
                            p.colors.takeIf { it.isNotEmpty() }?.joinToString(", "),
                        ).joinToString(" · ")
                        if (meta.isNotBlank()) Text(meta, style = HemType.bodyMuted.copy(fontSize = 12.sp))
                    }
                }

                if (res.palette_hex.isNotEmpty()) {
                    Spacer(Modifier.height(HemSpace.lg))
                    Text("PALETTE", style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 2.sp))
                    Spacer(Modifier.height(HemSpace.sm))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HemSpace.xs)) {
                        res.palette_hex.forEach { hex ->
                            val col = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(HemColors.Muted)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(col)
                                    .border(1.dp, HemColors.Hairline, RoundedCornerShape(8.dp)),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(HemSpace.xl))
                PrimaryButton(
                    label = when {
                        savedOk -> "Saved to closet"
                        saving -> "Saving…"
                        else -> "Save pieces to closet"
                    },
                    enabled = !saving && !savedOk && res.pieces.isNotEmpty(),
                    onClick = {
                        saving = true
                        scope.launch {
                            val uid = Repo.userId
                            if (uid == null) {
                                saving = false
                                ToastBus.post("Not signed in.")
                                return@launch
                            }
                            var okCount = 0
                            res.pieces.forEach { p ->
                                val name = "${p.silhouette} ${p.type}"
                                    .trim()
                                    .ifBlank { p.type }
                                    .replaceFirstChar { it.uppercase() }
                                val category = when (p.type.lowercase()) {
                                    "trousers", "pants", "jeans", "shorts", "skirt" -> "bottom"
                                    "boots", "shoes", "sneakers", "sandals", "heels" -> "footwear"
                                    "bag", "belt", "hat", "cap", "scarf", "sunglasses", "watch", "necklace" -> "accessory"
                                    "blazer", "jacket", "coat", "cardigan" -> "outerwear"
                                    else -> "top"
                                }
                                val hex = res.palette_hex.firstOrNull()?.take(7)
                                runCatching {
                                    Repo.insertClosetItem(
                                        ClosetItemInsert(
                                            user_id = uid,
                                            name = name,
                                            category = category,
                                            subcategory = p.type.lowercase().ifBlank { null },
                                            color_hex = hex,
                                            source = "decoded",
                                        ),
                                    )
                                    okCount += 1
                                }.onFailure { Log.w("Decode", "insert failed", it) }
                            }
                            saving = false
                            if (okCount > 0) {
                                savedOk = true
                                ToastBus.post("Added $okCount piece${if (okCount > 1) "s" else ""} to your closet.")
                            } else {
                                ToastBus.post("Save failed — try again.")
                            }
                        }
                    },
                )
                Spacer(Modifier.height(HemSpace.xl))
            } else {
                if (error != null) {
                    Spacer(Modifier.height(HemSpace.sm))
                    Text(error!!, style = HemType.bodyMuted.copy(color = Color(0xFFB0743A)))
                }
                Spacer(Modifier.height(HemSpace.lg))
                PrimaryButton(
                    label = if (busy) "Decoding…" else "Decode · ${Supa.DECODE_COST} credits",
                    enabled = pickedUri != null && !busy,
                    onClick = {
                        if (busy) return@PrimaryButton
                        busy = true
                        error = null
                        scope.launch {
                            val gate = CreditsGate.check(Supa.DECODE_COST)
                            if (gate !is GateResult.Ok) {
                                busy = false
                                if (gate is GateResult.InsufficientBalance) {
                                    ToastBus.post("Not enough credits — ${gate.need} needed.")
                                    onOpenPaywall()
                                } else CreditsGate.explainAndBlock(gate)
                                return@launch
                            }
                            runCatching {
                                val bytes = withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(pickedUri!!)?.buffered()?.use { it.readBytes() }
                                } ?: error("Could not read photo")
                                // Reference photos go into `closet` bucket → signed URL for FAL to fetch.
                                val path = withContext(Dispatchers.IO) { Repo.uploadClosetPhoto(bytes) }
                                val signed = Repo.signedClosetUrl(path) ?: error("Could not sign URL")
                                val res = CompareService.decode(signed)
                                if (res.error != null) error("Hem: ${res.error} — ${res.detail ?: ""}")
                                runCatching { Repo.spendCredits(Supa.DECODE_COST, "decode") }
                                result = res
                                // Auto-save reference photo to Journal as a decode entry.
                                runCatching {
                                    val outPath = withContext(Dispatchers.IO) { Repo.uploadOutfitPhoto(bytes) }
                                    Repo.insertOutfit(
                                        com.fitrater.app.data.model.OutfitInsert(
                                            user_id = Repo.userId ?: error("Not signed in"),
                                            photo_path = outPath,
                                            score = 0.0,
                                            occasion = "Decoded",
                                            hem_comment = res.style_signature.ifBlank { "Decoded look" },
                                            kind = "decode",
                                        ),
                                    )
                                }.onFailure { Log.w("Decode", "journal auto-save failed", it) }
                            }.onFailure {
                                Log.e("Decode", "failed", it)
                                error = it.message
                                ToastBus.post("Decode failed: ${it.message ?: "error"}")
                            }
                            busy = false
                        }
                    },
                )
                Spacer(Modifier.height(HemSpace.xl))
            }
        }
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(HemColors.Hairline))
}
