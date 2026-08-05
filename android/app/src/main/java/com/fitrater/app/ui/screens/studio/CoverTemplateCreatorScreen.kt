package com.fitrater.app.ui.screens.studio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import coil.compose.AsyncImage
import com.fitrater.app.data.Supa
import com.fitrater.app.data.model.ComposeCoverResponse
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.Hairline
import com.fitrater.app.ui.theme.SerifDisplay
import com.fitrater.app.util.CreditsGate
import com.fitrater.app.util.GateResult
import com.fitrater.app.util.ToastBus
import kotlinx.coroutines.launch

// ============================================================================
// Vocabulary — mirrors the iOS wizard 1:1
// ============================================================================

private val COVER_GENRES = listOf(
    "Fashion"    to "Vogue-adjacent editorial, sharp typography, restrained styling.",
    "Streetwear" to "Off-White / Hypebeast energy, casual grit, oversized text.",
    "Sport"      to "SI-style, dynamic, high contrast, muscular type.",
    "Culture"    to "Dazed / i-D voice — experimental, provocative, playful.",
    "Lifestyle"  to "Kinfolk / Cereal — slow, quiet, hairline detail.",
    "Music"      to "Rolling Stone / The Face — moody, portraiture-forward.",
    "Art"        to "Whitewall / gallery print — clean, generous whitespace.",
    "Business"   to "Fortune / Wallpaper — architectural, condensed sans.",
)

private val COVER_MOODS = listOf(
    "Editorial"    to "Classic Vogue: bold serif, tight columns, moody grade.",
    "Quiet luxury" to "Understated, cream backgrounds, generous whitespace.",
    "Brutalist"    to "Hard bars, condensed sans, high-contrast ink.",
    "Romantic"     to "Italic display, blush palette, cursive touches.",
    "Sport"        to "Dynamic italics, high-energy blocks, saturated color.",
    "Minimal"      to "One idea, tons of air, hairline rules.",
    "Punk"         to "Ransom cut, defaced type, grainy black-and-cream.",
    "Maximalist"   to "Everything on the page, layered type, saturated.",
)

private val COVER_LAYOUTS = listOf(
    "top"    to "Masthead pinned at the top — classic layout.",
    "center" to "Big masthead centered like an art print.",
    "bottom" to "Masthead as a footer — modern, poster-style.",
)

private data class CoverColorOpt(val name: String, val hex: String, val desc: String)

private val COVER_COLORS = listOf(
    CoverColorOpt("Cream",      "#F3EEE4", "Editorial default — warm, safe."),
    CoverColorOpt("Ink",        "#141210", "Ink cover, cream type — high drama."),
    CoverColorOpt("Bronze",     "#B4813E", "Warm accent — Fitrater signature."),
    CoverColorOpt("Bone",       "#EAE3D3", "Softer than cream, gallery-like."),
    CoverColorOpt("Slate",      "#3E4552", "Muted, corporate, considered."),
    CoverColorOpt("Terracotta", "#B4573E", "Warm clay — Kinfolk-esque."),
    CoverColorOpt("Sage",       "#7F8D6E", "Botanical, calm, quiet luxury."),
    CoverColorOpt("Blush",      "#E9C7BF", "Romantic, soft focus."),
)

private data class PoseOpt(val key: String, val name: String, val desc: String)

private val POSES = listOf(
    PoseOpt("standing",           "Standing", "Full-length, editorial front pose."),
    PoseOpt("seated",             "Seated",   "On a chair or floor — considered, intimate."),
    PoseOpt("walking",            "Walking",  "Motion, streetstyle-adjacent."),
    PoseOpt("editorial_portrait", "Portrait", "Bust / face-forward — the classic Vogue shot."),
    PoseOpt("detail",             "Detail",   "Close-up on fabric, hands, or accessory."),
)

private val COVER_LINE_SUGGESTIONS = listOf(
    "THE ART ISSUE",
    "50 IDEAS FOR FALL",
    "MEET THE NEW GUARD",
    "INSIDE THE ATELIER",
    "THE STYLE REPORT",
    "A NEW ROMANTICISM",
    "SEASON OF QUIET",
    "TALKING SHOP",
)

// ============================================================================
// Wizard state
// ============================================================================

