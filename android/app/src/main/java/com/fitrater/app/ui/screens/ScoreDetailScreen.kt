package com.fitrater.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.fitrater.app.data.model.Annotation
import com.fitrater.app.data.model.Outfit
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.ui.components.MissingPolaroidIllustration
import com.fitrater.app.ui.components.PhotoTile
import com.fitrater.app.ui.components.TwoUpHairlineRow
import com.fitrater.app.ui.components.shimmer
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.PrimaryButton
import com.fitrater.app.ui.theme.PullQuote
import com.fitrater.app.ui.theme.ScoreChip
import java.util.Locale
import kotlin.math.abs

@Composable
fun ScoreDetailScreen(outfitId: String, onScoreALook: () -> Unit, onClose: () -> Unit = {}) {
    val context = LocalContext.current
    var outfit by remember { mutableStateOf<Outfit?>(null) }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var average by remember { mutableStateOf<Double?>(null) }
    var loaded by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current

    LaunchedEffect(outfitId) {
        outfit = runCatching { Repo.outfitById(outfitId) }.getOrNull()
        val path = outfit?.photo_path
        if (path != null) {
            photoUrl = runCatching { Repo.signedOutfitUrl(path) }.getOrNull()
        }
        average = runCatching { Repo.averageScore() }.getOrNull()
        loaded = true
        // A single tick when the score reveal arrives — feels tactile.
        if (outfit?.score != null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val heroHeight = screenHeight * 0.65f

    Column(
        Modifier
            .fillMaxSize()
            .background(HemColors.Paper)
            .verticalScroll(rememberScrollState()),
    ) {
        val o = outfit
        if (!loaded) {
            // Skeleton: hero placeholder + row bars.
            Column(Modifier.padding(HemSpace.gutter)) {
                Spacer(Modifier.height(HemSpace.lg))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
                        .clip(RoundedCornerShape(20.dp))
                        .background(HemColors.CardCream)
                        .shimmer(),
                )
                Spacer(Modifier.height(HemSpace.lg))
                repeat(4) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(HemColors.CardCream)
                            .shimmer(),
                    )
                    Spacer(Modifier.height(HemSpace.md))
                }
            }
        } else if (o == null) {
            Column(
                Modifier.padding(HemSpace.gutter).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(HemSpace.lg))
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.size(28.dp).clickable(onClick = onClose)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = HemColors.Ink)
                    }
                }
                Spacer(Modifier.height(HemSpace.xl))
                MissingPolaroidIllustration()
                Spacer(Modifier.height(HemSpace.md))
                Text("Outfit not found.", style = HemType.serifSection)
                Spacer(Modifier.height(HemSpace.xs))
                Text("It may have been deleted.", style = HemType.bodyMuted)
            }
        } else {
            // Hero photo with overlaid annotations — subtle fade-in on first render.
            var heroVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { heroVisible = true }
            val heroAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (heroVisible) 1f else 0f,
                animationSpec = tween(350),
                label = "heroReveal",
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
                        .clip(RoundedCornerShape(20.dp))
                        .then(Modifier.graphicsLayer(alpha = heroAlpha)),
                ) {
                    if (photoUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(photoUrl).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        PhotoTile(modifier = Modifier.fillMaxSize(), tint = Color(0xFF7A6A55))
                    }

                    // Annotations overlay
                    val annotations = o?.annotations
                    if (!annotations.isNullOrEmpty()) {
                        AnnotationsOverlay(
                            annotations = annotations,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // Close X top-left
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(HemSpace.sm)
                            .size(32.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(HemColors.Paper.copy(alpha = 0.9f))
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = HemColors.Ink,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    // Score chip top-right
                    val score = o?.score
                    if (score != null) {
                        ScoreChip(
                            score = score,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(HemSpace.sm),
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = HemSpace.gutter)) {
                val avg = average
                val score = o?.score
                val delta = if (avg != null && score != null) score - avg else null
                val deltaText = when {
                    delta == null -> null
                    delta > 0 -> String.format(Locale.US, "%.1f above your average", delta)
                    delta < 0 -> String.format(Locale.US, "%.1f below your average", abs(delta))
                    else -> "on your average"
                }
                val subline = listOfNotNull(deltaText, o?.occasion?.uppercase()).joinToString(" · ")
                if (subline.isNotBlank()) {
                    Text(
                        subline,
                        style = HemType.bodyMuted.copy(fontSize = 12.sp, letterSpacing = 1.sp),
                    )
                }
                val q = o?.hem_comment ?: o?.notes
                if (!q.isNullOrBlank()) {
                    Spacer(Modifier.height(HemSpace.md))
                    PullQuote(q)
                }

                // BREAKDOWN — subscores grid
                val sub = o?.subscores
                val subEntries = listOfNotNull(
                    sub?.color?.let { "Color" to it },
                    sub?.fit?.let { "Fit" to it },
                    sub?.style_match?.let { "Style match" to it },
                    sub?.seasonal?.let { "Seasonal" to it },
                )
                if (subEntries.isNotEmpty()) {
                    Spacer(Modifier.height(HemSpace.lg))
                    Eyebrow("BREAKDOWN")
                    Spacer(Modifier.height(HemSpace.sm))
                    Column(Modifier.fillMaxWidth()) {
                        subEntries.forEach { (label, value) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, style = HemType.body, modifier = Modifier.weight(1f))
                                Text(
                                    String.format(Locale.US, "%.1f / 10", value),
                                    style = HemType.body.copy(fontSize = 14.sp),
                                )
                            }
                            androidx.compose.material3.HorizontalDivider(
                                color = HemColors.Hairline,
                                thickness = 0.5.dp,
                            )
                        }
                    }
                }

                // SWAPS — bulleted list
                val swaps = o?.swaps.orEmpty()
                if (swaps.isNotEmpty()) {
                    Spacer(Modifier.height(HemSpace.lg))
                    Eyebrow("SWAPS")
                    Spacer(Modifier.height(HemSpace.sm))
                    Column(Modifier.fillMaxWidth()) {
                        swaps.forEach { s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text("• ", style = HemType.body)
                                Text(s, style = HemType.body)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(HemSpace.lg))
                PrimaryButton(label = "✦ Score a look", onClick = onScoreALook)
                Spacer(Modifier.height(HemSpace.lg))
                TwoUpHairlineRow(
                    left = {
                        Column {
                            Eyebrow("THE MIRROR")
                            Spacer(Modifier.height(6.dp))
                            Text("Try it on →", style = HemType.body)
                        }
                    },
                    right = {
                        Column {
                            Eyebrow("ASK HEM")
                            Spacer(Modifier.height(6.dp))
                            Text("Talk it out →", style = HemType.body)
                        }
                    },
                )
                Spacer(Modifier.height(HemSpace.xl))
            }
        }
    }
}

private fun chipColorFor(score: Double): Color = when {
    score >= 8.0 -> Color(0xFF3E8C5E)
    score >= 6.0 -> Color(0xFFC99A5B)
    else -> Color(0xFFB23A2A)
}

@Composable
private fun AnnotationsOverlay(
    annotations: List<Annotation>,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var openedIndex by remember { mutableStateOf<Int?>(null) }
    BoxWithConstraints(modifier) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val minGapDp: Dp = 40.dp
        val minGapPx = with(density) { minGapDp.toPx() }

        // Sort by y for stable vertical layout, split into left/right by x
        data class Placed(
            val ann: Annotation,
            val anchorX: Float,
            val anchorY: Float,
            val leftSide: Boolean,
            var labelY: Float,
        )

        val placed = annotations.map { a ->
            val ax = (a.x_pct / 100.0).toFloat().coerceIn(0f, 1f) * wPx
            val ay = (a.y_pct / 100.0).toFloat().coerceIn(0f, 1f) * hPx
            Placed(
                ann = a,
                anchorX = ax,
                anchorY = ay,
                leftSide = a.x_pct < 50.0,
                labelY = ay,
            )
        }

        // Resolve overlaps within each side by pushing labels down (then up if past bottom)
        listOf(true, false).forEach { side ->
            val group = placed.filter { it.leftSide == side }.sortedBy { it.labelY }
            var prev = -Float.MAX_VALUE
            group.forEach { p ->
                if (p.labelY < prev + minGapPx) p.labelY = prev + minGapPx
                prev = p.labelY
            }
            // Clamp within canvas
            val padTop = with(density) { 12.dp.toPx() }
            val padBot = with(density) { 12.dp.toPx() }
            group.forEach { p ->
                if (p.labelY < padTop) p.labelY = padTop
                if (p.labelY > hPx - padBot) p.labelY = hPx - padBot
            }
        }

        // Draw connector lines + anchor dots on a Canvas
        Canvas(Modifier.fillMaxSize()) {
            placed.forEach { p ->
                val labelX = if (p.leftSide) with(density) { 6.dp.toPx() } else size.width - with(density) { 6.dp.toPx() }
                val lineColor = Color.White.copy(alpha = 0.85f)
                val strokeW = with(density) { 1.2.dp.toPx() }
                drawLine(
                    color = Color.Black.copy(alpha = 0.35f),
                    start = Offset(p.anchorX, p.anchorY),
                    end = Offset(labelX, p.labelY),
                    strokeWidth = strokeW + 1.4f,
                )
                drawLine(
                    color = lineColor,
                    start = Offset(p.anchorX, p.anchorY),
                    end = Offset(labelX, p.labelY),
                    strokeWidth = strokeW,
                )
                // Anchor dot
                val dotR = with(density) { 4.dp.toPx() }
                drawCircle(
                    color = Color.Black.copy(alpha = 0.35f),
                    radius = dotR + 1.2f,
                    center = Offset(p.anchorX, p.anchorY),
                )
                drawCircle(
                    color = Color.White,
                    radius = dotR,
                    center = Offset(p.anchorX, p.anchorY),
                )
                drawCircle(
                    color = HemColors.Ink,
                    radius = dotR,
                    center = Offset(p.anchorX, p.anchorY),
                    style = Stroke(width = with(density) { 1.5.dp.toPx() }),
                )
            }
        }

        // Draw label chips as composables with .offset
        placed.forEachIndexed { idx, p ->
            val bg = chipColorFor(p.ann.score)
            val scoreInt = p.ann.score.toInt()
            val chipText = "${p.ann.label} $scoreInt"
            LabelChip(
                text = chipText,
                bg = bg,
                leftSide = p.leftSide,
                yPx = p.labelY,
                onClick = { openedIndex = if (openedIndex == idx) null else idx },
            )
        }
        val open = openedIndex
        if (open != null && open in placed.indices) {
            val p = placed[open]
            val note = p.ann.note
            val label = p.ann.label
            val scoreInt = p.ann.score.toInt()
            NotePopover(
                title = "$label · $scoreInt",
                body = note.ifBlank { "No specific fix — this one's landing." },
                leftSide = p.leftSide,
                yPx = p.labelY,
                onDismiss = { openedIndex = null },
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.NotePopover(
    title: String,
    body: String,
    leftSide: Boolean,
    yPx: Float,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val yDp = with(density) { yPx.toDp() }
    val topOffset = (yDp + 20.dp).coerceAtLeast(0.dp)
    Box(
        Modifier
            .align(if (leftSide) Alignment.TopStart else Alignment.TopEnd)
            .padding(
                start = if (leftSide) 6.dp else 0.dp,
                end = if (leftSide) 0.dp else 6.dp,
                top = topOffset,
            )
            .clip(RoundedCornerShape(10.dp))
            .background(HemColors.Paper)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(onClick = onDismiss),
    ) {
        androidx.compose.foundation.layout.Column {
            Text(
                title.uppercase(),
                style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 1.5.sp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                body,
                style = HemType.body.copy(fontSize = 12.sp),
                modifier = Modifier.width(200.dp),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.LabelChip(
    text: String,
    bg: Color,
    leftSide: Boolean,
    yPx: Float,
    onClick: () -> Unit = {},
) {
    val density = LocalDensity.current
    val yDp = with(density) { yPx.toDp() }
    val chipHeight = 26.dp
    val topOffset = yDp - (chipHeight / 2)
    Box(
        Modifier
            .align(if (leftSide) Alignment.TopStart else Alignment.TopEnd)
            .padding(
                start = if (leftSide) 6.dp else 0.dp,
                end = if (leftSide) 0.dp else 6.dp,
                top = topOffset.coerceAtLeast(0.dp),
            )
            .height(chipHeight)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = HemType.body.copy(
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}
