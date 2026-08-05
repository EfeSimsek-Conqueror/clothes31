package com.fitrater.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.fitrater.app.data.model.Outfit
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.nav.Route
import com.fitrater.app.ui.components.KindPill
import com.fitrater.app.ui.components.MilestoneCard
import com.fitrater.app.ui.components.MonthEditorialLine
import com.fitrater.app.ui.components.MonthlyReflectionCard
import com.fitrater.app.ui.components.WeeklyWrappedCard
import com.fitrater.app.ui.components.JournalFacets
import com.fitrater.app.ui.components.TimeWindow
import com.fitrater.app.ui.components.applyFacets
import com.fitrater.app.ui.components.buildWeeklyWrapped
import com.fitrater.app.ui.components.detectMilestones
import com.fitrater.app.ui.components.hemMonthLine
import com.fitrater.app.ui.components.reflectionsFor
import com.fitrater.app.ui.components.shimmer
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.Hairline
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.PrimaryButton
import com.fitrater.app.ui.theme.SerifDisplay
import com.fitrater.app.ui.theme.SerifFamily
import com.fitrater.app.util.JournalExportData
import com.fitrater.app.util.JournalPdfExport
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

private val EN_MONTHS = listOf(
    "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
    "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
)

private data class JournalPhoto(val outfit: Outfit, val url: String?)

private sealed interface JournalItem {
    data class MonthHeader(val label: String, val editorial: String) : JournalItem
    data class Fit(val outfit: Outfit, val url: String?) : JournalItem
    data class Insight(val body: String) : JournalItem
    data class Throwback(val outfit: Outfit, val url: String?) : JournalItem
    data class Milestone(val label: String) : JournalItem
    data class Reflection(val monthName: String, val lines: List<String>) : JournalItem
}

private enum class FilterKind { All, Scored, Best, Occasion, Weekday }
private enum class KindTab { All, Fits, Studio }

private val FIT_KINDS = setOf<String?>(null, "score", "user_scan")
private val STUDIO_KINDS = setOf("studio_gen", "tryon", "roast", "decode")

