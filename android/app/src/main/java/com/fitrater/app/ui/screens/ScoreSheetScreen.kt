package com.fitrater.app.ui.screens

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.fitrater.app.data.model.OutfitInsert
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.data.service.HemService
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.PrimaryButton
import com.fitrater.app.ui.theme.SerifDisplay
import com.fitrater.app.util.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val occasions = listOf("Work", "Date", "Wedding", "Casual", "Everyday")

@Composable
fun ScoreSheetScreen(
    onClose: () -> Unit,
    onScored: (String) -> Unit,
    onOpenPaywall: () -> Unit = {},
    onOpenCamera: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickedOccasion by remember { mutableStateOf("Everyday") }
    var capturedUri by remember { mutableStateOf<Uri?>(null) }
    // Bytes captured in-app via CameraCaptureScreen (already cropped + compressed).
    var capturedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Pull any bytes handed off by CameraCaptureScreen on the way back.
    LaunchedEffect(Unit) {
        val pending = com.fitrater.app.util.CameraBus.consume()
        if (pending != null) {
            capturedBytes = pending
            capturedUri = null
        }
    }

    val cameraAvailable = true

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) { capturedUri = uri; capturedBytes = null } }

    Box(
        Modifier
            .fillMaxSize()
            .background(HemColors.Ink.copy(alpha = 0.2f)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 24.dp)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(HemColors.Paper)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HemSpace.gutter, vertical = HemSpace.lg),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SerifDisplay("Score a look")
                    Spacer(Modifier.height(HemSpace.xs))
                    Text(
                        "Snap it, pick an occasion, and Hem takes it from there.",
                        style = HemType.bodyMuted,
                    )
                }
                Box(
                    Modifier.size(36.dp).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            Spacer(Modifier.height(HemSpace.lg))

            // Photo tile
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.82f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(HemColors.CardCream)
                    .border(1.dp, HemColors.Hairline, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val u = capturedUri
                val b = capturedBytes
                if (b != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(b).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (u != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(u).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("No photo yet", style = HemType.bodyMuted)
                }
            }
            Spacer(Modifier.height(HemSpace.md))
            Row(horizontalArrangement = Arrangement.spacedBy(HemSpace.sm)) {
                if (cameraAvailable) {
                    OutlinePill(
                        label = "Take photo",
                        onClick = { onOpenCamera() },
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinePill(
                    label = "From gallery",
                    onClick = {
                        galleryLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            if (!cameraAvailable) {
                Spacer(Modifier.height(HemSpace.xs))
                Text("Camera unavailable", style = HemType.bodyMuted.copy(fontSize = 12.sp))
            }

            Spacer(Modifier.height(HemSpace.lg))
            Text("Occasion", style = HemType.body.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(HemSpace.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                occasions.forEach { opt ->
                    val selected = pickedOccasion == opt
                    Box(
                        Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) HemColors.Ink else Color.Transparent)
                            .border(1.dp, HemColors.Ink.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                            .clickable { pickedOccasion = opt },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            opt,
                            style = HemType.body.copy(
                                color = if (selected) HemColors.OnInk else HemColors.Ink,
                                fontSize = 13.sp,
                            ),
                        )
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(HemSpace.sm))
                Text(error!!, style = HemType.bodyMuted.copy(color = HemColors.Bronze))
            }

            Spacer(Modifier.height(HemSpace.lg))
            PrimaryButton(
                label = if (busy) "Analyzing…" else "Score it",
                onClick = {
                    val prepared = capturedBytes
                    val u = capturedUri
                    if (prepared == null && u == null) {
                        error = "Add a photo first"
                        return@PrimaryButton
                    }
                    if (busy) return@PrimaryButton
                    busy = true
                    error = null
                    scope.launch {
                        // Pre-check credits + trial/monthly caps.
                        val gate = com.fitrater.app.util.CreditsGate.check(com.fitrater.app.data.Supa.SCORE_COST)
                        if (gate !is com.fitrater.app.util.GateResult.Ok) {
                            busy = false
                            if (gate is com.fitrater.app.util.GateResult.InsufficientBalance) {
                                com.fitrater.app.util.ToastBus.post("Not enough credits — ${gate.need} needed to score.")
                                onOpenPaywall()
                            } else {
                                com.fitrater.app.util.CreditsGate.explainAndBlock(gate)
                            }
                            return@launch
                        }
                        runCatching {
                            val bytes = prepared ?: withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(u!!)?.buffered()?.use { it.readBytes() }
                                    ?: error("Could not read photo")
                            }
                            if (bytes.size < 800 * 1024) {
                                Log.w("Score", "photo bytes ${bytes.size} < 800KB, quality may be reduced")
                            }
                            val path = Repo.uploadOutfitPhoto(bytes)
                            val signed = Repo.signedOutfitUrl(path)
                                ?: error("Could not sign photo URL")
                            val honesty = runCatching { Repo.currentProfile()?.honesty }
                                .getOrNull() ?: "honest"
                            Log.i("score-outfit", "invoking honesty=$honesty occasion=$pickedOccasion")
                            val scored = HemService.score(signed, pickedOccasion, honesty)
                            val row = Repo.insertOutfit(
                                OutfitInsert(
                                    user_id = Repo.userId ?: error("Not signed in"),
                                    photo_path = path,
                                    score = scored.score,
                                    occasion = pickedOccasion,
                                    hem_comment = scored.hemComment,
                                    subscores = scored.subscores,
                                    swaps = scored.swaps,
                                    annotations = scored.annotations,
                                ),
                            )
                            // Charge credits only on success.
                            runCatching { Repo.spendCredits(com.fitrater.app.data.Supa.SCORE_COST, "outfit_score") }
                                .onFailure { Log.w("Score", "credit charge failed", it) }
                            row.id ?: error("Insert returned no id")
                        }.onSuccess { id ->
                            Log.i("Score", "outfit scored id=$id")
                            busy = false
                            onScored(id)
                        }.onFailure {
                            Log.e("Score", "score failed", it)
                            error = it.userMessage("Scoring failed. Please try again.")
                            com.fitrater.app.util.ToastBus.post(error!!)
                            busy = false
                        }
                    }
                },
                enabled = (capturedUri != null || capturedBytes != null) && !busy,
            )
            Spacer(Modifier.height(HemSpace.xl))
        }
    }
}

@Composable
private fun OutlinePill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, HemColors.Ink.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = HemType.body.copy(fontWeight = FontWeight.Medium))
    }
}