private class CoverWizardState {
    var genre: String? by mutableStateOf(null)
    var mood: String? by mutableStateOf("Editorial")
    var masthead: String by mutableStateOf("FITRATER")
    var layout: String by mutableStateOf("top")
    var headline: String by mutableStateOf("")
    var pullQuote: String by mutableStateOf("")
    var color: String by mutableStateOf("#F3EEE4")
    val coverLines: SnapshotStateList<String> = mutableStateListOf()
    var pose: String by mutableStateOf("standing")
    var price: String by mutableStateOf("$8.00")
}

// ============================================================================
// Screen
// ============================================================================

/**
 * Studio-side wizard for creating a magazine-cover TEMPLATE. 1-to-1 port of
 * the iOS `CoverTemplateCreatorView.swift`. Templates are stored in
 * `magazine_covers` (outfit_id NULL) and surface later in Camera → Compose
 * Cover as "My covers" references.
 */
@Composable
fun CoverTemplateCreatorScreen(onClose: () -> Unit) {
    val state = remember { CoverWizardState() }
    var stepIndex by rememberSaveable { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ComposeCoverResponse?>(null) }

    val steps = remember { (1..8).toList() }
    val currentStep = steps[stepIndex]
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(HemColors.Paper)) {
        val doneResult = result
        if (doneResult != null && !doneResult.cover_url.isNullOrBlank()) {
            ResultScreen(
                url = doneResult.cover_url,
                onMakeAnother = {
                    result = null
                    stepIndex = 0
                    // reset via constructor
                    state.genre = null; state.mood = "Editorial"; state.masthead = "FITRATER"
                    state.layout = "top"; state.headline = ""; state.pullQuote = ""
                    state.color = "#F3EEE4"; state.coverLines.clear()
                    state.pose = "standing"; state.price = "$8.00"
                },
                onDone = onClose,
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                // Progress hairline
                Box(
                    Modifier.fillMaxWidth().height(2.dp).background(HemColors.Hairline),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(((stepIndex + 1).toFloat() / steps.size).coerceIn(0f, 1f))
                            .height(2.dp)
                            .background(HemColors.Bronze),
                    )
                }

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .clickable {
                                    if (stepIndex == 0) onClose() else stepIndex -= 1
                                }
                                .padding(6.dp),
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = HemColors.Ink)
                        }
                        Spacer(Modifier.width(6.dp))
                        Eyebrow("STEP ${stepIndex + 1} OF ${steps.size}")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Design a cover",
                        style = HemType.serifDisplay.copy(fontSize = 28.sp, color = HemColors.Ink),
                    )
                    Spacer(Modifier.height(20.dp))

                    val prevStep = remember { mutableStateOf(currentStep) }
                    val forward = currentStep >= prevStep.value
                    SideEffect { prevStep.value = currentStep }
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            val enter = if (forward) {
                                slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(250))
                            } else {
                                slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(250))
                            }
                            val exit = if (forward) {
                                slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(200))
                            } else {
                                slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(200))
                            }
                            enter togetherWith exit
                        },
                        label = "coverWizard",
                    ) { step ->
                        when (step) {
                            1 -> Step1Genre(state)
                            2 -> Step2Mood(state)
                            3 -> Step3Masthead(state)
                            4 -> Step4Copy(state)
                            5 -> Step5CoverLines(state)
                            6 -> Step6Pose(state)
                            7 -> Step7Palette(state)
                            else -> Step8Review(state)
                        }
                    }

                    errorMsg?.let {
                        Text(
                            it,
                            style = HemType.body.copy(fontSize = 13.sp, color = HemColors.Bronze),
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    Spacer(Modifier.height(40.dp))
                }

                BottomBar(
                    stepIndex = stepIndex,
                    stepCount = steps.size,
                    currentStep = currentStep,
                    busy = busy,
                    canAdvance = canAdvance(currentStep, state),
                    onBack = { if (stepIndex == 0) onClose() else stepIndex -= 1 },
                    onNext = { if (canAdvance(currentStep, state)) stepIndex += 1 },
                    onCreate = {
                        scope.launch {
                            busy = true; errorMsg = null
                            try {
                                val gate = CreditsGate.check(Supa.COVER_TEMPLATE_COST)
                                if (gate !is GateResult.Ok) {
                                    CreditsGate.explainAndBlock(gate)
                                    busy = false; return@launch
                                }
                                val resp = Repo.createCoverTemplate(
                                    masthead = state.masthead.trim(),
                                    headline = state.headline.trim(),
                                    pullQuote = state.pullQuote.ifBlank { null },
                                    mood = state.mood?.lowercase(),
                                    color = state.color,
                                    layout = state.layout,
                                    coverLines = if (state.coverLines.isEmpty()) null else state.coverLines.toList(),
                                    pose = state.pose,
                                    price = state.price,
                                    includeBarcode = true,
                                )
                                if (!resp.error.isNullOrBlank()) {
                                    errorMsg = resp.error
                                    ToastBus.post("Template failed: ${resp.error}")
                                } else {
                                    runCatching { Repo.spendCredits(Supa.COVER_TEMPLATE_COST, "cover_template") }
                                    result = resp
                                }
                            } catch (e: Throwable) {
                                errorMsg = e.message ?: "Unknown error"
                                ToastBus.post("Template failed: ${e.message}")
                            } finally {
                                busy = false
                            }
                        }
                    },
                )
            }
        }

        if (busy) {
            BusyOverlay(
                eyebrow = "TYPESETTING",
                title = "Setting your template",
                tips = listOf(
                    "Choosing the type family…",
                    "Balancing the layout…",
                    "Adding the grain…",
                    "Free of charge — just typography.",
                ),
            )
        }
    }
}