private val WEEKDAY_ORDER = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
)
private val DEFAULT_OCCASIONS = listOf("Everyday", "Work", "Date", "Wedding", "Weekend", "Party", "Formal", "Travel")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(onOpenDetail: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    var outfits by remember { mutableStateOf<List<Outfit>>(emptyList()) }
    var photoUrls by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf(FilterKind.All) }
    var kindTab by remember { mutableStateOf(KindTab.All) }
    val selectedOccasions = remember { mutableStateListOf<String>() }
    val selectedWeekdays = remember { mutableStateListOf<DayOfWeek>() }
    var showOccasionSheet by remember { mutableStateOf(false) }
    var showWeekdaySheet by remember { mutableStateOf(false) }
    var showFacetsSheet by remember { mutableStateOf(false) }
    var facets by remember { mutableStateOf(JournalFacets()) }
    var searchQuery by remember { mutableStateOf("") }
    var latestHem by remember { mutableStateOf<String?>(null) }
    var weeklyDismissed by remember { mutableStateOf(false) }

    // Compare mode
    var compareMode by remember { mutableStateOf(false) }
    val compareSel = remember { mutableStateListOf<String>() }
    var showCompareSheet by remember { mutableStateOf(false) }

    // PDF export
    var exporting by remember { mutableStateOf(false) }

    // Cancellation-safe load: retry a few times if scoped coroutine gets cancelled by
    // rapid page transitions in the HorizontalPager. Without this, Journal renders empty.
    LaunchedEffect(Unit) {
        var attempt = 0
        while (attempt < 3 && outfits.isEmpty() && !loaded) {
            attempt++
            try {
                val list = Repo.outfits(120)
                val urls = mutableMapOf<String, String?>()
                list.forEach { o ->
                    val p = o.photo_path
                    val id = o.id
                    if (p != null && id != null) {
                        urls[id] = runCatching { Repo.signedOutfitUrl(p) }.getOrNull()
                    }
                }
                outfits = list
                photoUrls = urls
                latestHem = runCatching { Repo.latestHemNote()?.body }.getOrNull()
                loaded = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.w("Journal", "load failed attempt=$attempt", e)
                kotlinx.coroutines.delay(300L * attempt)
            }
        }
        if (!loaded) loaded = true // show empty state rather than perma-skeleton
    }
    // Refresh on resume: if the user comes back after scoring/editing, get fresh data.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && loaded) {
                loaded = false
                outfits = emptyList()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val now = remember { ZonedDateTime.now() }
    val thisMonth = now.monthValue
    val thisYear = now.year

    val filtered = remember(outfits, activeFilter, kindTab, facets, selectedOccasions.toList(), selectedWeekdays.toList(), searchQuery) {
        var out = outfits
        // Kind tab (primary axis) — Fits = scored real fits only; Studio = generated/tryon/roast/decode.
        out = when (kindTab) {
            KindTab.All -> out
            KindTab.Fits -> out.filter { (it.score ?: 0.0) > 0.0 && it.kind in FIT_KINDS }
            KindTab.Studio -> out.filter { it.kind in STUDIO_KINDS }
        }
        if (activeFilter == FilterKind.Scored) {
            out = out.filter { o -> o.kind == null || o.kind == "score" || o.kind == "user_scan" }
        }
        if (activeFilter == FilterKind.Best) {
            // Adaptive Best threshold: only count truly-scored fits (score > 0),
            // then take the higher of absolute 7.0 or (user's own avg + 0.5).
            val scored = outfits.mapNotNull { it.score }.filter { it > 0.0 }
            val avg = if (scored.isEmpty()) 0.0 else scored.average()
            val threshold = maxOf(7.0, avg + 0.5)
            out = out.filter { s -> (s.score ?: 0.0) >= threshold }
        }
        if (selectedOccasions.isNotEmpty()) {
            val occSet = selectedOccasions.map { it.lowercase() }.toSet()
            out = out.filter { (it.occasion ?: "").lowercase() in occSet }
        }
        if (selectedWeekdays.isNotEmpty()) {
            val dowSet = selectedWeekdays.toSet()
            out = out.filter { parseDate(it.created_at)?.dayOfWeek in dowSet }
        }
        val q = searchQuery.trim().lowercase()
        if (q.isNotBlank()) {
            out = out.filter {
                (it.hem_comment?.lowercase()?.contains(q) == true) ||
                    (it.occasion?.lowercase()?.contains(q) == true) ||
                    (it.notes?.lowercase()?.contains(q) == true)
            }
        }
        if (!facets.isEmpty) out = applyFacets(out, facets, now)
        out
    }

    // Summary card should always reflect real scored fits — never studio/roast/decode —
    // regardless of what tab the user is on.
    val scoredOnly = outfits.filter { (it.score ?: 0.0) > 0.0 && it.kind in FIT_KINDS }
    val thisMonthOutfits = scoredOnly.filter { parseDate(it.created_at)?.let { d -> d.monthValue == thisMonth && d.year == thisYear } == true }
    val avgThis = thisMonthOutfits.mapNotNull { it.score }.let { if (it.isEmpty()) null else it.average() }
    val lastMonthDate = now.minusMonths(1)
    val lastMonthOutfits = scoredOnly.filter {
        parseDate(it.created_at)?.let { d -> d.monthValue == lastMonthDate.monthValue && d.year == lastMonthDate.year } == true
    }
    val avgLast = lastMonthOutfits.mapNotNull { it.score }.let { if (it.isEmpty()) null else it.average() }
    val delta = if (avgThis != null && avgLast != null) avgThis - avgLast else null

    val palette = paletteFor(thisMonthOutfits)
    val spark = sparklineFor(scoredOnly, days = 30, now = now)
    val insights = deterministicInsights(outfits, Repo.userId ?: "")
    val throwback = throwbackOutfit(outfits, now)?.let { JournalPhoto(it, photoUrls[it.id]) }

    val showWrapped = now.dayOfMonth >= 25
    val weekly = remember(outfits, now, weeklyDismissed) {
        if (weeklyDismissed) null else buildWeeklyWrapped(outfits, now)
    }
    val allTimeAvg = remember(scoredOnly) {
        scoredOnly.mapNotNull { it.score }.takeIf { it.isNotEmpty() }?.average()
    }

    val items = remember(filtered, outfits, insights, throwback, photoUrls, allTimeAvg) {
        buildItems(filtered, outfits, photoUrls, insights, throwback, allTimeAvg)
    }

    val availableOccasions = remember(outfits) {
        val fromData = outfits.mapNotNull { it.occasion?.replaceFirstChar { c -> c.uppercase() } }.distinct()
        (DEFAULT_OCCASIONS + fromData).distinct()
    }

    Box(Modifier.fillMaxSize().background(HemColors.Paper)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                    })
                }
                .padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
        ) {
            // Compare mode top bar
            if (compareMode) {
                CompareTopBar(
                    count = compareSel.size,
                    onCancel = {
                        compareMode = false
                        compareSel.clear()
                    },
                )
                Spacer(Modifier.height(HemSpace.md))
            }

            SerifDisplay("Journal")
            Spacer(Modifier.height(HemSpace.md))

            if (weekly != null) {
                WeeklyWrappedCard(data = weekly, onDismiss = { weeklyDismissed = true })
                Spacer(Modifier.height(HemSpace.md))
            }

            if (showWrapped) {
                WrappedBanner(monthLabel = enMonthName(now.monthValue).uppercase())
                Spacer(Modifier.height(HemSpace.md))
            }

            MonthSummaryCard(
                // Full year — "JUL 26" read as July 26th.
                monthLabel = "${enMonthName(now.monthValue).take(3).uppercase()} ${now.year}",
                sparkline = spark,
                avg = avgThis,
                delta = delta,
                palette = palette,
                exportEnabled = thisMonthOutfits.isNotEmpty() && !exporting,
                exporting = exporting,
                onExport = {
                    if (thisMonthOutfits.isEmpty() || exporting) return@MonthSummaryCard
                    exporting = true
                    scope.launch {
                        runCatching {
                            val paletteInts = palette.map {
                                val a = (it.alpha * 255).toInt()
                                val r = (it.red * 255).toInt()
                                val g = (it.green * 255).toInt()
                                val b = (it.blue * 255).toInt()
                                android.graphics.Color.argb(a, r, g, b)
                            }
                            val data = JournalExportData(
                                monthLabel = enMonthName(now.monthValue),
                                year = now.year,
                                outfits = thisMonthOutfits,
                                urlsByOutfitId = photoUrls,
                                sparkline = spark,
                                palette = paletteInts,
                                average = avgThis,
                                delta = delta,
                            )
                            JournalPdfExport.exportAndShare(context, data)
                        }.onFailure {
                            com.fitrater.app.util.ToastBus.post("Export failed: ${it.message ?: "error"}")
                        }
                        exporting = false
                    }
                },
            )
            Spacer(Modifier.height(HemSpace.md))

            // Search field
            JournalSearchField(
                value = searchQuery,
                onValue = { searchQuery = it },
            )
            Spacer(Modifier.height(HemSpace.sm))

            // Level 1: Kind tabs (primary axis)
            KindTabRow(active = kindTab, onSelect = { kindTab = it })
            Spacer(Modifier.height(HemSpace.sm))

            // Level 2: secondary chips + Filters entry point
            FilterChipsRow(
                active = activeFilter,
                selectedOccasions = selectedOccasions,
                selectedWeekdays = selectedWeekdays,
                facetsActive = !facets.isEmpty,
                onSelect = { activeFilter = it },
                onOpenOccasion = { showOccasionSheet = true },
                onOpenWeekday = { showWeekdaySheet = true },
                onClearOccasion = { selectedOccasions.clear() },
                onClearWeekday = { selectedWeekdays.clear() },
                onOpenFilters = { showFacetsSheet = true },
            )
            Spacer(Modifier.height(HemSpace.md))

            if (!loaded) {
                com.fitrater.app.ui.screens.JournalGridSkeleton()
            } else if (items.isEmpty()) {
                JournalEmptyState(onScoreALook = {
                    // The Journal is inside a pager; there's no direct nav here,
                    // but the user should tap the camera in bottom nav. Show hint.
                    com.fitrater.app.util.ToastBus.post("Tap the camera in the bottom bar to score.")
                })
            } else {
                var i = 0
                while (i < items.size) {
                    val item = items[i]
                    when (item) {
                        is JournalItem.MonthHeader -> {
                            Column {
                                Spacer(Modifier.height(HemSpace.md))
                                Text(
                                    item.label,
                                    style = HemType.eyebrow.copy(
                                        color = HemColors.Bronze,
                                        letterSpacing = 3.sp,
                                        fontSize = 11.sp,
                                    ),
                                )
                                if (item.editorial.isNotBlank()) {
                                    MonthEditorialLine(item.editorial)
                                }
                                Spacer(Modifier.height(6.dp))
                                Hairline()
                                Spacer(Modifier.height(HemSpace.sm))
                            }
                            i += 1
                        }
                        is JournalItem.Milestone -> {
                            MilestoneCard(item.label)
                            i += 1
                        }
                        is JournalItem.Reflection -> {
                            MonthlyReflectionCard(item.monthName, item.lines)
                            i += 1
                        }
                        is JournalItem.Insight -> {
                            InsightCard(item.body)
                            Spacer(Modifier.height(HemSpace.md))
                            i += 1
                        }
                        is JournalItem.Throwback -> {
                            ThrowbackCard(item, onClick = { item.outfit.id?.let(onOpenDetail) })
                            Spacer(Modifier.height(HemSpace.md))
                            i += 1
                        }
                        is JournalItem.Fit -> {
                            val next = items.getOrNull(i + 1) as? JournalItem.Fit
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(HemSpace.sm),
                            ) {
                                FitCard(
                                    outfit = item.outfit,
                                    url = item.url,
                                    heightRatio = 1.25f,
                                    selected = item.outfit.id in compareSel,
                                    compareMode = compareMode,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (compareMode) toggleCompare(item.outfit.id, compareSel) {
                                            showCompareSheet = true
                                        } else item.outfit.id?.let(onOpenDetail)
                                    },
                                    onLongPress = {
                                        if (!compareMode) {
                                            compareMode = true
                                            item.outfit.id?.let { compareSel.add(it) }
                                        }
                                    },
                                )
                                if (next != null) {
                                    FitCard(
                                        outfit = next.outfit,
                                        url = next.url,
                                        heightRatio = 1.5f,
                                        selected = next.outfit.id in compareSel,
                                        compareMode = compareMode,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            if (compareMode) toggleCompare(next.outfit.id, compareSel) {
                                                showCompareSheet = true
                                            } else next.outfit.id?.let(onOpenDetail)
                                        },
                                        onLongPress = {
                                            if (!compareMode) {
                                                compareMode = true
                                                next.outfit.id?.let { compareSel.add(it) }
                                            }
                                        },
                                    )
                                    i += 2
                                } else {
                                    Box(Modifier.weight(1f))
                                    i += 1
                                }
                            }
                            Spacer(Modifier.height(HemSpace.sm))
                        }
                    }
                }
            }
            Spacer(Modifier.height(HemSpace.xxl))
        }

        if (exporting) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = HemSpace.md)
                    .clip(RoundedCornerShape(999.dp))
                    .background(HemColors.Ink)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("Preparing…", style = HemType.body.copy(color = HemColors.OnInk, fontSize = 12.sp))
            }
        }
    }

    if (showOccasionSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showOccasionSheet = false },
            sheetState = sheetState,
            containerColor = HemColors.Paper,
        ) {
            ChipMultiSelectSheet(
                title = "By occasion",
                options = availableOccasions.map { it to it },
                selected = selectedOccasions.map { it }.toSet(),
                onToggle = { v ->
                    if (v in selectedOccasions) selectedOccasions.remove(v) else selectedOccasions.add(v)
                },
                onDone = { showOccasionSheet = false },
            )
        }
    }

    if (showWeekdaySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showWeekdaySheet = false },
            sheetState = sheetState,
            containerColor = HemColors.Paper,
        ) {
            val opts = WEEKDAY_ORDER.map {
                it.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) to it.name
            }
            ChipMultiSelectSheet(
                title = "By weekday",
                options = opts,
                selected = selectedWeekdays.map { it.name }.toSet(),
                onToggle = { v ->
                    val d = DayOfWeek.valueOf(v)
                    if (d in selectedWeekdays) selectedWeekdays.remove(d) else selectedWeekdays.add(d)
                },
                onDone = { showWeekdaySheet = false },
            )
        }
    }

    if (showFacetsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showFacetsSheet = false },
            sheetState = sheetState,
            containerColor = HemColors.Paper,
        ) {
            FacetsSheet(
                initial = facets,
                allOutfits = outfits,
                availableOccasions = availableOccasions,
                now = now,
                onApply = { newFacets ->
                    facets = newFacets
                    showFacetsSheet = false
                },
                onReset = {
                    facets = JournalFacets()
                    showFacetsSheet = false
                },
            )
        }
    }

    if (showCompareSheet && compareSel.size == 2) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val a = outfits.firstOrNull { it.id == compareSel[0] }
        val b = outfits.firstOrNull { it.id == compareSel[1] }
        if (a != null && b != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    showCompareSheet = false
                    compareSel.clear()
                    compareMode = false
                },
                sheetState = sheetState,
                containerColor = HemColors.Paper,
            ) {
                CompareSheet(a, b, photoUrls[a.id], photoUrls[b.id])
            }
        }
    }
}

