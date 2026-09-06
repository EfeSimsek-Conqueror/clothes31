package com.fitrater.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Flag
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
import com.fitrater.app.data.model.Axis
import com.fitrater.app.data.model.DressCode
import com.fitrater.app.data.model.FitMap
import com.fitrater.app.data.model.Lever
import com.fitrater.app.data.model.Outfit
import com.fitrater.app.data.model.Piece
import com.fitrater.app.data.model.PresenceCheck
import com.fitrater.app.data.model.ScoreBreakdown
import com.fitrater.app.data.model.ScoreIntake
import com.fitrater.app.data.model.SwapV2
import com.fitrater.app.data.model.displayScore
import com.fitrater.app.scoring.AxisKey
import com.fitrater.app.scoring.PreviewIntake
import com.fitrater.app.scoring.RubricTables
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.ui.components.MissingPolaroidIllustration
import com.fitrater.app.ui.components.PhotoTile
import com.fitrater.app.ui.components.ReportContentSheet
import com.fitrater.app.ui.components.ReportKind
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
import kotlin.math.roundToInt

@Composable
fun ScoreDetailScreen(outfitId: String, onScoreALook: () -> Unit, onClose: () -> Unit = {}) {
    val context = LocalContext.current
    var outfit by remember { mutableStateOf<Outfit?>(null) }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    // Aug 2026: swipeable side view for Studio outfits (outfit_studio +
    // outfit_studio_side sibling).
    var sideUrl by remember { mutableStateOf<String?>(null) }
    var average by remember { mutableStateOf<Double?>(null) }
    var fitMap by remember { mutableStateOf<FitMap?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current

    LaunchedEffect(outfitId) {
        outfit = runCatching { Repo.outfitById(outfitId) }.getOrNull()
        val path = outfit?.photo_path
        if (path != null) {
            photoUrl = runCatching { Repo.signedOutfitUrl(path) }.getOrNull()
        }
        // If this is a Studio outfit, try to pick up the linked side view.
        if (outfit?.kind == "outfit_studio") {
            sideUrl = runCatching {
                val side = Repo.linkedOutfit(linkedTo = outfitId, kind = "outfit_studio_side")
                side?.photo_path?.let { Repo.signedOutfitUrl(it) }
            }.getOrNull()
        }
        // The average is taken within the row's own scoring version: v4 caps only
        // ever subtract, so a v4 look measured against a v3 history would read
        // "below your average" for a look that is fine.
        average = runCatching { Repo.averageScore(outfit?.scoring_version) }.getOrNull()
        fitMap = runCatching { Repo.loadFitMap(outfitId) }.getOrNull()
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
                    // Studio outfits carry a linked SIDE view — swipe between
                    // FRONT and SIDE. Without a sibling this stays a single image.
                    val hasSide = sideUrl != null
                    val heroPager = rememberPagerState(initialPage = 0) { if (hasSide) 2 else 1 }
                    if (hasSide) {
                        HorizontalPager(state = heroPager, modifier = Modifier.fillMaxSize()) { page ->
                            val url = if (page == 0) photoUrl else sideUrl
                            if (url != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(url).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    filterQuality = FilterQuality.High,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                PhotoTile(modifier = Modifier.fillMaxSize(), tint = Color(0xFF7A6A55))
                            }
                        }
                    } else if (photoUrl != null) {
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

                    // Annotations only make sense over the FRONT photo — never
                    // over the side view (matches iOS, which hides them entirely).
                    val onFrontPage = !hasSide || heroPager.currentPage == 0

                    // v1 subscore annotations overlay
                    val annotations = o?.annotations
                    if (onFrontPage && !annotations.isNullOrEmpty()) {
                        AnnotationsOverlay(
                            annotations = annotations,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    // v3 markup annotations overlay (arrows/lines/focus/swap)
                    val outfitIdVal = outfitId
                    var markups by remember(outfitIdVal) {
                        mutableStateOf<List<com.fitrater.app.data.model.MarkupAnnotation>>(emptyList())
                    }
                    LaunchedEffect(outfitIdVal) {
                        markups = runCatching { com.fitrater.app.data.repo.Repo.loadAnnotations(outfitIdVal) }.getOrDefault(emptyList())
                    }
                    if (onFrontPage && markups.isNotEmpty()) {
                        MarkupOverlay(annotations = markups, modifier = Modifier.fillMaxSize())
                    }

                    // FRONT / SIDE dots — only when a sibling side view exists.
                    if (hasSide) {
                        Row(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = HemSpace.sm)
                                .clip(RoundedCornerShape(999.dp))
                                .background(HemColors.Paper.copy(alpha = 0.9f))
                                .padding(horizontal = HemSpace.sm, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(HemSpace.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HeroPagerDot(active = heroPager.currentPage == 0, label = "FRONT")
                            HeroPagerDot(active = heroPager.currentPage == 1, label = "SIDE")
                        }
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
                    val score = o?.displayScore
                    if (score != null) {
                        ScoreChip(
                            score = score,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(HemSpace.sm),
                        )
                    }

                    // Report the read itself — Hem's score and comment are AI output.
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(HemSpace.sm)
                            .size(32.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(HemColors.Paper.copy(alpha = 0.9f))
                            .clickable { reporting = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Flag,
                            contentDescription = "Report this look",
                            tint = HemColors.Ink,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            if (reporting) {
                ReportContentSheet(
                    contentKind = ReportKind.OUTFIT,
                    contentId = outfitId,
                    onDismiss = { reporting = false },
                )
            }

            Column(Modifier.padding(horizontal = HemSpace.gutter)) {
                val avg = average
                val score = o?.displayScore
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

                val axes = o?.axes.orEmpty()

                // WHAT I GRADED YOU AGAINST — the brief, read back. Rebuilt from
                // the persisted `intake` rather than stored as prose, so it can
                // never drift from the rubric the weights were actually built on.
                val intake = o?.intake
                if (intake != null && axes.isNotEmpty()) {
                    val preview = intake.toPreviewIntake(o?.back_photo_path != null)
                    Spacer(Modifier.height(HemSpace.lg))
                    Eyebrow("WHAT I GRADED YOU AGAINST")
                    Spacer(Modifier.height(HemSpace.sm))
                    Text(
                        RubricTables.rubricLabel(preview),
                        style = HemType.serifSection.copy(fontSize = 19.sp),
                    )
                    Spacer(Modifier.height(HemSpace.xxs))
                    Text(RubricTables.briefLine(preview), style = HemType.bodyMuted)
                    val deltas = o?.score_breakdown?.weight_deltas.orEmpty()
                    if (deltas.isNotEmpty()) {
                        Spacer(Modifier.height(HemSpace.sm))
                        deltas.take(6).forEach { d ->
                            val axisLabel = AxisKey.from(d.axis)?.label ?: d.axis.orEmpty()
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(
                                    d.reason.orEmpty(),
                                    style = HemType.body.copy(fontSize = 13.sp),
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(HemSpace.xs))
                                Text(
                                    "$axisLabel ${signedPercent(d.delta)}",
                                    style = HemType.bodyMuted.copy(fontSize = 12.sp),
                                )
                            }
                        }
                    }
                }

                // BREAKDOWN
                //
                // v4 rows carry their own axis list — nine of them, weighted by
                // the brief. v3 rows have the four fixed subscores and nothing
                // else, so they keep the grid they have always had.
                if (axes.isNotEmpty()) {
                    Spacer(Modifier.height(HemSpace.lg))
                    Eyebrow("BREAKDOWN")
                    Spacer(Modifier.height(HemSpace.sm))
                    Column(Modifier.fillMaxWidth()) {
                        axes.forEach { axis ->
                            AxisRow(axis)
                            androidx.compose.material3.HorizontalDivider(
                                color = HemColors.Hairline,
                                thickness = 0.5.dp,
                            )
                        }
                    }
                } else {
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
                }

                // THE DRESS CODE — only where the brief actually carries one.
                val dressCode = o?.dress_code
                if (dressCode != null && dressCode.render) {
                    Spacer(Modifier.height(HemSpace.lg))
                    DressCodeSection(dressCode)
                }

                // WHY THIS NUMBER — the arithmetic, in fire order.
                val breakdown = o?.score_breakdown
                if (breakdown != null && axes.isNotEmpty()) {
                    Spacer(Modifier.height(HemSpace.lg))
                    WhyThisNumberSection(breakdown, o?.presence_check)
                }

                // THE ONE THING — the highest-value single change.
                val lever = o?.lever
                if (lever != null && lever.label != null) {
                    Spacer(Modifier.height(HemSpace.lg))
                    LeverCard(lever)
                }

                // SWAPS — v2 carries the reasoning and the effort; v3 was a
                // bare string, and rows written before v4 still only have that.
                val swapsV2 = o?.score_breakdown?.swaps_v2.orEmpty()
                val swaps = o?.swaps.orEmpty()
                if (swapsV2.isNotEmpty()) {
                    Spacer(Modifier.height(HemSpace.lg))
                    Eyebrow("SWAPS")
                    Spacer(Modifier.height(HemSpace.sm))
                    Column(Modifier.fillMaxWidth()) {
                        swapsV2.forEach { sw -> SwapRow(sw) }
                    }
                } else if (swaps.isNotEmpty()) {
                    Spacer(Modifier.height(HemSpace.lg))
                    Eyebrow("SWAPS")
                    Spacer(Modifier.height(HemSpace.sm))
                    Column(Modifier.fillMaxWidth()) {
                        swaps.forEach { sw ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text("• ", style = HemType.body)
                                Text(sw, style = HemType.body)
                            }
                        }
                    }
                }

                // THE PIECES — what each garment did for the look.
                val pieces = o?.pieces.orEmpty()
                if (pieces.isNotEmpty()) {
                    Spacer(Modifier.height(HemSpace.lg))
                    Eyebrow("THE PIECES")
                    Spacer(Modifier.height(HemSpace.sm))
                    Column(Modifier.fillMaxWidth()) {
                        pieces.forEach { piece -> PieceRow(piece) }
                    }
                }

                // FIT TENSION — the heatmap the score path now persists.
                val hotspots = fitMap?.hotspots.orEmpty()
                if (hotspots.isNotEmpty()) {
                    Spacer(Modifier.height(HemSpace.lg))
                    Eyebrow("FIT TENSION")
                    Spacer(Modifier.height(HemSpace.sm))
                    Column(Modifier.fillMaxWidth()) {
                        hotspots.forEach { h ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            if (h.severity >= 0) HemColors.Bronze else HemColors.Muted,
                                        ),
                                )
                                Spacer(Modifier.width(HemSpace.xs))
                                Text(h.label, style = HemType.body, modifier = Modifier.weight(1f))
                                Text(
                                    if (h.severity >= 0) "pulling" else "pooling",
                                    style = HemType.bodyMuted.copy(fontSize = 12.sp),
                                )
                            }
                        }
                    }
                }

                // WHAT I COULDN'T SEE — every read the photograph did not support,
                // and what it cost. Last, because it is a disclosure and not a note.
                val caveats = o?.caveats.orEmpty()
                if (caveats.isNotEmpty()) {
                    Spacer(Modifier.height(HemSpace.lg))
                    Eyebrow("WHAT I COULDN'T SEE")
                    Spacer(Modifier.height(HemSpace.sm))
                    Column(Modifier.fillMaxWidth()) {
                        caveats.forEach { c ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(c.copy.orEmpty(), style = HemType.body.copy(fontSize = 14.sp))
                                val cost = c.cost
                                if (!cost.isNullOrBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(cost, style = HemType.bodyMuted.copy(fontSize = 12.sp))
                                }
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

/** One dot + label of the FRONT / SIDE hero pager indicator. */
@Composable
private fun HeroPagerDot(active: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (active) HemColors.Ink else HemColors.Hairline),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = HemType.bodyMuted.copy(
                fontSize = 9.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (active) HemColors.Ink else HemColors.Muted,
            ),
        )
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
private fun MarkupOverlay(
    annotations: List<com.fitrater.app.data.model.MarkupAnnotation>,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val stroke = HemColors.Bronze
        annotations.forEach { a ->
            val coords = a.coords ?: return@forEach
            when (a.type) {
                "arrow" -> {
                    val f = coords.from; val t = coords.to
                    if (f != null && f.size == 2 && t != null && t.size == 2) {
                        val p1 = androidx.compose.ui.geometry.Offset(w * f[0].toFloat(), h * f[1].toFloat())
                        val p2 = androidx.compose.ui.geometry.Offset(w * t[0].toFloat(), h * t[1].toFloat())
                        drawLine(color = stroke, start = p1, end = p2, strokeWidth = 1.4f * density)
                        val angle = kotlin.math.atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())
                        val head = 10f * density
                        val leftAngle = angle - Math.PI / 6
                        val rightAngle = angle + Math.PI / 6
                        drawLine(
                            color = stroke,
                            start = p2,
                            end = androidx.compose.ui.geometry.Offset(
                                (p2.x - kotlin.math.cos(leftAngle) * head).toFloat(),
                                (p2.y - kotlin.math.sin(leftAngle) * head).toFloat(),
                            ),
                            strokeWidth = 1.4f * density,
                        )
                        drawLine(
                            color = stroke,
                            start = p2,
                            end = androidx.compose.ui.geometry.Offset(
                                (p2.x - kotlin.math.cos(rightAngle) * head).toFloat(),
                                (p2.y - kotlin.math.sin(rightAngle) * head).toFloat(),
                            ),
                            strokeWidth = 1.4f * density,
                        )
                    }
                }
                "line" -> {
                    val f = coords.from; val t = coords.to
                    if (f != null && f.size == 2 && t != null && t.size == 2) {
                        val p1 = androidx.compose.ui.geometry.Offset(w * f[0].toFloat(), h * f[1].toFloat())
                        val p2 = androidx.compose.ui.geometry.Offset(w * t[0].toFloat(), h * t[1].toFloat())
                        drawLine(
                            color = stroke, start = p1, end = p2,
                            strokeWidth = 1.2f * density,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f * density, 4f * density)),
                        )
                    }
                }
                "focus" -> {
                    val at = coords.at
                    if (at != null && at.size == 2) {
                        val cx = w * at[0].toFloat(); val cy = h * at[1].toFloat()
                        val r = w * (coords.radius?.toFloat() ?: 0.08f)
                        drawCircle(
                            color = stroke,
                            radius = r,
                            center = androidx.compose.ui.geometry.Offset(cx, cy),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4f * density),
                        )
                    }
                }
                "swap" -> {
                    val at = coords.at
                    if (at != null && at.size == 2) {
                        val cx = w * at[0].toFloat(); val cy = h * at[1].toFloat()
                        val s = 7f * density
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(cx, cy - s); lineTo(cx + s, cy)
                            lineTo(cx, cy + s); lineTo(cx - s, cy); close()
                        }
                        drawPath(path = path, color = stroke)
                    }
                }
            }
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

// ---------------------------------------------------------------------------
// Scoring v4 sections.
//
// Everything below reads only what the row persists. Nothing is recomputed from
// the axes: the headline, the caps and the costs are the server's arithmetic and
// re-deriving any of it on the client is how the two numbers drift apart.
// ---------------------------------------------------------------------------

/** The brief as it was sent, in the shape the local rubric tables read. */
private fun ScoreIntake.toPreviewIntake(hasBack: Boolean): PreviewIntake = PreviewIntake(
    occasion = occasion,
    formality = formality,
    presence = presence,
    role = role,
    venue = venue,
    room = room,
    onFeet = on_feet,
    weatherBand = weather?.band,
    precip = weather?.precip == true,
    timeOfDay = time_of_day,
    hasBack = hasBack,
)

/** "+3%" / "−1%" — a weight delta, in the unit the user sees weights in. */
private fun signedPercent(delta: Double): String {
    val pts = (delta * 100.0).roundToInt()
    return when {
        pts > 0 -> "+$pts%"
        pts < 0 -> "−${-pts}%"
        else -> "±0%"
    }
}

/**
 * One axis: a bar as long as the score, the share of the headline it carried,
 * the evidence, and any rule that clipped it. An axis the photograph could not
 * support is greyed and keeps its row — dropping it would hide the fact that it
 * was dropped.
 */
@Composable
private fun AxisRow(axis: Axis) {
    val unjudgeable = axis.unjudgeable == true || axis.score == null
    val label = axis.label ?: AxisKey.from(axis.key)?.label ?: axis.key
    val tint = if (unjudgeable) HemColors.Muted else HemColors.Ink
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = HemType.body.copy(color = tint),
                modifier = Modifier.weight(1f),
            )
            if (axis.is_intent_axis == true) {
                Text(
                    "WHAT YOU ASKED FOR",
                    style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 1.2.sp),
                )
                Spacer(Modifier.width(HemSpace.xs))
            }
            Text(
                if (unjudgeable) "—" else String.format(Locale.US, "%.1f", axis.score),
                style = HemType.body.copy(fontSize = 14.sp, color = tint),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "·${(axis.weight * 100.0).roundToInt()}%",
                style = HemType.bodyMuted.copy(fontSize = 12.sp),
            )
        }
        Spacer(Modifier.height(6.dp))
        // The bar is the score, not the weight — the weight is the numeral.
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(HemColors.Hairline),
        ) {
            if (!unjudgeable) {
                val fraction = ((axis.score ?: 0.0) / 10.0).coerceIn(0.0, 1.0).toFloat()
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (axis.capped_by != null) HemColors.Bronze else HemColors.Ink),
                )
            }
        }
        val evidence = axis.evidence
        if (!evidence.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                evidence,
                style = HemType.bodyMuted.copy(fontSize = 13.sp),
            )
        }
        if (unjudgeable) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Not visible in the photo — dropped, not guessed.",
                style = HemType.bodyMuted.copy(fontSize = 12.sp),
            )
        }
        val badges = listOfNotNull(
            axis.capped_by?.let { "CAPPED · $it" },
            axis.ceiling_rule?.let { rule ->
                val ceiling = axis.ceiling
                if (ceiling != null) {
                    "CEILING ${String.format(Locale.US, "%.1f", ceiling)} · $rule"
                } else "CEILING · $rule"
            },
            axis.floored_by?.let { "FLOORED · $it" },
        )
        if (badges.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                badges.forEach { badge -> RuleBadge(badge) }
            }
        }
    }
}

