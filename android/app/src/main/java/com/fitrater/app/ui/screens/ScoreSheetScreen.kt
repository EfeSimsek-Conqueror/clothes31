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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fitrater.app.data.Supa
import com.fitrater.app.data.model.OutfitInsert
import com.fitrater.app.data.model.ScoreIntake
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.data.service.HemService
import com.fitrater.app.scoring.AxisKey
import com.fitrater.app.scoring.PreviewIntake
import com.fitrater.app.scoring.RubricTables
import com.fitrater.app.scoring.RubricWeights
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.PrimaryButton
import com.fitrater.app.ui.theme.SerifDisplay
import com.fitrater.app.util.CameraBus
import com.fitrater.app.util.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Photos survive a trip to the camera screen here rather than in `remember`,
 * because navigating away disposes this composition. Everything else in the
 * brief is a scalar and rides `rememberSaveable`; the bytes are far too big to
 * put in a saved-state bundle.
 */
private object ScoreDraft {
    @Volatile var frontBytes: ByteArray? = null
    @Volatile var frontUri: Uri? = null
    @Volatile var backBytes: ByteArray? = null
    @Volatile var backUri: Uri? = null

    fun clear() {
        frontBytes = null; frontUri = null; backBytes = null; backUri = null
    }
}

private const val SLOT_FRONT = "score_front"
private const val SLOT_BACK = "score_back"