private fun toggleCompare(id: String?, sel: MutableList<String>, onFull: () -> Unit) {
    if (id == null) return
    if (id in sel) sel.remove(id) else if (sel.size < 2) sel.add(id)
    if (sel.size == 2) onFull()
}

@Composable
private fun CompareTopBar(count: Int, onCancel: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(HemColors.Ink)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (count < 2) "SELECT ONE MORE" else "COMPARING",
            style = HemType.label,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "Cancel",
            style = HemType.body.copy(color = HemColors.OnInk, fontWeight = FontWeight.Medium),
            modifier = Modifier.clickable { onCancel() },
        )
    }
}

@Composable
private fun JournalSearchField(value: String, onValue: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            cursorBrush = SolidColor(HemColors.Ink),
            textStyle = HemType.body,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            decorationBox = { inner ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⌕ ", style = HemType.bodyMuted)
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text("Search comments or occasions", style = HemType.bodyMuted)
                        }
                        inner()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(HemColors.Hairline),
        )
    }
}

@Composable
private fun FilterChipsRow(
    active: FilterKind,
    selectedOccasions: List<String>,
    selectedWeekdays: List<DayOfWeek>,
    facetsActive: Boolean,
    onSelect: (FilterKind) -> Unit,
    onOpenOccasion: () -> Unit,
    onOpenWeekday: () -> Unit,
    onClearOccasion: () -> Unit,
    onClearWeekday: () -> Unit,
    onOpenFilters: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScrollable(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        JournalChip(
            label = "All",
            selected = active == FilterKind.All && selectedOccasions.isEmpty() && selectedWeekdays.isEmpty() && !facetsActive,
            onClick = { onSelect(FilterKind.All) },
        )
        JournalChip(
            label = "Best",
            selected = active == FilterKind.Best,
            onClick = { onSelect(FilterKind.Best) },
        )
        JournalChip(
            label = if (selectedOccasions.isEmpty()) "Occasion" else "Occasion · ${selectedOccasions.size}",
            selected = selectedOccasions.isNotEmpty(),
            hasClear = selectedOccasions.isNotEmpty(),
            onClear = onClearOccasion,
            onClick = onOpenOccasion,
        )
        JournalChip(
            label = if (selectedWeekdays.isEmpty()) "Weekday" else "Weekday · ${selectedWeekdays.size}",
            selected = selectedWeekdays.isNotEmpty(),
            hasClear = selectedWeekdays.isNotEmpty(),
            onClear = onClearWeekday,
            onClick = onOpenWeekday,
        )
        JournalChip(
            label = if (facetsActive) "Filters · on" else "Filters",
            selected = facetsActive,
            onClick = onOpenFilters,
        )
    }
}

