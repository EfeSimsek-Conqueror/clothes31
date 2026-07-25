package com.fitrater.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.PrimaryButton
import com.fitrater.app.ui.theme.SerifDisplay

@Composable
fun PaywallScreen(onContinue: () -> Unit) {
    var picked by remember { mutableStateOf("annual") }
    Column(
        Modifier
            .fillMaxSize()
            .background(HemColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HemSpace.gutter, vertical = HemSpace.xl),
    ) {
        Spacer(Modifier.height(HemSpace.lg))
        // gold badge
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Brush.horizontalGradient(listOf(HemColors.GoldStart, HemColors.GoldEnd)))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                "FITSCORE PRO",
                style = HemType.smallLabel.copy(color = Color.White, letterSpacing = 2.sp),
            )
        }
        Spacer(Modifier.height(HemSpace.md))
        SerifDisplay("Every feature.\nNothing held back.")
        Spacer(Modifier.height(HemSpace.lg))
        listOf(
            "i." to "Unlimited look scoring with Hem's notes",
            "ii." to "The Mirror — try any piece on your photo",
            "iii." to "Closet auto-tagging & the Sunday letter",
        ).forEach { (roman, body) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(
                    roman,
                    style = HemType.serifQuote.copy(color = HemColors.Bronze),
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(body, style = HemType.body)
            }
        }
        Spacer(Modifier.height(HemSpace.lg))
        PlanCard(
            title = "Annual",
            price = "7 days free, then $59.99 / year",
            badge = "SAVE 50%",
            selected = picked == "annual",
            onClick = { picked = "annual" },
        )
        Spacer(Modifier.height(HemSpace.sm))
        PlanCard(
            title = "Monthly",
            price = "$9.99 / month",
            selected = picked == "monthly",
            onClick = { picked = "monthly" },
        )
        Spacer(Modifier.height(HemSpace.xl))
        PrimaryButton(label = "Start 7-day free trial", onClick = onContinue)
        Spacer(Modifier.height(HemSpace.sm))
        Text(
            "Restore purchases",
            style = HemType.bodyMuted,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onContinue() }
                .padding(vertical = HemSpace.sm),
        )
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    badge: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) HemColors.Bronze else HemColors.Ink.copy(alpha = 0.2f)
    val borderWidth = if (selected) 2.dp else 1.dp
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HemColors.CardCream)
            .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(HemSpace.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = HemType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp))
                Spacer(Modifier.height(2.dp))
                Text(price, style = HemType.bodyMuted)
            }
            if (badge != null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(HemColors.Bronze)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(badge, style = HemType.smallLabel.copy(color = Color.White, letterSpacing = 1.5.sp))
                }
            }
        }
    }
}
