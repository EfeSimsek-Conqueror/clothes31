package com.fitrater.app.ui.screens.onboarding

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitrater.app.data.model.BodyProfile
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.SerifDisplay
import com.fitrater.app.util.ToastBus
import kotlinx.coroutines.launch

/**
 * Port of iOS `BodyCalibrationView.swift`. One-time body calibration:
 * front photo required, side optional → analyze-body edge fn → persist +
 * hand back the profile.
 */
@Composable
fun BodyCalibrationScreen(onFinished: (BodyProfile?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var frontBytes by remember { mutableStateOf<ByteArray?>(null) }
    var sideBytes by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val frontPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { scope.launch { frontBytes = readBytes(context, it) } }
    }
    val sidePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { scope.launch { sideBytes = readBytes(context, it) } }
    }

    fun analyze() {
        val front = frontBytes ?: return
        scope.launch {
            busy = true; errorMsg = null
            try {
                val frontPath = Repo.uploadOutfitPhoto(front, "jpg")
                val frontSigned = Repo.signedOutfitUrl(frontPath) ?: throw IllegalStateException("Could not sign front photo.")
                var sidePath: String? = null
                var sideSigned: String? = null
                sideBytes?.let { s ->
                    sidePath = Repo.uploadOutfitPhoto(s, "jpg")
                    sideSigned = Repo.signedOutfitUrl(sidePath!!)
                }
                val resp = Repo.analyzeBody(frontSigned, sideSigned)
                val profile = resp.profile ?: throw IllegalStateException(resp.error ?: "Analysis failed")
                Repo.saveBodyProfile(profile, frontPath, sidePath)
                onFinished(profile)
            } catch (e: Throwable) {
                errorMsg = e.message ?: "Unknown error"
                ToastBus.post("Calibration failed: ${e.message}")
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
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(30.dp))
            Eyebrow("PERSONALIZE")
            Spacer(Modifier.height(6.dp))
            Text("Calibrate your fit.", style = HemType.serifDisplay.copy(fontSize = 34.sp, color = HemColors.Ink))
            Spacer(Modifier.height(8.dp))
            Text(
                "One photo. Every rating from now on is measured against you, not a mannequin.",
                style = HemType.body.copy(fontSize = 15.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted),
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CalibrationTile(
                    bytes = frontBytes, label = "FRONT", required = true,
                    onPick = { frontPicker.launch("image/*") },
                    onClear = { frontBytes = null },
                    modifier = Modifier.weight(1f),
                )
                CalibrationTile(
                    bytes = sideBytes, label = "SIDE (OPT)", required = false,
                    onPick = { sidePicker.launch("image/*") },
                    onClear = { sideBytes = null },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Wear something fitted so we can read your proportions. We never estimate weight, height, or BMI — only your silhouette and coloring.",
                style = HemType.body.copy(fontSize = 12.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted),
            )
            errorMsg?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = HemType.body.copy(fontSize = 13.sp, color = HemColors.Bronze))
            }
            Spacer(Modifier.height(30.dp))
            PrimaryFullWidth(
                text = if (busy) "Analyzing…" else "Analyze me",
                enabled = frontBytes != null && !busy,
                onClick = { analyze() },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Skip for now",
                style = HemType.body.copy(fontSize = 14.sp, color = HemColors.Muted),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 24.dp)
                    .clickable { onFinished(null) },
            )
        }
        if (busy) BusyOverlay()
    }
}

/**
 * Post-calibration reveal + persistent viewer. Port of
 * `BodyProfileRevealView.swift`.
 */