@Composable
private fun KindTabRow(active: KindTab, onSelect: (KindTab) -> Unit) {
    val entries = listOf(KindTab.All to "All", KindTab.Fits to "Fits", KindTab.Studio to "Studio")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, HemColors.Ink.copy(alpha = 0.35f), RoundedCornerShape(999.dp)),
    ) {
        entries.forEach { (k, label) ->
            val sel = k == active
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onSelect(k) }
                    .background(if (sel) HemColors.Ink else Color.Transparent)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    style = HemType.body.copy(
                        color = if (sel) HemColors.OnInk else HemColors.Ink,
                        fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                )
                if (sel) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .width(20.dp)
                            .height(2.dp)
                            .background(HemColors.Bronze),
                    )
                }
            }
        }
    }
}

@Composable
private fun Modifier.horizontalScrollable(): Modifier {
    val state = rememberScrollState()
    return this.then(Modifier.horizontalScroll(state))
}

@Composable
private fun JournalChip(
    label: String,
    selected: Boolean,
    hasClear: Boolean = false,
    onClear: () -> Unit = {},
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) HemColors.Ink else Color.Transparent)
            .border(1.dp, HemColors.Ink.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = HemType.body.copy(
                color = if (selected) HemColors.OnInk else HemColors.Ink,
                fontSize = 13.sp,
            ),
        )
        if (hasClear) {
            Spacer(Modifier.width(6.dp))
            Text(
                "×",
                style = HemType.body.copy(
                    color = if (selected) HemColors.OnInk else HemColors.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.clickable { onClear() },
            )
        }
    }
}

@Composable
private fun ChipMultiSelectSheet(
    title: String,
    options: List<Pair<String, String>>, // display label -> value
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDone: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
    ) {
        Text(title, style = HemType.serifSection)
        Spacer(Modifier.height(HemSpace.md))
        // Flow-like wrap: simulate with vertical + horizontal rows
        val rows = options.chunked(3)
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    val sel = value in selected
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (sel) HemColors.Ink else Color.Transparent)
                            .border(1.dp, HemColors.Ink.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                            .clickable { onToggle(value) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = HemType.body.copy(
                                color = if (sel) HemColors.OnInk else HemColors.Ink,
                                fontSize = 13.sp,
                            ),
                        )
                    }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(HemSpace.lg))
        PrimaryButton(label = "Done", onClick = onDone)
        Spacer(Modifier.height(HemSpace.lg))
    }
}

