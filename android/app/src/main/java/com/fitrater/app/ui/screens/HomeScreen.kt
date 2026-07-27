package com.fitrater.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.fitrater.app.data.model.ClosetItem
import com.fitrater.app.data.model.Outfit
import com.fitrater.app.data.model.SundayLetter
import com.fitrater.app.data.repo.LatestActivity
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.data.weather.Weather
import com.fitrater.app.ui.components.CircleToken
import com.fitrater.app.ui.components.PhotoTile
import com.fitrater.app.ui.components.SkeletonBar
import com.fitrater.app.ui.components.TanBorderedCard
import com.fitrater.app.ui.components.TwoUpHairlineRow
import com.fitrater.app.ui.components.shimmer
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.EyebrowRow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.PrimaryButton
import com.fitrater.app.ui.theme.PullQuote
import com.fitrater.app.ui.theme.ScoreChip
import com.fitrater.app.ui.theme.SerifFamily
import com.fitrater.app.ui.theme.SerifTitle
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun HomeScreen(
    onScoreALook: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenJournal: () -> Unit = {},
    onOpenStudio: () -> Unit = {},
    onOpenCamera: () -> Unit = {},
    onOpenCredits: () -> Unit = {},
    onOpenPaywall: () -> Unit = {},
    isPro: Boolean = false,
) {
    val context = LocalContext.current
    var latestAct by remember { mutableStateOf<LatestActivity?>(null) }
    var selectedPiece by remember { mutableStateOf<ClosetItem?>(null) }
    var selectedPieceUrl by remember { mutableStateOf<String?>(null) }
    var bestOutfit by remember { mutableStateOf<Outfit?>(null) }
    var averageScore by remember { mutableStateOf<Double?>(null) }
    var displayName by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf<String?>(null) }
    var hemNoteBody by remember { mutableStateOf<String?>(null) }
    var tempC by remember { mutableStateOf<Int?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var recentOutfits by remember { mutableStateOf<List<Outfit>>(emptyList()) }
    var recentUrls by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var sundayLetter by remember { mutableStateOf<SundayLetter?>(null) }
    var firstRunDone by remember { mutableStateOf(true) }
    var closetCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val profile = runCatching { Repo.currentProfile() }.getOrNull()
        displayName = profile?.display_name
        email = Repo.userEmail
        firstRunDone = profile?.first_run_done ?: false
        latestAct = runCatching { Repo.latestActivity() }
            .onFailure { com.fitrater.app.util.ToastBus.post("Couldn't load latest") }
            .getOrNull()
        averageScore = runCatching { Repo.averageScore() }.getOrNull()
        bestOutfit = runCatching { Repo.bestOutfit() }.getOrNull()
        hemNoteBody = runCatching { Repo.latestHemNote()?.body }.getOrNull()
        tempC = runCatching { Weather.temperatureCelsius(context) }.getOrNull()
        val recents = runCatching { Repo.outfits(5) }.getOrDefault(emptyList())
        recentOutfits = recents
        val urls = mutableMapOf<String, String?>()
        recents.forEach { o ->
            val id = o.id
            val p = o.photo_path
            if (id != null && p != null) {
                urls[id] = runCatching { Repo.signedOutfitUrl(p) }.getOrNull()
            }
        }
        recentUrls = urls
        sundayLetter = runCatching { Repo.latestSundayLetter() }.getOrNull()
        closetCount = runCatching { Repo.closetItemCount() }.getOrDefault(0)
        // First-run persistence: once any content exists, mark done.
        if (!firstRunDone && (recents.isNotEmpty() || closetCount > 0)) {
            runCatching { Repo.markFirstRunDone() }
            firstRunDone = true
        }
        loaded = true
    }

    val greetingName = displayName?.substringBefore(" ") ?: email?.substringBefore("@") ?: ""
    val isFirstRun = loaded && !firstRunDone && recentOutfits.isEmpty() && closetCount == 0

    if (!loaded) {
        HomeSkeleton()
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(HemColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
    ) {
        val credits by com.fitrater.app.util.CreditsBus.balance.collectAsState()
        HomeHeader(
            name = greetingName,
            tempC = tempC,
            initial = greetingName.firstOrNull()?.uppercaseChar()?.toString() ?: "•",
            credits = credits,
            onOpenCredits = onOpenCredits,
        )
        Spacer(Modifier.height(HemSpace.lg))

        // Low credits nudge — renders only when balance < 30 and not dismissed today.
        // Suppressed on first run so it can't stack above the onboarding banner.
        if (!isFirstRun) {
            com.fitrater.app.ui.components.LowCreditsBanner(onOpenCredits = onOpenCredits)
        }

        if (!hemNoteBody.isNullOrBlank() && !isFirstRun) {
            HemMorningCard(hemNoteBody!!)
            Spacer(Modifier.height(HemSpace.lg))
        }

        if (isFirstRun) {
            FirstRunBanner(
                onOpenCamera = onOpenCamera,
                onOpenStudio = onOpenStudio,
            )
            Spacer(Modifier.height(HemSpace.xl))
            return@Column
        }

        val act = latestAct
        if (act != null) {
            EyebrowRow("LATEST")
            Spacer(Modifier.height(HemSpace.sm))
            when (act) {
                is LatestActivity.OutfitItem -> {
                    val outfit = act.outfit
                    val url = act.signedUrl
                    val id = outfit.id
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.82f)
                            .clickable {
                                if (id != null) onOpenDetail(id)
                                else com.fitrater.app.util.ToastBus.post("This look isn't ready yet")
                            },
                    ) {
                        if (url != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(url).build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                filterQuality = FilterQuality.High,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)),
                            )
                        } else {
                            PhotoTile(modifier = Modifier.fillMaxSize(), tint = Color(0xFF7A6A55))
                        }
                        val kind = outfit.kind
                        val isScored = (kind == null || kind == "score" || kind == "user_scan") &&
                            outfit.score != null && outfit.score > 0.0
                        if (isScored) {
                            ScoreChip(
                                score = outfit.score!!,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(HemSpace.md),
                            )
                        } else if (kind != null) {
                            KindPill(
                                label = kindLabel(kind),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(HemSpace.md),
                            )
                        }
                    }
                    Spacer(Modifier.height(HemSpace.sm))
                    val subline = sublineForOutfit(outfit, averageScore)
                    if (subline.isNotBlank()) {
                        Text(
                            subline,
                            style = HemType.bodyMuted.copy(fontSize = 12.sp, letterSpacing = 1.sp),
                        )
                    }
                    val q = outfit.hem_comment ?: outfit.notes
                    if (!q.isNullOrBlank()) {
                        Spacer(Modifier.height(HemSpace.md))
                        PullQuote(q)
                    }
                }
                is LatestActivity.Piece -> {
                    val piece = act.item
                    val url = act.signedUrl
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.82f)
                            .clickable {
                                selectedPiece = piece
                                selectedPieceUrl = url
                            },
                    ) {
                        if (url != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(url).build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                filterQuality = FilterQuality.High,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)),
                            )
                        } else {
                            PhotoTile(modifier = Modifier.fillMaxSize(), tint = Color(0xFF7A6A55))
                        }
                        KindPill(
                            label = "STUDIO",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(HemSpace.md),
                        )
                    }
                    Spacer(Modifier.height(HemSpace.sm))
                    val cat = (piece.category ?: "piece").replaceFirstChar { it.uppercase() }
                    val ago = relativeTime(piece.created_at)
                    val subline = listOfNotNull(cat.takeIf { it.isNotBlank() }, ago.takeIf { it.isNotBlank() })
                        .joinToString(" · ")
                        .uppercase()
                    if (subline.isNotBlank()) {
                        Text(
                            subline,
                            style = HemType.bodyMuted.copy(fontSize = 12.sp, letterSpacing = 1.sp),
                        )
                    }
                    val name = piece.name
                    if (!name.isNullOrBlank()) {
                        Spacer(Modifier.height(HemSpace.md))
                        Text(
                            "\"$name\"",
                            style = HemType.serifQuote.copy(fontStyle = FontStyle.Italic),
                        )
                    }
                }
            }
        } else {
            EmptyLatestOutfit(onScoreALook = onScoreALook)
        }

        Spacer(Modifier.height(HemSpace.lg))
        PrimaryButton(label = "✦ Score a look", onClick = onScoreALook)
        Spacer(Modifier.height(HemSpace.lg))

        // Style challenge of the week
        StyleChallengeCard(
            title = StyleChallenges.current(),
            onSeeProgress = onOpenJournal,
        )
        Spacer(Modifier.height(HemSpace.md))

        // Sunday letter preview
        SundayLetterPreview(
            letter = sundayLetter,
            isPro = isPro,
            onOpen = onOpenJournal,
            onOpenPaywall = onOpenPaywall,
        )
        Spacer(Modifier.height(HemSpace.lg))

        // Recent activity
        if (recentOutfits.isNotEmpty()) {
            EyebrowRow("RECENT")
            Spacer(Modifier.height(HemSpace.sm))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(HemSpace.xs),
            ) {
                // oldest right → so reverse (list is desc from repo)
                recentOutfits.forEach { o ->
                    val id = o.id
                    val url = id?.let { recentUrls[it] }
                    Box(
                        Modifier
                            .size(width = 48.dp, height = 60.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(HemColors.CardCream)
                            .border(1.dp, HemColors.Hairline, RoundedCornerShape(6.dp))
                            .clickable(enabled = id != null) { id?.let(onOpenDetail) },
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
                }
            }
            Spacer(Modifier.height(HemSpace.lg))
        }

        val best = bestOutfit
        val bestScoreText = best?.score?.let { "%.1f · view".format(it) } ?: "No fits yet"
        TwoUpHairlineRow(
            left = {
                Column(
                    Modifier.clickable(enabled = best?.id != null) {
                        best?.id?.let(onOpenDetail)
                    },
                ) {
                    Eyebrow("YOUR BEST FIT")
                    Spacer(Modifier.height(6.dp))
                    Text("$bestScoreText →", style = HemType.body)
                }
            },
            right = {
                Column(Modifier.clickable { onOpenJournal() }) {
                    Eyebrow("THIS WEEK")
                    Spacer(Modifier.height(6.dp))
                    Text("Open journal →", style = HemType.body)
                }
            },
        )
        Spacer(Modifier.height(HemSpace.xl))
    }

    val sp = selectedPiece
    if (sp != null) {
        PieceDetailSheet(
            item = sp,
            imageUrl = selectedPieceUrl,
            onDismiss = {
                selectedPiece = null
                selectedPieceUrl = null
            },
            onEdit = {
                com.fitrater.app.util.EditRequestBus.set(sp, selectedPieceUrl)
                selectedPiece = null
                selectedPieceUrl = null
                onOpenStudio()
            },
        )
    }
}

