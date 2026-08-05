package com.fitrater.app.ui.screens.subpages

import android.app.Activity
import android.util.Log
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalContext
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
import com.fitrater.app.util.userMessage
import kotlinx.coroutines.launch

@Composable
fun AppearanceScreen(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf("system") }
    var scale by remember { mutableStateOf(1.0) }
    var reduceMotion by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Show the local copy immediately — it is what the app is actually rendering —
        // then fold in the server profile if it has anything to say.
        theme = AppPrefs.theme.value
        scale = AppPrefs.textScale.value
        reduceMotion = AppPrefs.reduceMotion.value
        runCatching { Repo.currentProfile() }.getOrNull()?.let { p ->
            AppPrefs.hydrateFromProfile(p.theme, p.text_scale, p.reduce_motion)
            theme = AppPrefs.theme.value
            scale = AppPrefs.textScale.value
            reduceMotion = AppPrefs.reduceMotion.value
        }
        loaded = true
    }

    fun persist() {
        scope.launch {
            runCatching {
                Repo.updateAppearance(theme = theme, textScale = scale, reduceMotion = reduceMotion)
            }.onFailure {
                // Never `it.message` here: Postgrest's exception text is the whole
                // request dump, including the Authorization bearer token and apikey,
                // and ToastBus puts it straight on screen. The choice is already
                // applied and stored locally, so this is only about the server copy.
                Log.w("Appearance", "Couldn't sync appearance to profile", it)
                ToastBus.post(it.userMessage("Couldn't sync that setting to your profile."))
            }
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
                    AppPrefs.setTheme(it)
                    persist()
                },
            )

            Spacer(Modifier.height(HemSpace.lg))
            Eyebrow("TEXT SIZE")
            Spacer(Modifier.height(HemSpace.sm))
            // Bucket with a margin rather than on exact equality: the value round-trips
            // through a 32-bit float in SharedPreferences, so a stored 1.15 reads back as
            // 1.1499999… and an exact `>= 1.15` would show the wrong segment selected.
            val scaleKey = when {
                scale < 0.95 -> "small"
                scale > 1.05 -> "large"
                else -> "regular"
            }
            Segmented(
                options = listOf("small" to "Small", "regular" to "Regular", "large" to "Large"),
                selected = scaleKey,
                onSelect = {
                    val next = when (it) {
                        "small" -> 0.9
                        "large" -> 1.15
                        else -> 1.0
                    }
                    if (next != scale) {
                        scale = next
                        AppPrefs.setTextScale(next)
                        persist()
                        // Text size is an activity configuration override (so it reaches
                        // bottom sheets too), which only re-reads on recreate. The nav
                        // back stack is saved, so this screen stays open.
                        (context as? Activity)?.recreate()
                    }
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
                        AppPrefs.setReduceMotion(it)
                        persist()
                    },
                    enabled = loaded,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = HemColors.Ink,
                        checkedThumbColor = HemColors.OnInk,
                        uncheckedTrackColor = HemColors.Paper,
                        uncheckedThumbColor = HemColors.Muted,
                        uncheckedBorderColor = HemColors.ChipBorder,
                    ),
                )
            }
            SubHairline()
            Spacer(Modifier.height(HemSpace.xl))
            Text(
                "Theme and text size apply everywhere, right away.",
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
                    .heightIn(min = 44.dp)
                    .background(if (isSel) HemColors.Ink else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label.uppercase(),
                    style = HemType.smallLabel.copy(
                        color = if (isSel) HemColors.OnInk else HemColors.Ink,
                        letterSpacing = 2.sp,
                    ),
                )
            }
        }
    }
}