private fun canAdvance(step: Int, state: CoverWizardState): Boolean = when (step) {
    1 -> state.genre != null
    2 -> state.mood != null
    3 -> state.masthead.trim().isNotEmpty()
    4 -> state.headline.trim().isNotEmpty()
    5 -> true
    6 -> state.pose.isNotEmpty()
    else -> true
}

// ============================================================================
// Bottom bar
// ============================================================================

@Composable
private fun BottomBar(
    stepIndex: Int,
    stepCount: Int,
    currentStep: Int,
    busy: Boolean,
    canAdvance: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onCreate: () -> Unit,
) {
    Column {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .background(HemColors.Paper)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, HemColors.Ink.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text("Back", style = HemType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = HemColors.Ink))
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${stepIndex + 1}",
                    style = HemType.serifDisplay.copy(fontSize = 22.sp, color = HemColors.Bronze),
                )
                Text(
                    "/$stepCount",
                    style = HemType.body.copy(fontSize = 14.sp, color = HemColors.Muted),
                )
            }
            Spacer(Modifier.weight(1f))
            if (currentStep == 8) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(HemColors.Ink)
                        .clickable(enabled = !busy, onClick = onCreate)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(
                        if (busy) "Setting…" else "Create · ${Supa.COVER_TEMPLATE_COST} credits",
                        style = HemType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HemColors.OnInk),
                    )
                }
            } else {
                val bg = if (canAdvance) HemColors.Ink else HemColors.Muted
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(bg)
                        .clickable(enabled = canAdvance, onClick = onNext)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(
                        "Next",
                        style = HemType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HemColors.OnInk),
                    )
                }
            }
        }
    }
}

// ============================================================================
// Result screen
// ============================================================================

@Composable
private fun ResultScreen(url: String, onMakeAnother: () -> Unit, onDone: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("SAVED")
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(36.dp)
                    .clickable(onClick = onDone),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = HemColors.Ink)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Your template is ready.",
            style = HemType.serifDisplay.copy(fontSize = 28.sp, color = HemColors.Ink),
        )
        Spacer(Modifier.height(16.dp))
        AsyncImage(
            model = url,
            contentDescription = "Cover template",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(16.dp))
                .background(HemColors.CardCream),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Next time you Compose a Cover from your own photo, this template will appear in the REFERENCE picker as \"My covers\".",
            style = HemType.body.copy(fontSize = 14.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted),
        )
        Spacer(Modifier.height(20.dp))
        OutlinePillFullWidth(text = "Make another", onClick = onMakeAnother)
        Spacer(Modifier.height(10.dp))
        OutlinePillFullWidth(text = "Done", onClick = onDone)
    }
}

