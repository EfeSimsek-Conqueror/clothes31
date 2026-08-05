package com.fitrater.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitrater.app.data.model.ProfileUpsert
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.PrimaryButton
import com.fitrater.app.ui.theme.SerifDisplay
import kotlinx.coroutines.launch

private object OnboardBuffer {
    var gender: String? = null
}

@Composable
fun Onboard1Screen(onContinue: () -> Unit) {
    val options = listOf("Female", "Male", "Other")
    var picked by remember { mutableStateOf(OnboardBuffer.gender) }
    Column(
        Modifier
            .fillMaxSize()
            .background(HemColors.Paper)
            .padding(horizontal = HemSpace.gutter, vertical = HemSpace.xl),
    ) {
        Spacer(Modifier.height(HemSpace.xl))
        Eyebrow("STEP 1 OF 2")
        Spacer(Modifier.height(HemSpace.md))
        SerifDisplay("Who's dressing?")
        Spacer(Modifier.height(HemSpace.md))
        Text("Hem tailors cuts and verdicts to you.", style = HemType.bodyMuted)
        Spacer(Modifier.height(HemSpace.xl))
        options.forEach { label ->
            OnboardChoiceCard(label, selected = picked == label) { picked = label }
            Spacer(Modifier.height(HemSpace.sm))
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(
            label = "Continue",
            onClick = {
                OnboardBuffer.gender = picked
                onContinue()
            },
            enabled = picked != null,
        )
        Spacer(Modifier.height(HemSpace.md))
    }
}

@Composable
private fun OnboardChoiceCard(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) HemColors.Ink else HemColors.CardCream
    val fg = if (selected) HemColors.OnInk else HemColors.Ink
    Box(
        Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, HemColors.Ink.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = HemSpace.lg),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            label,
            style = HemType.serifSection.copy(color = fg),
        )
    }
}

@Composable
fun Onboard2Screen(onDone: () -> Unit) {
    val chips = listOf(
        "minimal", "streetwear", "classic", "romantic",
        "sporty", "bold prints", "earth tones", "monochrome",
    )
    val picked = remember { mutableStateOf(setOf<String>()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(HemColors.Paper)
            .padding(horizontal = HemSpace.gutter, vertical = HemSpace.xl),
    ) {
        Spacer(Modifier.height(HemSpace.xl))
        Eyebrow("STEP 2 OF 2")
        Spacer(Modifier.height(HemSpace.md))
        SerifDisplay("Your vibe.")
        Spacer(Modifier.height(HemSpace.md))
        Text(
            "Pick a few — Hem scores against your taste, not a runway's.",
            style = HemType.bodyMuted,
        )
        Spacer(Modifier.height(HemSpace.xl))
        ChipFlow(chips = chips, picked = picked.value, onToggle = { chip ->
            picked.value = if (chip in picked.value) picked.value - chip else picked.value + chip
        })
        Spacer(Modifier.weight(1f))
        if (error != null) {
            Text(error!!, style = HemType.bodyMuted.copy(color = HemColors.Bronze, fontSize = 13.sp))
            Spacer(Modifier.height(HemSpace.xs))
        }
        PrimaryButton(
            label = if (saving) "Saving…" else "Continue — ${picked.value.size} picked",
            onClick = {
                if (saving) return@PrimaryButton
                val uid = Repo.userId
                if (uid == null) {
                    error = "Not signed in"
                    return@PrimaryButton
                }
                saving = true
                error = null
                scope.launch {
                    runCatching {
                        Repo.upsertProfile(
                            ProfileUpsert(
                                id = uid,
                                email = Repo.userEmail,
                                gender = OnboardBuffer.gender,
                                style_tags = picked.value.toList(),
                                onboarded = true,
                            ),
                        )
                    }.onSuccess {
                        OnboardBuffer.gender = null
                        onDone()
                    }.onFailure {
                        error = it.message ?: "Failed to save"
                    }
                    saving = false
                }
            },
            enabled = picked.value.isNotEmpty() && !saving,
        )
        Spacer(Modifier.height(HemSpace.md))
    }
}

@Composable
private fun ChipFlow(chips: List<String>, picked: Set<String>, onToggle: (String) -> Unit) {
    Column {
        chips.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = HemSpace.xs)) {
                row.forEachIndexed { idx, chip ->
                    Chip(
                        label = chip,
                        selected = chip in picked,
                        onClick = { onToggle(chip) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = if (idx == 0) HemSpace.xs else 0.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) HemColors.Ink else Color.Transparent
    val fg = if (selected) HemColors.OnInk else HemColors.Ink
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, HemColors.Ink.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = HemType.body.copy(color = fg, fontSize = 14.sp),
        )
    }
}