@Composable
private fun CompareSheet(a: Outfit, b: Outfit, aUrl: String?, bUrl: String?) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
    ) {
        Eyebrow("SIDE BY SIDE")
        Spacer(Modifier.height(HemSpace.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HemSpace.md)) {
            ComparePane(Modifier.weight(1f), a, aUrl)
            ComparePane(Modifier.weight(1f), b, bUrl)
        }
        Spacer(Modifier.height(HemSpace.md))
        val sA = a.score
        val sB = b.score
        if (sA != null && sB != null) {
            val d = sB - sA
            val label = if (d > 0) "+${String.format(Locale.US, "%.1f", d)} higher" else "${String.format(Locale.US, "%.1f", d)} lower"
            Text(label, style = HemType.serifSection.copy(color = HemColors.Bronze, fontSize = 22.sp))
        }
        Spacer(Modifier.height(HemSpace.md))
        Hairline()
        Spacer(Modifier.height(HemSpace.sm))
        Eyebrow("BREAKDOWN")
        Spacer(Modifier.height(HemSpace.xs))
        SubRow("Color", a.subscores?.color, b.subscores?.color)
        SubRow("Fit", a.subscores?.fit, b.subscores?.fit)
        SubRow("Style match", a.subscores?.style_match, b.subscores?.style_match)
        SubRow("Seasonal", a.subscores?.seasonal, b.subscores?.seasonal)
        Spacer(Modifier.height(HemSpace.xl))
    }
}

@Composable
private fun ComparePane(modifier: Modifier, o: Outfit, url: String?) {
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)
                .clip(RoundedCornerShape(12.dp))
                .background(HemColors.CardCream),
        ) {
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(HemSpace.xs))
        Text(
            o.score?.let { String.format(Locale.US, "%.1f", it) } ?: "–",
            style = HemType.serifSection.copy(color = HemColors.Bronze, fontSize = 22.sp),
        )
        Text(o.occasion ?: "Look", style = HemType.bodyMuted.copy(fontSize = 11.sp))
    }
}

@Composable
private fun SubRow(label: String, a: Double?, b: Double?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = HemType.body.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
        Text(a?.let { String.format(Locale.US, "%.1f", it) } ?: "–", style = HemType.body.copy(fontSize = 13.sp))
        Spacer(Modifier.width(HemSpace.md))
        Text(b?.let { String.format(Locale.US, "%.1f", it) } ?: "–", style = HemType.body.copy(fontSize = 13.sp))
        Spacer(Modifier.width(HemSpace.sm))
        if (a != null && b != null) {
            val d = b - a
            val (glyph, color) = when {
                d > 0 -> "▲" to HemColors.Success
                d < 0 -> "▼" to HemColors.Warning
                else -> "·" to HemColors.Muted
            }
            Text(glyph, style = HemType.body.copy(color = color, fontSize = 12.sp))
        }
    }
}

@Composable
private fun JournalEmptyState(onScoreALook: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = HemSpace.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        com.fitrater.app.ui.components.JournalEmptyIllustration()
        Spacer(Modifier.height(HemSpace.md))
        Text("Your Journal writes itself.", style = HemType.serifSection)
        Spacer(Modifier.height(HemSpace.sm))
        Text(
            "Score a fit and Hem will start noting patterns — best colors, weekday averages, and monthly wrap-ups.",
            style = HemType.bodyMuted,
        )
        Spacer(Modifier.height(HemSpace.md))
        PrimaryButton(label = "Score your first look", onClick = onScoreALook)
    }
}

@Composable
private fun MonthSummaryCard(
    monthLabel: String,
    sparkline: List<Double>,
    avg: Double?,
    delta: Double?,
    palette: List<Color>,
    exportEnabled: Boolean,
    exporting: Boolean,
    onExport: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(14.dp))
            .padding(HemSpace.md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("THIS MONTH · $monthLabel")
            Spacer(Modifier.weight(1f))
            Text(
                if (exporting) "PREPARING…" else "EXPORT →",
                style = HemType.eyebrow.copy(
                    color = if (exportEnabled) HemColors.Bronze else HemColors.Muted.copy(alpha = 0.5f),
                ),
                modifier = Modifier.clickable(enabled = exportEnabled) { onExport() },
            )
        }
        Spacer(Modifier.height(HemSpace.sm))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text("SCORE TREND", style = HemType.smallLabel.copy(color = HemColors.Muted, letterSpacing = 1.5.sp))
                Spacer(Modifier.height(6.dp))
                Sparkline(sparkline)
            }
            Box(Modifier.width(1.dp).height(56.dp).background(HemColors.Hairline))
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text("AVERAGE", style = HemType.smallLabel.copy(color = HemColors.Muted, letterSpacing = 1.5.sp))
                Spacer(Modifier.height(4.dp))
                Text(
                    avg?.let { String.format(Locale.US, "%.1f", it) } ?: "–",
                    style = HemType.serifSection.copy(fontSize = 28.sp),
                )
                if (delta != null) {
                    val sign = if (delta >= 0) "+" else ""
                    Text(
                        "$sign${String.format(Locale.US, "%.1f", delta)} vs last",
                        style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 1.sp),
                    )
                }
            }
            Box(Modifier.width(1.dp).height(56.dp).background(HemColors.Hairline))
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text("PALETTE", style = HemType.smallLabel.copy(color = HemColors.Muted, letterSpacing = 1.5.sp))
                Spacer(Modifier.height(6.dp))
                PaletteStrip(palette)
            }
        }
    }
}