@Composable
private fun OutlinePillFullWidth(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, HemColors.Ink.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HemType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Ink))
    }
}

// ============================================================================
// Steps
// ============================================================================

@Composable
private fun Step1Genre(state: CoverWizardState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("What kind of magazine?", style = HemType.serifDisplay.copy(fontSize = 22.sp, color = HemColors.Ink))
        Text(
            "Sets the overall register — how tight, how loud, how quiet.",
            style = HemType.body.copy(fontSize = 14.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            COVER_GENRES.forEach { (name, desc) ->
                OptionRow(title = name, subtitle = desc, on = state.genre == name) {
                    state.genre = name
                    state.mood = when (name) {
                        "Sport"      -> "Sport"
                        "Streetwear" -> "Brutalist"
                        "Lifestyle"  -> "Quiet luxury"
                        "Culture"    -> "Punk"
                        "Music"      -> "Editorial"
                        "Art"        -> "Minimal"
                        "Business"   -> "Minimal"
                        else         -> "Editorial"
                    }
                }
            }
        }
    }
}

@Composable
private fun Step2Mood(state: CoverWizardState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Pick the mood.", style = HemType.serifDisplay.copy(fontSize = 22.sp, color = HemColors.Ink))
        Text(
            "Drives the typography, spacing, and grain. Your genre pre-picked one — override if you want.",
            style = HemType.body.copy(fontSize = 14.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            COVER_MOODS.forEach { (name, desc) ->
                OptionRow(title = name, subtitle = desc, on = state.mood == name) {
                    state.mood = name
                }
            }
        }
    }
}

@Composable
private fun Step3Masthead(state: CoverWizardState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Name the publication.", style = HemType.serifDisplay.copy(fontSize = 22.sp, color = HemColors.Ink))
        Text(
            "What's at the top of the cover? Keep it short, 3-12 characters.",
            style = HemType.body.copy(fontSize = 14.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted),
        )
        BorderedTextField(
            value = state.masthead,
            onValueChange = { state.masthead = it.uppercase() },
            placeholder = "FITRATER",
            textStyle = HemType.serifDisplay.copy(fontSize = 22.sp, color = HemColors.Ink),
            capitalize = KeyboardCapitalization.Characters,
        )
        Eyebrow("POSITION")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            COVER_LAYOUTS.forEach { (key, desc) ->
                OptionRow(title = key.replaceFirstChar { it.uppercase() }, subtitle = desc, on = state.layout == key) {
                    state.layout = key
                }
            }
        }
    }
}

@Composable
private fun Step4Copy(state: CoverWizardState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Write the headline.", style = HemType.serifDisplay.copy(fontSize = 22.sp, color = HemColors.Ink))
        Text(
            "The big line that grabs the reader. Uppercase reads best.",
            style = HemType.body.copy(fontSize = 14.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted),
        )
        BorderedTextField(
            value = state.headline,
            onValueChange = { state.headline = it.uppercase() },
            placeholder = "A STUDY IN BRONZE",
            textStyle = HemType.serifDisplay.copy(fontSize = 20.sp, color = HemColors.Ink),
            capitalize = KeyboardCapitalization.Characters,
            multiline = true,
        )
        Eyebrow("PULL QUOTE (OPT)")
        Text(
            "An italic line beneath the headline. Short — 8 to 12 words.",
            style = HemType.body.copy(fontSize = 13.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted),
        )
        BorderedTextField(
            value = state.pullQuote,
            onValueChange = { state.pullQuote = it },
            placeholder = "She wore restraint and won the room.",
            textStyle = HemType.body.copy(fontSize = 15.sp, fontStyle = FontStyle.Italic, color = HemColors.Ink),
            multiline = true,
        )
    }
}

