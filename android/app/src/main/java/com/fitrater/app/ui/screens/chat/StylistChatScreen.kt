package com.fitrater.app.ui.screens.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Flag
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import coil.compose.AsyncImage
import com.fitrater.app.data.service.StylistApi
import com.fitrater.app.data.service.StylistTurn
import com.fitrater.app.ui.components.ReportContentSheet
import com.fitrater.app.ui.components.ReportKind
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.Hairline
import com.fitrater.app.ui.theme.SerifDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"

/** Hem's opener — same copy as iOS so both platforms read identically. */
private const val OPENER =
    "Hey — I'm Hem. Send a fit photo or ask about tonight's look. I'll be honest and constructive."

/** The one chip we ship: it sends a real message through the same send path. */
private const val CHIP_LABEL = "🔁 3 alternatives"
private const val CHIP_PROMPT = "Give me 3 alternatives to that look, numbered."

/** Longest edge we upload — keeps the base64 payload sane over mobile data. */
private const val MAX_IMAGE_EDGE = 1280

private class ChatLine(
    val id: Long,
    val role: String,
    val text: String,
    val imageBytes: ByteArray? = null,
)

/**
 * Stylist chat — the moderate-tone replacement for the old brutal "Roast" flow.
 * Transcript + photo attachment + composer, backed by the `stylist-chat` edge
 * function. Errors surface as a visible row with Retry, never as a fake reply.
 */
@Composable
fun StylistChatScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var nextId by remember { mutableStateOf(1L) }
    var lines by remember { mutableStateOf(listOf(ChatLine(0L, ROLE_ASSISTANT, OPENER))) }
    var input by remember { mutableStateOf("") }
    var pickedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    // Image that belongs to the turn currently in flight (or the failed one we can retry).
    var inFlightImage by remember { mutableStateOf<ByteArray?>(null) }
    // Which of Hem's replies the user is reporting, by transcript line id.
    var reportingLineId by remember { mutableStateOf<Long?>(null) }

    /** Fires the request for whatever is already in the transcript. Used by send + retry. */
    fun ask() {
        if (busy) return
        busy = true
        errorText = null
        scope.launch {
            val history = lines.map { StylistTurn(role = it.role, content = it.text) }
            runCatching { StylistApi.send(history, inFlightImage) }
                .onSuccess { reply ->
                    lines = lines + ChatLine(nextId, ROLE_ASSISTANT, reply)
                    nextId += 1
                    inFlightImage = null
                }
                .onFailure {
                    Log.e("StylistChat", "send failed", it)
                    errorText = it.message ?: "Couldn't reach Hem."
                }
            busy = false
        }
    }

    /** Appends a user turn (text and/or photo) and asks Hem. */
    fun send(text: String, image: ByteArray?) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && image == null) return
        if (busy) return
        lines = lines + ChatLine(nextId, ROLE_USER, trimmed, image)
        nextId += 1
        input = ""
        pickedBytes = null
        inFlightImage = image
        ask()
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { loadDownscaledJpeg(context, uri) } }
                    .onSuccess { pickedBytes = it }
                    .onFailure {
                        Log.e("StylistChat", "photo read failed", it)
                        errorText = "Couldn't read that photo."
                    }
            }
        }
    }

    // Keep the newest line in view as the transcript grows.
    LaunchedEffect(lines.size, busy) {
        val target = lines.size + if (busy) 1 else 0
        if (target > 0) listState.animateScrollToItem(target - 1)
    }

    // No imePadding() here: MainActivity applies it once for the whole app (the
    // edge-to-edge window no longer resizes itself). Applying it again would
    // shrink the content band by twice the keyboard height and push the
    // composer — send button included — behind the keyboard.
    Box(
        Modifier
            .fillMaxSize()
            .background(HemColors.Paper),
    ) {
        Column(Modifier.fillMaxSize()) {
            // ---- Header ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = HemSpace.gutter,
                        end = HemSpace.gutter,
                        top = HemSpace.md,
                        bottom = HemSpace.sm,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "STYLIST",
                        style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 2.sp),
                    )
                    Spacer(Modifier.height(HemSpace.xs))
                    SerifDisplay("Chat with Hem")
                }
                Box(
                    Modifier.size(36.dp).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Close, contentDescription = "Close") }
            }
            Hairline()

            // ---- Transcript ----
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = HemSpace.md,
                    vertical = HemSpace.md,
                ),
                verticalArrangement = Arrangement.spacedBy(HemSpace.sm),
            ) {
                items(lines) { line ->
                    // The opener (id 0) is hardcoded copy, not model output.
                    val reportable = line.role == ROLE_ASSISTANT && line.id != 0L
                    MessageBubble(
                        line = line,
                        onReport = if (reportable) ({ reportingLineId = line.id }) else null,
                    )
                }
                if (busy) {
                    item("typing") { TypingIndicator() }
                }
                val err = errorText
                if (err != null) {
                    item("error") { ErrorRow(message = err, onRetry = { ask() }) }
                }
                // One working chip — only after Hem has actually answered something.
                if (!busy && errorText == null && lines.size > 1 && lines.last().role == ROLE_ASSISTANT) {
                    item("chip") {
                        ChipRow(label = CHIP_LABEL, onClick = { send(CHIP_PROMPT, null) })
                    }
                }
            }
            Hairline()

            // ---- Composer ----
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                val staged = pickedBytes
                if (staged != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(
                            start = HemSpace.md,
                            end = HemSpace.md,
                            top = HemSpace.sm,
                        ),
                    ) {
                        Box {
                            AsyncImage(
                                model = staged,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(HemColors.CardCream),
                            )
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(HemColors.Ink)
                                    .clickable { pickedBytes = null },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = HemSpace.md, vertical = HemSpace.sm),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .clickable {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.PhotoLibrary,
                            contentDescription = "Attach a photo",
                            tint = HemColors.Ink,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(HemSpace.xs))
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(HemColors.CardCream)
                            .border(1.dp, HemColors.Hairline, RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    ) {
                        if (input.isEmpty()) {
                            Text(
                                "Ask Hem — a fit, an occasion, a swap…",
                                style = HemType.bodyMuted.copy(fontSize = 15.sp),
                            )
                        }
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            textStyle = HemType.body.copy(fontSize = 15.sp, color = HemColors.Ink),
                            cursorBrush = SolidColor(HemColors.Bronze),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
                        )
                    }
                    Spacer(Modifier.width(HemSpace.xs))
                    val canSend = (input.trim().isNotEmpty() || pickedBytes != null) && !busy
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (canSend) HemColors.Ink else HemColors.Muted)
                            .clickable(enabled = canSend) { send(input, pickedBytes) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // Chat turns aren't persisted, so the transcript line id is the only
        // handle we have on a specific reply.
        reportingLineId?.let { id ->
            ReportContentSheet(
                contentKind = ReportKind.CHAT_MESSAGE,
                contentId = id.toString(),
                onDismiss = { reportingLineId = null },
            )
        }
    }
}

