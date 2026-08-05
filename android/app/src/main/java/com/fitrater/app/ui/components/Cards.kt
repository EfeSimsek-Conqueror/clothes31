package com.fitrater.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType

/** Placeholder image tile — we don't ship photos, so we render a warm gradient tile. */
@Composable
fun PhotoTile(
    modifier: Modifier = Modifier,
    tint: Color = HemColors.Bronze,
    label: String? = null,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(tint.copy(alpha = 0.35f), tint.copy(alpha = 0.75f)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(label, style = HemType.serifTitle.copy(color = HemColors.OnScrim))
        }
    }
}

@Composable
fun TanBorderedCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Bronze.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(HemSpace.md),
    ) { content() }
}

@Composable
fun CircleToken(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = HemType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
        )
    }
}

@Composable
fun TwoUpHairlineRow(
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Box(
            Modifier
                .weight(1f)
                .padding(top = HemSpace.md, bottom = HemSpace.md, end = HemSpace.md),
        ) { left() }
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(HemColors.Hairline),
        )
        Box(
            Modifier
                .weight(1f)
                .padding(top = HemSpace.md, bottom = HemSpace.md, start = HemSpace.md),
        ) { right() }
    }
}