@Composable
private fun Step5CoverLines(state: CoverWizardState) {
    var draft by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Add cover lines.", style = HemType.serifDisplay.copy(fontSize = 22.sp, color = HemColors.Ink))
        Text(
            "The little tag lines scattered around the model on real Vogue covers. Up to 4. Skip if you want it clean.",
            style = HemType.body.copy(fontSize = 14.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted),
        )

        if (state.coverLines.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.coverLines.forEachIndexed { idx, line ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, HemColors.Hairline, RoundedCornerShape(10.dp))
                            .background(HemColors.CardCream)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            line,
                            style = HemType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Ink),
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            Modifier
                                .clickable { state.coverLines.removeAt(idx) }
                                .padding(4.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = HemColors.Muted, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        if (state.coverLines.size < 4) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    BorderedTextField(
                        value = draft,
                        onValueChange = { draft = it.uppercase() },
                        placeholder = "THE STYLE REPORT",
                        textStyle = HemType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Ink),
                        capitalize = KeyboardCapitalization.Characters,
                    )
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (draft.trim().isEmpty()) HemColors.Muted else HemColors.Ink)
                        .clickable(enabled = draft.trim().isNotEmpty(), onClick = {
                            val cleaned = draft.trim()
                            if (cleaned.isNotEmpty()) {
                                state.coverLines.add(cleaned)
                                draft = ""
                            }
                        }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = HemColors.OnInk, modifier = Modifier.size(18.dp))
                }
            }
        }

        Eyebrow("SUGGESTIONS")
        val picks = COVER_LINE_SUGGESTIONS.filter { it !in state.coverLines }.take(6)
        FlowRowChips(picks) { s ->
            if (state.coverLines.size < 4) state.coverLines.add(s)
        }
    }
}

@Composable
private fun Step6Pose(state: CoverWizardState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Placeholder pose.", style = HemType.serifDisplay.copy(fontSize = 22.sp, color = HemColors.Ink))
        Text(
            "What shape should the placeholder subject take? This is a stand-in — later, when you compose a real cover, your photo replaces it.",
            style = HemType.body.copy(fontSize = 14.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            POSES.forEach { p ->
                OptionRow(title = p.name, subtitle = p.desc, on = state.pose == p.key) {
                    state.pose = p.key
                }
            }
        }
    }
}

@Composable
private fun Step7Palette(state: CoverWizardState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Choose a background.", style = HemType.serifDisplay.copy(fontSize = 22.sp, color = HemColors.Ink))
        Text(
            "Type color is picked automatically to stay readable on the chosen background.",
            style = HemType.body.copy(fontSize = 14.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.heightIn(min = 200.dp, max = 600.dp),
        ) {
            items(COVER_COLORS) { c ->
                val sel = state.color.equals(c.hex, ignoreCase = true)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = if (sel) 2.dp else 1.dp,
                            color = if (sel) HemColors.Bronze else HemColors.Hairline,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .background(if (sel) HemColors.CardCream else Color.Transparent)
                        .clickable { state.color = c.hex }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(parseHex(c.hex) ?: HemColors.CardCream)
                            .border(1.dp, HemColors.Hairline, CircleShape),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            c.name,
                            style = HemType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Ink),
                        )
                        Text(
                            c.desc,
                            style = HemType.body.copy(fontSize = 11.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted),
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Step8Review(state: CoverWizardState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Ready to set.", style = HemType.serifDisplay.copy(fontSize = 22.sp, color = HemColors.Ink))
        Text(
            "This is what your cover will look like. Nothing is saved yet — hit Create when you're happy.",
            style = HemType.body.copy(fontSize = 14.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted),
        )
        LivePreviewCard(state)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryRow("Genre", state.genre ?: "—")
            SummaryRow("Mood", state.mood ?: "—")
            SummaryRow("Masthead", state.masthead)
            SummaryRow("Position", state.layout.replaceFirstChar { it.uppercase() })
            SummaryRow("Headline", state.headline)
            if (state.pullQuote.isNotEmpty()) SummaryRow("Pull", "“${state.pullQuote}”")
            if (state.coverLines.isNotEmpty()) SummaryRow("Lines", state.coverLines.joinToString(" · "))
            SummaryRow("Pose", state.pose.replace('_', ' ').replaceFirstChar { it.uppercase() })
            SummaryRow("Color", state.color.uppercase())
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label.uppercase(),
            style = HemType.smallLabel.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Bronze, letterSpacing = 1.5.sp),
            modifier = Modifier.width(78.dp),
        )
        Text(
            value,
            style = HemType.body.copy(fontSize = 14.sp, color = HemColors.Ink),
            modifier = Modifier.weight(1f),
        )
    }
}