@Composable
private fun Sparkline(values: List<Double>) {
    val stroke = HemColors.Bronze
    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
        if (values.size < 2) return@Canvas
        val minV = values.min()
        val maxV = values.max()
        val range = (maxV - minV).takeIf { it > 0.0001 } ?: 1.0
        val stepX = size.width / (values.size - 1).toFloat()
        var prev: Offset? = null
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - minV) / range).toFloat() * size.height
            val cur = Offset(x, y)
            val p = prev
            if (p != null) {
                drawLine(color = stroke, start = p, end = cur, strokeWidth = 2f, cap = StrokeCap.Round)
            }
            prev = cur
        }
    }
}

@Composable
private fun PaletteStrip(colors: List<Color>) {
    val padded = (colors + List(5) { HemColors.Muted.copy(alpha = 0.3f) }).take(5)
    Row(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        padded.forEach { c ->
            Box(Modifier.weight(1f).fillMaxWidth().background(c))
        }
    }
}

@Composable
private fun FitCard(
    outfit: Outfit,
    url: String?,
    heightRatio: Float,
    selected: Boolean = false,
    compareMode: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
) {
    val borderColor = when {
        selected -> HemColors.Bronze
        else -> HemColors.Hairline
    }
    val borderW = if (selected) 2.dp else 1.dp
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(HemColors.CardCream)
            .border(borderW, borderColor, RoundedCornerShape(14.dp))
            .pointerInput(outfit.id, compareMode) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = { onClick() },
                )
            },
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f / heightRatio.coerceIn(1f, 1.6f))
                    .background(HemColors.Muted.copy(alpha = 0.15f)),
            ) {
                if (!url.isNullOrBlank()) {
                    AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(
                    outfit.occasion ?: outfit.name ?: "Look",
                    style = HemType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                )
                Text(
                    relativeDate(outfit.created_at),
                    style = HemType.bodyMuted.copy(fontSize = 11.sp),
                )
            }
        }
        val kind = outfit.kind
        val isScored = (kind == null || kind == "score" || kind == "user_scan") &&
            outfit.score != null && outfit.score > 0.0
        if (isScored) {
            outfit.score?.let { s ->
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .rotate(3f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(HemColors.CardCream)
                        .border(1.dp, HemColors.Bronze, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        String.format(Locale.US, "%.1f", s),
                        style = HemType.body.copy(
                            fontFamily = SerifFamily,
                            color = HemColors.Bronze,
                            fontSize = 13.sp,
                        ),
                    )
                }
            }
        }
        if (kind != null && kind != "score" && kind != "user_scan") {
            val (label, color) = when (kind) {
                "tryon" -> "TRY-ON" to HemColors.Bronze
                "roast" -> "ROAST" to HemColors.Warning
                "decode" -> "DECODED" to HemColors.Bronze
                "studio_gen" -> "STUDIO" to HemColors.Bronze
                else -> kind.uppercase() to HemColors.Bronze
            }
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(HemColors.CardCream)
                    .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    label,
                    style = HemType.smallLabel.copy(
                        color = color,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun InsightCard(body: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(12.dp)),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(HemColors.Bronze),
        )
        Column(Modifier.padding(HemSpace.md)) {
            Eyebrow("HEM · INSIGHT")
            Spacer(Modifier.height(6.dp))
            Text(body, style = HemType.serifQuote.copy(fontSize = 16.sp))
        }
    }
}

@Composable
private fun ThrowbackCard(item: JournalItem.Throwback, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(HemSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(HemColors.Muted.copy(alpha = 0.2f)),
        ) {
            if (!item.url.isNullOrBlank()) {
                AsyncImage(model = item.url, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(HemSpace.sm))
        Column(Modifier.weight(1f)) {
            Eyebrow("30 DAYS AGO TODAY")
            Spacer(Modifier.height(4.dp))
            Text(item.outfit.occasion ?: "Look", style = HemType.body.copy(fontWeight = FontWeight.SemiBold))
        }
        item.outfit.score?.let {
            Text(
                String.format(Locale.US, "%.1f", it),
                style = HemType.serifSection.copy(color = HemColors.Bronze, fontSize = 20.sp),
            )
        }
    }
}

@Composable
private fun WrappedBanner(monthLabel: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HemColors.CardCream)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(listOf(HemColors.GoldStart, HemColors.GoldEnd)),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(HemSpace.md),
    ) {
        Eyebrow("$monthLabel WRAPPING UP")
        Spacer(Modifier.height(4.dp))
        Text("Your Wrapped is being prepared.", style = HemType.serifSection.copy(fontSize = 20.sp))
        Text("The card unlocks at month end.", style = HemType.bodyMuted)
    }
}

// ---------- Helpers ----------

