package com.fitrater.app.ui.screens.subpages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitrater.app.data.model.Outfit
import com.fitrater.app.data.model.SundayLetter
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.ui.components.SkeletonBar
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.PrimaryButton
import com.fitrater.app.ui.theme.SerifDisplay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun WeeklyLetterScreen(
    onClose: () -> Unit,
    isPro: Boolean = false,
    onOpenPaywall: () -> Unit = {},
) {
    var loaded by remember { mutableStateOf(false) }
    var letters by remember { mutableStateOf<List<SundayLetter>>(emptyList()) }
    // Map: letter.id -> best outfits
    var outfitsByLetter by remember { mutableStateOf<Map<String, List<Outfit>>>(emptyMap()) }
    var thumbUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    if (!isPro) {
        SubpageScaffold(eyebrow = "SUNDAY", title = "Weekly Letter", onClose = onClose) {
            LockedLetterState(onOpenPaywall = onOpenPaywall)
        }
        return
    }

    LaunchedEffect(Unit) {
        val list = runCatching { Repo.sundayLetters(12) }.getOrDefault(emptyList())
        letters = list
        val map = mutableMapOf<String, List<Outfit>>()
        val urlMap = mutableMapOf<String, String>()
        list.forEach { letter ->
            val start = letter.week_start ?: return@forEach
            val end = letter.week_end ?: run {
                runCatching { LocalDate.parse(start).plusDays(6).toString() }.getOrNull() ?: return@forEach
            }
            val startIso = "${start}T00:00:00+00:00"
            val endIso = "${end}T23:59:59+00:00"
            val outfits = runCatching { Repo.outfitsInWindow(startIso, endIso, 3) }.getOrDefault(emptyList())
            val id = letter.id ?: return@forEach
            map[id] = outfits
            outfits.forEach { o ->
                val path = o.photo_path ?: return@forEach
                if (path !in urlMap) {
                    val signed = runCatching { Repo.signedOutfitUrl(path) }.getOrNull()
                    if (signed != null) urlMap[path] = signed
                }
            }
        }
        outfitsByLetter = map
        thumbUrls = urlMap
        loaded = true
    }

    SubpageScaffold(eyebrow = "SUNDAY", title = "Weekly Letter", onClose = onClose) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            if (!loaded) {
                repeat(2) {
                    Spacer(Modifier.height(HemSpace.md))
                    SkeletonBar(height = 120.dp, corner = 14.dp)
                }
            } else if (letters.isEmpty()) {
                EmptyLetterState()
            } else {
                letters.forEach { letter ->
                    LetterCard(
                        letter = letter,
                        outfits = outfitsByLetter[letter.id.orEmpty()].orEmpty(),
                        urlFor = { path -> thumbUrls[path] },
                    )
                    Spacer(Modifier.height(HemSpace.md))
                }
            }
            Spacer(Modifier.height(HemSpace.xxl))
        }
    }
}

@Composable
private fun LetterCard(letter: SundayLetter, outfits: List<Outfit>, urlFor: (String) -> String?) {
    var expanded by remember { mutableStateOf(false) }
    val start = letter.week_start
    val end = letter.week_end ?: start?.let {
        runCatching { LocalDate.parse(it).plusDays(6).toString() }.getOrNull()
    }
    val eyebrowText = formatWeekRange(start, end)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(14.dp))
            .padding(HemSpace.md),
    ) {
        Text(
            eyebrowText,
            style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 2.sp),
        )
        Spacer(Modifier.height(HemSpace.sm))
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .padding(end = HemSpace.sm)
                    .fillMaxHeight()
                    .background(HemColors.Bronze)
                    .padding(1.dp),
            ) { Text(" ", style = HemType.body) }
            val body = letter.body.orEmpty()
            Text(
                text = body,
                style = HemType.serifQuote.copy(fontSize = 18.sp, lineHeight = 27.sp, fontStyle = FontStyle.Italic),
                maxLines = if (expanded) Int.MAX_VALUE else 3,
            )
        }
        if (letter.body.orEmpty().length > 140 && !expanded) {
            Spacer(Modifier.height(HemSpace.xs))
            Text(
                "Read more →",
                style = HemType.body.copy(color = HemColors.Bronze),
                modifier = Modifier.clickable { expanded = true },
            )
        }
        if (outfits.isNotEmpty()) {
            Spacer(Modifier.height(HemSpace.md))
            Row(horizontalArrangement = Arrangement.spacedBy(HemSpace.xs)) {
                outfits.take(3).forEach { o ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.75f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(HemColors.Paper)
                            .border(1.dp, HemColors.Hairline, RoundedCornerShape(8.dp)),
                    ) {
                        val url = o.photo_path?.let(urlFor)
                        if (url != null) {
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().fillMaxHeight().clip(RoundedCornerShape(8.dp)),
                            )
                        }
                    }
                }
                // Pad remaining slots.
                repeat(3 - outfits.take(3).size) {
                    Box(Modifier.weight(1f)) {}
                }
            }
        }
    }
}

@Composable
private fun EmptyLetterState() {
    val today = LocalDate.now()
    val nextSunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, nextSunday).toInt().coerceAtLeast(0)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = HemSpace.xl),
    ) {
        Text(
            "Your first letter arrives this Sunday.",
            style = HemType.serifSection,
        )
        Spacer(Modifier.height(HemSpace.sm))
        Text(
            when (days) {
                0 -> "Today. Keep scanning — the letter builds from your week."
                1 -> "1 day to go."
                else -> "$days days to go."
            },
            style = HemType.bodyMuted,
        )
    }
}

@Composable
private fun LockedLetterState(onOpenPaywall: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = HemSpace.xl),
    ) {
        SerifDisplay("Weekly Letter, on the house.")
        Spacer(Modifier.height(HemSpace.md))
        Text(
            "Every Sunday, Hem writes you a short letter — what you wore, what worked, what to try. It arrives with Pro.",
            style = HemType.body.copy(fontSize = 16.sp, lineHeight = 24.sp),
        )
        Spacer(Modifier.height(HemSpace.xl))
        PrimaryButton(label = "Start 7-day trial →", onClick = onOpenPaywall)
        Spacer(Modifier.height(HemSpace.sm))
        Text(
            "One free preview when you subscribe.",
            style = HemType.bodyMuted.copy(fontSize = 13.sp),
        )
    }
}

private fun formatWeekRange(start: String?, end: String?): String {
    if (start == null) return "WEEK OF —"
    val s = runCatching { LocalDate.parse(start) }.getOrNull() ?: return "WEEK OF —"
    val e = end?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: s.plusDays(6)
    val fmt = DateTimeFormatter.ofPattern("MMM d")
    return "WEEK OF ${s.format(fmt)} – ${e.format(fmt)}".uppercase()
}