// ============================================================================
// Live preview
// ============================================================================

@Composable
private fun LivePreviewCard(state: CoverWizardState) {
    val bg = parseHex(state.color) ?: HemColors.CardCream
    // Contrast is against the user's chosen cover colour, not against the app theme —
    // these two must stay literal or a dark-theme Ink (cream) lands on a pale cover.
    val textColor = if (isDark(state.color)) Color.White else Color(0xFF141210)
    val centered = state.layout == "center"
    val align = if (centered) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start

    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(14.dp))
            .padding(20.dp),
        horizontalAlignment = align,
    ) {
        if (state.layout == "top") {
            Masthead(state.masthead, textColor)
            Spacer(Modifier.weight(1f))
        }
        if (centered) Spacer(Modifier.weight(1f))
        if (centered) Masthead(state.masthead, textColor)
        val hl = state.headline.ifEmpty { "YOUR HEADLINE" }
        Text(
            hl,
            style = HemType.serifDisplay.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = textAlign,
                letterSpacing = 1.sp,
            ),
            maxLines = 3,
        )
        if (state.pullQuote.isNotEmpty()) {
            Text(
                "“${state.pullQuote}”",
                style = HemType.body.copy(
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    color = textColor.copy(alpha = 0.7f),
                    textAlign = textAlign,
                ),
                maxLines = 3,
            )
        }
        if (state.layout == "bottom") {
            Spacer(Modifier.weight(1f))
            Masthead(state.masthead, textColor)
        }
    }
}

@Composable
private fun Masthead(text: String, color: Color) {
    Column {
        Text(
            text,
            style = HemType.serifDisplay.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                letterSpacing = 3.sp,
            ),
        )
        Text(
            "VOL 47 · JULY",
            style = HemType.body.copy(
                fontSize = 9.sp,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Medium,
                color = color.copy(alpha = 0.7f),
            ),
        )
    }
}

// ============================================================================
// Shared components local to this screen
// ============================================================================

@Composable
private fun OptionRow(title: String, subtitle: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (on) 2.dp else 1.dp,
                color = if (on) HemColors.Bronze else HemColors.Hairline,
                shape = RoundedCornerShape(12.dp),
            )
            .background(if (on) HemColors.CardCream else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (on) HemColors.Bronze else Color.Transparent)
                .border(1.dp, if (on) HemColors.Bronze else HemColors.Hairline, CircleShape)
                .padding(top = 2.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = HemType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Ink))
            Text(subtitle, style = HemType.body.copy(fontSize = 13.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted))
        }
    }
}

@Composable
private fun BorderedTextField(
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
            .padding(14.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                placeholder,
                style = textStyle.copy(color = HemColors.Muted),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle,
            cursorBrush = SolidColor(HemColors.Bronze),
            singleLine = !multiline,
            keyboardOptions = KeyboardOptions(capitalization = capitalize),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(items: List<String>, onPick: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items.forEach { s ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, HemColors.Hairline, RoundedCornerShape(999.dp))
                    .clickable { onPick(s) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(s, style = HemType.body.copy(fontSize = 12.sp, color = HemColors.Ink))
            }
        }
    }
}

@Composable
private fun BusyOverlay(eyebrow: String, title: String, tips: List<String>) {
    Box(
        Modifier
            .fillMaxSize()
            .background(HemColors.Paper.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Eyebrow(eyebrow)
            Text(title, style = HemType.serifDisplay.copy(fontSize = 22.sp, color = HemColors.Ink), textAlign = TextAlign.Center)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tips.forEach {
                    Text(it, style = HemType.body.copy(fontSize = 13.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ============================================================================
// Utilities
// ============================================================================

private fun parseHex(hex: String): Color? {
    val clean = hex.removePrefix("#")
    if (clean.length != 6) return null
    val v = clean.toLongOrNull(16) ?: return null
    val r = ((v shr 16) and 0xff).toInt() / 255f
    val g = ((v shr 8) and 0xff).toInt() / 255f
    val b = (v and 0xff).toInt() / 255f
    return Color(r, g, b, 1f)
}

private fun isDark(hex: String): Boolean {
    val c = parseHex(hex) ?: return false
    val lum = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
    return lum < 0.5f
}
