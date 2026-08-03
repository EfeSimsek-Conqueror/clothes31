package com.fitrater.app.ui.screens.subpages

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitrater.app.data.model.ClosetItem
import com.fitrater.app.data.model.Outfit
import com.fitrater.app.data.model.Profile
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs

// ============================================================================
// Data
// ============================================================================

data class StyleDna(
    val month: String,
    val fitCount: Int,
    val studioCount: Int,
    val avgScore: Double?,
    val bestScore: Double?,
    val paletteHex: List<String>,
    val topOccasion: String?,
    val topKind: String?,
    val moodWord: String,
    val archetypeDeclared: String?,
    val archetypeObserved: String,
    val driftScore: Double,
    val bestFitPath: String?,
) {
    val subline: String
        get() {
            val mood = moodWord.lowercase()
            val decl = archetypeDeclared
            return if (decl != null && !decl.equals(archetypeObserved, true))
                "A declared ${decl.lowercase()} leaning ${archetypeObserved.lowercase()} — $mood."
            else
                "A ${archetypeObserved.lowercase()} month — $mood."
        }
}

object StyleDnaBuilder {
    private val STUDIO_KINDS = setOf("studio_gen", "tryon", "roast", "decode")

    fun build(outfits: List<Outfit>, closet: List<ClosetItem>, profile: Profile?, monthKey: String? = null): StyleDna {
        val scoped: List<Outfit> = if (monthKey == null) outfits else outfits.filter { (it.created_at ?: "").startsWith(monthKey) }
        val scored = scoped.mapNotNull { it.score }.filter { it > 0 }
        val studioCount = scoped.count { it.kind in STUDIO_KINDS }
        val fitCount = scoped.size - studioCount
        val avg = if (scored.isEmpty()) null else scored.sum() / scored.size
        val best = scored.maxOrNull()
        val bestOutfit = scoped.maxByOrNull { it.score ?: -1.0 }

        val topOcc = scoped.mapNotNull { it.occasion?.lowercase() }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key
            ?.replaceFirstChar { it.uppercase() }

        val topKind = scoped.mapNotNull { it.kind }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key

        val palette = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (o in scoped) {
            colorsFromOutfit(o).forEach { hex ->
                if (palette.size < 5 && seen.add(hex)) palette.add(hex)
            }
            if (palette.size >= 5) break
        }
        if (palette.size < 5) {
            for (c in closet) {
                val h = c.color_hex ?: continue
                if (seen.add(h)) palette.add(h)
                if (palette.size >= 5) break
            }
        }

        val observed = observeArchetype(closet, palette, avg)
        val declared = profile?.style_tags?.firstOrNull()?.replaceFirstChar { it.uppercase() }
        val drift = if (declared != null && !declared.equals(observed, true)) 0.55 else 0.0

        val mood = when {
            avg == null -> "quiet"
            avg >= 8.5 -> "confident"
            avg >= 7.5 -> "sharp"
            avg >= 6.5 -> "steady"
            else -> "exploratory"
        }

        return StyleDna(
            month = monthKey ?: "",
            fitCount = fitCount,
            studioCount = studioCount,
            avgScore = avg, bestScore = best,
            paletteHex = palette,
            topOccasion = topOcc, topKind = topKind,
            moodWord = mood,
            archetypeDeclared = declared, archetypeObserved = observed,
            driftScore = drift,
            bestFitPath = bestOutfit?.photo_path,
        )
    }

    private fun colorsFromOutfit(o: Outfit): List<String> {
        // Best-effort: legacy hem_comment sometimes carries a hex prefix.
        val hc = o.hem_comment
        if (!hc.isNullOrBlank() && hc.startsWith("#")) return listOf(hc.take(7))
        return emptyList()
    }

    private fun observeArchetype(closet: List<ClosetItem>, palette: List<String>, avg: Double?): String {
        val cats = closet.mapNotNull { it.category?.lowercase() }
        val hasStreet = cats.count { it == "outerwear" || it == "shoes" } > 3
        val dressy = cats.count { it == "dress" } > 2
        val earthy = palette.count { isEarthy(it) } >= 2
        return when {
            dressy && earthy -> "Romantic"
            hasStreet -> "Streetwear"
            earthy -> "Minimalist"
            avg != null && avg >= 8 -> "Classic"
            else -> "Modern"
        }
    }

    private fun isEarthy(hex: String): Boolean {
        val clean = hex.removePrefix("#").take(6)
        val v = clean.toIntOrNull(16) ?: return false
        val r = (v shr 16) and 0xFF; val g = (v shr 8) and 0xFF; val b = v and 0xFF
        return r > b && maxOf(r, g, b) < 220 && abs(r - g) < 60
    }
}

// ============================================================================
// Screen
// ============================================================================