private fun buildItems(
    filtered: List<Outfit>,
    allOutfits: List<Outfit>,
    urls: Map<String, String?>,
    insights: List<String>,
    throwback: JournalPhoto?,
    allTimeAvg: Double?,
): List<JournalItem> {
    val out = mutableListOf<JournalItem>()
    // Milestones detected across all data — inserted at the outfit id they follow.
    val milestones = detectMilestones(allOutfits).groupBy { it.afterOutfitId }
    // Group filtered by month for reflection cards + editorial lines.
    val byMonth = filtered.groupBy {
        val d = parseDate(it.created_at) ?: return@groupBy 0
        d.year * 100 + d.monthValue
    }
    // Determine previous chronological month for delta context in reflections.
    fun prevMonthOutfits(key: Int): List<Outfit> {
        val y = key / 100; val m = key % 100
        val pm = if (m == 1) (y - 1) * 100 + 12 else y * 100 + (m - 1)
        return allOutfits.filter {
            val d = parseDate(it.created_at) ?: return@filter false
            d.year * 100 + d.monthValue == pm && (it.score ?: 0.0) > 0.0
        }
    }

    var lastMonth: Int? = null
    var lastYear: Int? = null
    var lastMonthKey: Int? = null
    var lastMonthName: String? = null
    var insightIdx = 0
    var fitCount = 0
    var throwbackInserted = false

    filtered.forEach { o ->
        val d = parseDate(o.created_at)
        if (d != null) {
            val key = d.year * 100 + d.monthValue
            if (d.monthValue != lastMonth || d.year != lastYear) {
                // Close out previous month with a reflection card if it had enough fits.
                val prevKey = lastMonthKey
                val prevName = lastMonthName
                if (prevKey != null && prevName != null) {
                    val monthList = byMonth[prevKey].orEmpty()
                    val refl = reflectionsFor(monthList, prevMonthOutfits(prevKey), prevName)
                    if (refl.size >= 3) {
                        out.add(JournalItem.Reflection(prevName, refl))
                    }
                }
                val label = "${enMonthName(d.monthValue).uppercase()} '${d.year.toString().takeLast(2)}"
                val monthList = byMonth[key].orEmpty()
                val editorial = hemMonthLine(enMonthName(d.monthValue), monthList, allTimeAvg)
                out.add(JournalItem.MonthHeader(label, editorial))
                lastMonth = d.monthValue
                lastYear = d.year
                lastMonthKey = key
                lastMonthName = enMonthName(d.monthValue)
            }
        }
        out.add(JournalItem.Fit(o, urls[o.id]))
        fitCount += 1
        // Milestones — inserted right after the outfit that triggered them.
        o.id?.let { id ->
            milestones[id]?.forEach { m ->
                out.add(JournalItem.Milestone(m.label))
            }
        }
        if (fitCount % 5 == 0 && insightIdx < insights.size) {
            out.add(JournalItem.Insight(insights[insightIdx]))
            insightIdx += 1
        }
        if (!throwbackInserted && throwback != null && fitCount == 3) {
            out.add(JournalItem.Throwback(throwback.outfit, throwback.url))
            throwbackInserted = true
        }
    }
    // Trailing reflection for the final month.
    val prevKey = lastMonthKey
    val prevName = lastMonthName
    if (prevKey != null && prevName != null) {
        val monthList = byMonth[prevKey].orEmpty()
        val refl = reflectionsFor(monthList, prevMonthOutfits(prevKey), prevName)
        if (refl.size >= 3) {
            out.add(JournalItem.Reflection(prevName, refl))
        }
    }
    return out
}

private fun parseDate(iso: String?): ZonedDateTime? {
    if (iso == null) return null
    return runCatching { ZonedDateTime.parse(iso.replace(" ", "T")) }.getOrNull()
}

private fun enMonthName(m: Int): String = EN_MONTHS.getOrElse(m - 1) { "" }

private fun relativeDate(iso: String?): String {
    val d = parseDate(iso) ?: return ""
    val days = java.time.Duration.between(d.toInstant(), java.time.Instant.now()).toDays()
    return when {
        days <= 0 -> "today"
        days == 1L -> "yesterday"
        days < 7 -> "$days days ago"
        days < 30 -> "${days / 7} weeks ago"
        else -> d.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
    }
}

private fun sparklineFor(outfits: List<Outfit>, days: Int, now: ZonedDateTime): List<Double> {
    val byDay = outfits.mapNotNull { o ->
        val d = parseDate(o.created_at) ?: return@mapNotNull null
        val s = o.score ?: return@mapNotNull null
        d.toLocalDate() to s
    }.groupBy { it.first }.mapValues { (_, list) -> list.map { it.second }.average() }

    val today = now.toLocalDate()
    val values = mutableListOf<Double>()
    var lastKnown: Double? = null
    for (i in (days - 1) downTo 0) {
        val date = today.minusDays(i.toLong())
        val v = byDay[date]
        if (v != null) {
            lastKnown = v
            values.add(v)
        } else if (lastKnown != null) {
            values.add(lastKnown!!)
        }
    }
    return values
}

private fun paletteFor(outfits: List<Outfit>): List<Color> {
    val hexes = outfits.flatMap { it.dominant_colors.orEmpty() }.take(5)
    if (hexes.isNotEmpty()) return hexes.mapNotNull { parseHex(it) }
    return listOf(
        Color(0xFFB0743A), Color(0xFF7A6A55), Color(0xFF3E362E),
        Color(0xFFC4A883), Color(0xFF8B7355),
    )
}

private fun parseHex(hex: String): Color? = runCatching {
    val h = hex.removePrefix("#").padStart(6, '0')
    Color(("FF$h").toLong(16))
}.getOrNull()

private fun deterministicInsights(outfits: List<Outfit>, uid: String): List<String> {
    if (outfits.isEmpty()) return emptyList()
    val scored = outfits.mapNotNull { o ->
        val d = parseDate(o.created_at) ?: return@mapNotNull null
        val s = o.score ?: return@mapNotNull null
        Triple(d, s, o)
    }
    val overallAvg = scored.map { it.second }.average()
    val byDow = scored.groupBy { it.first.dayOfWeek }
        .mapValues { (_, l) -> l.map { it.second }.average() }
    val worstDow = byDow.entries.minByOrNull { it.value }
    val results = mutableListOf<String>()
    if (worstDow != null && overallAvg - worstDow.value > 0.6) {
        val dayName = worstDow.key.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH).replaceFirstChar { it.uppercase() }
        results.add("Your ${dayName} average is ${String.format(Locale.US, "%.1f", overallAvg - worstDow.value)} lower. Take a look at ${dayName}s.")
    }
    if (scored.size >= 3) {
        val streak = scored.sortedByDescending { it.first }
            .fold(mutableListOf<LocalDate>()) { acc, t -> acc.add(t.first.toLocalDate()); acc }
        val distinctDays = streak.distinct().size
        if (distinctDays >= 3) {
            results.add("You scored on $distinctDays different days. Consistency respected.")
        }
    }
    val weekOfYear = java.time.LocalDate.now().get(WeekFields.of(Locale.getDefault()).weekOfYear())
    val seed = abs((uid + weekOfYear.toString()).hashCode())
    return results.take(2).let { list ->
        if (list.isEmpty()) list else list.drop(seed % list.size) + list.take(seed % list.size)
    }.take(2)
}

