package com.fitrater.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitrater.app.data.model.Outfit
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

// =============================================================================
// Kind pill (top-left of FitCard) — 10sp, tracked, uppercase, 4dp radius.
// =============================================================================

private val ROAST_RED: Color get() = HemColors.Danger

@Composable
fun KindPill(kind: String?, modifier: Modifier = Modifier) {
    val (label, bg) = when (kind) {
        "studio_gen" -> "STUDIO" to HemColors.Bronze
        "tryon" -> "TRY-ON" to HemColors.Bronze
        "roast" -> "ROAST" to ROAST_RED
        "decode" -> "DECODED" to HemColors.Bronze
        else -> return
    }
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            label,
            style = HemType.smallLabel.copy(
                color = HemColors.OnAccent,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

// =============================================================================
// Editorial monthly line — one italic serif sentence generated from the data.
// =============================================================================

private val DOM_TONE_TABLE = listOf(
    Triple("rust", listOf("B0743A", "9C5A2E", "A0522D", "8B4513", "C4661F"), "rust"),
    Triple("cream", listOf("F5EBD0", "F7F2E8", "E8DCC0", "EFE7D2"), "cream"),
    Triple("black", listOf("000000", "141210", "1A1A1A", "222222"), "black"),
    Triple("navy", listOf("1F2A44", "1A2238", "0F1E3D"), "navy"),
    Triple("olive", listOf("6B7A3A", "556B2F", "7A8450"), "olive"),
    Triple("stone", listOf("A9A18C", "7A6A55", "8B8378"), "stone"),
)

private fun dominantToneLabel(outfits: List<Outfit>): String? {
    val hexes = outfits.flatMap { it.dominant_colors.orEmpty() }
        .mapNotNull { it.removePrefix("#").padStart(6, '0').uppercase().takeIf { s -> s.length >= 6 }?.take(6) }
    if (hexes.isEmpty()) return null
    val tally = mutableMapOf<String, Int>()
    hexes.forEach { hex ->
        val bucket = DOM_TONE_TABLE.firstOrNull { entry ->
            entry.second.any { ref -> colorClose(hex, ref) }
        }?.third
        val key = bucket ?: "mixed"
        tally[key] = (tally[key] ?: 0) + 1
    }
    return tally.entries.filter { it.key != "mixed" }.maxByOrNull { it.value }?.key
}

private fun colorClose(a: String, b: String): Boolean {
    return try {
        val ar = a.substring(0, 2).toInt(16); val ag = a.substring(2, 4).toInt(16); val ab = a.substring(4, 6).toInt(16)
        val br = b.substring(0, 2).toInt(16); val bg = b.substring(2, 4).toInt(16); val bb = b.substring(4, 6).toInt(16)
        val d = abs(ar - br) + abs(ag - bg) + abs(ab - bb)
        d < 120
    } catch (_: Throwable) { false }
}

/**
 * Deterministic one-liner for a month's fits. Returns empty if there's nothing
 * meaningful to say (fewer than 2 scored fits).
 */
fun hemMonthLine(monthName: String, monthOutfits: List<Outfit>, allTimeAvg: Double?): String {
    val scored = monthOutfits.mapNotNull { it.score }.filter { it > 0.0 }
    if (scored.size < 2) return ""
    val avg = scored.average()
    val delta = if (allTimeAvg != null) avg - allTimeAvg else 0.0
    val tone = dominantToneLabel(monthOutfits)
    val count = scored.size

    // Priority: strong delta phrasing takes precedence.
    if (count == 1) return ""
    if (count <= 3 && delta > 0.3) {
        return "$monthName: only $count fits — all wins."
    }
    if (tone != null && delta > 0.3) {
        return "$monthName: brighter, warmer, a little brave."
    }
    if (tone != null && delta < -0.3) {
        return "$monthName: $tone everything, quieter than usual."
    }
    if (tone != null) {
        return "$monthName: $tone phase — you leaned that way."
    }
    if (delta > 0.3) return "$monthName: your best month in a while."
    if (delta < -0.3) return "$monthName: a quieter run."
    // Occasion heavy?
    val occ = monthOutfits.mapNotNull { it.occasion?.lowercase() }.groupBy { it }.mapValues { it.value.size }
    val top = occ.maxByOrNull { it.value }
    if (top != null && top.value >= (monthOutfits.size * 0.6).toInt() && top.value >= 3) {
        val name = top.key.replaceFirstChar { it.uppercase() }
        return "$monthName: $name fits, $name fits, $name fits."
    }
    return "$monthName: $count fits, steady hand."
}

@Composable
fun MonthEditorialLine(text: String) {
    if (text.isBlank()) return
    Text(
        text,
        style = HemType.serifSection.copy(
            fontStyle = FontStyle.Italic,
            fontSize = 15.sp,
            color = HemColors.Ink.copy(alpha = 0.75f),
        ),
        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 6.dp),
    )
}

// =============================================================================
// Monthly reflection card ("5 things I noticed") — end of month block.
// =============================================================================

private val ROMANS = listOf("i.", "ii.", "iii.", "iv.", "v.")

fun reflectionsFor(outfits: List<Outfit>, prevMonthOutfits: List<Outfit>, monthName: String): List<String> {
    val scored = outfits.mapNotNull { o ->
        val d = parseIsoDate(o.created_at) ?: return@mapNotNull null
        val s = o.score ?: return@mapNotNull null
        Triple(d, s, o)
    }
    if (scored.size < 3) return emptyList()

    val out = mutableListOf<String>()

    // 1) Most-worn occasion / color
    val topOcc = outfits.mapNotNull { it.occasion?.lowercase() }
        .groupBy { it }.maxByOrNull { it.value.size }
    val topTone = dominantToneLabel(outfits)
    when {
        topOcc != null && topOcc.value.size >= 2 -> {
            val name = topOcc.key.replaceFirstChar { it.uppercase() }
            out.add("$name looks kept coming back — ${topOcc.value.size} of them.")
        }
        topTone != null -> out.add("A $topTone thread ran through the month.")
        else -> out.add("You mixed it up — no single lane took over.")
    }

    // 2) Best day
    val byDow = scored.groupBy { it.first.dayOfWeek }
        .mapValues { (_, l) -> l.map { it.second }.average() }
    val bestDow = byDow.maxByOrNull { it.value }
    if (bestDow != null) {
        val name = bestDow.key.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        out.add("$name was your highest — averaged ${"%.1f".format(Locale.US, bestDow.value)}.")
    }

    // 3) Worst day (if delta > 0.5)
    val worstDow = byDow.minByOrNull { it.value }
    if (bestDow != null && worstDow != null && bestDow.value - worstDow.value > 0.5) {
        val name = worstDow.key.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        out.add("${name}s dragged a little — worth a rethink.")
    }

    // 4) Streak — longest consecutive-day run
    val dates = scored.map { it.first.toLocalDate() }.distinct().sorted()
    var longest = 0
    var cur = 0
    var prev: LocalDate? = null
    for (d in dates) {
        cur = if (prev != null && d == prev.plusDays(1)) cur + 1 else 1
        if (cur > longest) longest = cur
        prev = d
    }
    if (longest >= 7) {
        out.add("You strung together $longest scoring days in a row.")
    } else if (longest >= 3) {
        out.add("Best streak: $longest days back to back.")
    }

    // 5) Growth vs last month
    val prevAvg = prevMonthOutfits.mapNotNull { it.score }.filter { it > 0.0 }
        .takeIf { it.isNotEmpty() }?.average()
    val avg = scored.map { it.second }.average()
    if (prevAvg != null) {
        val d = avg - prevAvg
        val dir = if (d >= 0) "up" else "down"
        out.add("Average ${"%.1f".format(Locale.US, avg)} — $dir ${"%.1f".format(Locale.US, abs(d))} from last month.")
    } else {
        out.add("Average settled at ${"%.1f".format(Locale.US, avg)}.")
    }

    return out.take(5)
}

@Composable
fun MonthlyReflectionCard(monthName: String, items: List<String>) {
    if (items.size < 3) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Eyebrow("HEM · REFLECTION")
        Spacer(Modifier.height(6.dp))
        Text(
            "${items.size} things I noticed in $monthName.",
            style = HemType.serifSection.copy(fontSize = 20.sp),
        )
        Spacer(Modifier.height(12.dp))
        items.forEachIndexed { i, line ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(
                    ROMANS.getOrNull(i) ?: "${i + 1}.",
                    style = HemType.body.copy(
                        color = HemColors.Bronze,
                        fontStyle = FontStyle.Italic,
                        fontSize = 13.sp,
                    ),
                    modifier = Modifier.width(28.dp),
                )
                Text(
                    line,
                    style = HemType.body.copy(fontSize = 14.sp, color = HemColors.Ink.copy(alpha = 0.9f)),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// =============================================================================
// Milestone card — hairline pill callout inserted inline.
// =============================================================================

@Composable
fun MilestoneCard(label: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HemColors.Paper)
            .border(1.dp, HemColors.Bronze.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Eyebrow("MILESTONE")
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = HemType.serifSection.copy(fontSize = 16.sp, color = HemColors.Ink),
        )
    }
}

// =============================================================================
// Weekly wrapped mini — top-of-Journal, Mondays only.
// =============================================================================

data class WeeklyWrapped(
    val weekLabel: String,
    val count: Int,
    val avg: Double,
    val bestDay: DayOfWeek?,
    val palette: List<Color>,
)

fun buildWeeklyWrapped(outfits: List<Outfit>, now: ZonedDateTime): WeeklyWrapped? {
    if (now.dayOfWeek != DayOfWeek.MONDAY) return null
    val weekFields = WeekFields.ISO
    val target = now.toLocalDate().minusWeeks(1)
    val targetWeek = target.get(weekFields.weekOfWeekBasedYear())
    val targetYear = target.get(weekFields.weekBasedYear())
    val week = outfits.mapNotNull { o ->
        val d = parseIsoDate(o.created_at) ?: return@mapNotNull null
        val s = o.score ?: return@mapNotNull null
        if (s <= 0) return@mapNotNull null
        val date = d.toLocalDate()
        if (date.get(weekFields.weekOfWeekBasedYear()) == targetWeek &&
            date.get(weekFields.weekBasedYear()) == targetYear
        ) Triple(d, s, o) else null
    }
    if (week.size < 2) return null
    val avg = week.map { it.second }.average()
    val bestDay = week.groupBy { it.first.dayOfWeek }
        .mapValues { (_, l) -> l.map { it.second }.average() }
        .maxByOrNull { it.value }?.key
    val palette = week.flatMap { it.third.dominant_colors.orEmpty() }
        .distinct().take(5).mapNotNull { parseHexColor(it) }
    return WeeklyWrapped(
        weekLabel = "Week of ${target.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${target.dayOfMonth}",
        count = week.size,
        avg = avg,
        bestDay = bestDay,
        palette = palette,
    )
}

@Composable
fun WeeklyWrappedCard(data: WeeklyWrapped, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Bronze.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("LAST WEEK")
            Spacer(Modifier.weight(1f))
            Text(
                "×",
                style = HemType.body.copy(color = HemColors.Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.clickable { onDismiss() }.padding(horizontal = 6.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        val bestDayName = data.bestDay?.getDisplayName(TextStyle.FULL, Locale.ENGLISH) ?: "no clear standout"
        val line = "${data.count} fits, avg ${"%.1f".format(Locale.US, data.avg)}. Best on $bestDayName."
        Text(line, style = HemType.serifSection.copy(fontSize = 18.sp))
        if (data.palette.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp)),
            ) {
                val padded = (data.palette + List(5) { HemColors.Muted.copy(alpha = 0.3f) }).take(5)
                padded.forEach { c ->
                    Box(Modifier.weight(1f).fillMaxWidth().background(c))
                }
            }
        }
    }
}

// =============================================================================
// Faceted filters sheet.
// =============================================================================

data class JournalFacets(
    val scoreMin: Float = 0f,
    val scoreMax: Float = 10f,
    val occasions: Set<String> = emptySet(),
    val weekdays: Set<DayOfWeek> = emptySet(),
    val kinds: Set<String> = emptySet(),
    val timeWindow: TimeWindow = TimeWindow.All,
    val onlyWithPalette: Boolean = false,
) {
    val isEmpty: Boolean get() =
        scoreMin == 0f && scoreMax == 10f && occasions.isEmpty() && weekdays.isEmpty() &&
            kinds.isEmpty() && timeWindow == TimeWindow.All && !onlyWithPalette
}

enum class TimeWindow(val label: String) {
    All("All"), ThisMonth("This month"), Last3("Last 3 months"), ThisYear("This year")
}

fun applyFacets(outfits: List<Outfit>, f: JournalFacets, now: ZonedDateTime): List<Outfit> {
    var out = outfits
    if (f.scoreMin > 0f || f.scoreMax < 10f) {
        out = out.filter { (it.score ?: 0.0) in f.scoreMin.toDouble()..f.scoreMax.toDouble() }
    }
    if (f.occasions.isNotEmpty()) {
        val set = f.occasions.map { it.lowercase() }.toSet()
        out = out.filter { (it.occasion ?: "").lowercase() in set }
    }
    if (f.weekdays.isNotEmpty()) {
        out = out.filter { parseIsoDate(it.created_at)?.dayOfWeek in f.weekdays }
    }
    if (f.kinds.isNotEmpty()) {
        out = out.filter { (it.kind ?: "score") in f.kinds }
    }
    if (f.onlyWithPalette) {
        out = out.filter { !it.dominant_colors.isNullOrEmpty() }
    }
    when (f.timeWindow) {
        TimeWindow.All -> Unit
        TimeWindow.ThisMonth -> out = out.filter {
            val d = parseIsoDate(it.created_at) ?: return@filter false
            d.year == now.year && d.monthValue == now.monthValue
        }
        TimeWindow.Last3 -> {
            val cutoff = now.minusMonths(3)
            out = out.filter { (parseIsoDate(it.created_at) ?: return@filter false).isAfter(cutoff) }
        }
        TimeWindow.ThisYear -> out = out.filter {
            (parseIsoDate(it.created_at) ?: return@filter false).year == now.year
        }
    }
    return out
}

// =============================================================================
// Milestone detection — walks fits chronologically and emits label strings
// tagged with the outfit id they should appear after.
// =============================================================================

data class MilestoneMark(val afterOutfitId: String, val label: String)

fun detectMilestones(outfitsNewestFirst: List<Outfit>): List<MilestoneMark> {
    // Walk oldest → newest so tallies grow the way they actually did in time.
    val chronological = outfitsNewestFirst.reversed()
    val out = mutableListOf<MilestoneMark>()

    var scoredCount = 0
    var bestSoFar = 0.0
    val firstOf = mutableMapOf<String, Boolean>() // kind -> already seen
    val monthToneTally = mutableMapOf<Pair<Int, String>, Int>() // (yearMonth, tone) -> count
    // Streak tracking (7 consecutive days with a score)
    val scoredDates = mutableListOf<LocalDate>()

    chronological.forEach { o ->
        val id = o.id ?: return@forEach
        val kind = o.kind
        val score = o.score ?: 0.0
        val isScored = score > 0.0 && (kind == null || kind == "score" || kind == "user_scan")

        // First-of-kind milestones (excluding scored fits)
        when (kind) {
            "tryon" -> if (firstOf["tryon"] != true) {
                firstOf["tryon"] = true
                out.add(MilestoneMark(id, "First try-on. Hem approves."))
            }
            "roast" -> if (firstOf["roast"] != true) {
                firstOf["roast"] = true
                out.add(MilestoneMark(id, "First roast. Brave of you."))
            }
            "decode" -> if (firstOf["decode"] != true) {
                firstOf["decode"] = true
                out.add(MilestoneMark(id, "First decode. The eye sharpens."))
            }
        }

        if (isScored) {
            scoredCount += 1
            // Nth judged fit milestones
            when (scoredCount) {
                50 -> out.add(MilestoneMark(id, "Your 50th judged fit."))
                100 -> out.add(MilestoneMark(id, "Your 100th judged fit."))
                250 -> out.add(MilestoneMark(id, "Your 250th judged fit."))
            }
            // Personal best
            if (scoredCount > 5 && score > bestSoFar + 0.01) {
                val prev = bestSoFar
                out.add(
                    MilestoneMark(
                        id,
                        "Highest score yet: ${"%.1f".format(Locale.US, score)} (was ${"%.1f".format(Locale.US, prev)}).",
                    ),
                )
            }
            if (score > bestSoFar) bestSoFar = score

            // Streak
            val d = parseIsoDate(o.created_at)?.toLocalDate()
            if (d != null) {
                scoredDates.add(d)
                val distinctSorted = scoredDates.distinct().sorted()
                // longest consecutive run ending on d
                var run = 1
                var i = distinctSorted.indexOf(d)
                while (i > 0 && distinctSorted[i] == distinctSorted[i - 1].plusDays(1)) {
                    run += 1; i -= 1
                }
                if (run == 7) out.add(MilestoneMark(id, "A full week of daily fits."))
            }

            // Color-of-season milestone
            val hexes = o.dominant_colors.orEmpty()
                .mapNotNull { it.removePrefix("#").padStart(6, '0').uppercase().take(6).takeIf { s -> s.length == 6 } }
            val tone = hexes.firstNotNullOfOrNull { hex ->
                DOM_TONE_TABLE.firstOrNull { entry -> entry.second.any { ref -> colorClose(hex, ref) } }?.third
            }
            val ym = parseIsoDate(o.created_at)?.let { it.year * 100 + it.monthValue }
            if (tone != null && ym != null) {
                val key = ym to tone
                val newCount = (monthToneTally[key] ?: 0) + 1
                monthToneTally[key] = newCount
                if (newCount == 3) {
                    out.add(MilestoneMark(id, "3rd $tone fit this month."))
                }
            }
        }
    }
    return out
}

// =============================================================================
// Shared helpers — date + hex parsers, kept private to this file to avoid
// collisions with JournalScreen's own private helpers.
// =============================================================================

internal fun parseIsoDate(iso: String?): ZonedDateTime? {
    if (iso == null) return null
    return runCatching { ZonedDateTime.parse(iso.replace(" ", "T")) }.getOrNull()
}

internal fun parseHexColor(hex: String): Color? = runCatching {
    val h = hex.removePrefix("#").padStart(6, '0')
    Color(("FF$h").toLong(16))
}.getOrNull()
