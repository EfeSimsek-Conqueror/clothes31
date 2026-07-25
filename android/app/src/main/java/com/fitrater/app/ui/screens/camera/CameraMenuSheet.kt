package com.fitrater.app.ui.screens.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitrater.app.data.Supa
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.PrimaryButton
import com.fitrater.app.ui.theme.SerifDisplay

/** One-tap menu shown when the bottom-nav camera FAB is pressed. */
@Composable
fun CameraMenuSheet(
    onClose: () -> Unit,
    onPickScore: () -> Unit,
    onPickTryOn: () -> Unit,
    onPickVersus: () -> Unit,
    onPickRoast: () -> Unit,
    onPickDecode: () -> Unit,
    onOpenPaywall: () -> Unit = {},
    isPro: Boolean = false,
) {
    // Only one accordion open at a time. null = all collapsed.
    var openId by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.padding(end = HemSpace.sm)) {
                Text(
                    "TONIGHT'S TOOLS",
                    style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 2.sp),
                )
                Spacer(Modifier.height(HemSpace.xs))
                SerifDisplay("What's the look for?")
            }
            Spacer(Modifier.fillMaxWidth().weight(1f, fill = false))
            Box(
                Modifier.size(36.dp).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }
        Spacer(Modifier.height(HemSpace.lg))

        val toggle: (String) -> Unit = { id -> openId = if (openId == id) null else id }

        MenuHairline()
        AccordionRow(
            id = "score",
            icon = Icons.Filled.CameraAlt,
            title = "Score a look",
            subtitle = "One photo, one honest number.",
            cost = "${Supa.SCORE_COST} credits",
            description = "Snap or upload a fit and Hem calls it — score, per-piece notes, and one line of verdict.",
            needs = "1 photo of the outfit.",
            open = openId == "score",
            onTap = { toggle("score") },
            onGo = { onPickScore() },
        )
        MenuHairline()
        AccordionRow(
            id = "tryon",
            icon = Icons.Filled.AutoAwesome,
            title = "Try on",
            subtitle = "Wear a Studio piece on your own photo.",
            cost = "${Supa.TRYON_COST} credits",
            description = "Wear a Studio piece — or a garment you upload — on your own photo. Hem does the compositing.",
            needs = "1 photo of yourself + 1 garment.",
            open = openId == "tryon",
            onTap = { toggle("tryon") },
            onGo = { if (isPro) onPickTryOn() else onOpenPaywall() },
            goLabel = if (isPro) "GO →" else "Start 7-day trial →",
            proLocked = !isPro,
        )
        MenuHairline()
        AccordionRow(
            id = "versus",
            icon = Icons.Filled.People,
            title = "A vs B",
            subtitle = "Two photos, one winner.",
            cost = "${Supa.VERSUS_COST} credits",
            description = "Two fits, one winner. Hem picks and explains, sharp and one-sentence.",
            needs = "2 photos.",
            open = openId == "versus",
            onTap = { toggle("versus") },
            onGo = { onPickVersus() },
        )
        MenuHairline()
        AccordionRow(
            id = "roast",
            icon = Icons.Filled.LocalFireDepartment,
            title = "Roast this",
            subtitle = "Brutal mode, shareable card.",
            cost = "${Supa.ROAST_COST} credits",
            description = "Brutal mode, locked. Hem roasts the outfit, never the person, and always ends with a fix.",
            needs = "1 photo.",
            open = openId == "roast",
            onTap = { toggle("roast") },
            onGo = { onPickRoast() },
        )
        MenuHairline()
        AccordionRow(
            id = "decode",
            icon = Icons.Filled.Palette,
            title = "Decode style",
            subtitle = "Any reference — get the recipe.",
            cost = "${Supa.DECODE_COST} credits",
            description = "Any reference — celebrity, magazine, IG. Hem breaks down the pieces, palette, and one-line style signature.",
            needs = "1 reference photo.",
            open = openId == "decode",
            onTap = { toggle("decode") },
            onGo = { onPickDecode() },
        )
        MenuHairline()
        Spacer(Modifier.height(HemSpace.xl))
    }
}

@Composable
private fun MenuHairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(HemColors.Hairline))
}

@Composable
private fun AccordionRow(
    id: String,
    icon: ImageVector,
    title: String,
    subtitle: String,
    cost: String,
    description: String,
    needs: String,
    open: Boolean,
    onTap: () -> Unit,
    onGo: () -> Unit,
    goLabel: String = "GO →",
    proLocked: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onTap)
                .padding(vertical = HemSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = HemColors.Ink,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.size(HemSpace.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = HemType.serifSection.copy(fontSize = 20.sp))
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = HemType.bodyMuted.copy(fontSize = 13.sp))
            }
            Spacer(Modifier.size(HemSpace.sm))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    cost.uppercase(),
                    style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 1.5.sp),
                )
                if (proLocked) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(HemColors.Bronze)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "PRO",
                            style = HemType.smallLabel.copy(
                                color = Color.White,
                                letterSpacing = 1.5.sp,
                                fontSize = 10.sp,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.size(HemSpace.xs))
            // Chevron: right when collapsed, down when open. Rotated for a smooth transition.
            val icn = if (open) Icons.Default.ExpandMore else Icons.Default.KeyboardArrowRight
            Icon(
                icn,
                contentDescription = null,
                tint = HemColors.Bronze,
                modifier = Modifier.size(22.dp).rotate(0f),
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(180)) + expandVertically(
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            ),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = HemSpace.md + 24.dp, end = HemSpace.xs, bottom = HemSpace.md),
            ) {
                Text(
                    description,
                    style = HemType.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
                )
                Spacer(Modifier.height(HemSpace.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "NEEDS",
                        style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 1.5.sp),
                    )
                    Spacer(Modifier.size(HemSpace.xs))
                    Text(
                        needs,
                        style = HemType.bodyMuted.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    )
                }
                Spacer(Modifier.height(HemSpace.md))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    Box(Modifier.fillMaxWidth(if (proLocked) 0.75f else 0.55f)) {
                        PrimaryButton(label = goLabel, onClick = onGo)
                    }
                }
            }
        }
    }
}
