package com.fitrater.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.SerifDisplay

enum class VaryAxis { Colors, Silhouette, Fabric, Details, Surprise }

private data class AxisRow(
    val axis: VaryAxis,
    val title: String,
    val subtitle: String,
)

private val ROWS = listOf(
    AxisRow(VaryAxis.Colors, "Colors", "Same cut, three new palettes."),
    AxisRow(VaryAxis.Silhouette, "Silhouette", "Same colors, three different fits."),
    AxisRow(VaryAxis.Fabric, "Fabric", "Same shape, three material stories."),
    AxisRow(VaryAxis.Details, "Details", "Same base, three added notes."),
    AxisRow(VaryAxis.Surprise, "Surprise me", "Let Hem freewheel across every axis."),
)

@Composable
fun VariationAxisSheetContent(onPick: (VaryAxis) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
    ) {
        Eyebrow("VARY BY")
        Spacer(Modifier.height(HemSpace.xs))
        SerifDisplay("How should Hem riff on this?")
        Spacer(Modifier.height(HemSpace.lg))

        ROWS.forEachIndexed { idx, row ->
            AxisRowView(row, onClick = { onPick(row.axis) })
            if (idx < ROWS.size - 1) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(HemColors.Hairline))
            }
        }
        Spacer(Modifier.height(HemSpace.xl))
    }
}

@Composable
private fun AxisRowView(row: AxisRow, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = HemSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                style = HemType.serifSection.copy(fontSize = 20.sp),
            )
            Spacer(Modifier.height(2.dp))
            Text(row.subtitle, style = HemType.bodyMuted.copy(fontSize = 13.sp))
        }
        Text(
            "3 × ${com.fitrater.app.data.Supa.SINGLE_PIECE_COST} CREDITS",
            style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 1.5.sp),
        )
    }
}
