package com.fitrater.app.ui.screens.camera

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitrater.app.data.Supa
import io.github.jan.supabase.storage.storage
import com.fitrater.app.data.model.ClosetItem
import com.fitrater.app.data.model.OutfitInsert
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.PrimaryButton
import com.fitrater.app.ui.theme.SerifDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TRYON_PROMPT =
    "Two-image composition. Image 1 shows a person. Image 2 shows a specific garment. " +
        "Show the person from image 1 wearing the EXACT garment from image 2. " +
        "Rules: The garment must be identical to image 2 in every detail — color, silhouette, cut, fabric, print, seams, and any decorative elements. " +
        "Do NOT redesign, restyle, reinterpret, or 'improve' the garment; copy it faithfully. " +
        "If the person in image 1 is already wearing a similar type of garment, REPLACE it with the one from image 2. " +
        "Keep the person's face, hair, skin, body proportions, pose, and background 100% identical to image 1. " +
        "Match lighting and shadows realistically. " +
        "Output: a single photorealistic editorial fashion photograph."

/** Flow 2 — Try on: a Studio piece worn on the user's own photo. */
@Composable
fun TryOnScreen(
    onClose: () -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenStudioCreate: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pieces by remember { mutableStateOf<List<ClosetItem>>(emptyList()) }
    var loadingPieces by remember { mutableStateOf(true) }
    var pickedPiece by remember { mutableStateOf<ClosetItem?>(null) }
    var pickedPieceUrl by remember { mutableStateOf<String?>(null) }

    // Alternate source: user uploads a photo of a real garment they own.
    // "studio" = pick from generated pieces; "upload" = user photo of a piece.
    var pieceSource by remember { mutableStateOf("studio") }
    var importedPieceBytes by remember { mutableStateOf<ByteArray?>(null) }
    var importedPieceUrl by remember { mutableStateOf<String?>(null) } // signed URL after upload
    var importingPiece by remember { mutableStateOf(false) }

    var personUri by remember { mutableStateOf<Uri?>(null) }
    var personBytes by remember { mutableStateOf<ByteArray?>(null) }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resultUrl by remember { mutableStateOf<String?>(null) }
    var savingLook by remember { mutableStateOf(false) }
    var savedOk by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) { personUri = u; personBytes = null }
    }
    val pieceGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u == null) return@rememberLauncherForActivityResult
        scope.launch {
            // 1) Read bytes and show the preview IMMEDIATELY — before any network I/O.
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(u)?.buffered()?.use { it.readBytes() } }
                    .getOrNull()
            }
            if (bytes == null) {
                com.fitrater.app.util.ToastBus.post("Import failed: could not read image")
                return@launch
            }
            importedPieceBytes = bytes
            pickedPiece = null
            pickedPieceUrl = null
            importedPieceUrl = null
            importingPiece = true
            Log.i("TryOn", "import start size=${bytes.size}")
            // 2) Upload to the OUTFITS bucket (proven RLS) at tryon-ref-<uuid>.jpg. We still use a
            //    signed URL so the try-on backend can fetch it.
            val outcome = runCatching {
                val uid = Repo.userId ?: error("Not signed in")
                val path = "$uid/tryon-ref-${java.util.UUID.randomUUID()}.jpg"
                withContext(Dispatchers.IO) {
                    com.fitrater.app.data.Supa.client.storage.from("outfits").upload(path, bytes) { upsert = false }
                }
                val signed = Repo.signedOutfitUrl(path) ?: error("Could not sign URL")
                Log.i("TryOn", "uploaded path=$path url=$signed")
                signed
            }
            outcome
                .onSuccess { importedPieceUrl = it }
                .onFailure {
                    Log.e("TryOn", "piece upload failed", it)
                    com.fitrater.app.util.ToastBus.post("Import failed: ${it.message ?: "error"}")
                }
            importingPiece = false
        }
    }

    LaunchedEffect(Unit) {
        // Belt-and-suspenders: Try-on is Pro-only. If a Free user lands here via deep link
        // or state restoration, bounce them straight to the paywall.
        val proNow = runCatching { com.fitrater.app.data.billing.RcBilling.refreshCustomerInfo() }
            .getOrNull()
            ?.let { com.fitrater.app.data.billing.RcBilling.isPro(it) } == true
        if (!proNow) {
            onClose()
            onOpenPaywall()
            return@LaunchedEffect
        }
        pieces = runCatching { Repo.closetItems() }.getOrDefault(emptyList())
        loadingPieces = false
        // Pull any bytes from CameraCaptureScreen return.
        val pending = com.fitrater.app.util.CameraBus.consume()
        if (pending != null) {
            personBytes = pending
            personUri = null
        }
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
                    Text("WEAR IT", style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 2.sp))
                    Spacer(Modifier.height(HemSpace.xs))
                    SerifDisplay("Try on")
                }
                Spacer(Modifier.fillMaxWidth().weight(1f, fill = false))
                Box(Modifier.size(36.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            val gen = resultUrl
            if (gen != null) {
                Spacer(Modifier.height(HemSpace.lg))
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
                        model = gen,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(HemSpace.lg))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(HemColors.CardCream)
                        .border(1.dp, HemColors.Bronze.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (savingLook) {
                        androidx.compose.material3.CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = HemColors.Bronze,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(HemSpace.sm))
                        Text("Saving to Journal…", style = HemType.body.copy(color = HemColors.Bronze))
                    } else {
                        Text(
                            if (savedOk) "✓ Saved to Journal" else "Save didn't stick — try again next time",
                            style = HemType.body.copy(
                                color = HemColors.Bronze,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(HemSpace.sm))
                OutlinedRow(
                    label = "Try another piece",
                    onClick = {
                        resultUrl = null
                        savedOk = false
                        pickedPiece = null
                        pickedPieceUrl = null
                    },
                )
                Spacer(Modifier.height(HemSpace.sm))
                OutlinedRow(label = "Done", onClick = onClose)
                Spacer(Modifier.height(HemSpace.xl))
            } else {
                Spacer(Modifier.height(HemSpace.lg))
                // ---- Step 1: pick a piece ----
                Text("STEP 1 · PIECE", style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 2.sp))
                Spacer(Modifier.height(HemSpace.sm))
                // Two-source segmented toggle.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(HemColors.CardCream)
                        .border(1.dp, HemColors.Hairline, RoundedCornerShape(999.dp)),
                ) {
                    listOf("studio" to "From Studio", "upload" to "Upload photo").forEach { (key, label) ->
                        val active = pieceSource == key
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (active) HemColors.Ink else Color.Transparent)
                                .clickable { pieceSource = key }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                style = HemType.body.copy(
                                    color = if (active) Color.White else HemColors.Ink,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                ),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(HemSpace.sm))
                if (pieceSource == "upload") {
                    // Uploaded piece preview + tap-to-pick zone.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.4f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(HemColors.CardCream)
                            .border(1.dp, HemColors.Hairline, RoundedCornerShape(18.dp))
                            .clickable(enabled = !importingPiece) {
                                pieceGalleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            importedPieceBytes != null -> {
                                AsyncImage(
                                    model = importedPieceBytes,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (importingPiece) {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.35f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            androidx.compose.material3.CircularProgressIndicator(
                                                strokeWidth = 2.dp,
                                                color = Color.White,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(HemSpace.sm))
                                            Text(
                                                "Uploading…",
                                                style = HemType.body.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                                            )
                                        }
                                    }
                                }
                            }
                            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("+ Add garment photo", style = HemType.body.copy(fontWeight = FontWeight.SemiBold))
                                Spacer(Modifier.height(HemSpace.xs))
                                Text("Flat lay works best.", style = HemType.bodyMuted.copy(fontSize = 12.sp))
                            }
                        }
                    }
                    Spacer(Modifier.height(HemSpace.lg))
                    // Skip Studio row when in upload mode
                    // (fall through to Step 2 below)
                } else if (loadingPieces) {
                    Text("Loading your Studio…", style = HemType.bodyMuted)
                } else if (pieces.isEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(HemColors.CardCream)
                            .border(1.dp, HemColors.Hairline, RoundedCornerShape(18.dp))
                            .padding(HemSpace.md),
                    ) {
                        Text("Your closet is empty.", style = HemType.body.copy(fontWeight = FontWeight.SemiBold))
                        Spacer(Modifier.height(HemSpace.xs))
                        Text("Design a piece in Studio first, then come back to try it on.", style = HemType.bodyMuted)
                        Spacer(Modifier.height(HemSpace.md))
                        OutlinedRow(label = "Design new", onClick = onOpenStudioCreate)
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(HemSpace.sm),
                    ) {
                        pieces.forEach { piece ->
                            PieceCard(
                                piece = piece,
                                selected = pickedPiece?.id == piece.id,
                                onTap = {
                                    scope.launch {
                                        pickedPiece = piece
                                        pickedPieceUrl = null
                                        val path = piece.image_path
                                        if (path != null) {
                                            pickedPieceUrl = Repo.signedClosetUrl(path)
                                        }
                                    }
                                },
                            )
                        }
                        // Trailing "Design new" ghost.
                        Box(
                            Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Transparent)
                                .border(1.dp, HemColors.Hairline, RoundedCornerShape(14.dp))
                                .clickable(onClick = onOpenStudioCreate),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("+ Design", style = HemType.body.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp))
                        }
                    }
                }

                Spacer(Modifier.height(HemSpace.lg))
                // ---- Step 2: your photo ----
                Text("STEP 2 · YOUR PHOTO", style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 2.sp))
                Spacer(Modifier.height(HemSpace.sm))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.82f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(HemColors.CardCream)
                        .border(1.dp, HemColors.Hairline, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        personBytes != null -> AsyncImage(model = personBytes, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        personUri != null -> AsyncImage(model = personUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else -> Text("Add a full-length photo of you", style = HemType.bodyMuted)
                    }
                }
                Spacer(Modifier.height(HemSpace.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(HemSpace.sm)) {
                    OutlinedRow(label = "Take photo", onClick = onOpenCamera, modifier = Modifier.weight(1f))
                    OutlinedRow(
                        label = "From gallery",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    )
                }

                if (error != null) {
                    Spacer(Modifier.height(HemSpace.sm))
                    Text(error!!, style = HemType.bodyMuted.copy(color = Color(0xFFB0743A)))
                }

                Spacer(Modifier.height(HemSpace.lg))
                val hasPiece = when (pieceSource) {
                    "upload" -> importedPieceUrl != null
                    else -> pickedPiece != null
                }
                val ready = hasPiece && (personBytes != null || personUri != null)
                PrimaryButton(
                    label = if (busy) "Composing look…" else "Generate try-on · ${Supa.TRYON_COST} credits",
                    enabled = ready && !busy,
                    onClick = {
                        if (busy) return@PrimaryButton
                        busy = true
                        error = null
                        scope.launch {
                            val gate = com.fitrater.app.util.CreditsGate.check(Supa.TRYON_COST)
                            if (gate !is com.fitrater.app.util.GateResult.Ok) {
                                busy = false
                                if (gate is com.fitrater.app.util.GateResult.InsufficientBalance) {
                                    com.fitrater.app.util.ToastBus.post("Not enough credits — ${gate.need} needed.")
                                    onOpenPaywall()
                                } else com.fitrater.app.util.CreditsGate.explainAndBlock(gate)
                                return@launch
                            }
                            runCatching {
                                val personBytesFinal = personBytes ?: withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(personUri!!)?.buffered()?.use { it.readBytes() }
                                } ?: error("Could not read photo")
                                personBytes = personBytesFinal
                                val personPath = Repo.uploadOutfitPhoto(personBytesFinal)
                                val personSigned = Repo.signedOutfitUrl(personPath) ?: error("Could not sign person URL")
                                val pieceSigned = when (pieceSource) {
                                    "upload" -> importedPieceUrl ?: error("No garment photo uploaded")
                                    else -> pickedPieceUrl ?: pickedPiece?.image_path?.let { Repo.signedClosetUrl(it) }
                                        ?: error("No piece selected")
                                }
                                // Map our category → Fashn model's expected category token.
                                val cat = if (pieceSource == "upload") null else pickedPiece?.category?.lowercase()
                                val fashnCategory = when (cat) {
                                    "top", "outerwear", "shirt", "hoodie", "blazer", "cardigan", "tank", "polo" -> "tops"
                                    "bottom", "trousers", "jeans", "shorts", "skirt", "cargo", "sweats", "chinos" -> "bottoms"
                                    "dress" -> "one-pieces"
                                    else -> "auto"
                                }
                                val resp = Repo.tryOnPiece(personSigned, pieceSigned, fashnCategory)
                                val url = resp.image_url ?: error(resp.error ?: "Try-on failed")
                                runCatching { Repo.spendCredits(Supa.TRYON_COST, "tryon") }
                                resultUrl = url
                                // Auto-save to Journal (outfits row, kind='tryon') — no manual step.
                                savingLook = true
                                savedOk = false
                                runCatching {
                                    val outBytes = Repo.downloadBytes(url)
                                    val outPath = withContext(Dispatchers.IO) { Repo.uploadOutfitPhoto(outBytes, ext = "png") }
                                    val pieceName = pickedPiece?.name
                                        ?: if (pieceSource == "upload") "uploaded garment" else "piece"
                                    Repo.insertOutfit(
                                        OutfitInsert(
                                            user_id = Repo.userId ?: error("Not signed in"),
                                            photo_path = outPath,
                                            score = 0.0,
                                            occasion = "Try-on",
                                            hem_comment = "Try-on: $pieceName",
                                            kind = "tryon",
                                        ),
                                    )
                                    savedOk = true
                                }.onFailure { Log.e("TryOn", "auto-save failed", it) }
                                savingLook = false
                            }.onFailure {
                                Log.e("TryOn", "generate failed", it)
                                error = it.message
                                com.fitrater.app.util.ToastBus.post("Try-on failed: ${it.message ?: "error"}")
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
private fun PieceCard(piece: ClosetItem, selected: Boolean, onTap: () -> Unit) {
    var signed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(piece.id) {
        signed = piece.image_path?.let { Repo.signedClosetUrl(it) }
    }
    Column(
        Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HemColors.CardCream)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) HemColors.Bronze else HemColors.Hairline,
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onTap),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            if (signed != null) {
                AsyncImage(
                    model = signed,
                    contentDescription = piece.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("…", style = HemType.bodyMuted)
            }
        }
        Text(
            (piece.name ?: piece.category ?: "piece").take(18),
            style = HemType.body.copy(fontSize = 12.sp),
            modifier = Modifier.padding(6.dp),
        )
    }
}
