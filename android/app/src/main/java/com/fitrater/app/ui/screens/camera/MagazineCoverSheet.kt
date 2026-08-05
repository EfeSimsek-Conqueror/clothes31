package com.fitrater.app.ui.screens.camera

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import coil.compose.AsyncImage
import com.fitrater.app.data.Supa
import com.fitrater.app.data.model.ComposeCoverResponse
import com.fitrater.app.data.model.MagazineCover
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.SerifDisplay
import com.fitrater.app.util.CreditsGate
import com.fitrater.app.util.GateResult
import com.fitrater.app.util.ToastBus
import kotlinx.coroutines.launch

private val COVER_MOODS = listOf(
    "Quiet luxury", "Editorial", "Brutalist", "Romantic",
    "Sport", "Minimal", "Punk", "Maximalist",
)

/**
 * Port of iOS `MagazineCoverSheet.swift` — two-photo compose-cover flow with
 * mood/masthead/prompt overrides.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MagazineCoverSheetContent(
    outfitId: String? = null,
    userName: String? = null,
    onOpenCamera: () -> Unit = {},
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var subjectBytes by remember { mutableStateOf<ByteArray?>(null) }
    var subjectUrl by remember { mutableStateOf<String?>(null) }
    var referenceBytes by remember { mutableStateOf<ByteArray?>(null) }
    var referenceUrl by remember { mutableStateOf<String?>(null) }

    var mood by rememberSaveable { mutableStateOf("") }
    var masthead by rememberSaveable { mutableStateOf("FITRATER") }
    var showMastheadEditor by remember { mutableStateOf(false) }
    var showMyCovers by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var cover by remember { mutableStateOf<ComposeCoverResponse?>(null) }
    var templates by remember { mutableStateOf<List<MagazineCover>>(emptyList()) }

    // Edit overrides
    var userPrompt by rememberSaveable { mutableStateOf("") }
    var customMasthead by rememberSaveable { mutableStateOf("") }
    var customHeadline by rememberSaveable { mutableStateOf("") }
    var customPullQuote by rememberSaveable { mutableStateOf("") }
    val editCoverLines = remember { mutableStateListOf<String>() }

    val subjectPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                readBytes(context, it)?.let { b -> subjectBytes = b; subjectUrl = null }
            }
        }
    }
    val referencePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                readBytes(context, it)?.let { b -> referenceBytes = b; referenceUrl = null }
            }
        }
    }

    // Consume any camera capture handed back via CameraBus (subject slot).
    LaunchedEffect(Unit) {
        val pending = com.fitrater.app.util.CameraBus.consume()
        if (pending != null) {
            subjectBytes = pending; subjectUrl = null
        }
    }
    LaunchedEffect(outfitId) {
        if (outfitId != null && subjectBytes == null) {
            runCatching {
                val outfit = Repo.outfitById(outfitId)
                val path = outfit?.photo_path
                if (path != null) {
                    val signed = Repo.signedOutfitUrl(path)
                    subjectUrl = signed
                    signed?.let { url ->
                        subjectBytes = runCatching { Repo.downloadBytes(url) }.getOrNull()
                    }
                }
            }
        }
        templates = runCatching { Repo.loadCoverTemplates() }.getOrDefault(emptyList())
    }

    val cost = if (referenceBytes == null && referenceUrl == null) Supa.MAGAZINE_COVER_COST else Supa.MAGAZINE_COVER_COST + 3

    fun compose() {
        if (subjectBytes == null && subjectUrl == null) return
        scope.launch {
            busy = true; errorMsg = null
            try {
                val gate = CreditsGate.check(cost)
                if (gate !is GateResult.Ok) {
                    CreditsGate.explainAndBlock(gate); busy = false; return@launch
                }
                var subjSigned = subjectUrl
                if (subjSigned == null && subjectBytes != null) {
                    val path = Repo.uploadOutfitPhoto(subjectBytes!!, "jpg")
                    subjSigned = Repo.signedOutfitUrl(path)
                }
                if (subjSigned == null) throw IllegalStateException("Could not sign subject photo.")
                var refSigned = referenceUrl
                if (refSigned == null && referenceBytes != null) {
                    val path = Repo.uploadOutfitPhoto(referenceBytes!!, "jpg")
                    refSigned = Repo.signedOutfitUrl(path)
                }
                val resp = Repo.composeCover(
                    sourceImageUrl = subjSigned,
                    outfitId = outfitId,
                    userName = userName,
                    referenceCoverUrl = refSigned,
                    masthead = masthead.ifEmpty { null },
                    mood = mood.ifEmpty { null },
                    customHeadline = customHeadline.ifEmpty { null },
                    customPullQuote = customPullQuote.ifEmpty { null },
                    coverLines = if (editCoverLines.isEmpty()) null else editCoverLines.toList(),
                    userPrompt = userPrompt.ifEmpty { null },
                    customMasthead = customMasthead.ifEmpty { null },
                )
                if (!resp.error.isNullOrBlank()) {
                    val msg = when (resp.error) {
                        "content_flagged" -> "The photo was flagged by our safety filter. Try a photo with a shirt on."
                        "swap_failed" -> "Couldn't compose the swap — try a clearer subject photo or a different reference."
                        else -> resp.error
                    }
                    errorMsg = msg
                    ToastBus.post("Cover failed: $msg")
                } else {
                    runCatching { Repo.spendCredits(cost, "magazine_cover") }
                    cover = resp
                }
            } catch (e: Throwable) {
                errorMsg = e.message ?: "Unknown error"
                ToastBus.post("Cover failed: ${e.message}")
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(HemColors.Paper)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("EDITORIAL")
                    Spacer(Modifier.height(6.dp))
                    SerifDisplay("Compose a cover.")
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Close, contentDescription = "Close", tint = HemColors.Ink) }
            }
            Spacer(Modifier.height(18.dp))

            val doneCover = cover
            if (doneCover != null && !doneCover.cover_url.isNullOrBlank()) {
                FinishedBlock(
                    coverUrl = doneCover.cover_url,
                    cover = doneCover,
                    onShare = { shareUrl(context, doneCover.cover_url) },
                    onNewHeadline = {
                        scope.launch {
                            val cid = doneCover.cover_id ?: return@launch
                            val resp = runCatching { Repo.regenerateCoverHeadline(cid) }.getOrNull()
                            if (resp?.headline != null) {
                                cover = doneCover.copy(
                                    headline = resp.headline,
                                    pull_quote = resp.pull_quote ?: doneCover.pull_quote,
                                )
                            }
                        }
                    },
                    onTryAnother = {
                        cover = null
                        subjectBytes = null; subjectUrl = null
                        referenceBytes = null; referenceUrl = null
                    },
                    onDone = onClose,
                )
            } else {
                Text(
                    "Two photos + a prompt. Tell Hem what to change on the cover.",
                    style = HemType.body.copy(fontSize = 15.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhotoTile(
                        label = "YOU",
                        bytes = subjectBytes,
                        placeholder = "Your photo",
                        chips = {
                            SourceChip(icon = Icons.Default.CameraAlt, label = "Camera") { onOpenCamera() }
                            SourceChip(icon = Icons.Default.PhotoLibrary, label = "Gallery") { subjectPicker.launch("image/*") }
                        },
                        onClear = { subjectBytes = null; subjectUrl = null },
                        modifier = Modifier.weight(1f),
                    )
                    PhotoTile(
                        label = "REFERENCE (OPT)",
                        bytes = referenceBytes,
                        placeholder = "Magazine cover to mimic",
                        chips = {
                            SourceChip(icon = Icons.Default.PhotoLibrary, label = "Gallery") { referencePicker.launch("image/*") }
                            SourceChip(
                                icon = Icons.Default.GridView,
                                label = if (templates.isEmpty()) "Studio" else "Studio (${templates.size})",
                                enabled = templates.isNotEmpty(),
                            ) { showMyCovers = true }
                        },
                        onClear = { referenceBytes = null; referenceUrl = null },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(16.dp))

                LabeledField(label = "YOUR PROMPT") {
                    BorderedText(
                        value = userPrompt,
                        onValueChange = { userPrompt = it },
                        placeholder = "What should Hem do? e.g. 'put me in the corset dress and keep the pose'",
                        textStyle = HemType.body.copy(fontSize = 15.sp, color = HemColors.Ink),
                        multiline = true,
                    )
                }
                Spacer(Modifier.height(12.dp))

                LabeledField(label = "MOOD (OPT)") {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        COVER_MOODS.forEach { m ->
                            val on = mood == m
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .border(1.dp, if (on) HemColors.Ink else HemColors.Hairline, RoundedCornerShape(999.dp))
                                    .background(if (on) HemColors.Ink else Color.Transparent)
                                    .clickable { mood = if (on) "" else m }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(m, style = HemType.body.copy(fontSize = 13.sp, color = if (on) HemColors.OnInk else HemColors.Ink))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Masthead row
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(HemColors.CardCream)
                        .border(1.dp, HemColors.Hairline, RoundedCornerShape(12.dp))
                        .clickable { showMastheadEditor = true }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.TextFields, contentDescription = null, tint = HemColors.Bronze, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("MASTHEAD", style = HemType.smallLabel.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Bronze, letterSpacing = 1.5.sp))
                        Text(masthead, style = HemType.serifDisplay.copy(fontSize = 16.sp, color = HemColors.Ink))
                    }
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = HemColors.Muted, modifier = Modifier.size(14.dp))
                }

                errorMsg?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = HemType.body.copy(fontSize = 13.sp, color = HemColors.Bronze))
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Edit outline button
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .border(1.dp, HemColors.Ink.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                            .clickable(enabled = subjectBytes != null && !busy) { showEdit = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Edit",
                            style = HemType.body.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (subjectBytes != null) HemColors.Ink else HemColors.Muted,
                            ),
                        )
                    }
                    // Compose primary
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (subjectBytes != null && !busy) HemColors.Ink else HemColors.Muted)
                            .clickable(enabled = subjectBytes != null && !busy) { compose() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (busy) "Composing…" else "Compose · $cost",
                            style = HemType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HemColors.OnInk),
                        )
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }

        if (busy) BusyOverlay()
    }

    if (showMastheadEditor) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showMastheadEditor = false }, sheetState = sheetState, containerColor = HemColors.Paper) {
            MastheadEditorContent(current = masthead, onSave = { masthead = it; showMastheadEditor = false }, onClose = { showMastheadEditor = false })
        }
    }
    if (showMyCovers) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showMyCovers = false }, sheetState = sheetState, containerColor = HemColors.Paper) {
            MyCoversPickerContent(templates = templates, onPick = { picked ->
                val path = picked.image_path
                if (path != null) {
                    val publicUrl = "https://ilrzqifdmjvooeyqvexd.supabase.co/storage/v1/object/public/magazine_covers/$path"
                    referenceUrl = publicUrl
                    scope.launch {
                        referenceBytes = runCatching { Repo.downloadBytes(publicUrl) }.getOrNull()
                    }
                }
                showMyCovers = false
            }, onClose = { showMyCovers = false })
        }
    }
    if (showEdit) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showEdit = false }, sheetState = sheetState, containerColor = HemColors.Paper) {
            EditCoverContent(
                userPrompt = userPrompt, onUserPrompt = { userPrompt = it },
                customMasthead = customMasthead, onCustomMasthead = { customMasthead = it },
                customHeadline = customHeadline, onCustomHeadline = { customHeadline = it },
                customPullQuote = customPullQuote, onCustomPullQuote = { customPullQuote = it },
                coverLines = editCoverLines,
                referenceMode = referenceBytes != null || referenceUrl != null,
                composeCostCredits = cost,
                onCompose = { showEdit = false; compose() },
                onClose = { showEdit = false },
            )
        }
    }
}

// ============================================================================
// Photo tile
// ============================================================================

@Composable
private fun PhotoTile(
    label: String,
    bytes: ByteArray?,
    placeholder: String,
    chips: @Composable RowScope.() -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(14.dp))
                .background(HemColors.CardCream)
                .border(1.dp, HemColors.Hairline, RoundedCornerShape(14.dp)),
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("+", style = HemType.serifDisplay.copy(fontSize = 28.sp, color = HemColors.Ink))
                    Text(
                        placeholder,
                        style = HemType.body.copy(fontSize = 11.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = HemType.smallLabel.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Bronze, letterSpacing = 1.5.sp),
                modifier = Modifier.weight(1f),
            )
            if (bytes != null) {
                Box(Modifier.size(20.dp).clickable(onClick = onClear), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = HemColors.Muted, modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips() }
    }
}

@Composable
private fun RowScope.SourceChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, HemColors.Bronze.copy(alpha = alpha), RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = HemColors.Bronze.copy(alpha = alpha), modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = HemType.body.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = HemColors.Bronze.copy(alpha = alpha)))
    }
}

// ============================================================================
// Finished
// ============================================================================

@Composable
private fun FinishedBlock(
    coverUrl: String,
    cover: ComposeCoverResponse,
    onShare: () -> Unit,
    onNewHeadline: () -> Unit,
    onTryAnother: () -> Unit,
    onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AsyncImage(
            model = coverUrl,
            contentDescription = "Composed cover",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(16.dp))
                .background(HemColors.CardCream),
        )
        cover.headline?.let { Text(it, style = HemType.serifDisplay.copy(fontSize = 20.sp, color = HemColors.Ink)) }
        cover.pull_quote?.let { Text("“$it”", style = HemType.body.copy(fontSize = 14.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted)) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinePill(text = "Share", modifier = Modifier.weight(1f), onClick = onShare)
            OutlinePill(text = "New headline", modifier = Modifier.weight(1f), onClick = onNewHeadline)
        }
        OutlinePill(text = "Try another", modifier = Modifier.fillMaxWidth(), onClick = onTryAnother)
        OutlinePill(text = "Done", modifier = Modifier.fillMaxWidth(), onClick = onDone)
    }
}

@Composable
private fun OutlinePill(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, HemColors.Ink.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = HemType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Ink)) }
}

// ============================================================================
// My covers picker
// ============================================================================

@Composable
private fun MyCoversPickerContent(templates: List<MagazineCover>, onPick: (MagazineCover) -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("My covers", style = HemType.serifDisplay.copy(fontSize = 22.sp, color = HemColors.Ink), modifier = Modifier.weight(1f))
            Text("Done", style = HemType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Bronze), modifier = Modifier.clickable(onClick = onClose))
        }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.heightIn(min = 200.dp, max = 600.dp),
        ) {
            items(templates) { cov ->
                val path = cov.image_path
                Column(Modifier.clickable { onPick(cov) }) {
                    if (path != null) {
                        val url = "https://ilrzqifdmjvooeyqvexd.supabase.co/storage/v1/object/public/magazine_covers/$path"
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(9f / 16f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, HemColors.Hairline, RoundedCornerShape(12.dp))
                                .background(HemColors.CardCream),
                        )
                    }
                    cov.headline?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = HemType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Ink), maxLines = 1)
                    }
                }
            }
        }
    }
}

// ============================================================================
// Masthead editor
// ============================================================================

@Composable
private fun MastheadEditorContent(current: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Row {
            Eyebrow("MASTHEAD"); Spacer(Modifier.weight(1f))
            Box(Modifier.size(28.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = HemColors.Ink)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("What's on the top of your cover?", style = HemType.serifDisplay.copy(fontSize = 20.sp, color = HemColors.Ink))
        Text("Default is FITRATER. Keep it short — 3-10 chars.", style = HemType.body.copy(fontSize = 13.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted))
        Spacer(Modifier.height(14.dp))
        BorderedText(
            value = text,
            onValueChange = { text = it.uppercase() },
            placeholder = "FITRATER",
            textStyle = HemType.serifDisplay.copy(fontSize = 20.sp, color = HemColors.Ink),
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinePill(text = "Reset", modifier = Modifier.weight(1f)) { onSave("FITRATER") }
            Box(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (text.trim().isNotEmpty()) HemColors.Ink else HemColors.Muted)
                    .clickable(enabled = text.trim().isNotEmpty()) { onSave(text.trim().take(20)) },
                contentAlignment = Alignment.Center,
            ) {
                Text("Use this", style = HemType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HemColors.OnInk))
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ============================================================================
// Edit cover
// ============================================================================

@Composable
private fun EditCoverContent(
    userPrompt: String, onUserPrompt: (String) -> Unit,
    customMasthead: String, onCustomMasthead: (String) -> Unit,
    customHeadline: String, onCustomHeadline: (String) -> Unit,
    customPullQuote: String, onCustomPullQuote: (String) -> Unit,
    coverLines: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    referenceMode: Boolean,
    composeCostCredits: Int,
    onCompose: () -> Unit,
    onClose: () -> Unit,
) {
    var newLine by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Eyebrow("EDIT"); Spacer(Modifier.height(6.dp))
                SerifDisplay("Fine-tune it.")
            }
            Box(Modifier.size(36.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = HemColors.Ink)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (referenceMode)
                "The cover's masthead + typography come from your reference. Everything else is up to you."
            else
                "Override what Hem would pick by default. Leave any field blank to let AI decide.",
            style = HemType.body.copy(fontSize = 14.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted),
        )
        Spacer(Modifier.height(14.dp))
        LabeledField("YOUR DIRECTION (OPT)") {
            BorderedText(
                value = userPrompt, onValueChange = onUserPrompt,
                placeholder = "e.g. make it feel like summer · add contrast · shift subject left",
                textStyle = HemType.body.copy(fontSize = 15.sp, color = HemColors.Ink), multiline = true,
            )
        }
        if (!referenceMode) {
            Spacer(Modifier.height(12.dp))
            LabeledField("MASTHEAD (OPT)") {
                BorderedText(
                    value = customMasthead, onValueChange = { onCustomMasthead(it.uppercase()) },
                    placeholder = "FITRATER",
                    textStyle = HemType.serifDisplay.copy(fontSize = 18.sp, color = HemColors.Ink),
                    capitalize = KeyboardCapitalization.Characters,
                )
            }
            Spacer(Modifier.height(12.dp))
            LabeledField("HEADLINE (OPT)") {
                BorderedText(
                    value = customHeadline, onValueChange = { onCustomHeadline(it.uppercase()) },
                    placeholder = "Auto — a study in bronze",
                    textStyle = HemType.serifDisplay.copy(fontSize = 18.sp, color = HemColors.Ink),
                    capitalize = KeyboardCapitalization.Characters, multiline = true,
                )
            }
            Spacer(Modifier.height(12.dp))
            LabeledField("PULL QUOTE (OPT)") {
                BorderedText(
                    value = customPullQuote, onValueChange = onCustomPullQuote,
                    placeholder = "Auto — an italic subline",
                    textStyle = HemType.body.copy(fontSize = 15.sp, fontStyle = FontStyle.Italic, color = HemColors.Ink),
                    multiline = true,
                )
            }
            Spacer(Modifier.height(14.dp))
            Eyebrow("COVER LINES (OPT)")
            Spacer(Modifier.height(6.dp))
            if (coverLines.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    coverLines.forEachIndexed { idx, line ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, HemColors.Hairline, RoundedCornerShape(10.dp))
                                .background(HemColors.CardCream)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(line, style = HemType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Ink), modifier = Modifier.weight(1f))
                            Box(Modifier.clickable { coverLines.removeAt(idx) }.padding(4.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = HemColors.Muted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (coverLines.size < 4) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        BorderedText(
                            value = newLine, onValueChange = { newLine = it.uppercase() },
                            placeholder = "THE STYLE REPORT",
                            textStyle = HemType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Ink),
                            capitalize = KeyboardCapitalization.Characters,
                        )
                    }
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (newLine.trim().isEmpty()) HemColors.Muted else HemColors.Ink)
                            .clickable(enabled = newLine.trim().isNotEmpty()) {
                                val c = newLine.trim()
                                if (c.isNotEmpty()) { coverLines.add(c); newLine = "" }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = HemColors.OnInk, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(HemColors.Ink)
                .clickable(onClick = onCompose),
            contentAlignment = Alignment.Center,
        ) {
            Text("Compose · $composeCostCredits credits", style = HemType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HemColors.OnInk))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Cancel",
            style = HemType.body.copy(fontSize = 14.sp, color = HemColors.Muted),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clickable(onClick = onClose),
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ============================================================================
// Shared bits
// ============================================================================

@Composable
private fun LabeledField(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(label)
        content()
    }
}

@Composable
private fun BorderedText(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    capitalize: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    multiline: Boolean = false,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, style = textStyle.copy(color = HemColors.Muted))
        BasicTextField(
            value = value, onValueChange = onValueChange,
            textStyle = textStyle,
            cursorBrush = SolidColor(HemColors.Bronze),
            singleLine = !multiline,
            keyboardOptions = KeyboardOptions(capitalization = capitalize),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BusyOverlay() {
    Box(
        Modifier.fillMaxSize().background(HemColors.Paper.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow("COMPOSING")
            Text("Printing your cover", style = HemType.serifDisplay.copy(fontSize = 22.sp, color = HemColors.Ink))
            listOf(
                "Reading your photo for face and crop…",
                "Studying the reference style…",
                "Rendering the base image…",
                "Setting the type on top…",
                "Usually 30-60 seconds.",
            ).forEach {
                Text(it, style = HemType.body.copy(fontSize = 13.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted), textAlign = TextAlign.Center)
            }
        }
    }
}

// ============================================================================
// Helpers
// ============================================================================

// Bitmap → ImageBitmap uses the built-in extension in androidx.compose.ui.graphics


private suspend fun readBytes(context: Context, uri: Uri): ByteArray? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()
}

private fun shareUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "$url\n\nMade on Fitrater · fitrater.ai")
    }
    context.startActivity(Intent.createChooser(intent, "Share cover"))
}
