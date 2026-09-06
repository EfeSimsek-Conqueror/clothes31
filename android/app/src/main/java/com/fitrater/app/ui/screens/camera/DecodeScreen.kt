package com.fitrater.app.ui.screens.camera

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
import com.fitrater.app.util.CreditsBus
import com.fitrater.app.util.CreditsGate
import com.fitrater.app.util.GateResult
import com.fitrater.app.util.ToastBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val DECODE_TIMEOUT_MS = 45_000L

private val DecodeStages = listOf(
    "Reading the room" to "Light, cut, context…",
    "Naming the pieces" to "Cut, cloth, colour…",
    "Sampling the palette" to "The five that carry it…",
    "Writing the signature" to "One line, no cliches…",
)

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
    val balance by CreditsBus.balance.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) { pickedUri = u; result = null; savedOk = false; error = null }
    }
    fun openPicker() {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun runDecode() {
        if (busy) return
        val uri = pickedUri ?: return
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
                    context.contentResolver.openInputStream(uri)?.buffered()?.use { it.readBytes() }
                } ?: error("Could not read photo")
                // Reference photos go into `closet` bucket → signed URL for FAL to fetch.
                val path = withContext(Dispatchers.IO) { Repo.uploadClosetPhoto(bytes) }
                val signed = Repo.signedClosetUrl(path) ?: error("Could not sign URL")
                val res = withTimeoutOrNull(DECODE_TIMEOUT_MS) { CompareService.decode(signed) }
                    ?: DecodeResponse(error = "timeout", detail = "Decode took too long. Try again.")
                if (res.error != null) error("Hem: ${res.error} — ${res.detail ?: ""}")
                // Credits are spent ONLY after a successful decode.
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
                error = it.message ?: "Something went wrong."
                ToastBus.post("Decode failed: ${it.message ?: "error"}")
            }
            busy = false
        }
    }

    fun reset() {
        result = null
        pickedUri = null
        savedOk = false
        saving = false
        error = null
    }

    Box(Modifier.fillMaxSize().background(HemColors.Paper)) {
        if (busy) {
            DecodeLoadingBody()
            return@Box
        }

        Column(Modifier.fillMaxSize()) {
            // ---- scrolling body ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = HemSpace.gutter)
                    .padding(top = HemSpace.lg),
            ) {
                val res = result
                DecodeHeader(
                    subtitle = if (res != null) {
                        "One signature, the pieces behind it, and the palette."
                    } else {
                        "Pick a look you want to understand.\nPhotos or files."
                    },
                    onClose = onClose,
                )
                Spacer(Modifier.height(HemSpace.lg))

                DropZone(
                    uri = pickedUri,
                    ratio = if (res != null) 2.4f else 1.15f,
                    onTap = {
                        if (res != null) reset()
                        openPicker()
                    },
                )

                if (res != null) {
                    Spacer(Modifier.height(HemSpace.lg))
                    Eyebrow("STYLE SIGNATURE")
                    Spacer(Modifier.height(HemSpace.xs))
                    Text(
                        res.style_signature.ifBlank { "—" },
                        style = HemType.serifSection.copy(fontSize = 22.sp, lineHeight = 30.sp),
                    )

                    Spacer(Modifier.height(HemSpace.md))
                    Hairline()
                    Spacer(Modifier.height(HemSpace.md))

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow("PIECES")
                        Spacer(Modifier.weight(1f))
                        if (res.pieces.isNotEmpty()) {
                            Text(
                                text = when {
                                    savedOk -> "ADDED ✓"
                                    saving -> "ADDING…"
                                    else -> "ADD TO CLOSET →"
                                },
                                style = HemType.eyebrow.copy(letterSpacing = 1.8.sp),
                                modifier = Modifier.clickable(enabled = !saving && !savedOk) {
                                    saving = true
                                    scope.launch {
                                        val ok = saveToCloset(res)
                                        saving = false
                                        if (ok > 0) {
                                            savedOk = true
                                            ToastBus.post("Added $ok piece${if (ok > 1) "s" else ""} to your closet.")
                                        } else {
                                            ToastBus.post("Save failed — try again.")
                                        }
                                    }
                                },
                            )
                        }
                    }

                    res.pieces.forEachIndexed { idx, p ->
                        if (idx > 0) Hairline()
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 11.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                p.displayName,
                                style = HemType.body.copy(fontSize = 15.sp),
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.size(HemSpace.sm))
                            Text(
                                p.displayMeta,
                                style = HemType.bodyMuted.copy(fontSize = 11.sp, lineHeight = 16.sp),
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    if (res.palette_hex.isNotEmpty()) {
                        Spacer(Modifier.height(HemSpace.md))
                        Eyebrow("PALETTE")
                        Spacer(Modifier.height(HemSpace.sm))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            res.palette_hex.forEach { hex ->
                                val col = runCatching { Color(android.graphics.Color.parseColor(hex)) }
                                    .getOrDefault(HemColors.Muted)
                                Column(
                                    Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(col)
                                            .border(1.dp, HemColors.Hairline, RoundedCornerShape(10.dp)),
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        hex.uppercase(),
                                        style = HemType.bodyMuted.copy(fontSize = 8.sp, lineHeight = 10.sp),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                } else if (error != null) {
                    Spacer(Modifier.height(HemSpace.lg))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(HemColors.CardCream)
                            .border(1.dp, HemColors.Hairline, RoundedCornerShape(16.dp))
                            .padding(HemSpace.md),
                    ) {
                        Eyebrow("COULDN'T READ IT")
                        Spacer(Modifier.height(HemSpace.xs))
                        Text(error!!, style = HemType.bodyMuted.copy(fontSize = 13.sp))
                    }
                }

                Spacer(Modifier.height(HemSpace.xl))
            }

            // ---- pinned CTA ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HemSpace.gutter)
                    .padding(bottom = HemSpace.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val res = result
                when {
                    res != null -> {
                        PrimaryButton(label = "Decode another", onClick = { reset() })
                        Spacer(Modifier.height(HemSpace.xs))
                        Text(
                            "The only read that grows your closet",
                            style = HemType.bodyMuted.copy(fontSize = 11.sp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    error != null -> {
                        PrimaryButton(label = "Try again", onClick = { runDecode() })
                        Spacer(Modifier.height(HemSpace.xs))
                        Text(
                            "Close",
                            style = HemType.bodyMuted.copy(fontSize = 12.sp),
                            modifier = Modifier.clickable(onClick = onClose).padding(HemSpace.xxs),
                        )
                    }
                    else -> {
                        PrimaryButton(
                            label = "Decode it · ${Supa.DECODE_COST} credits",
                            enabled = pickedUri != null && !busy,
                            onClick = { runDecode() },
                        )
                        Spacer(Modifier.height(HemSpace.xs))
                        Text(
                            balance?.let { "$it credits left · result in ~15 seconds" }
                                ?: "Result in ~15 seconds",
                            style = HemType.bodyMuted.copy(fontSize = 11.sp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** Header: eyebrow + serif title + subtitle, with a circular close chip top-right. */
@Composable
private fun DecodeHeader(subtitle: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(end = HemSpace.sm)) {
            Eyebrow("READ THE ROOM")
            Spacer(Modifier.height(HemSpace.xs))
            Text(
                "Decode style",
                style = HemType.serifDisplay.copy(fontSize = 28.sp, lineHeight = 34.sp),
            )
            Spacer(Modifier.height(HemSpace.xs))
            Text(subtitle, style = HemType.bodyMuted.copy(fontSize = 13.sp, lineHeight = 19.sp))
        }
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(HemColors.CardCream)
                .border(1.dp, HemColors.Hairline, RoundedCornerShape(999.dp))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                tint = HemColors.Ink,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** Dashed drop zone when empty; the picked image (with a CHANGE chip) when filled. */
@Composable
private fun DropZone(uri: Uri?, ratio: Float, onTap: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f) }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(shape)
            .background(HemColors.CardCream)
            .then(
                if (uri == null) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = HemColors.Hairline,
                            size = Size(size.width, size.height),
                            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
                        )
                    }
                } else {
                    Modifier.border(1.dp, HemColors.Hairline, shape)
                },
            )
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(HemSpace.xs)
                    .clip(RoundedCornerShape(999.dp))
                    .background(HemColors.CardCream)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text("CHANGE", style = HemType.eyebrow.copy(letterSpacing = 1.8.sp))
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.Image,
                    contentDescription = null,
                    tint = HemColors.Muted.copy(alpha = 0.65f),
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text("Reference photo", style = HemType.body.copy(fontSize = 13.sp))
                Spacer(Modifier.height(10.dp))
                Text(
                    "or browse files",
                    style = HemType.bodyMuted.copy(
                        fontSize = 12.sp,
                        textDecoration = TextDecoration.Underline,
                    ),
                    modifier = Modifier.clickable(onClick = onTap),
                )
            }
        }
    }
}

/** Non-dismissible loading state — replaces the whole body while a decode is in flight. */
@Composable
private fun DecodeLoadingBody() {
    var stageIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3500)
            stageIndex = (stageIndex + 1) % DecodeStages.size
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(HemColors.CardCream)
                .border(1.dp, HemColors.Hairline, RoundedCornerShape(28.dp))
                .padding(horizontal = HemSpace.lg, vertical = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Eyebrow("READING")
            Spacer(Modifier.height(HemSpace.sm))
            Crossfade(targetState = stageIndex, animationSpec = tween(450), label = "decodeStage") { i ->
                val (title, sub) = DecodeStages[i]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        title,
                        style = HemType.serifTitle.copy(fontSize = 26.sp, lineHeight = 32.sp),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(HemSpace.xs))
                    Text(
                        sub,
                        style = HemType.serifQuote.copy(fontSize = 14.sp, lineHeight = 20.sp, color = HemColors.Muted),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(36.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                color = HemColors.Bronze,
                trackColor = HemColors.Hairline,
                strokeWidth = 2.5.dp,
            )
        }
        Spacer(Modifier.height(HemSpace.md))
        Text(
            "This usually takes 10–20 seconds. Please keep the app open.",
            style = HemType.bodyMuted.copy(fontSize = 11.sp),
            textAlign = TextAlign.Center,
        )
    }
}

/** Closet save loop — unchanged behaviour, returns the number of rows inserted. */
private suspend fun saveToCloset(res: DecodeResponse): Int {
    val uid = Repo.userId
    if (uid == null) {
        ToastBus.post("Not signed in.")
        return 0
    }
    var okCount = 0
    res.pieces.forEach { p ->
        val name = p.displayName.ifBlank { p.type }
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
    return okCount
}

@Composable
private fun Eyebrow(text: String) {
    Text(text, style = HemType.eyebrow)
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(HemColors.Hairline))
}
