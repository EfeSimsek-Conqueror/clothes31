package com.fitrater.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * One editorial palette. Light is the original cream-paper brief; dark is the same
 * identity inverted — warm near-black paper, cream ink, a lifted bronze — rather than
 * a neutral grey dark mode.
 *
 * Semantic names, not literal ones: [ink] is "the strong foreground / filled-button
 * colour", which is near-black on light and cream on dark. Anything drawn *on top of*
 * an ink fill must use [onInk], never `Color.White`.
 */
@Immutable
data class HemPalette(
    val isDark: Boolean,
    /** Page background. */
    val paper: Color,
    /** Raised card sitting on [paper]. */
    val cardCream: Color,
    /** Tiles/chips that were pure white in the light brief. */
    val surface: Color,
    /** Primary text, and the fill of primary buttons. */
    val ink: Color,
    /** Foreground drawn on top of an [ink] fill. */
    val onInk: Color,
    /** Secondary text. */
    val muted: Color,
    /** Brand accent. */
    val bronze: Color,
    val goldStart: Color,
    val goldEnd: Color,
    /** Foreground on top of a bronze/gold fill. */
    val onAccent: Color,
    val hairline: Color,
    val chipBorder: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    /** Wash over photography so overlaid text stays readable. Dark in both themes. */
    val scrim: Color,
    /** Foreground on top of [scrim] or a photo. Light in both themes. */
    val onScrim: Color,
)

val LightPalette = HemPalette(
    isDark = false,
    paper = Color(0xFFF3EEE4),
    cardCream = Color(0xFFF7F2E8),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF141210),
    onInk = Color(0xFFFFFFFF),
    // Was #6B6459 — 4.54:1 on paper, a hair over AA and a documented borderline fail
    // on some panels. Darkened to ~5.2:1.
    muted = Color(0xFF625B50),
    bronze = Color(0xFFB0743A),
    goldStart = Color(0xFFC99A5B),
    goldEnd = Color(0xFF8C6033),
    onAccent = Color(0xFFFFFFFF),
    hairline = Color(0x22141210),
    chipBorder = Color(0x33141210),
    success = Color(0xFF3E8C5E),
    warning = Color(0xFFB0553A),
    danger = Color(0xFFB23A2A),
    scrim = Color(0xFF000000),
    onScrim = Color(0xFFFFFFFF),
)

val DarkPalette = HemPalette(
    isDark = true,
    paper = Color(0xFF14120F),
    cardCream = Color(0xFF1E1A15),
    surface = Color(0xFF262119),
    ink = Color(0xFFF2EDE3),
    onInk = Color(0xFF14120F),
    // ~6.9:1 on paper.
    muted = Color(0xFFA79B8B),
    // #B0743A is only ~3.3:1 on near-black; lifted to ~7.5:1 while staying bronze.
    bronze = Color(0xFFD2A063),
    goldStart = Color(0xFFE0B77C),
    goldEnd = Color(0xFFA87A45),
    onAccent = Color(0xFF14120F),
    hairline = Color(0x24F2EDE3),
    chipBorder = Color(0x3DF2EDE3),
    success = Color(0xFF6FBF8B),
    warning = Color(0xFFD9A05B),
    danger = Color(0xFFE8776A),
    scrim = Color(0xFF000000),
    onScrim = Color(0xFFFFFFFF),
)

/**
 * The live palette.
 *
 * Backed by snapshot state, so every `HemColors.X` read inside a composable is a
 * snapshot read and the whole tree recomposes when the theme flips — no call-site
 * changes and no CompositionLocal plumbing through ~65 files.
 *
 * Written synchronously (from `Activity.onCreate` before `setContent`, and from the
 * Appearance picker) so a cold start never paints a frame in the wrong theme.
 */
object HemTheme {
    var palette: HemPalette by mutableStateOf(LightPalette)
        private set

    /** @param pref "system" | "light" | "dark". */
    fun apply(pref: String, systemDark: Boolean) {
        val dark = when (pref) {
            "dark" -> true
            "light" -> false
            else -> systemDark
        }
        val next = if (dark) DarkPalette else LightPalette
        if (next != palette) palette = next
    }
}