private fun sublineForOutfit(outfit: Outfit, avg: Double?): String {
    val kind = outfit.kind
    return when (kind) {
        "tryon" -> {
            val piece = outfit.hem_comment?.removePrefix("Try-on: ")?.trim().orEmpty()
            if (piece.isNotBlank()) "TRY-ON · ${piece.uppercase()}"
            else "TRY-ON"
        }
        "roast" -> {
            val occ = outfit.occasion?.uppercase().orEmpty()
            if (occ.isNotBlank()) "ROASTED · $occ" else "ROASTED"
        }
        "decode" -> "DECODED"
        "studio_gen" -> "STUDIO"
        else -> {
            val delta = if (avg != null && outfit.score != null) outfit.score - avg else null
            val deltaText = when {
                delta == null -> null
                delta > 0 -> String.format(Locale.US, "%.1f above your average", delta)
                delta < 0 -> String.format(Locale.US, "%.1f below your average", abs(delta))
                else -> "on your average"
            }
            listOfNotNull(deltaText, outfit.occasion?.uppercase()).joinToString(" · ")
        }
    }
}

private fun kindLabel(kind: String): String = when (kind) {
    "tryon" -> "TRY-ON"
    "roast" -> "ROAST"
    "decode" -> "DECODED"
    "studio_gen" -> "STUDIO"
    else -> kind.uppercase()
}

