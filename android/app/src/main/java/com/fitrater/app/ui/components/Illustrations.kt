package com.fitrater.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.fitrater.app.ui.theme.HemColors

/**
 * Editorial pen-sketch illustrations, drawn with hairline ink strokes.
 * All sized 120dp square and use `HemColors.Ink` at low alpha.
 */

private val InkStroke = Stroke(
    width = 1.5f,
    cap = StrokeCap.Round,
    join = StrokeJoin.Round,
)

@Composable
fun StudioEmptyIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier.size(120.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val ink = HemColors.Ink.copy(alpha = 0.55f)

        // Hanger hook — small arc at top
        val hook = Path().apply {
            moveTo(cx, h * 0.14f)
            cubicTo(
                cx + w * 0.10f, h * 0.10f,
                cx + w * 0.10f, h * 0.22f,
                cx, h * 0.22f,
            )
        }
        drawPath(hook, ink, style = InkStroke)

        // Hanger triangle bar
        val bar = Path().apply {
            moveTo(cx, h * 0.22f)
            lineTo(w * 0.22f, h * 0.36f)
            lineTo(w * 0.78f, h * 0.36f)
            close()
        }
        drawPath(bar, ink, style = InkStroke)

        // Garment silhouette hanging from the hanger — dress-like drape
        val garment = Path().apply {
            // shoulders
            moveTo(w * 0.28f, h * 0.36f)
            lineTo(w * 0.20f, h * 0.48f)
            // sleeve down + hem out
            lineTo(w * 0.16f, h * 0.86f)
            cubicTo(
                w * 0.30f, h * 0.94f,
                w * 0.70f, h * 0.94f,
                w * 0.84f, h * 0.86f,
            )
            lineTo(w * 0.80f, h * 0.48f)
            lineTo(w * 0.72f, h * 0.36f)
        }
        drawPath(garment, ink, style = InkStroke)

        // Center seam — dashed to feel like a tailor's mark
        val seam = Path().apply {
            moveTo(cx, h * 0.40f)
            lineTo(cx, h * 0.88f)
        }
        drawPath(
            seam,
            ink.copy(alpha = 0.35f),
            style = Stroke(
                width = 1.2f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f),
            ),
        )
    }
}

@Composable
fun JournalEmptyIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier.size(120.dp)) {
        val w = size.width
        val h = size.height
        val ink = HemColors.Ink.copy(alpha = 0.55f)

        // Notebook — open book with two facing pages, viewed slightly above
        val leftPage = Path().apply {
            moveTo(w * 0.10f, h * 0.30f)
            lineTo(w * 0.50f, h * 0.22f)
            lineTo(w * 0.50f, h * 0.80f)
            lineTo(w * 0.10f, h * 0.70f)
            close()
        }
        drawPath(leftPage, ink, style = InkStroke)

        val rightPage = Path().apply {
            moveTo(w * 0.50f, h * 0.22f)
            lineTo(w * 0.90f, h * 0.30f)
            lineTo(w * 0.90f, h * 0.70f)
            lineTo(w * 0.50f, h * 0.80f)
            close()
        }
        drawPath(rightPage, ink, style = InkStroke)

        // Handwritten squiggle lines on both pages
        val muted = ink.copy(alpha = 0.4f)
        val squiggleStroke = Stroke(width = 1f, cap = StrokeCap.Round)
        listOf(0.36f, 0.46f, 0.56f, 0.66f).forEach { yFrac ->
            val y = h * yFrac
            val left = Path().apply {
                moveTo(w * 0.16f, y)
                cubicTo(
                    w * 0.24f, y - 3f,
                    w * 0.36f, y + 3f,
                    w * 0.44f, y,
                )
            }
            drawPath(left, muted, style = squiggleStroke)
            val right = Path().apply {
                moveTo(w * 0.56f, y)
                cubicTo(
                    w * 0.64f, y - 3f,
                    w * 0.76f, y + 3f,
                    w * 0.84f, y,
                )
            }
            drawPath(right, muted, style = squiggleStroke)
        }
    }
}

@Composable
fun MissingPolaroidIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier.size(120.dp)) {
        val w = size.width
        val h = size.height
        val ink = HemColors.Ink.copy(alpha = 0.55f)

        // Polaroid outline
        val frame = Path().apply {
            moveTo(w * 0.18f, h * 0.16f)
            lineTo(w * 0.82f, h * 0.16f)
            lineTo(w * 0.82f, h * 0.86f)
            lineTo(w * 0.18f, h * 0.86f)
            close()
        }
        drawPath(frame, ink, style = InkStroke)

        // Inner photo window
        val window = Path().apply {
            moveTo(w * 0.24f, h * 0.22f)
            lineTo(w * 0.76f, h * 0.22f)
            lineTo(w * 0.76f, h * 0.68f)
            lineTo(w * 0.24f, h * 0.68f)
            close()
        }
        drawPath(window, ink, style = InkStroke)

        // The X marking a lost image
        drawLine(
            color = ink,
            start = Offset(w * 0.28f, h * 0.26f),
            end = Offset(w * 0.72f, h * 0.64f),
            strokeWidth = 1.8f,
        )
        drawLine(
            color = ink,
            start = Offset(w * 0.72f, h * 0.26f),
            end = Offset(w * 0.28f, h * 0.64f),
            strokeWidth = 1.8f,
        )
    }
}
