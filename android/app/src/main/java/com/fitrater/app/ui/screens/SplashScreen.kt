package com.fitrater.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.PrimaryButton
import com.fitrater.app.ui.theme.SerifDisplay
import androidx.compose.material3.Text

@Composable
fun SplashScreen(onContinue: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(HemColors.Paper)
            .padding(horizontal = HemSpace.gutter, vertical = HemSpace.xl),
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Eyebrow("FITRATER")
            Spacer(Modifier.height(HemSpace.md))
            SerifDisplay("Your closet,\nfinally honest.")
            Spacer(Modifier.height(HemSpace.md))
            Text(
                "Score your looks, try pieces on in the mirror, and let Hem keep the journal.",
                style = HemType.bodyMuted,
            )
            Spacer(Modifier.height(HemSpace.xl))
            PrimaryButton(label = "Continue", onClick = onContinue)
            Spacer(Modifier.height(HemSpace.md))
        }
    }
}