private fun relativeTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val ts = runCatching { java.time.OffsetDateTime.parse(iso).toInstant() }.getOrNull()
        ?: runCatching { java.time.Instant.parse(iso) }.getOrNull()
        ?: return ""
    val mins = java.time.Duration.between(ts, java.time.Instant.now()).toMinutes()
    return when {
        mins < 1L -> "just now"
        mins < 60L -> "$mins min ago"
        mins < 60L * 24 -> {
            val h = mins / 60
            if (h == 1L) "1 hour ago" else "$h hours ago"
        }
        mins < 60L * 24 * 7 -> {
            val d = mins / (60 * 24)
            if (d == 1L) "yesterday" else "$d days ago"
        }
        else -> {
            val w = mins / (60 * 24 * 7)
            if (w == 1L) "1 week ago" else "$w weeks ago"
        }
    }
}

@Composable
private fun KindPill(label: String, modifier: Modifier = Modifier) {
    val bg = HemColors.Paper.copy(alpha = 0.92f)
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, HemColors.Bronze.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            style = HemType.smallLabel.copy(
                color = HemColors.Bronze,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun HomeHeader(
    name: String,
    tempC: Int?,
    initial: String,
    credits: Int?,
    onOpenCredits: () -> Unit,
) {
    val now = remember { LocalDateTime.now() }
    val dateLabel = remember(now) {
        now.format(DateTimeFormatter.ofPattern("EEEE · d MMMM", Locale.ENGLISH)).uppercase()
    }
    val hour = now.hour
    val greeting = when {
        hour < 5 -> "Late night"
        hour < 12 -> "Morning"
        hour < 17 -> "Afternoon"
        hour < 21 -> "Evening"
        else -> "Night"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(dateLabel, style = HemType.eyebrowMuted)
            Spacer(Modifier.height(HemSpace.xs))
            val greetName = if (name.isNotBlank()) "$greeting, $name." else "$greeting."
            SerifTitle(greetName)
        }
        Row(verticalAlignment = Alignment.Top) {
            CircleToken(if (tempC != null) "$tempC°" else "--°")
            Spacer(Modifier.width(HemSpace.xs))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clickable { onOpenCredits() }) {
                    CircleToken(initial)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (credits != null) "$credits CR" else "— CR",
                    style = HemType.smallLabel.copy(
                        color = if (credits != null) HemColors.Bronze else HemColors.Muted,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                    ),
                    modifier = Modifier.clickable { onOpenCredits() },
                )
            }
        }
    }
}

