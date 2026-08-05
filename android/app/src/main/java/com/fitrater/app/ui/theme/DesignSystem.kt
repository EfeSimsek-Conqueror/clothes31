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
import androidx.compose.foundation.layout.heightIn
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
import java.util.Locale

/**
 * Palette from the design brief, resolved against the live theme.
 *
 * These are getters, not constants: each read is a snapshot read of [HemTheme.palette],
 * so switching theme recomposes every screen that touches a token. See [HemPalette] for
 * what each name means — in particular [OnInk] (not `Color.White`) for anything drawn on
 * top of an [Ink] fill, since Ink is cream in dark mode.
 */
object HemColors {
    val Paper: Color get() = HemTheme.palette.paper
    val CardCream: Color get() = HemTheme.palette.cardCream
    val Surface: Color get() = HemTheme.palette.surface
    val Ink: Color get() = HemTheme.palette.ink
    val OnInk: Color get() = HemTheme.palette.onInk
    val Muted: Color get() = HemTheme.palette.muted
    val Bronze: Color get() = HemTheme.palette.bronze
    val GoldStart: Color get() = HemTheme.palette.goldStart
    val GoldEnd: Color get() = HemTheme.palette.goldEnd
    val OnAccent: Color get() = HemTheme.palette.onAccent
    val Hairline: Color get() = HemTheme.palette.hairline
    val ChipBorder: Color get() = HemTheme.palette.chipBorder
    val Success: Color get() = HemTheme.palette.success
    val Warning: Color get() = HemTheme.palette.warning
    val Danger: Color get() = HemTheme.palette.danger
    val Scrim: Color get() = HemTheme.palette.scrim
    val OnScrim: Color get() = HemTheme.palette.onScrim
    val IsDark: Boolean get() = HemTheme.palette.isDark
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

/**
 * Reusable text styles.
 *
 * Sizes stay in `sp` and are scaled app-wide by the Text Size preference, which
 * overrides `LocalDensity.fontScale` at the root (see `FitraterApp`) — that way the
 * setting also reaches the many inline `.copy(fontSize = …)` call sites, not just
 * these tokens.
 *
 * Colours follow the live palette. The styles are rebuilt only when the palette
 * actually changes, so reading `HemType.body` in a hot composable stays cheap.
 */
object HemType {
    private class Styles(p: HemPalette) {
        val eyebrow = TextStyle(
            fontFamily = SansFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            color = p.bronze,
        )
        val eyebrowMuted = eyebrow.copy(color = p.muted)
        val serifDisplay = TextStyle(
            fontFamily = SerifFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 40.sp,
            lineHeight = 46.sp,
            color = p.ink,
        )
        val serifTitle = TextStyle(
            fontFamily = SerifFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            color = p.ink,
        )
        val serifSection = TextStyle(
            fontFamily = SerifFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            color = p.ink,
        )
        val serifQuote = TextStyle(
            fontFamily = SerifFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 18.sp,
            lineHeight = 26.sp,
            color = p.ink,
        )
        val body = TextStyle(
            fontFamily = SansFamily,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = p.ink,
        )
        val bodyMuted = body.copy(color = p.muted)
        val label = TextStyle(
            fontFamily = SansFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 2.sp,
            color = p.onInk,
        )
        val labelInk = label.copy(color = p.ink)
        val smallLabel = TextStyle(
            fontFamily = SansFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            color = p.ink,
        )
    }

    @Volatile
    private var cached: Pair<HemPalette, Styles>? = null

    private val current: Styles
        get() {
            // Reading HemTheme.palette here is what registers the snapshot read on the
            // caller's behalf, so composables that only touch HemType still recompose.
            val p = HemTheme.palette
            cached?.let { (key, styles) -> if (key == p) return styles }
            return Styles(p).also { cached = p to it }
        }

    val eyebrow: TextStyle get() = current.eyebrow
    val eyebrowMuted: TextStyle get() = current.eyebrowMuted
    val serifDisplay: TextStyle get() = current.serifDisplay
    val serifTitle: TextStyle get() = current.serifTitle
    val serifSection: TextStyle get() = current.serifSection
    val serifQuote: TextStyle get() = current.serifQuote
    val body: TextStyle get() = current.body
    val bodyMuted: TextStyle get() = current.bodyMuted
    val label: TextStyle get() = current.label
    val labelInk: TextStyle get() = current.labelInk
    val smallLabel: TextStyle get() = current.smallLabel
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
            .background(HemColors.Surface)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = String.format(Locale.US, "%.1f", score),
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
            // heightIn, not height: at the Large text setting the label needs the room.
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) HemColors.Ink else HemColors.Muted)
            .padding(vertical = 8.dp)
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
    val bg = if (filled) HemColors.Surface else Color.Transparent
    val border = BorderStroke(1.dp, HemColors.Ink.copy(alpha = 0.55f))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
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
