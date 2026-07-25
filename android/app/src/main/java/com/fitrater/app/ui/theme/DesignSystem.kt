package com.fitrater.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitrater.app.R

/** Palette from the design brief. */
object HemColors {
    val Paper = Color(0xFFF3EEE4)
    val CardCream = Color(0xFFF7F2E8)
    val Ink = Color(0xFF141210)
    val Muted = Color(0xFF6B6459)
    val Bronze = Color(0xFFB0743A)
    val GoldStart = Color(0xFFC99A5B)
    val GoldEnd = Color(0xFF8C6033)
    val Hairline = Color(0x22141210)
    val ChipBorder = Color(0x33141210)
}

/** Spacing tokens. */
object HemSpace {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val gutter = 24.dp
}

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val playfair = GoogleFont("Playfair Display")
private val inter = GoogleFont("Inter")

val SerifFamily = FontFamily(
    Font(googleFont = playfair, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = playfair, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = playfair, fontProvider = provider, weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(googleFont = playfair, fontProvider = provider, weight = FontWeight.SemiBold),
)

val SansFamily = FontFamily(
    Font(googleFont = inter, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = inter, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = inter, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = inter, fontProvider = provider, weight = FontWeight.Bold),
)

/** Reusable text styles. */
object HemType {
    val eyebrow = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        color = HemColors.Bronze,
    )
    val eyebrowMuted = eyebrow.copy(color = HemColors.Muted)
    val serifDisplay = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        color = HemColors.Ink,
    )
    val serifTitle = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        color = HemColors.Ink,
    )
    val serifSection = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = HemColors.Ink,
    )
    val serifQuote = TextStyle(
        fontFamily = SerifFamily,
        fontStyle = FontStyle.Italic,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        color = HemColors.Ink,
    )
    val body = TextStyle(
        fontFamily = SansFamily,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = HemColors.Ink,
    )
    val bodyMuted = body.copy(color = HemColors.Muted)
    val label = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 2.sp,
        color = Color.White,
    )
    val labelInk = label.copy(color = HemColors.Ink)
    val smallLabel = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
        color = HemColors.Ink,
    )
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, muted: Boolean = false) {
    Text(
        text = text.uppercase(),
        style = if (muted) HemType.eyebrowMuted else HemType.eyebrow,
        modifier = modifier,
    )
}

@Composable
fun SerifDisplay(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = HemType.serifDisplay, modifier = modifier)
}

@Composable
fun SerifTitle(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = HemType.serifTitle, modifier = modifier)
}

@Composable
fun PullQuote(text: String, attribution: String = "— Hem", modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = "“$text”", style = HemType.serifQuote)
        Spacer(Modifier.height(HemSpace.xs))
        Text(attribution, style = HemType.bodyMuted.copy(fontStyle = FontStyle.Italic))
    }
}

@Composable
fun ScoreChip(score: Double, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = String.format("%.1f", score),
            style = TextStyle(
                fontFamily = SerifFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = HemColors.Ink,
            ),
        )
    }
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingGlyph: String? = null,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) HemColors.Ink else HemColors.Muted)
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leadingGlyph != null) {
            Text(leadingGlyph, style = HemType.label)
            Spacer(Modifier.width(8.dp))
        }
        Text(label.uppercase(), style = HemType.label)
    }
}

@Composable
fun OutlinedPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    filled: Boolean = false,
) {
    val bg = if (filled) Color.White else Color.Transparent
    val border = BorderStroke(1.dp, HemColors.Ink.copy(alpha = 0.55f))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = HemType.body.copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
fun SectionRow(
    title: String,
    description: String,
    badge: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = HemSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = HemType.body.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(2.dp))
            Text(description, style = HemType.bodyMuted.copy(fontSize = 13.sp))
        }
        if (badge != null) {
            Spacer(Modifier.width(HemSpace.sm))
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, HemColors.Bronze, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    badge.uppercase(),
                    style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 1.5.sp),
                )
            }
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(HemColors.Hairline),
    )
}

@Composable
fun PageScaffold(
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(HemColors.Paper),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = HemSpace.gutter),
        ) { content() }
    }
}

fun buildTitleWithBreak(line1: String, line2: String) = buildAnnotatedString {
    withStyle(SpanStyle(color = HemColors.Ink)) { append(line1) }
    append("\n")
    withStyle(SpanStyle(color = HemColors.Ink)) { append(line2) }
}

@Composable
fun EyebrowRow(text: String, trailing: String? = null) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Eyebrow(text)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(
                trailing.uppercase(),
                style = HemType.eyebrow.copy(color = HemColors.Ink),
            )
        }
    }
}

/** Fills a modifier's default paddings for screen bodies. */
val ScreenPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