/** No chip picked. Stored rather than null so it survives `rememberSaveable`. */
private const val NO_CHIP = ""

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScoreSheetScreen(
    onClose: () -> Unit,
    onScored: (String) -> Unit,
    onOpenPaywall: () -> Unit = {},
    onOpenCamera: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // ---- the brief ----
    var occasion by rememberSaveable { mutableStateOf(RubricTables.EVERYDAY) }
    var formality by rememberSaveable {
        mutableStateOf(RubricTables.SEED_FORMALITY.getValue(RubricTables.EVERYDAY))
    }
    var presence by rememberSaveable {
        mutableStateOf(RubricTables.SEED_PRESENCE.getValue(RubricTables.EVERYDAY))
    }
    var role by rememberSaveable { mutableStateOf(RubricTables.DEFAULT_ROLE) }
    var venue by rememberSaveable { mutableStateOf(RubricTables.DEFAULT_VENUE) }
    var room by rememberSaveable { mutableStateOf(RubricTables.DEFAULT_ROOM) }
    var onFeet by rememberSaveable { mutableStateOf(RubricTables.DEFAULT_ON_FEET) }
    var intentChip by rememberSaveable { mutableStateOf(NO_CHIP) }
    var intentText by rememberSaveable { mutableStateOf("") }

    // ---- photos ----
    var frontUri by remember { mutableStateOf(ScoreDraft.frontUri) }
    var frontBytes by remember { mutableStateOf(ScoreDraft.frontBytes) }
    var backUri by remember { mutableStateOf(ScoreDraft.backUri) }
    var backBytes by remember { mutableStateOf(ScoreDraft.backBytes) }

    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    // The hour is a fact about the device, not a guess, so it is safe to send.
    // Weather deliberately is NOT sent — see the comment at the request below.
    val timeOfDay = remember {
        if (java.time.LocalTime.now().hour >= 17) "evening" else "daytime"
    }

    // Pull any bytes handed back by CameraCaptureScreen into the slot they were
    // taken for. A capture with no slot is the front — that is what the button
    // did before there was a back view.
    LaunchedEffect(Unit) {
        val (slot, pending) = CameraBus.consumeWithSlot()
        if (pending != null) {
            if (slot == SLOT_BACK) {
                backBytes = pending; backUri = null
                ScoreDraft.backBytes = pending; ScoreDraft.backUri = null
            } else {
                frontBytes = pending; frontUri = null
                ScoreDraft.frontBytes = pending; ScoreDraft.frontUri = null
            }
        }
    }

    val frontGallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            frontUri = uri; frontBytes = null
            ScoreDraft.frontUri = uri; ScoreDraft.frontBytes = null
        }
    }
    val backGallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            backUri = uri; backBytes = null
            ScoreDraft.backUri = uri; ScoreDraft.backBytes = null
        }
    }

    val hasFront = frontUri != null || frontBytes != null
    val hasBack = backUri != null || backBytes != null

    // The axis a tapped chip buys. Free text is mapped by the model, which
    // returns `intent_axis` on the response — so the preview strip simply does
    // not move for typed text, which is honest.
    val intentAxis: AxisKey? = remember(intentChip) {
        RubricTables.INTENT_CHIPS.firstOrNull { it.label == intentChip }?.axis
    }
    val intentValue: String? = remember(intentChip, intentText) {
        val typed = intentText.trim()
        when {
            intentChip != NO_CHIP -> intentChip
            typed.isNotEmpty() -> typed.take(RubricTables.INTENT_MAX_CHARS)
            else -> null
        }
    }

    val preview = PreviewIntake(
        occasion = occasion,
        formality = formality,
        presence = presence,
        role = if (occasion == RubricTables.WEDDING) role else null,
        venue = if (occasion == RubricTables.WEDDING) venue else null,
        room = if (occasion == RubricTables.WORK) room else null,
        onFeet = if (occasion == RubricTables.EVERYDAY) onFeet else null,
        intentAxis = intentAxis,
        weatherBand = null,
        precip = false,
        timeOfDay = timeOfDay,
        hasBack = hasBack,
    )
    val briefLine = remember(preview) { RubricTables.briefLine(preview) }
    val hardestOn = remember(preview) { RubricWeights.primaryAxes(preview, 4) }

    val cost = Supa.SCORE_COST + (if (hasBack) Supa.FRONT_BACK_EXTRA_COST else 0)

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
                        "Tell Hem what it was for. The brief decides what gets graded hardest.",
                        style = HemType.bodyMuted,
                    )
                }
                Box(
                    // Closing abandons the draft. Without this the next look
                    // would open on the last one's photos with a fresh brief.
                    Modifier.size(36.dp).clickable { ScoreDraft.clear(); onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            Spacer(Modifier.height(HemSpace.lg))

            // ---- the photo ----
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.82f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(HemColors.CardCream)
                    .border(1.dp, HemColors.Hairline, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val model: Any? = frontBytes ?: frontUri
                if (model != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(model).build(),
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
                OutlinePill(
                    label = "Take photo",
                    onClick = {
                        CameraBus.slot = SLOT_FRONT
                        onOpenCamera()
                    },
                    modifier = Modifier.weight(1f),
                )
                OutlinePill(
                    label = "From gallery",
                    onClick = {
                        frontGallery.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            // ---- the back view ----
            //
            // Without one the fit read is a partial one: the server weights FIT
            // down and ceilings it at 8.5. Saying so is the only thing that makes
            // the extra photo worth taking.
            Spacer(Modifier.height(HemSpace.lg))
            Eyebrow("BACK VIEW · OPTIONAL")
            Spacer(Modifier.height(HemSpace.xs))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(HemColors.CardCream)
                        .border(1.dp, HemColors.Hairline, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    val backModel: Any? = backBytes ?: backUri
                    if (backModel != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(backModel).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text("＋", style = HemType.bodyMuted.copy(fontSize = 20.sp))
                    }
                }
                Spacer(Modifier.size(HemSpace.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (hasBack) {
                            "Full fit read. +${Supa.FRONT_BACK_EXTRA_COST} credits."
                        } else {
                            "Front only — fit is graded lower and capped at 8.5."
                        },
                        style = HemType.bodyMuted.copy(fontSize = 13.sp),
                    )
                    Spacer(Modifier.height(HemSpace.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(HemSpace.xs)) {
                        SmallPill(
                            label = "Camera",
                            onClick = {
                                CameraBus.slot = SLOT_BACK
                                onOpenCamera()
                            },
                        )
                        SmallPill(
                            label = "Gallery",
                            onClick = {
                                backGallery.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                        )
                        if (hasBack) {
                            SmallPill(
                                label = "Remove",
                                onClick = {
                                    backBytes = null; backUri = null
                                    ScoreDraft.backBytes = null; ScoreDraft.backUri = null
                                },
                            )
                        }
                    }
                }
            }

            // ---- occasion ----
            Spacer(Modifier.height(HemSpace.lg))
            Eyebrow("WHAT WAS IT FOR")
            Spacer(Modifier.height(HemSpace.sm))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RubricTables.OCCASIONS.forEach { key ->
                    ChoiceChip(
                        label = RubricTables.OCCASION_LABELS[key] ?: key,
                        selected = occasion == key,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            occasion = key
                            // Both dials snap to the occasion's seeds — the first
                            // visible proof that the chip did something.
                            formality = RubricTables.SEED_FORMALITY.getValue(key)
                            presence = RubricTables.SEED_PRESENCE.getValue(key)
                        },
                    )
                }
            }

            // ---- the two dials ----
            Spacer(Modifier.height(HemSpace.lg))
            DetentDial(
                eyebrow = "HOW DRESSED UP",
                value = formality,
                caption = RubricTables.formalityCaption(occasion, formality),
                onChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    formality = it
                },
            )
            Spacer(Modifier.height(HemSpace.lg))
            DetentDial(
                eyebrow = "HOW MUCH DO YOU WANT TO BE LOOKED AT",
                value = presence,
                caption = RubricTables.presenceCaption(presence),
                onChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    presence = it
                },
            )

            // ---- band 4: the questions no dial can encode ----
            when (occasion) {
                RubricTables.WEDDING -> {
                    Spacer(Modifier.height(HemSpace.lg))
                    ChipGroup(
                        eyebrow = "YOUR ROLE",
                        options = RubricTables.ROLES,
                        labels = RubricTables.ROLE_LABELS,
                        selected = role,
                        onSelect = { role = it },
                    )
                    Spacer(Modifier.height(HemSpace.md))
                    ChipGroup(
                        eyebrow = "WHERE",
                        options = RubricTables.VENUES,
                        labels = RubricTables.VENUE_LABELS,
                        selected = venue,
                        onSelect = { venue = it },
                    )
                }
                RubricTables.WORK -> {
                    Spacer(Modifier.height(HemSpace.lg))
                    ChipGroup(
                        eyebrow = "THE ROOM",
                        options = RubricTables.ROOMS,
                        labels = RubricTables.ROOM_LABELS,
                        selected = room,
                        onSelect = { room = it },
                    )
                }
                RubricTables.EVERYDAY -> {
                    Spacer(Modifier.height(HemSpace.lg))
                    ChipGroup(
                        eyebrow = "ON YOUR FEET",
                        options = RubricTables.ON_FEET,
                        labels = RubricTables.ON_FEET_LABELS,
                        selected = onFeet,
                        onSelect = { onFeet = it },
                    )
                }
                else -> Unit
            }

            // ---- what you're going for ----
            Spacer(Modifier.height(HemSpace.lg))
            Eyebrow("WHAT ARE YOU GOING FOR · OPTIONAL")
            Spacer(Modifier.height(HemSpace.sm))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RubricTables.INTENT_CHIPS.forEach { chip ->
                    ChoiceChip(
                        label = chip.label,
                        selected = intentChip == chip.label,
                        onClick = {
                            // Re-tapping the selected chip clears it.
                            intentChip = if (intentChip == chip.label) NO_CHIP else chip.label
                            if (intentChip != NO_CHIP) intentText = ""
                        },
                    )
                }
            }
            Spacer(Modifier.height(HemSpace.xs))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HemColors.CardCream)
                    .border(1.dp, HemColors.Hairline, RoundedCornerShape(12.dp))
                    .padding(horizontal = HemSpace.md, vertical = HemSpace.sm),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = intentText,
                    onValueChange = {
                        intentText = it.take(RubricTables.INTENT_MAX_CHARS)
                        if (intentText.isNotBlank()) intentChip = NO_CHIP
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = HemColors.Ink, fontSize = 14.sp),
                    cursorBrush = SolidColor(HemColors.Ink),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (intentText.isEmpty()) {
                    Text(
                        "…or say it yourself: \"make my legs look longer\"",
                        style = HemType.bodyMuted.copy(fontSize = 14.sp),
                    )
                }
            }

            // ---- the brief, read back ----
            Spacer(Modifier.height(HemSpace.lg))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HemColors.CardCream)
                    .border(1.dp, HemColors.Hairline, RoundedCornerShape(12.dp))
                    .padding(HemSpace.md),
            ) {
                Eyebrow("GRADING THIS AS")
                Spacer(Modifier.height(HemSpace.xs))
                Text(briefLine, style = HemType.serifQuote.copy(fontSize = 16.sp))
                Spacer(Modifier.height(HemSpace.sm))
                Text(
                    "Hardest on: " + hardestOn.joinToString(" · ") { it.label.lowercase() },
                    style = HemType.bodyMuted.copy(fontSize = 12.sp, letterSpacing = 0.8.sp),
                )
            }

            if (error != null) {
                Spacer(Modifier.height(HemSpace.sm))
                Text(error!!, style = HemType.bodyMuted.copy(color = HemColors.Bronze))
            }

            Spacer(Modifier.height(HemSpace.lg))
            PrimaryButton(
                label = if (busy) "Analyzing…" else "Score it · $cost credits",
                onClick = {
                    if (busy) return@PrimaryButton
                    if (!hasFront) {
                        error = "Add a photo first"
                        return@PrimaryButton
                    }
                    busy = true
                    error = null
                    scope.launch {
                        val gate = com.fitrater.app.util.CreditsGate.check(cost)
                        if (gate !is com.fitrater.app.util.GateResult.Ok) {
                            busy = false
                            if (gate is com.fitrater.app.util.GateResult.InsufficientBalance) {
                                com.fitrater.app.util.ToastBus.post(
                                    "Not enough credits — ${gate.need} needed to score.",
                                )
                                onOpenPaywall()
                            } else {
                                com.fitrater.app.util.CreditsGate.explainAndBlock(gate)
                            }
                            return@launch
                        }
                        runCatching {
                            val frontRaw = frontBytes ?: withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(frontUri!!)
                                    ?.buffered()?.use { it.readBytes() }
                                    ?: error("Could not read photo")
                            }
                            if (frontRaw.size < 800 * 1024) {
                                Log.w("Score", "photo bytes ${frontRaw.size} < 800KB, quality may be reduced")
                            }
                            val frontPath = Repo.uploadOutfitPhoto(frontRaw)
                            val frontSigned = Repo.signedOutfitUrl(frontPath)
                                ?: error("Could not sign photo URL")

                            var backPath: String? = null
                            var backSigned: String? = null
                            if (hasBack) {
                                val backRaw = backBytes ?: withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(backUri!!)
                                        ?.buffered()?.use { it.readBytes() }
                                }
                                if (backRaw != null) {
                                    backPath = Repo.uploadOutfitPhoto(backRaw)
                                    backSigned = Repo.signedOutfitUrl(backPath)
                                }
                            }

                            // Weather is deliberately absent. The only source on
                            // this device falls back to a fixed city when location
                            // is denied, and the contract is explicit: omit it
                            // rather than guess, so the weather rules disarm.
                            val intake = ScoreIntake(
                                occasion = occasion,
                                formality = formality,
                                presence = presence,
                                time_of_day = timeOfDay,
                                role = if (occasion == RubricTables.WEDDING) role else null,
                                venue = if (occasion == RubricTables.WEDDING) venue else null,
                                room = if (occasion == RubricTables.WORK) room else null,
                                on_feet = if (occasion == RubricTables.EVERYDAY) onFeet else null,
                                intent = intentValue,
                                weather = null,
                            )
                            val legacyOccasion = RubricTables.OCCASION_LABELS[occasion] ?: "Everyday"
                            val bodyProfile = runCatching { Repo.loadBodyProfile() }.getOrNull()

                            val scored = HemService.score(
                                imageUrl = frontSigned,
                                occasion = legacyOccasion,
                                intake = intake,
                                backUrl = backSigned,
                                bodyProfile = bodyProfile,
                                intent = intentValue,
                            )

                            val row = Repo.insertOutfit(
                                OutfitInsert(
                                    user_id = Repo.userId ?: error("Not signed in"),
                                    photo_path = frontPath,
                                    score = scored.score,
                                    occasion = legacyOccasion,
                                    hem_comment = scored.hemComment,
                                    verdict = scored.verdict,
                                    subscores = scored.subscores,
                                    swaps = scored.swaps,
                                    annotations = scored.annotations,
                                    back_photo_path = backPath,
                                    intake = intake,
                                    rubric_id = scored.rubric?.id,
                                    rubric_version = scored.rubric?.version,
                                    scoring_version = scored.scoringVersion ?: "v3_mean",
                                    axes = scored.axes.ifEmpty { null },
                                    score_breakdown = scored.breakdown?.copy(
                                        swaps_v2 = scored.swapsV2.ifEmpty { null },
                                    ),
                                    dress_code = scored.dressCode,
                                    presence_check = scored.presenceCheck,
                                    lever = scored.lever,
                                    caveats = scored.caveats.ifEmpty { null },
                                    pieces = scored.pieces.ifEmpty { null },
                                    signals = scored.signals,
                                    raw_axes = scored.rawAxes,
                                ),
                            )
                            val id = row.id ?: error("Insert returned no id")
                            // The overlay and the heatmap live in their own tables.
                            Repo.persistScoreExtras(id, scored.markupAnnotations, scored.fitMap)
                            // Charge credits only on success.
                            runCatching { Repo.spendCredits(cost, "outfit_score") }
                                .onFailure { Log.w("Score", "credit charge failed", it) }
                            id
                        }.onSuccess { id ->
                            Log.i("Score", "outfit scored id=$id")
                            ScoreDraft.clear()
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
                enabled = hasFront && !busy,
            )
            Spacer(Modifier.height(HemSpace.xl))
        }
    }
}

