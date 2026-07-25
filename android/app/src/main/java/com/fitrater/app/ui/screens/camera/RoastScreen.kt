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
import androidx.compose.runtime.LaunchedEffect
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
import com.fitrater.app.data.model.OutfitInsert
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.data.service.HemService
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.PrimaryButton
import com.fitrater.app.ui.theme.SerifDisplay
import com.fitrater.app.ui.theme.SerifFamily
import com.fitrater.app.util.CreditsGate
import com.fitrater.app.util.GateResult
import com.fitrater.app.util.ShareCard
import com.fitrater.app.util.ToastBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val OCCASIONS = listOf("Work", "Date", "Wedding", "Casual", "Everyday")

/** Flow 4 — "Roast this": brutal-mode scoring + shareable card. */
@Composable
fun RoastScreen(
    onClose: () -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenCamera: () -> Unit,
    isPro: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var capturedUri by remember { mutableStateOf<Uri?>(null) }
    var capturedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pickedOccasion by remember { mutableStateOf("Everyday") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var roastComment by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val pending = com.fitrater.app.util.CameraBus.consume()
        if (pending != null) {
            capturedBytes = pending
            capturedUri = null
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) { capturedUri = uri; capturedBytes = null } }

    Box(Modifier.fillMaxSize().background(HemColors.Paper)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HemSpace.gutter, vertical = HemSpace.lg),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.padding(end = HemSpace.sm)) {
                    Text("BRUTAL MODE", style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 2.sp))
                    Spacer(Modifier.height(HemSpace.xs))
                    SerifDisplay("Roast this")
                }
                Spacer(Modifier.fillMaxWidth().weight(1f, fill = false))
                Box(Modifier.size(36.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            Spacer(Modifier.height(HemSpace.lg))

            val roast = roastComment
            val bytes = capturedBytes
            val uri = capturedUri
            if (roast != null && bytes != null) {
                // Result screen
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.82f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(HemColors.CardCream)
                        .border(1.dp, HemColors.Hairline, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = bytes,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(HemSpace.lg))
                Text(
                    "“$roast”",
                    style = HemType.serifQuote.copy(
                        fontFamily = SerifFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(Modifier.height(HemSpace.md))
                Text("— Hem", style = HemType.bodyMuted.copy(fontStyle = FontStyle.Italic))

                Spacer(Modifier.height(HemSpace.xl))
                PrimaryButton(
                    label = "SHARE THE ROAST",
                    onClick = {
                        runCatching {
                            val uriShare = ShareCard.renderRoast(context, bytes, roast)
                            ShareCard.launchShare(context, uriShare, "Share roast")
                        }.onFailure {
                            Log.e("Roast", "share failed", it)
                            ToastBus.post("Couldn't build share card: ${it.message ?: "error"}")
                        }
                    },
                )
                Spacer(Modifier.height(HemSpace.sm))
                OutlinedRow(label = "Done", onClick = onClose)
                Spacer(Modifier.height(HemSpace.xl))
            } else {
                // Input screen
                PhotoTile(bytes = bytes, uri = uri)
                Spacer(Modifier.height(HemSpace.md))
                Row(horizontalArrangement = Arrangement.spacedBy(HemSpace.sm)) {
                    OutlinedRow(label = "Take photo", onClick = onOpenCamera, modifier = Modifier.weight(1f))
                    OutlinedRow(
                        label = "From gallery",
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(HemSpace.lg))
                Text("Occasion", style = HemType.body.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(HemSpace.sm))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OCCASIONS.forEach { opt ->
                        val selected = pickedOccasion == opt
                        Box(
                            Modifier
                                .weight(1f).height(38.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (selected) HemColors.Ink else Color.Transparent)
                                .border(1.dp, HemColors.Ink.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                                .clickable { pickedOccasion = opt },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                opt,
                                style = HemType.body.copy(
                                    color = if (selected) Color.White else HemColors.Ink,
                                    fontSize = 13.sp,
                                ),
                            )
                        }
                    }
                }

                if (error != null) {
                    Spacer(Modifier.height(HemSpace.sm))
                    Text(error!!, style = HemType.bodyMuted.copy(color = Color(0xFFB0743A)))
                }
                Spacer(Modifier.height(HemSpace.lg))
                PrimaryButton(
                    label = if (busy) "Roasting…" else "Roast it · ${Supa.ROAST_COST} credits",
                    enabled = (bytes != null || uri != null) && !busy,
                    onClick = {
                        if (busy) return@PrimaryButton
                        busy = true
                        error = null
                        scope.launch {
                            // Free-tier daily cap: 1 roast/day. Brutal-mode stays a Pro teaser.
                            if (!isPro) {
                                val used = runCatching { Repo.roastsToday() }.getOrDefault(0)
                                if (used >= 1) {
                                    busy = false
                                    ToastBus.post("Free plan: 1 roast/day. Upgrade for unlimited.")
                                    onOpenPaywall()
                                    return@launch
                                }
                            }
                            val gate = CreditsGate.check(Supa.ROAST_COST)
                            if (gate !is GateResult.Ok) {
                                busy = false
                                if (gate is GateResult.InsufficientBalance) {
                                    ToastBus.post("Not enough credits — ${gate.need} needed.")
                                    onOpenPaywall()
                                } else CreditsGate.explainAndBlock(gate)
                                return@launch
                            }
                            runCatching {
                                val b = bytes ?: withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(uri!!)?.buffered()?.use { it.readBytes() }
                                        ?: error("Could not read photo")
                                }
                                capturedBytes = b
                                val path = Repo.uploadOutfitPhoto(b)
                                val signed = Repo.signedOutfitUrl(path)
                                    ?: error("Could not sign photo URL")
                                val scored = HemService.score(signed, pickedOccasion, "brutal")
                                Repo.insertOutfit(
                                    OutfitInsert(
                                        user_id = Repo.userId ?: error("Not signed in"),
                                        photo_path = path,
                                        score = scored.score,
                                        occasion = pickedOccasion,
                                        hem_comment = scored.hemComment,
                                        subscores = scored.subscores,
                                        swaps = scored.swaps,
                                        annotations = scored.annotations,
                                        kind = "roast",
                                    ),
                                )
                                runCatching { Repo.spendCredits(Supa.ROAST_COST, "roast") }
                                roastComment = scored.hemComment
                            }.onFailure {
                                Log.e("Roast", "failed", it)
                                error = it.message
                                ToastBus.post("Roast failed: ${it.message ?: "error"}")
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
private fun PhotoTile(bytes: ByteArray?, uri: Uri?) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .clip(RoundedCornerShape(20.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bytes != null -> AsyncImage(
                model = bytes,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            uri != null -> AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Text("No photo yet", style = HemType.bodyMuted)
        }
    }
}

@Composable
internal fun OutlinedRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, HemColors.Ink.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = HemType.body.copy(fontWeight = FontWeight.Medium))
    }
}