@Composable
private fun RuleBadge(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, HemColors.Bronze, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 1.2.sp),
        )
    }
}

/** What the brief asked for against what the photograph reads, plus the
 * checklist. A marker the photo could not show is a dash, never a cross. */
@Composable
private fun DressCodeSection(dc: DressCode) {
    Column(Modifier.fillMaxWidth()) {
        Eyebrow("THE DRESS CODE")
        Spacer(Modifier.height(HemSpace.sm))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DressCodeReading("YOU ASKED FOR", dc.claimed, Modifier.weight(1f))
            DressCodeReading("THE PHOTO READS", dc.read, Modifier.weight(1f))
        }
        val line = dc.line
        if (!line.isNullOrBlank()) {
            Spacer(Modifier.height(HemSpace.sm))
            Text(line, style = HemType.body)
        }
        val evidence = dc.evidence
        if (!evidence.isNullOrBlank()) {
            Spacer(Modifier.height(HemSpace.xxs))
            Text(evidence, style = HemType.bodyMuted.copy(fontSize = 13.sp))
        }
        val markers = dc.markers.orEmpty()
        if (markers.isNotEmpty()) {
            Spacer(Modifier.height(HemSpace.sm))
            markers.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when (m.present) {
                            true -> "✓"
                            false -> "✕"
                            null -> "–"
                        },
                        style = HemType.body.copy(
                            color = when (m.present) {
                                true -> HemColors.Ink
                                false -> HemColors.Bronze
                                null -> HemColors.Muted
                            },
                        ),
                        modifier = Modifier.width(18.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = HemType.body.copy(fontSize = 14.sp))
                        val note = m.note
                        if (!note.isNullOrBlank()) {
                            Text(note, style = HemType.bodyMuted.copy(fontSize = 12.sp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DressCodeReading(eyebrow: String, rung: Int?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Eyebrow(eyebrow, muted = true, size = 9.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            if (rung == null) "—" else RubricTables.FORMALITY_READ_LABELS.getOrNull(rung - 1) ?: "—",
            style = HemType.serifSection.copy(fontSize = 18.sp),
        )
    }
}

/**
 * The waterfall. `raw_weighted + Σ headline_cost == final` is an identity on the
 * server, so the ledger is printed rather than re-added: a rule with no cost is
 * still shown, because a rule that fired and cost nothing is information too.
 */
@Composable
private fun WhyThisNumberSection(bd: ScoreBreakdown, presence: PresenceCheck?) {
    Column(Modifier.fillMaxWidth()) {
        Eyebrow("WHY THIS NUMBER")
        Spacer(Modifier.height(HemSpace.sm))
        val raw = bd.raw_weighted
        if (raw != null) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Weighted mean", style = HemType.body, modifier = Modifier.weight(1f))
                Text(String.format(Locale.US, "%.2f", raw), style = HemType.body)
            }
        }
        bd.rules_fired.orEmpty().forEach { rule ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        rule.name ?: rule.id.orEmpty(),
                        style = HemType.body.copy(fontSize = 14.sp),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (rule.headline_cost == 0.0) {
                            "no cost"
                        } else {
                            String.format(Locale.US, "%+.2f", rule.headline_cost)
                        },
                        style = HemType.body.copy(fontSize = 14.sp, color = HemColors.Bronze),
                    )
                }
                val effect = rule.effect ?: rule.trigger
                if (!effect.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(effect, style = HemType.bodyMuted.copy(fontSize = 13.sp))
                }
            }
        }
        // Rules running in caveat_only say their piece and cost nothing.
        bd.downgraded_to_caveat.orEmpty().forEach { d ->
            val copy = d.copy
            if (!copy.isNullOrBlank()) {
                Spacer(Modifier.height(HemSpace.xxs))
                Text(copy, style = HemType.bodyMuted.copy(fontSize = 13.sp))
            }
        }
        val finalScore = bd.finalScore
        if (finalScore != null) {
            Spacer(Modifier.height(HemSpace.xs))
            androidx.compose.material3.HorizontalDivider(
                color = HemColors.Hairline,
                thickness = 0.5.dp,
            )
            Row(Modifier.fillMaxWidth().padding(top = HemSpace.xs)) {
                Text(
                    "Final",
                    style = HemType.body.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    String.format(Locale.US, "%.1f", finalScore),
                    style = HemType.body.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
        // The presence check only earns a line when the read and the ask differ.
        if (presence != null && presence.render) {
            val line = presence.line
            if (!line.isNullOrBlank()) {
                Spacer(Modifier.height(HemSpace.sm))
                Text(line, style = HemType.body.copy(fontSize = 14.sp))
            }
            val mechanism = presence.mechanism
            if (!mechanism.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(mechanism, style = HemType.bodyMuted.copy(fontSize = 13.sp))
            }
        }
    }
}

/** One change, and where it would land the score. */
@Composable
private fun LeverCard(lever: Lever) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(12.dp))
            .padding(HemSpace.md),
    ) {
        Eyebrow("THE ONE THING")
        Spacer(Modifier.height(HemSpace.xs))
        Text(
            lever.action ?: lever.label.orEmpty(),
            style = HemType.serifSection.copy(fontSize = 19.sp),
        )
        val copy = lever.copy
        if (!copy.isNullOrBlank()) {
            Spacer(Modifier.height(HemSpace.xxs))
            Text(copy, style = HemType.bodyMuted)
        }
        val projected = lever.projected
        if (projected != null) {
            Spacer(Modifier.height(HemSpace.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    lever.label.orEmpty(),
                    style = HemType.bodyMuted.copy(fontSize = 12.sp),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    String.format(Locale.US, "→ %.1f", projected),
                    style = HemType.body.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

@Composable
private fun SwapRow(swap: SwapV2) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(swap.from, swap.to).joinToString(" → "),
                style = HemType.body,
                modifier = Modifier.weight(1f),
            )
            val effort = swap.effort
            if (!effort.isNullOrBlank()) {
                Spacer(Modifier.width(HemSpace.xs))
                Text(
                    effortLabel(effort),
                    style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 1.2.sp),
                )
            }
        }
        val why = swap.why
        if (!why.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(why, style = HemType.bodyMuted.copy(fontSize = 13.sp))
        }
    }
}

private fun effortLabel(effort: String): String = when (effort) {
    "swap" -> "SWAP"
    "tailor" -> "TAILOR"
    "buy_or_rent" -> "BUY OR RENT"
    else -> effort.uppercase(Locale.US)
}

@Composable
private fun PieceRow(piece: Piece) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(piece.label.orEmpty(), style = HemType.body)
            val note = piece.note
            if (!note.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(note, style = HemType.bodyMuted.copy(fontSize = 13.sp))
            }
        }
        Spacer(Modifier.width(HemSpace.xs))
        Text(
            when (piece.verdict) {
                "works" -> "WORKS"
                "works_elsewhere" -> "WRONG ROOM"
                "drags" -> "DRAGS"
                else -> ""
            },
            style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 1.2.sp),
        )
        val score = piece.score
        if (score != null) {
            Spacer(Modifier.width(HemSpace.xs))
            Text(String.format(Locale.US, "%.1f", score), style = HemType.body.copy(fontSize = 14.sp))
        }
    }
}
