package com.fitrater.app.ui.screens.subpages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.SerifTitle

/** Shared shell for the "You" sub-pages — cream paper, X close, eyebrow + serif title. */
@Composable
fun SubpageScaffold(
    eyebrow: String,
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(HemColors.Paper),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = HemColors.Ink)
            }
        }
        Column(
            Modifier.padding(horizontal = HemSpace.gutter),
        ) {
            Eyebrow(eyebrow)
            Spacer(Modifier.height(HemSpace.xs))
            SerifTitle(title)
            Spacer(Modifier.height(HemSpace.md))
            content()
        }
    }
}

@Composable
fun SubHairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(HemColors.Hairline))
}
