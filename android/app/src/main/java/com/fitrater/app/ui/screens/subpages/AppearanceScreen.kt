package com.fitrater.app.ui.screens.subpages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.util.AppPrefs
import com.fitrater.app.util.ToastBus
import kotlinx.coroutines.launch

@Composable
fun AppearanceScreen(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf("system") }
    var scale by remember { mutableStateOf(1.0) }
    var reduceMotion by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { Repo.currentProfile() }.getOrNull()?.let { p ->
            theme = p.theme ?: "system"
            scale = p.text_scale ?: 1.0
            reduceMotion = p.reduce_motion ?: false
        }
        // Sync in-memory prefs
        AppPrefs.theme.value = theme
        AppPrefs.textScale.value = scale
        AppPrefs.reduceMotion.value = reduceMotion
        loaded = true
    }

    fun persist() {
        scope.launch {
            runCatching {
                Repo.updateAppearance(theme = theme, textScale = scale, reduceMotion = reduceMotion)
            }.onFailure { ToastBus.post("Couldn't save: ${it.message ?: "error"}") }
        }
    }

    SubpageScaffold(eyebrow = "SETTINGS", title = "Appearance", onClose = onClose) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Eyebrow("THEME")
            Spacer(Modifier.height(HemSpace.sm))
            Segmented(
                options = listOf("system" to "System", "light" to "Light", "dark" to "Dark"),
                selected = theme,
                onSelect = {
                    theme = it
                    AppPrefs.theme.value = it
                    persist()
                },
            )

            Spacer(Modifier.height(HemSpace.lg))
            Eyebrow("TEXT SIZE")
            Spacer(Modifier.height(HemSpace.sm))
            val scaleKey = when {
                scale <= 0.9 -> "small"
                scale >= 1.15 -> "large"
                else -> "regular"
            }
            Segmented(
                options = listOf("small" to "Small", "regular" to "Regular", "large" to "Large"),
                selected = scaleKey,
                onSelect = {
                    scale = when (it) {
                        "small" -> 0.9
                        "large" -> 1.2
                        else -> 1.0
                    }
                    AppPrefs.textScale.value = scale
                    persist()
                },
            )

            Spacer(Modifier.height(HemSpace.lg))
            Eyebrow("MOTION")
            Spacer(Modifier.height(HemSpace.sm))
            SubHairline()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = HemSpace.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Reduce animations", style = HemType.body.copy(fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(2.dp))
                    Text("Skip transitions and shimmer effects.", style = HemType.bodyMuted.copy(fontSize = 13.sp))
                }
                Switch(
                    checked = reduceMotion,
                    onCheckedChange = {
                        reduceMotion = it
                        AppPrefs.reduceMotion.value = it
                        persist()
                    },
                    enabled = loaded,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = HemColors.Ink,
                        checkedThumbColor = Color.White,
                    ),
                )
            }
            SubHairline()
            Spacer(Modifier.height(HemSpace.xl))
            Text(
                "Text size and theme apply on next app launch.",
                style = HemType.bodyMuted.copy(fontSize = 12.sp),
            )
            Spacer(Modifier.height(HemSpace.xxl))
        }
    }
}

@Composable
private fun Segmented(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(10.dp)),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        options.forEach { (key, label) ->
            val isSel = key == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(if (isSel) HemColors.Ink else Color.Transparent)
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label.uppercase(),
                    style = HemType.smallLabel.copy(
                        color = if (isSel) Color.White else HemColors.Ink,
                        letterSpacing = 2.sp,
                    ),
                )
            }
        }
    }
}