@Composable
fun StyleDnaScreen(monthKey: String? = null, onClose: () -> Unit) {
    val context = LocalContext.current
    var dna by remember { mutableStateOf<StyleDna?>(null) }
    var heroUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(monthKey) {
        runCatching {
            coroutineScope {
                val outfitsD = async { Repo.outfits(limit = 300) }
                val closetD = async { Repo.closetItems() }
                val profileD = async { Repo.currentProfile() }
                val d = StyleDnaBuilder.build(outfitsD.await(), closetD.await(), profileD.await(), monthKey)
                dna = d
                d.bestFitPath?.let { p ->
                    heroUrl = runCatching { Repo.signedOutfitUrl(p) }.getOrNull()
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(HemColors.Paper)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("STYLE DNA", style = HemType.smallLabel.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Bronze, letterSpacing = 2.5.sp))
                    Spacer(Modifier.height(8.dp))
                    Text(title(monthKey), style = HemType.serifDisplay.copy(fontSize = 32.sp, color = HemColors.Ink))
                }
                Box(Modifier.size(36.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = HemColors.Ink)
                }
            }
            Spacer(Modifier.height(20.dp))
            val d = dna
            if (d == null) {
                Text("Loading…", style = HemType.body.copy(fontSize = 14.sp, color = HemColors.Muted))
            } else {
                Text(d.subline, style = HemType.body.copy(fontSize = 16.sp, fontStyle = FontStyle.Italic, color = HemColors.Muted))
                Spacer(Modifier.height(18.dp))
                Preview(d, heroUrl, monthKey)
                Spacer(Modifier.height(18.dp))
                StatsRow(d)
                Spacer(Modifier.height(18.dp))
                PaletteBlock(d)
                Spacer(Modifier.height(20.dp))
                ShareButton { shareText(context, d) }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun Preview(d: StyleDna, heroUrl: String?, monthKey: String?) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 380.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(8.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("YOUR STYLE", style = HemType.smallLabel.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Bronze, letterSpacing = 2.sp))
        Text(title(monthKey), style = HemType.serifDisplay.copy(fontSize = 22.sp, color = HemColors.Ink))
        Box(
            Modifier
                .size(width = 160.dp, height = 200.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(HemColors.Muted.copy(alpha = 0.15f)),
        ) {
            heroUrl?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            d.paletteHex.forEach { hex ->
                Box(Modifier.size(16.dp).clip(CircleShape).background(parseHex(hex) ?: HemColors.Muted))
            }
        }
        Text("FITRATER", style = HemType.smallLabel.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Ink, letterSpacing = 2.sp))
    }
}

@Composable
private fun StatsRow(d: StyleDna) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(HemColors.Hairline))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Stat("FITS", d.fitCount.toString())
            d.avgScore?.let { Stat("AVG", "%.1f".format(it)) }
            d.bestScore?.let { Stat("BEST", "%.1f".format(it)) }
            d.topOccasion?.let { Stat("MOSTLY", it.uppercase()) }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = HemType.smallLabel.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Muted, letterSpacing = 1.5.sp))
        Text(value, style = HemType.serifDisplay.copy(fontSize = 16.sp, color = HemColors.Ink))
    }
}

@Composable
private fun PaletteBlock(d: StyleDna) {
    Column {
        Text("PALETTE", style = HemType.smallLabel.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = HemColors.Bronze, letterSpacing = 2.sp))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            d.paletteHex.forEach { hex ->
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(parseHex(hex) ?: HemColors.Muted)
                        .border(1.dp, HemColors.Ink.copy(alpha = 0.1f), RoundedCornerShape(6.dp)),
                )
            }
        }
    }
}

@Composable
private fun ShareButton(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(HemColors.Ink)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text("Share Wrapped Card", style = HemType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
        }
    }
}

// ============================================================================
// Helpers
// ============================================================================

private fun title(monthKey: String?): String {
    if (monthKey.isNullOrBlank()) return "All-Time"
    val parts = monthKey.split("-")
    val mm = parts.getOrNull(1)?.toIntOrNull() ?: return monthKey
    if (mm !in 1..12) return monthKey
    val names = listOf("January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December")
    return "${names[mm - 1]} ${parts[0]}"
}

private fun parseHex(hex: String): Color? {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6) return null
    val v = clean.toLongOrNull(16) ?: return null
    val r = ((v shr 16) and 0xff).toInt() / 255f
    val g = ((v shr 8) and 0xff).toInt() / 255f
    val b = (v and 0xff).toInt() / 255f
    return Color(r, g, b, 1f)
}

private fun shareText(context: Context, d: StyleDna) {
    val text = buildString {
        appendLine("My Fitrater Style DNA")
        appendLine(d.subline)
        appendLine()
        appendLine("Fits: ${d.fitCount}")
        d.avgScore?.let { appendLine("Avg: ${"%.1f".format(it)}") }
        d.bestScore?.let { appendLine("Best: ${"%.1f".format(it)}") }
        d.topOccasion?.let { appendLine("Mostly: $it") }
        append("\nFitrater · fitrater.ai")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Style DNA"))
}