private fun throwbackOutfit(outfits: List<Outfit>, now: ZonedDateTime): Outfit? {
    val target = now.toLocalDate().minusDays(30)
    return outfits.firstOrNull { o ->
        val d = parseDate(o.created_at)?.toLocalDate() ?: return@firstOrNull false
        abs(java.time.temporal.ChronoUnit.DAYS.between(d, target)) <= 1
    }
}

@Composable
internal fun JournalGridSkeleton() {
    Column(Modifier.fillMaxWidth()) {
        // Two rows of two tile placeholders.
        repeat(2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HemSpace.sm)) {
                repeat(2) {
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.85f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(HemColors.CardCream)
                            .shimmer(),
                    )
                }
            }
            Spacer(Modifier.height(HemSpace.sm))
        }
    }
}

// ---------- Faceted filter panel ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FacetsSheet(
    initial: JournalFacets,
    allOutfits: List<Outfit>,
    availableOccasions: List<String>,
    now: ZonedDateTime,
    onApply: (JournalFacets) -> Unit,
    onReset: () -> Unit,
) {
    var range by remember { mutableStateOf(initial.scoreMin..initial.scoreMax) }
    val occasions = remember { mutableStateListOf<String>().apply { addAll(initial.occasions) } }
    val weekdays = remember { mutableStateListOf<DayOfWeek>().apply { addAll(initial.weekdays) } }
    val kinds = remember { mutableStateListOf<String>().apply { addAll(initial.kinds) } }
    var window by remember { mutableStateOf(initial.timeWindow) }
    var palette by remember { mutableStateOf(initial.onlyWithPalette) }

    val current = JournalFacets(
        scoreMin = range.start,
        scoreMax = range.endInclusive,
        occasions = occasions.toSet(),
        weekdays = weekdays.toSet(),
        kinds = kinds.toSet(),
        timeWindow = window,
        onlyWithPalette = palette,
    )
    val previewCount = remember(current, allOutfits) { applyFacets(allOutfits, current, now).size }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
    ) {
        Text("Filters", style = HemType.serifSection)
        Spacer(Modifier.height(HemSpace.md))

        // Score range
        Eyebrow("SCORE")
        Spacer(Modifier.height(6.dp))
        Text(
            "${String.format(Locale.US, "%.1f", range.start)} – ${String.format(Locale.US, "%.1f", range.endInclusive)}",
            style = HemType.body.copy(fontSize = 13.sp),
        )
        androidx.compose.material3.RangeSlider(
            value = range,
            onValueChange = { range = it },
            valueRange = 0f..10f,
            steps = 19,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = HemColors.Ink,
                activeTrackColor = HemColors.Bronze,
                inactiveTrackColor = HemColors.Hairline,
            ),
        )
        Spacer(Modifier.height(HemSpace.md))

        // Time window
        Eyebrow("TIME")
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().horizontalScrollable(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeWindow.values().forEach { tw ->
                JournalChip(label = tw.label, selected = window == tw, onClick = { window = tw })
            }
        }
        Spacer(Modifier.height(HemSpace.md))

        // Kind multi-select
        Eyebrow("KIND")
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().horizontalScrollable(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("score" to "Scored", "studio_gen" to "Studio", "tryon" to "Try-on", "roast" to "Roast", "decode" to "Decoded")
                .forEach { (k, label) ->
                    JournalChip(
                        label = label,
                        selected = k in kinds,
                        onClick = { if (k in kinds) kinds.remove(k) else kinds.add(k) },
                    )
                }
        }
        Spacer(Modifier.height(HemSpace.md))

        // Occasion
        Eyebrow("OCCASION")
        Spacer(Modifier.height(6.dp))
        Column(Modifier.fillMaxWidth()) {
            availableOccasions.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { occ ->
                        val sel = occ in occasions
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (sel) HemColors.Ink else Color.Transparent)
                                .border(1.dp, HemColors.Ink.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                                .clickable { if (sel) occasions.remove(occ) else occasions.add(occ) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                occ,
                                style = HemType.body.copy(
                                    color = if (sel) HemColors.OnInk else HemColors.Ink,
                                    fontSize = 13.sp,
                                ),
                            )
                        }
                    }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(HemSpace.md))

        // Weekday
        Eyebrow("WEEKDAY")
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().horizontalScrollable(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WEEKDAY_ORDER.forEach { d ->
                val label = d.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
                JournalChip(
                    label = label,
                    selected = d in weekdays,
                    onClick = { if (d in weekdays) weekdays.remove(d) else weekdays.add(d) },
                )
            }
        }
        Spacer(Modifier.height(HemSpace.md))

        // Palette toggle
        Row(
            Modifier.fillMaxWidth().clickable { palette = !palette }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Only with palette data", style = HemType.body.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(width = 40.dp, height = 22.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (palette) HemColors.Ink else HemColors.Hairline)
                    .padding(3.dp),
                contentAlignment = if (palette) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(HemColors.OnInk),
                )
            }
        }

        Spacer(Modifier.height(HemSpace.lg))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HemSpace.sm)) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, HemColors.Ink.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                    .clickable { onReset() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Reset", style = HemType.body.copy(fontSize = 14.sp))
            }
            Box(
                Modifier
                    .weight(2f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(HemColors.Ink)
                    .clickable { onApply(current) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Apply ($previewCount results)",
                    style = HemType.body.copy(color = HemColors.OnInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
        Spacer(Modifier.height(HemSpace.lg))
    }
}