@Composable
fun BodyProfileRevealScreen(profile: BodyProfile, onDone: () -> Unit, onRecalibrate: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().background(HemColors.Paper)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(30.dp))
            Eyebrow("YOUR BASELINE")
            Spacer(Modifier.height(6.dp))
            Text("Locked in.", style = HemType.serifDisplay.copy(fontSize = 34.sp, color = HemColors.Ink))
            Spacer(Modifier.height(18.dp))
            ShapeCard(profile)
            Spacer(Modifier.height(14.dp))
            ColoringCard(profile)
            Spacer(Modifier.height(14.dp))
            ImpactCard()
            Spacer(Modifier.height(28.dp))
            PrimaryFullWidth(text = "Continue", enabled = true, onClick = onDone)
            if (onRecalibrate != null) {
                Text(
                    "Recalibrate",
                    style = HemType.body.copy(fontSize = 14.sp, color = HemColors.Muted),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 30.dp)
                        .clickable(onClick = onRecalibrate),
                )
            } else {
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

// ============================================================================
// Cards
// ============================================================================

@Composable
private fun ShapeCard(profile: BodyProfile) {
    RevealCard {
        Eyebrow("SHAPE")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ShapeBadge(shape = profile.body_shape ?: "rectangle", modifier = Modifier.size(width = 60.dp, height = 88.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(displayShapeName(profile.body_shape), style = HemType.serifDisplay.copy(fontSize = 20.sp, color = HemColors.Ink))
                val s = profile.shoulder_hip_ratio; val t = profile.torso_leg_ratio
                if (s != null && t != null) {
                    Text(
                        "Shoulder:hip ${"%.2f".format(s)}  ·  Torso:leg ${"%.2f".format(t)}",
                        style = HemType.body.copy(fontSize = 12.sp, color = HemColors.Muted),
                    )
                }
                val n = profile.notes
                if (!n.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(n, style = HemType.body.copy(fontSize = 14.sp, fontStyle = FontStyle.Italic, color = HemColors.Ink.copy(alpha = 0.75f)))
                }
            }
        }
    }
}

@Composable
private fun ColoringCard(profile: BodyProfile) {
    RevealCard {
        Eyebrow("COLORING")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0 until 6).forEach { i ->
                val hex = profile.palette_hex?.getOrNull(i) ?: "#D9CDBF"
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(parseHex(hex) ?: HemColors.CardCream)
                        .border(0.5.dp, HemColors.Hairline, RoundedCornerShape(6.dp)),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(displaySeasonName(profile.coloring_season), style = HemType.serifDisplay.copy(fontSize = 18.sp, color = HemColors.Ink))
        profile.skin_undertone?.let {
            Text(
                "${it.replaceFirstChar { c -> c.uppercase() }} undertone.",
                style = HemType.body.copy(fontSize = 14.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted),
            )
        }
    }
}

@Composable
private fun ImpactCard() {
    RevealCard {
        Eyebrow("WHAT THIS CHANGES")
        Spacer(Modifier.height(10.dp))
        listOf(
            "Every score is measured against your proportions.",
            "Try-on results use your actual scale.",
            "Studio favors your palette.",
            "Sunday Letter reads for your body type.",
        ).forEach { txt ->
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
                Box(
                    Modifier
                        .padding(top = 8.dp, end = 10.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(HemColors.Bronze),
                )
                Text(txt, style = HemType.body.copy(fontSize = 14.sp, color = HemColors.Ink))
            }
        }
    }
}

@Composable
private fun RevealCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(18.dp))
            .padding(16.dp),
        content = content,
    )
}

// ============================================================================
// Tile
// ============================================================================

@Composable
private fun CalibrationTile(
    bytes: ByteArray?,
    label: String,
    required: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .aspectRatio(0.66f)
            .clip(RoundedCornerShape(18.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(18.dp))
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
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(HemColors.Ink)
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("+", style = HemType.serifDisplay.copy(fontSize = 30.sp, color = HemColors.Ink))
                Text(
                    label,
                    style = HemType.smallLabel.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Bronze, letterSpacing = 2.sp),
                )
                if (!required) {
                    Text("Optional", style = HemType.body.copy(fontSize = 11.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted))
                }
            }
        }
    }
}

// ============================================================================
// Shape badge
// ============================================================================

@Composable
private fun ShapeBadge(shape: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val stroke = HemColors.Bronze
        val path = Path()
        when (shape) {
            "inverted_triangle" -> {
                path.moveTo(0f, 0f); path.lineTo(w, 0f); path.lineTo(w * 0.5f, h); path.close()
            }
            "triangle" -> {
                path.moveTo(w * 0.5f, 0f); path.lineTo(0f, h); path.lineTo(w, h); path.close()
            }
            "hourglass" -> {
                path.moveTo(0f, 0f); path.lineTo(w, 0f)
                path.lineTo(w * 0.35f, h * 0.5f); path.lineTo(w, h)
                path.lineTo(0f, h); path.lineTo(w * 0.65f, h * 0.5f); path.close()
            }
            "apple" -> path.addOval(Rect(Offset(0f, h * 0.1f), Size(w, h * 0.8f)))
            else -> path.addRect(Rect(Offset(w * 0.15f, 0f), Size(w * 0.7f, h)))
        }
        drawPath(path = path, color = stroke, style = Stroke(width = 1.5f))
    }
}

// ============================================================================
// Utilities
// ============================================================================

@Composable
private fun BusyOverlay() {
    Box(
        Modifier.fillMaxSize().background(HemColors.Paper.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow("READING")
            Text("Learning your frame", style = HemType.serifDisplay.copy(fontSize = 22.sp, color = HemColors.Ink))
            listOf(
                "Measuring your proportions…",
                "Sampling your natural palette…",
                "Locking in your baseline…",
                "This is one photo, one time.",
            ).forEach {
                Text(it, style = HemType.body.copy(fontSize = 13.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted))
            }
        }
    }
}

@Composable
private fun PrimaryFullWidth(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) HemColors.Ink else HemColors.Muted)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HemType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
    }
}

private fun displayShapeName(raw: String?): String = when (raw) {
    "inverted_triangle" -> "Inverted triangle."
    "triangle" -> "Triangle."
    "hourglass" -> "Hourglass."
    "rectangle" -> "Rectangle."
    "apple" -> "Round."
    else -> "Balanced."
}

private fun displaySeasonName(raw: String?): String = when (raw) {
    "spring" -> "Spring."
    "summer" -> "Summer."
    "autumn" -> "Autumn."
    "winter" -> "Winter."
    else -> "Neutral palette."
}

private fun parseHex(hex: String): Color? {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6) return null
    val v = clean.toLongOrNull(16) ?: return null
    val r = ((v shr 16) and 0xff).toInt() / 255f
    val g = ((v shr 8) and 0xff).toInt() / 255f
    val b = (v and 0xff).toInt() / 255f
    return Color(r, g, b, 1f)
}

private suspend fun readBytes(context: Context, uri: Uri): ByteArray? =
    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