@Composable
private fun HemMorningCard(body: String) {
    Column {
        Eyebrow("HEM · THIS MORNING")
        Spacer(Modifier.height(HemSpace.sm))
        TanBorderedCard {
            Text(
                "\"$body\"",
                style = HemType.serifQuote,
            )
        }
    }
}

@Composable
private fun EmptyLatestOutfit(onScoreALook: () -> Unit) {
    TanBorderedCard {
        Text(
            "You haven't scored a look yet.",
            style = HemType.serifSection,
        )
        Spacer(Modifier.height(HemSpace.xs))
        Text(
            "Tap the camera below and let Hem take the first read.",
            style = HemType.bodyMuted,
        )
    }
}

@Composable
private fun StyleChallengeCard(title: String, onSeeProgress: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(14.dp))
            .padding(HemSpace.md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("THIS WEEK'S CHALLENGE")
            Spacer(Modifier.weight(1f))
            Text(
                "SEE PROGRESS →",
                style = HemType.eyebrow,
                modifier = Modifier.clickable { onSeeProgress() },
            )
        }
        Spacer(Modifier.height(HemSpace.sm))
        Text(title, style = HemType.serifSection)
        Spacer(Modifier.height(HemSpace.xs))
        Text(
            "Score a fit that matches — Hem will note it in your Journal.",
            style = HemType.bodyMuted,
        )
    }
}

