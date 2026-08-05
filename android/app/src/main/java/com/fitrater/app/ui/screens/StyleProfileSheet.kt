package com.fitrater.app.ui.screens

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.PrimaryButton
import com.fitrater.app.ui.theme.SerifDisplay
import com.fitrater.app.util.ToastBus
import kotlinx.coroutines.launch

private val GENDERS = listOf("Female", "Male", "Other")
private val STYLE_TAGS = listOf(
    "minimal", "streetwear", "classic", "romantic",
    "sporty", "bold prints", "earth tones", "monochrome",
)
private val FABRIC_OPTIONS = listOf(
    "Cotton", "Linen", "Denim", "Wool", "Leather", "Silk", "Knit",
)

private data class Swatch(val name: String, val hex: String)
private val COLOR_OPTIONS = listOf(
    Swatch("Bone", "#EAE1D0"), Swatch("Cream", "#F3EEE4"), Swatch("Sand", "#D7C4A3"),
    Swatch("Cocoa", "#5B3A22"), Swatch("Ink", "#141210"), Swatch("Bronze", "#B0743A"),
    Swatch("Rust", "#9E4A22"), Swatch("Sage", "#93A187"), Swatch("Olive", "#5C6032"),
    Swatch("Slate", "#5A6B73"), Swatch("Wine", "#5A1E29"), Swatch("Powder", "#B7C9D9"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StyleProfileSheetContent(onClose: () -> Unit) {
    var loaded by remember { mutableStateOf(false) }
    var gender by remember { mutableStateOf<String?>(null) }
    val tags = remember { mutableStateListOf<String>() }
    val fabrics = remember { mutableStateListOf<String>() }
    val colors = remember { mutableStateListOf<String>() }
    var sensitivities by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var savedGender by remember { mutableStateOf<String?>(null) }
    val savedTags = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        runCatching { Repo.currentProfile() }.getOrNull()?.let { p ->
            gender = p.gender
            savedGender = p.gender
            p.style_tags?.let {
                tags.clear(); tags.addAll(it)
                savedTags.clear(); savedTags.addAll(it)
            }
            p.preferred_fabrics?.let { fabrics.clear(); fabrics.addAll(it) }
            p.preferred_colors?.let { colors.clear(); colors.addAll(it) }
            sensitivities = p.sensitivities.orEmpty()
        }
        loaded = true
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("STYLE PROFILE")
                Spacer(Modifier.height(HemSpace.xs))
                SerifDisplay("Style profile")
            }
            Text(
                "×",
                style = HemType.serifSection.copy(fontSize = 28.sp),
                modifier = Modifier.clickable(onClick = onClose).padding(8.dp),
            )
        }
        Spacer(Modifier.height(HemSpace.xs))
        Text(
            "Hem uses this to tune every generation and score.",
            style = HemType.bodyMuted,
        )

        // Current at-a-glance review (read-only)
        if (loaded && (!savedGender.isNullOrBlank() || savedTags.isNotEmpty())) {
            Spacer(Modifier.height(HemSpace.lg))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HemColors.CardCream)
                    .border(1.dp, HemColors.Hairline, RoundedCornerShape(12.dp))
                    .padding(HemSpace.md),
            ) {
                Text(
                    "CURRENT",
                    style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 2.sp),
                )
                Spacer(Modifier.height(HemSpace.xs))
                Text(
                    savedGender ?: "—",
                    style = HemType.body.copy(fontWeight = FontWeight.SemiBold),
                )
                if (savedTags.isNotEmpty()) {
                    Spacer(Modifier.height(HemSpace.xs))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        savedTags.forEach { tag ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .border(1.dp, HemColors.Hairline, RoundedCornerShape(999.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(tag, style = HemType.body.copy(fontSize = 12.sp, color = HemColors.Muted))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(HemSpace.lg))
        Eyebrow("GENDER")
        Spacer(Modifier.height(HemSpace.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HemSpace.xs)) {
            GENDERS.forEach { g ->
                RadioPill(
                    label = g,
                    selected = gender == g,
                    onClick = { gender = g },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(HemSpace.lg))
        Eyebrow("STYLE TAGS")
        Spacer(Modifier.height(HemSpace.xs))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            STYLE_TAGS.forEach { t ->
                val active = tags.contains(t)
                SimpleChip(label = t, active = active) {
                    if (active) tags.remove(t) else tags.add(t)
                }
            }
        }

        Spacer(Modifier.height(HemSpace.lg))
        Eyebrow("PREFERRED FABRICS")
        Spacer(Modifier.height(HemSpace.xs))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FABRIC_OPTIONS.forEach { f ->
                val active = fabrics.contains(f)
                SimpleChip(label = f, active = active) {
                    if (active) fabrics.remove(f) else fabrics.add(f)
                }
            }
        }

        Spacer(Modifier.height(HemSpace.lg))
        Eyebrow("PREFERRED COLORS")
        Spacer(Modifier.height(HemSpace.xs))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            COLOR_OPTIONS.forEach { c ->
                val active = colors.contains(c.name)
                SwatchChip(swatch = c, active = active) {
                    if (active) colors.remove(c.name) else colors.add(c.name)
                }
            }
        }

        Spacer(Modifier.height(HemSpace.lg))
        Eyebrow("SENSITIVITIES")
        Spacer(Modifier.height(HemSpace.xs))
        Box(
            Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HemColors.CardCream)
                .border(1.dp, HemColors.Hairline, RoundedCornerShape(12.dp))
                .padding(HemSpace.md),
        ) {
            BasicTextField(
                value = sensitivities,
                onValueChange = { sensitivities = it },
                textStyle = TextStyle(color = HemColors.Ink, fontSize = 14.sp),
                cursorBrush = SolidColor(HemColors.Ink),
                modifier = Modifier.fillMaxWidth(),
            )
            if (sensitivities.isEmpty()) {
                Text(
                    "e.g. avoid animal materials, allergic to wool…",
                    style = HemType.bodyMuted.copy(fontSize = 14.sp),
                )
            }
        }

        Spacer(Modifier.height(HemSpace.xl))
        PrimaryButton(
            label = if (saving) "Saving…" else "Save changes",
            enabled = loaded && !saving,
            onClick = {
                if (saving) return@PrimaryButton
                saving = true
                scope.launch {
                    runCatching {
                        Repo.updateStyleProfile(
                            gender = gender,
                            style_tags = tags.toList(),
                            preferred_fabrics = fabrics.toList(),
                            preferred_colors = colors.toList(),
                            sensitivities = sensitivities.ifBlank { "" },
                        )
                    }.onSuccess {
                        ToastBus.post("Style profile updated")
                        onClose()
                    }.onFailure {
                        ToastBus.post("Couldn't save: ${it.message ?: "error"}")
                    }
                    saving = false
                }
            },
        )
        Spacer(Modifier.height(HemSpace.xl))
    }
}

@Composable
private fun RadioPill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) HemColors.Ink else Color.Transparent)
            .border(1.dp, HemColors.Ink.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = HemType.body.copy(
                color = if (selected) HemColors.OnInk else HemColors.Ink,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
        )
    }
}

@Composable
private fun SimpleChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) HemColors.Ink else Color.Transparent)
            .border(1.dp, HemColors.Ink.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = HemType.body.copy(
                color = if (active) HemColors.OnInk else HemColors.Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun SwatchChip(swatch: Swatch, active: Boolean, onClick: () -> Unit) {
    val col = runCatching {
        Color(("FF${swatch.hex.removePrefix("#")}").toLong(16))
    }.getOrDefault(HemColors.Muted)
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) HemColors.Ink else Color.Transparent)
            .border(1.dp, HemColors.Ink.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(col)
                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            swatch.name,
            style = HemType.body.copy(
                color = if (active) HemColors.OnInk else HemColors.Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}
