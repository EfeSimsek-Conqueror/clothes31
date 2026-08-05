package com.fitrater.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitrater.app.ui.theme.HemColors

/** Adds a subtle animated shimmer over any surface — used for skeleton placeholders. */
@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    return this.drawWithContent {
        drawContent()
        val width = size.width
        val startX = -width + (progress * width * 2f)
        val brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                HemColors.OnScrim.copy(alpha = 0.32f),
                Color.Transparent,
            ),
            start = Offset(startX, 0f),
            end = Offset(startX + width, size.height),
        )
        drawRect(brush = brush)
    }
}

@Composable
fun SkeletonBar(
    height: Dp = 14.dp,
    widthFraction: Float = 1f,
    corner: Dp = 6.dp,
) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(HemColors.CardCream)
            .shimmer(),
    )
}