@Composable
private fun SundayLetterPreview(
    letter: SundayLetter?,
    isPro: Boolean,
    onOpen: () -> Unit,
    onOpenPaywall: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(14.dp))
            .padding(HemSpace.md),
    ) {
        Eyebrow("THE SUNDAY LETTER")
        Spacer(Modifier.height(HemSpace.sm))
        if (!isPro) {
            // Locked teaser — Free tier never sees the actual letter body on Home.
            Text(
                "This week's letter is waiting…",
                style = HemType.serifQuote.copy(fontStyle = FontStyle.Italic),
            )
            Spacer(Modifier.height(HemSpace.xs))
            Text(
                "PRO UNLOCKS THE FULL LETTER",
                style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 2.sp),
            )
            Spacer(Modifier.height(HemSpace.sm))
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, HemColors.Ink.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                    .clickable { onOpenPaywall() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    "View →",
                    style = HemType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
            }
            return@Column
        }
        val body = letter?.body
        if (body.isNullOrBlank()) {
            Text(
                "Your first Sunday letter will arrive after a week of fits.",
                style = HemType.bodyMuted,
            )
        } else {
            Text(
                body,
                style = HemType.serifQuote.copy(fontStyle = FontStyle.Italic),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(HemSpace.sm))
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, HemColors.Ink.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                    .clickable { onOpen() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    "Read the full letter →",
                    style = HemType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

@Composable
private fun FirstRunBanner(onOpenCamera: () -> Unit, onOpenStudio: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("Let's start your closet.", style = HemType.serifDisplay.copy(fontSize = 34.sp, lineHeight = 40.sp))
        Spacer(Modifier.height(HemSpace.sm))
        Text(
            "Two quick ways in — pick whichever suits you today.",
            style = HemType.bodyMuted,
        )
        Spacer(Modifier.height(HemSpace.lg))

        // Card 1 — Camera
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(HemColors.CardCream)
                .border(1.dp, HemColors.Hairline, RoundedCornerShape(16.dp))
                .padding(HemSpace.lg),
        ) {
            Eyebrow("01 · CAMERA")
            Spacer(Modifier.height(HemSpace.xs))
            Text("Snap your first look", style = HemType.serifSection)
            Spacer(Modifier.height(HemSpace.xs))
            Text("Score a fit you're wearing.", style = HemType.bodyMuted)
            Spacer(Modifier.height(HemSpace.md))
            PrimaryButton(label = "Open camera", onClick = onOpenCamera)
        }

        Spacer(Modifier.height(HemSpace.md))

        // Card 2 — Studio
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(HemColors.CardCream)
                .border(1.dp, HemColors.Hairline, RoundedCornerShape(16.dp))
                .padding(HemSpace.lg),
        ) {
            Eyebrow("02 · STUDIO")
            Spacer(Modifier.height(HemSpace.xs))
            Text("Design a piece", style = HemType.serifSection)
            Spacer(Modifier.height(HemSpace.xs))
            Text("Generate a garment from scratch.", style = HemType.bodyMuted)
            Spacer(Modifier.height(HemSpace.md))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, HemColors.Ink.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .clickable { onOpenStudio() },
                contentAlignment = Alignment.Center,
            ) {
                Text("OPEN STUDIO", style = HemType.label.copy(color = HemColors.Ink))
            }
        }
    }
}

@Composable
private fun HomeSkeleton() {
    Column(
        Modifier
            .fillMaxSize()
            .background(HemColors.Paper)
            .padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
    ) {
        // Greeting row
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                SkeletonBar(height = 10.dp, widthFraction = 0.5f, corner = 3.dp)
                Spacer(Modifier.height(HemSpace.sm))
                SkeletonBar(height = 30.dp, widthFraction = 0.7f)
            }
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(HemColors.CardCream)
                    .shimmer(),
            )
        }
        Spacer(Modifier.height(HemSpace.xl))
        // Hem card placeholder
        Box(
            Modifier
                .fillMaxWidth()
                .height(90.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(HemColors.CardCream)
                .shimmer(),
        )
        Spacer(Modifier.height(HemSpace.lg))
        SkeletonBar(height = 10.dp, widthFraction = 0.3f, corner = 3.dp)
        Spacer(Modifier.height(HemSpace.sm))
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)
                .clip(RoundedCornerShape(20.dp))
                .background(HemColors.CardCream)
                .shimmer(),
        )
        Spacer(Modifier.height(HemSpace.lg))
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(HemColors.CardCream)
                .shimmer(),
        )
        Spacer(Modifier.height(HemSpace.lg))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HemSpace.sm)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(70.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HemColors.CardCream)
                    .shimmer(),
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(70.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HemColors.CardCream)
                    .shimmer(),
            )
        }
    }
}