@Composable
private fun MessageBubble(line: ChatLine, onReport: (() -> Unit)? = null) {
    val isUser = line.role == ROLE_USER
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier.fillMaxWidth(0.86f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            val bytes = line.imageBytes
            if (bytes != null) {
                AsyncImage(
                    model = bytes,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .sizeIn(maxWidth = 200.dp, maxHeight = 240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(HemColors.CardCream),
                )
                if (line.text.isNotEmpty()) Spacer(Modifier.height(HemSpace.xs))
            }
            if (line.text.isNotEmpty()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(
                        Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isUser) HemColors.Ink else HemColors.CardCream)
                            .then(
                                if (isUser) Modifier
                                else Modifier.border(1.dp, HemColors.Hairline, RoundedCornerShape(14.dp)),
                            )
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        Text(
                            line.text,
                            style = HemType.body.copy(
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                                color = if (isUser) Color.White else HemColors.Ink,
                            ),
                        )
                    }
                    // Hem's replies are AI output — report lives on the bubble itself.
                    if (onReport != null) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(onClick = onReport),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Flag,
                                contentDescription = "Report this reply",
                                tint = HemColors.Muted,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(HemColors.CardCream)
                .border(1.dp, HemColors.Hairline, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text("…", style = HemType.bodyMuted.copy(fontSize = 16.sp))
        }
    }
}

/** Visible failure — no fabricated reply, and one tap to try the same turn again. */
@Composable
private fun ErrorRow(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            message,
            style = HemType.body.copy(fontSize = 13.sp, color = HemColors.Bronze),
        )
        Spacer(Modifier.height(HemSpace.xs))
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, HemColors.Ink.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                "Retry",
                style = HemType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

@Composable
private fun ChipRow(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(HemColors.CardCream)
                .border(1.dp, HemColors.Hairline, RoundedCornerShape(999.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                label,
                style = HemType.smallLabel.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HemColors.Ink,
                    letterSpacing = 1.2.sp,
                ),
            )
        }
    }
}

/** Reads a picked image and re-encodes it as a bounded JPEG for the base64 payload. */
private fun loadDownscaledJpeg(context: Context, uri: Uri): ByteArray {
    val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Could not read photo")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_EDGE) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size, opts) ?: return raw
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return out.toByteArray()
}