/**
 * A five-detent dial. Tap targets, not a drag handle: this sheet scrolls, and a
 * horizontal drag inside a vertical scroller loses the gesture race often enough
 * that the control feels broken. Filled from the left so the row still reads as
 * a dial rather than five unrelated buttons.
 */
@Composable
private fun DetentDial(
    eyebrow: String,
    value: Int,
    caption: String,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Eyebrow(eyebrow)
        Spacer(Modifier.height(HemSpace.xs))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            (1..5).forEach { detent ->
                val filled = detent <= value
                val current = detent == value
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (filled) HemColors.Ink else Color.Transparent)
                        .border(
                            1.dp,
                            if (current) HemColors.Bronze else HemColors.Ink.copy(alpha = 0.35f),
                            RoundedCornerShape(6.dp),
                        )
                        .clickable { onChange(detent) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        detent.toString(),
                        style = HemType.body.copy(
                            fontSize = 13.sp,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (filled) Color.White else HemColors.Muted,
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(HemSpace.xs))
        Text(
            caption,
            style = HemType.body.copy(fontWeight = FontWeight.Medium),
        )
    }
}

/** Eyebrow + a wrapping row of single-select chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(
    eyebrow: String,
    options: List<String>,
    labels: Map<String, String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Eyebrow(eyebrow)
        Spacer(Modifier.height(HemSpace.sm))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { key ->
                ChoiceChip(
                    label = labels[key] ?: key,
                    selected = selected == key,
                    onClick = { onSelect(key) },
                )
            }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) HemColors.Ink else Color.Transparent)
            .border(1.dp, HemColors.Ink.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = HemType.body.copy(
                color = if (selected) Color.White else HemColors.Ink,
                fontSize = 13.sp,
            ),
        )
    }
}

@Composable
private fun SmallPill(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, HemColors.Ink.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = HemType.body.copy(fontSize = 12.sp))
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
