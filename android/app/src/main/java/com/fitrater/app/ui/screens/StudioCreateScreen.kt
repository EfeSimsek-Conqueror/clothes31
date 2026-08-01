package com.fitrater.app.ui.screens

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.fitrater.app.data.model.ClosetItemInsert
import com.fitrater.app.ui.components.shimmer
import com.fitrater.app.data.repo.Repo
import io.github.jan.supabase.storage.storage
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.PrimaryButton
import com.fitrater.app.ui.theme.SerifDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val GEN_COST = 15

// ---------- Static option tables (kept top-level for reuse and testability) ----------

private val TYPES = listOf("Top", "Bottom", "Outerwear", "Dress", "Shoes", "Accessory")

private val SUBTYPES: Map<String, List<String>> = mapOf(
    "Top" to listOf("T-shirt", "Hoodie", "Blazer", "Shirt", "Tank", "Polo", "Cardigan", "Turtleneck"),
    "Bottom" to listOf("Trousers", "Jeans", "Shorts", "Skirt", "Cargo", "Sweats", "Chinos"),
    "Outerwear" to listOf("Bomber", "Trench", "Blazer", "Puffer", "Denim jacket", "Moto", "Overcoat", "Vest"),
    "Dress" to listOf("Mini", "Midi", "Maxi", "Slip", "Wrap", "Shirt-dress"),
    "Shoes" to listOf("Sneakers", "Loafers", "Boots", "Heels", "Sandals", "Derbies"),
    "Accessory" to listOf("Bag", "Belt", "Scarf", "Hat", "Jewelry", "Sunglasses"),
)

private val SILHOUETTES = listOf(
    "Slim", "Regular", "Oversized", "Cropped", "Boxy", "Drape", "Wide", "Relaxed", "Tailored", "Deconstructed",
)

private val SLEEVE = listOf("Sleeveless", "Short", "3/4", "Long", "Extra-long")
private val BOTTOM_LENGTH = listOf("Micro", "Short", "Above-knee", "Below-knee", "Ankle", "Full", "Puddle")
private val DRESS_LENGTH = listOf("Mini", "Above-knee", "Midi", "Below-knee", "Ankle", "Maxi")

private val DETAILS_DEFAULT = listOf(
    "Pocket", "Hood", "Zip", "Buttons", "Embroidery", "Print", "Distress", "Patchwork",
    "Drawstring", "Contrast piping", "Ribbed", "Quilted", "Fringe", "Lace", "Mesh", "Panel-blocking",
)
private val DETAILS_SHOES = listOf(
    "Laces", "Buckles", "Straps", "Zipper", "Perforations", "Contrast sole",
    "Stitched welt", "Metal tips", "Rubber toe", "Chunky sole", "Platform", "Cleated grip",
)
private val DETAILS_ACCESSORY = listOf(
    "Metal hardware", "Buckle", "Chain strap", "Woven strap", "Monogram print",
    "Embossed logo", "Contrast stitching", "Tassel", "Fringe", "Studs", "Pearl", "Braided",
)
private fun detailsFor(type: String?): List<String> = when (type) {
    "Shoes" -> DETAILS_SHOES
    "Accessory" -> DETAILS_ACCESSORY
    else -> DETAILS_DEFAULT
}

// Category-specific extra option pools.
private val SHOE_SOLES = listOf("Flat", "Chunky", "Block", "Stiletto", "Platform", "Lug", "Wedge", "Cleated")
private val SHOE_TOES = listOf("Round", "Pointed", "Square", "Almond", "Open", "Cap")
private val SHOE_HEIGHTS = listOf("Ankle", "Mid-calf", "Knee", "Thigh", "Flat", "2cm", "5cm", "8cm", "10cm+")
private val SHOE_CLOSURES = listOf("Laces", "Slip-on", "Zip", "Buckle", "Velcro", "Elastic")

private val BAG_TYPES = listOf("Tote", "Crossbody", "Clutch", "Backpack", "Bucket", "Hobo", "Satchel")
private val BAG_SIZES = listOf("Mini", "Small", "Medium", "Large", "Oversized")
private val BAG_STRAPS = listOf("Chain", "Leather", "Woven", "Adjustable", "No strap")
private val BAG_CLOSURES = listOf("Zip", "Magnetic", "Drawstring", "Buckle", "Open")
private val BAG_HARDWARE = listOf("Gold", "Silver", "Matte black", "None")

private fun subtypeNeedsShoeHeight(subtype: String?): Boolean =
    subtype == "Boots" || subtype == "Heels"

private data class Preset(val name: String, val hex: String)
private val COLOR_PRESETS = listOf(
    Preset("Bone", "#EAE1D0"), Preset("Khaki", "#8B7A56"), Preset("Cocoa", "#5B3A22"),
    Preset("Ink", "#141210"), Preset("Bronze", "#B0743A"), Preset("Rust", "#9E4A22"),
    Preset("Sage", "#93A187"), Preset("Cream", "#F3EEE4"), Preset("Charcoal", "#3A3833"),
    Preset("Olive", "#5C6032"), Preset("Butter", "#EED89A"), Preset("Terracotta", "#B25B3A"),
    Preset("Slate", "#5A6B73"), Preset("Wine", "#5A1E29"), Preset("Peach", "#F0B39B"),
    Preset("Powder-blue", "#B7C9D9"), Preset("Sand", "#D7C4A3"), Preset("Forest", "#264534"),
)

private val FABRICS_DEFAULT = listOf(
    "Cotton", "Linen", "Denim", "Wool", "Leather", "Silk", "Knit", "Cashmere",
    "Nylon", "Velvet", "Tweed", "Gabardine", "Corduroy", "Suede", "Satin", "Jersey",
)
private val FABRICS_SHOES = listOf(
    "Leather", "Suede", "Canvas", "Mesh", "Rubber", "Patent leather", "Nubuck", "Knit", "Vegan leather",
)
private val FABRICS_ACCESSORY = listOf(
    "Leather", "Suede", "Canvas", "Nylon", "Silk", "Wool", "Cotton", "Metal", "Beaded", "Straw",
)
private fun fabricsFor(type: String?): List<String> = when (type) {
    "Shoes" -> FABRICS_SHOES
    "Accessory" -> FABRICS_ACCESSORY
    else -> FABRICS_DEFAULT
}
// Backwards compat alias — some callers still reference FABRICS.
private val FABRICS: List<String> get() = FABRICS_DEFAULT
private val TEXTURES = listOf("Matte", "Glossy", "Heather", "Washed", "Coated", "Ribbed", "Slub")

private val OCCASIONS = listOf(
    "Everyday", "Work", "Date", "Wedding", "Gym", "Travel", "Party", "Formal", "Loungewear",
)
private val SEASONS = listOf("Spring", "Summer", "Fall", "Winter", "All-year")

private val PROMPT_ADDONS = listOf(
    "Add embroidery on chest",
    "Longer sleeves",
    "Contrast stitching",
    "Vintage wash",
    "Modern minimal cut",
    "Feminine drape",
    "Streetwear proportions",
)

// ---------- Templates ----------

private data class Template(
    val name: String,
    val glyph: String,
    val type: String,
    val subtype: String,
    val silhouette: String?,
    val colors: List<String>,
    val fabrics: List<String>,
    val texture: String? = null,
    val occasion: String = "Everyday",
    val season: String = "All-year",
    val details: List<String> = emptyList(),
    val sleeveOrLength: String? = null,
)

private val TEMPLATES = listOf(
    Template("Oversized bomber", "◇", "Outerwear", "Bomber", "Oversized", listOf("Khaki"), listOf("Cotton"), "Washed", "Everyday", "Fall", listOf("Zip", "Pocket"), "Long"),
    Template("Linen shirt", "◈", "Top", "Shirt", "Regular", listOf("Bone"), listOf("Linen"), "Slub", "Everyday", "Summer", listOf("Buttons"), "Long"),
    Template("Wide-leg trousers", "▽", "Bottom", "Trousers", "Wide", listOf("Ink"), listOf("Wool"), "Matte", "Work", "Fall", listOf("Pocket"), "Full"),
    Template("Boxy tee", "▢", "Top", "T-shirt", "Boxy", listOf("Cream"), listOf("Cotton"), "Heather", "Everyday", "Summer", emptyList(), "Short"),
    Template("Trench coat", "❒", "Outerwear", "Trench", "Drape", listOf("Cocoa"), listOf("Gabardine"), "Matte", "Work", "Fall", listOf("Buttons"), "Extra-long"),
    Template("Denim jacket", "◐", "Outerwear", "Denim jacket", "Cropped", listOf("Ink"), listOf("Denim"), "Washed", "Everyday", "Spring", listOf("Buttons", "Pocket"), "Long"),
    Template("Wrap midi dress", "❦", "Dress", "Wrap", "Drape", listOf("Rust"), listOf("Silk"), "Glossy", "Date", "Summer", emptyList(), "Midi"),
    Template("Cargo pants", "☰", "Bottom", "Cargo", "Relaxed", listOf("Olive"), listOf("Cotton"), "Washed", "Everyday", "Fall", listOf("Pocket", "Drawstring"), "Full"),
    Template("Cashmere cardigan", "≋", "Top", "Cardigan", "Oversized", listOf("Sage"), listOf("Knit", "Cashmere"), "Ribbed", "Loungewear", "Winter", listOf("Buttons"), "Long"),
    Template("Leather biker", "◆", "Outerwear", "Moto", "Slim", listOf("Ink"), listOf("Leather"), "Glossy", "Party", "Fall", listOf("Zip"), "Long"),
    Template("Pleated skirt", "❉", "Bottom", "Skirt", null, listOf("Charcoal"), listOf("Wool"), "Matte", "Work", "Winter", listOf("Buttons"), "Below-knee"),
    Template("Slip dress", "❋", "Dress", "Slip", "Slim", listOf("Bronze"), listOf("Silk"), "Glossy", "Date", "Summer", emptyList(), "Midi"),
)

// ---------- Wizard state ----------

private class WizardState {
    var type by mutableStateOf<String?>(null)
    var subtype by mutableStateOf<String?>(null)
    var silhouette by mutableStateOf<String?>(null)
    var sleeveOrLength by mutableStateOf<String?>(null)
    val details = mutableStateListOf<String>()
    val colors = mutableStateListOf<String>()      // preset name or "#RRGGBB"
    val fabrics = mutableStateListOf<String>()
    var texture by mutableStateOf<String?>(null)
    var occasion by mutableStateOf("Everyday")
    var season by mutableStateOf("All-year")
    var freeText by mutableStateOf("")
    var referenceUrl by mutableStateOf<String?>(null)
    // Category-specific: Shoes
    var sole by mutableStateOf<String?>(null)
    var toe by mutableStateOf<String?>(null)
    var shoeHeight by mutableStateOf<String?>(null)
    // Shared: Shoes + Bag
    var closureType by mutableStateOf<String?>(null)
    // Category-specific: Bag (Accessory subtype)
    var bagType by mutableStateOf<String?>(null)
    var bagSize by mutableStateOf<String?>(null)
    var strap by mutableStateOf<String?>(null)
    var hardware by mutableStateOf<String?>(null)
}

/** Minimal snapshot of the wizard state to seed variation prompts. */
private data class WizardSnapshot(
    val type: String?,
    val subtype: String?,
    val silhouette: String?,
    val sleeveOrLength: String?,
    val details: List<String>,
    val colors: List<String>,
    val fabrics: List<String>,
    val texture: String?,
    val occasion: String,
    val season: String,
    val freeText: String,
    val referenceUrl: String?,
    val mannequinGender: String,
    val prefFabrics: List<String>,
    val prefColors: List<String>,
    val sole: String? = null,
    val toe: String? = null,
    val shoeHeight: String? = null,
    val closureType: String? = null,
    val bagType: String? = null,
    val bagSize: String? = null,
    val strap: String? = null,
    val hardware: String? = null,
)

private fun WizardState.snapshot(mannequin: String, prefFabrics: List<String>, prefColors: List<String>) = WizardSnapshot(
    type = type,
    subtype = subtype,
    silhouette = silhouette,
    sleeveOrLength = sleeveOrLength,
    details = details.toList(),
    colors = colors.toList(),
    fabrics = fabrics.toList(),
    texture = texture,
    occasion = occasion,
    season = season,
    freeText = freeText,
    referenceUrl = referenceUrl,
    mannequinGender = mannequin,
    prefFabrics = prefFabrics,
    prefColors = prefColors,
    sole = sole,
    toe = toe,
    shoeHeight = shoeHeight,
    closureType = closureType,
    bagType = bagType,
    bagSize = bagSize,
    strap = strap,
    hardware = hardware,
)

/** Deterministic palette shifts for the "Colors" variation axis. */
private fun colorVariations(originals: List<String>): List<List<String>> {
    // Three curated palette shifts. Kept top-level so it's testable and predictable.
    val base = originals.firstOrNull()?.lowercase() ?: "neutral"
    val moody = listOf("Ink", "Bronze")
    val fresh = listOf("Sage", "Cream")
    val warm = listOf("Rust", "Charcoal")
    return when {
        base.contains("bone") || base.contains("cream") || base.contains("sand") -> listOf(moody, fresh, warm)
        base.contains("ink") || base.contains("charcoal") -> listOf(fresh, listOf("Cocoa", "Bone"), listOf("Slate", "Cream"))
        base.contains("cocoa") || base.contains("bronze") || base.contains("rust") -> listOf(fresh, moody, listOf("Sand", "Ink"))
        else -> listOf(moody, fresh, warm)
    }
}

private val SILHOUETTE_ALTS = listOf("Oversized", "Cropped", "Boxy", "Wide", "Tailored", "Relaxed")
private val FABRIC_ALTS = listOf("Linen", "Denim", "Wool", "Silk", "Knit", "Leather")
private val DETAIL_ALTS = listOf(
    listOf("Contrast stitching"),
    listOf("Pocket", "Drawstring"),
    listOf("Embroidery"),
    listOf("Panel-blocking"),
    listOf("Ribbed"),
)

/** Build a prompt with an axis-specific override applied to the snapshot. */
private fun buildPromptFor(snap: WizardSnapshot, variationIndex: Int, axis: VaryAxis): String {
    val overridden = when (axis) {
        VaryAxis.Colors -> {
            val palettes = colorVariations(snap.colors)
            snap.copy(colors = palettes[variationIndex % palettes.size])
        }
        VaryAxis.Silhouette -> {
            val current = snap.silhouette
            val pool = SILHOUETTE_ALTS.filter { !it.equals(current, ignoreCase = true) }
            snap.copy(silhouette = pool[variationIndex % pool.size])
        }
        VaryAxis.Fabric -> {
            val current = snap.fabrics.firstOrNull()
            val pool = FABRIC_ALTS.filter { !it.equals(current, ignoreCase = true) }
            snap.copy(fabrics = listOf(pool[variationIndex % pool.size]))
        }
        VaryAxis.Details -> {
            val add = DETAIL_ALTS[variationIndex % DETAIL_ALTS.size]
            snap.copy(details = (snap.details + add).distinct())
        }
        VaryAxis.Surprise -> {
            // Rotate through the four concrete axes deterministically per variation index.
            val ordered = listOf(VaryAxis.Colors, VaryAxis.Silhouette, VaryAxis.Fabric, VaryAxis.Details)
            return buildPromptFor(snap, variationIndex, ordered[variationIndex % ordered.size])
        }
    }
    return renderPrompt(overridden)
}

private fun renderPrompt(s: WizardSnapshot): String {
    val subtypeToken = s.subtype?.lowercase() ?: s.type?.lowercase() ?: "garment"
    val silhouettePart = s.silhouette?.lowercase() ?: "well-cut"
    val colorPart = s.colors.joinToString(" and ") { it.lowercase() }.ifBlank { "neutral" }
    val fabricPart = s.fabrics.joinToString(" / ") { it.lowercase() }.ifBlank { "cotton" }
    val texturePart = s.texture?.let { " with ${it.lowercase()} finish" }.orEmpty()
    val detailsPart = if (s.details.isNotEmpty()) {
        ", " + s.details.joinToString(", ") { it.lowercase() }
    } else ""
    val lenPart = s.sleeveOrLength?.let { ", ${it.lowercase()} length" }.orEmpty()
    val occasion = s.occasion.lowercase()
    val season = s.season.lowercase()
    val trailing = s.freeText.trim().let { if (it.isNotEmpty()) " $it" else "" }
    val hintPieces = mutableListOf<String>()
    if (s.colors.isEmpty() && s.prefColors.isNotEmpty()) {
        hintPieces.add("User leans toward ${s.prefColors.joinToString(", ") { it.lowercase() }}")
    }
    if (s.fabrics.isEmpty() && s.prefFabrics.isNotEmpty()) {
        hintPieces.add("prefers ${s.prefFabrics.joinToString(", ") { it.lowercase() }}")
    }
    val prefHint = if (hintPieces.isNotEmpty()) " " + hintPieces.joinToString(", ") + "." else ""

    // Composition varies by type. Shoes and accessories are product shots (no mannequin, no body,
    // just the piece). Everything else is worn on an editorial mannequin.
    return when (s.type) {
        "Shoes" -> {
            // Isolated product shot — shoe only, 3/4 angle, cream backdrop.
            val sole = s.sole?.lowercase() ?: "flat"
            val toe = s.toe?.lowercase() ?: "round"
            val heightPart = s.shoeHeight?.let { " with ${it.lowercase()} height" }.orEmpty()
            val closure = s.closureType?.lowercase() ?: "slip-on"
            val subtypeShoe = s.subtype?.lowercase() ?: "shoe"
            "Studio product photograph of a pair of $subtypeShoe with $sole sole, " +
                "$toe toe$heightPart, $closure closure, in $colorPart tones, $fabricPart$texturePart$detailsPart, " +
                "for $occasion $season wear. " +
                "Photographed at ground level from a 3/4 side angle. " +
                "Isolated on a clean cream studio backdrop — NO person, NO mannequin, NO legs, NO pants, " +
                "NO other garments in frame. Only the pair of shoes. Soft diffused studio lighting, " +
                "natural contact shadow, sharp material and stitching detail, editorial catalog aesthetic.$trailing$prefHint"
        }
        "Accessory" -> if (s.subtype == "Bag") {
            val bt = s.bagType?.lowercase() ?: "tote"
            val bs = s.bagSize?.lowercase() ?: "medium"
            val strap = s.strap?.lowercase() ?: "leather"
            val closure = s.closureType?.lowercase() ?: "zip"
            val hardware = s.hardware?.lowercase() ?: "gold"
            "Studio product photograph of a $bs $bt bag with $strap strap and $closure closure, " +
                "$hardware hardware, in $colorPart tones, $fabricPart$texturePart$detailsPart, " +
                "for $occasion $season wear. " +
                "Isolated on a clean cream studio backdrop — NO person, NO mannequin, NO other garments in frame. " +
                "Only the bag itself, elegantly presented. Soft diffused studio lighting, subtle floor shadow, " +
                "sharp material and hardware detail, editorial catalog aesthetic.$trailing$prefHint"
        } else {
            // Accessory: styled flat lay or floating product shot, no body.
            "Studio product photograph of a single $subtypeToken " +
                "in $colorPart tones, $fabricPart$texturePart$detailsPart, for $occasion $season wear. " +
                "Isolated on a clean cream studio backdrop — NO person, NO mannequin, NO other garments in frame. " +
                "Only the accessory itself, elegantly presented. Soft diffused studio lighting, subtle shadow, " +
                "sharp material and hardware detail, editorial catalog aesthetic.$trailing$prefHint"
        }
        else -> {
            // Garments worn on mannequin.
            "Editorial fashion photograph of a $silhouettePart $subtypeToken in $colorPart tones, " +
                "$fabricPart$texturePart$detailsPart$lenPart, for $occasion $season wear — " +
                "worn on a minimalist headless matte-white ${s.mannequinGender}-form mannequin against a clean cream studio backdrop, " +
                "front-facing, magazine editorial styling, natural studio lighting, sharp fabric detail.$trailing$prefHint"
        }
    }
}

/**
 * Which wizard steps apply to the current type. Shoes/Accessory silently skip
 * silhouette and length steps and inject category-specific questions.
 *
 * Step IDs:
 *   0=Reference (implicit — the "atStart" screen), 1=Type+Subtype, 2=Silhouette,
 *   3=Sleeve/Length, 4=Details, 5=Colors, 6=Fabric, 7=Occasion+Season,
 *   8=Refinement text, 9=Preview.
 *   10=Shoe sole, 11=Shoe toe, 12=Shoe height (Boots/Heels only), 13=Shoe closure.
 *   14=Bag type, 15=Bag size, 16=Bag strap, 17=Bag closure, 18=Bag hardware.
 */
private fun applicableSteps(type: String?, subtype: String? = null): List<Int> {
    return when (type) {
        "Shoes" -> {
            val head = mutableListOf(1, 10, 11)
            if (subtypeNeedsShoeHeight(subtype)) head += 12
            head += 13
            head += listOf(4, 5, 6, 7, 8, 9)
            head
        }
        "Accessory" -> when (subtype) {
            "Bag" -> listOf(1, 14, 15, 16, 17, 18, 5, 6, 7, 8, 9)
            else -> listOf(1, 4, 5, 6, 7, 8, 9)
        }
        else -> (1..9).toList()
    }
}

// ---------- Root ----------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudioCreateScreen(
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit = onBack,
    onAddedGoHome: () -> Unit = onBack,
    editingPieceId: String? = null,
    presetType: String? = null,
    presetStyles: List<String> = emptyList(),
    presetColors: List<String> = emptyList(),
    presetFabrics: List<String> = emptyList(),
    presetReferenceUrl: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val state = remember {
        WizardState().apply {
            type = presetType
            colors.addAll(presetColors)
            fabrics.addAll(presetFabrics)
            referenceUrl = presetReferenceUrl
        }
    }
    // Step index into applicableSteps
    var stepIndex by remember { mutableStateOf(0) }
    // Special value: -1 == Step 0 "Start from" screen
    var atStart by remember { mutableStateOf(editingPieceId == null && presetReferenceUrl == null) }

    var referenceUploading by remember { mutableStateOf(false) }
    var mannequinGender by remember { mutableStateOf("androgynous") }

    var generating by remember { mutableStateOf(false) }
    var generatedUrl by remember { mutableStateOf<String?>(null) }
    // Variations: url + savedOk flag so each thumbnail can show its own check.
    var variations by remember { mutableStateOf<List<VariationResult>>(emptyList()) }
    var generatingVariations by remember { mutableStateOf(false) }
    var showAxisSheet by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var addingToStudio by remember { mutableStateOf(false) }
    var addedOk by remember { mutableStateOf(false) }
    // Session-scoped dedup: prevents multiple Journal entries for the same generated URL.
    val savedJournalUrls = remember { mutableSetOf<String>() }

    suspend fun autoSaveToJournal(url: String, source: String) {
        if (url in savedJournalUrls) return
        savedJournalUrls.add(url)
        runCatching {
            val uid = Repo.userId ?: return@runCatching
            val bytes = Repo.downloadBytes(url)
            val path = withContext(Dispatchers.IO) { Repo.uploadOutfitPhoto(bytes, ext = "png") }
            val name = niceName(state)
            Repo.insertOutfit(
                com.fitrater.app.data.model.OutfitInsert(
                    user_id = uid,
                    photo_path = path,
                    score = 0.0,
                    occasion = "Studio",
                    hem_comment = "Generated: $name",
                    kind = "studio_gen",
                ),
            )
        }.onFailure {
            savedJournalUrls.remove(url) // allow retry on next save
            Log.w("Studio", "journal auto-save failed for $source", it)
        }
    }

    // Style-profile-derived defaults used only inside prompt building.
    var prefFabrics by remember { mutableStateOf<List<String>>(emptyList()) }
    var prefColors by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        runCatching {
            val p = Repo.currentProfile()
            mannequinGender = when (p?.gender) {
                "Female" -> "female"
                "Male" -> "male"
                else -> "androgynous"
            }
            prefFabrics = p?.preferred_fabrics.orEmpty()
            prefColors = p?.preferred_colors.orEmpty()
        }
    }

    val pickReferenceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        referenceUploading = true
        error = null
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.buffered()?.use { it.readBytes() }
                        ?: error("Could not read image")
                }
                val uid = Repo.userId ?: error("Not signed in")
                // RLS policy on closet bucket requires storage.foldername(name)[1] = auth.uid().
                // So uid MUST be the first path segment. Sub-folder "references/" comes AFTER.
                val path = "$uid/references/${java.util.UUID.randomUUID()}.jpg"
                withContext(Dispatchers.IO) {
                    com.fitrater.app.data.Supa.client.storage.from("closet").upload(path, bytes) { upsert = false }
                }
                val signed = Repo.signedClosetUrl(path)
                    ?: error("Could not get signed URL")
                state.referenceUrl = signed
                Log.i("Studio", "reference uploaded path=$path")
            }.onFailure {
                Log.e("Studio", "reference upload failed", it)
                error = it.message ?: "Reference upload failed"
                com.fitrater.app.util.ToastBus.post("Reference upload failed: ${it.message ?: "error"}")
            }
            referenceUploading = false
        }
    }

    val steps = applicableSteps(state.type, state.subtype)
    val currentStep = steps.getOrNull(stepIndex) ?: steps.last()

    fun canAdvance(): Boolean = when (currentStep) {
        1 -> state.type != null && state.subtype != null
        2 -> state.silhouette != null
        3 -> state.sleeveOrLength != null
        4 -> true
        5 -> state.colors.isNotEmpty()
        6 -> state.fabrics.isNotEmpty()
        7 -> true
        8 -> true
        9 -> true
        // Shoes
        10 -> state.sole != null
        11 -> state.toe != null
        12 -> state.shoeHeight != null
        13 -> state.closureType != null
        // Bag
        14 -> state.bagType != null
        15 -> state.bagSize != null
        16 -> state.strap != null
        17 -> state.closureType != null
        18 -> state.hardware != null
        else -> true
    }

    fun buildPrompt(): String =
        renderPrompt(state.snapshot(mannequinGender, prefFabrics, prefColors))

    fun applyTemplate(t: Template) {
        state.type = t.type
        state.subtype = t.subtype
        state.silhouette = t.silhouette
        state.sleeveOrLength = t.sleeveOrLength
        state.details.clear(); state.details.addAll(t.details)
        state.colors.clear(); state.colors.addAll(t.colors)
        state.fabrics.clear(); state.fabrics.addAll(t.fabrics)
        state.texture = t.texture
        state.occasion = t.occasion
        state.season = t.season
        val stepList = applicableSteps(t.type, t.subtype)
        stepIndex = stepList.indexOf(9).coerceAtLeast(stepList.lastIndex)
        atStart = false
    }

    suspend fun runGeneration() {
        generating = true
        error = null
        try {
            val gate = com.fitrater.app.util.CreditsGate.check(GEN_COST)
            if (gate !is com.fitrater.app.util.GateResult.Ok) {
                generating = false
                if (gate is com.fitrater.app.util.GateResult.InsufficientBalance) {
                    onOpenPaywall()
                } else {
                    com.fitrater.app.util.CreditsGate.explainAndBlock(gate)
                }
                return
            }
            val prompt = buildPrompt()
            val refs = listOfNotNull(state.referenceUrl)
            Log.i("Studio", "generate-piece prompt=$prompt refs=${refs.size}")
            val resp = Repo.generatePiece(prompt, refs)
            val url = resp.image_url
            if (url.isNullOrBlank()) {
                error = resp.error ?: "Generation failed"
                Log.e("Studio", "generate failed err=${resp.error}")
                com.fitrater.app.util.ToastBus.post("Generation failed: ${resp.error ?: "no image"}")
            } else {
                Log.i("Studio", "generate resp url=$url")
                runCatching { Repo.spendCredits(GEN_COST, "studio_gen") }
                    .onFailure { Log.w("Studio", "spend failed", it) }
                generatedUrl = url
                autoSaveToJournal(url, "hero")
            }
        } catch (t: Throwable) {
            Log.e("Studio", "generate crash", t)
            error = t.message ?: "Generation failed"
            com.fitrater.app.util.ToastBus.post("Generation failed: ${t.message ?: "error"}")
        } finally {
            generating = false
        }
    }

    suspend fun runVariations(axis: VaryAxis) {
        if (generatingVariations) return
        generatingVariations = true
        error = null
        try {
            val snap = state.snapshot(mannequinGender, prefFabrics, prefColors)
            val refs = listOfNotNull(snap.referenceUrl)
            // Kick off all three in parallel.
            val results = coroutineScope {
                (0 until 3).map { i ->
                    async(Dispatchers.IO) {
                        val prompt = buildPromptFor(snap, i, axis)
                        Log.i("Studio", "variation[$i] axis=$axis")
                        val r = runCatching { Repo.generatePiece(prompt, refs) }
                        val ok = r.getOrNull()?.image_url
                        if (!ok.isNullOrBlank()) {
                            runCatching { Repo.spendCredits(GEN_COST, "studio_gen_variation") }
                            ok
                        } else {
                            Log.w("Studio", "variation[$i] failed err=${r.exceptionOrNull()?.message}")
                            null
                        }
                    }
                }.awaitAll()
            }
            val successful = results.filterNotNull()
            val failed = results.count { it == null }
            variations = successful.map { VariationResult(url = it, savedOk = false) }
            // Persist each successful alternative to Journal so they survive past this session.
            for (url in successful) {
                autoSaveToJournal(url, "variation-auto")
            }
            if (failed > 0) {
                com.fitrater.app.util.ToastBus.post(
                    "$failed variation${if (failed > 1) "s" else ""} failed — kept ${GEN_COST * failed} credits",
                )
            }
        } finally {
            generatingVariations = false
        }
    }

    suspend fun saveVariationToStudio(url: String): Boolean {
        val uid = Repo.userId ?: return false
        return runCatching {
            val bytes = Repo.downloadBytes(url)
            val path = withContext(Dispatchers.IO) { Repo.uploadClosetPhoto(bytes, ext = "png") }
            Repo.insertClosetItem(
                ClosetItemInsert(
                    user_id = uid,
                    name = niceName(state),
                    category = state.type?.lowercase() ?: "top",
                    subcategory = state.subtype?.lowercase(),
                    image_path = path,
                    parent_id = editingPieceId,
                ),
            )
            true
        }.getOrElse {
            Log.e("Studio", "save-variation failed", it)
            com.fitrater.app.util.ToastBus.post("Save failed: ${it.message ?: "error"}")
            false
        }
    }

    // ---- Result screen ----
    if (generatedUrl != null) {
        if (showAxisSheet) {
            val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { showAxisSheet = false },
                sheetState = sheetState,
                containerColor = HemColors.Paper,
            ) {
                VariationAxisSheetContent(onPick = { axis ->
                    showAxisSheet = false
                    scope.launch { runVariations(axis) }
                })
            }
        }
        GeneratedResultScreen(
            heroUrl = generatedUrl!!,
            variations = variations,
            generatingVariations = generatingVariations,
            addingBusy = addingToStudio,
            addedOk = addedOk,
            errorText = error,
            onTryAgain = {
                generatedUrl = null
                variations = emptyList()
                addedOk = false
                error = null
                stepIndex = 0
                atStart = editingPieceId == null
            },
            onPickVariation = { url ->
                generatedUrl = url
                addedOk = false
            },
            onSaveVariation = { url ->
                scope.launch {
                    val ok = saveVariationToStudio(url)
                    if (ok) {
                        variations = variations.map {
                            if (it.url == url) it.copy(savedOk = true) else it
                        }
                        autoSaveToJournal(url, "variation")
                        com.fitrater.app.util.ToastBus.post("Saved to Studio")
                    }
                }
            },
            onGenerateVariations = {
                scope.launch {
                    val gate = com.fitrater.app.util.CreditsGate.check(GEN_COST * 3)
                    when (gate) {
                        is com.fitrater.app.util.GateResult.Ok -> showAxisSheet = true
                        is com.fitrater.app.util.GateResult.InsufficientBalance -> onOpenPaywall()
                        else -> com.fitrater.app.util.CreditsGate.explainAndBlock(gate)
                    }
                }
            },
            onAddToStudio = {
                if (addingToStudio) return@GeneratedResultScreen
                scope.launch {
                    addingToStudio = true
                    error = null
                    runCatching {
                        val bytes = Repo.downloadBytes(generatedUrl!!)
                        val path = withContext(Dispatchers.IO) { Repo.uploadClosetPhoto(bytes, ext = "png") }
                        val uid = Repo.userId ?: error("Not signed in")
                        if (editingPieceId != null) {
                            // Edits become new rows chained via parent_id, per iteration history spec.
                            Repo.insertClosetItem(
                                ClosetItemInsert(
                                    user_id = uid,
                                    name = niceName(state),
                                    category = state.type?.lowercase() ?: "top",
                                    subcategory = state.subtype?.lowercase(),
                                    image_path = path,
                                    parent_id = editingPieceId,
                                ),
                            )
                            com.fitrater.app.util.ToastBus.post("Saved as variation")
                        } else {
                            Repo.insertClosetItem(
                                ClosetItemInsert(
                                    user_id = uid,
                                    name = niceName(state),
                                    category = state.type?.lowercase() ?: "top",
                                    subcategory = state.subtype?.lowercase(),
                                    image_path = path,
                                ),
                            )
                            com.fitrater.app.util.ToastBus.post("Saved to Studio")
                        }
                        addedOk = true
                    }.onFailure {
                        Log.e("Studio", "addToStudio failed", it)
                        error = it.message ?: "Save failed"
                        com.fitrater.app.util.ToastBus.post("Upload failed: ${it.message ?: "error"}")
                    }
                    addingToStudio = false
                }
            },
            onDone = onAddedGoHome,
            onBack = onBack,
        )
        return
    }

    if (generating) {
        GeneratingScreen()
        return
    }

    // ---- Step 0 / templates picker ----
    if (atStart) {
        StartFromScreen(
            onBlank = { atStart = false; stepIndex = 0 },
            onPickTemplate = { applyTemplate(it) },
            onBack = onBack,
        )
        return
    }

    // ---- Main wizard shell ----
    Column(
        Modifier
            .fillMaxSize()
            .background(HemColors.Paper)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            },
    ) {
        // Progress hairline
        val progress = ((stepIndex + 1).toFloat() / steps.size.toFloat()).coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(HemColors.Hairline),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(2.dp)
                    .background(HemColors.Bronze),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clickable(onClick = {
                            if (stepIndex == 0) atStart = editingPieceId == null else stepIndex -= 1
                        })
                        .padding(6.dp),
                ) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                Spacer(Modifier.width(4.dp))
                Eyebrow("STEP ${stepIndex + 1} OF ${steps.size}")
            }
            Spacer(Modifier.height(HemSpace.sm))
            SerifDisplay(if (editingPieceId != null) "Edit piece" else "Guided design")
            Spacer(Modifier.height(HemSpace.lg))

            val prevStep = remember { androidx.compose.runtime.mutableIntStateOf(currentStep) }
            val forward = currentStep >= prevStep.intValue
            androidx.compose.runtime.SideEffect { prevStep.intValue = currentStep }
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    val slide = if (forward) {
                        slideInHorizontally(tween(250)) { it / 4 } + fadeIn(tween(250))
                    } else {
                        slideInHorizontally(tween(250)) { -it / 4 } + fadeIn(tween(250))
                    }
                    val out = if (forward) {
                        slideOutHorizontally(tween(250)) { -it / 4 } + fadeOut(tween(200))
                    } else {
                        slideOutHorizontally(tween(250)) { it / 4 } + fadeOut(tween(200))
                    }
                    slide togetherWith out
                },
                label = "wizardStep",
            ) { step ->
            when (step) {
                1 -> Step1TypeAndReference(
                    state = state,
                    referenceUploading = referenceUploading,
                    onImportRef = {
                        pickReferenceLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onRemoveRef = { state.referenceUrl = null },
                )
                2 -> ChipSingleStep(
                    title = "Silhouette",
                    subtitle = "How does it sit on the body?",
                    options = SILHOUETTES,
                    selected = state.silhouette,
                    onSelect = { state.silhouette = it },
                )
                3 -> ChipSingleStep(
                    title = if (state.type == "Bottom" || state.type == "Dress") "Length" else "Sleeve",
                    subtitle = if (state.type == "Bottom" || state.type == "Dress")
                        "Where does the hem land?" else "How long are the sleeves?",
                    options = when (state.type) {
                        "Bottom" -> BOTTOM_LENGTH
                        "Dress" -> DRESS_LENGTH
                        else -> SLEEVE
                    },
                    selected = state.sleeveOrLength,
                    onSelect = { state.sleeveOrLength = it },
                )
                4 -> ChipMultiStep(
                    title = "Details",
                    subtitle = "Optional — up to 4.",
                    options = detailsFor(state.type),
                    selected = state.details,
                    max = 4,
                )
                5 -> ColorsStep(state = state)
                6 -> FabricStep(state = state)
                7 -> OccasionSeasonStep(state = state)
                8 -> RefinementsStep(
                    text = state.freeText,
                    onChange = { state.freeText = it },
                    onDone = { focusManager.clearFocus() },
                )
                9 -> PreviewStep(
                    state = state,
                    prompt = buildPrompt(),
                )
                // ----- Shoes category-specific -----
                10 -> ChipSingleStep(
                    title = "Sole",
                    subtitle = "What sits underneath?",
                    options = SHOE_SOLES,
                    selected = state.sole,
                    onSelect = { state.sole = it },
                )
                11 -> ChipSingleStep(
                    title = "Toe shape",
                    subtitle = "How does the front finish?",
                    options = SHOE_TOES,
                    selected = state.toe,
                    onSelect = { state.toe = it },
                )
                12 -> ChipSingleStep(
                    title = "Height",
                    subtitle = "How high does it rise?",
                    options = SHOE_HEIGHTS,
                    selected = state.shoeHeight,
                    onSelect = { state.shoeHeight = it },
                )
                13 -> ChipSingleStep(
                    title = "Closure",
                    subtitle = "How does it fasten?",
                    options = SHOE_CLOSURES,
                    selected = state.closureType,
                    onSelect = { state.closureType = it },
                )
                // ----- Bag (Accessory subtype) -----
                14 -> ChipSingleStep(
                    title = "Bag type",
                    subtitle = "What shape are you after?",
                    options = BAG_TYPES,
                    selected = state.bagType,
                    onSelect = { state.bagType = it },
                )
                15 -> ChipSingleStep(
                    title = "Size",
                    subtitle = "How much does it hold?",
                    options = BAG_SIZES,
                    selected = state.bagSize,
                    onSelect = { state.bagSize = it },
                )
                16 -> ChipSingleStep(
                    title = "Strap",
                    subtitle = "How is it carried?",
                    options = BAG_STRAPS,
                    selected = state.strap,
                    onSelect = { state.strap = it },
                )
                17 -> ChipSingleStep(
                    title = "Closure",
                    subtitle = "How does it fasten?",
                    options = BAG_CLOSURES,
                    selected = state.closureType,
                    onSelect = { state.closureType = it },
                )
                18 -> ChipSingleStep(
                    title = "Hardware",
                    subtitle = "Metal accents & finish.",
                    options = BAG_HARDWARE,
                    selected = state.hardware,
                    onSelect = { state.hardware = it },
                )
            }
            }

            if (error != null) {
                Spacer(Modifier.height(HemSpace.md))
                Text(error!!, style = HemType.bodyMuted.copy(color = Color(0xFFB0743A)))
            }
            Spacer(Modifier.height(HemSpace.xl))
        }

        // Persistent bottom bar
        WizardBottomBar(
            stepIndex = stepIndex,
            totalSteps = steps.size,
            isFinal = currentStep == 9,
            canAdvance = canAdvance(),
            hasReference = !state.referenceUrl.isNullOrBlank(),
            onBack = {
                if (stepIndex == 0) atStart = editingPieceId == null else stepIndex -= 1
            },
            onNext = { if (canAdvance()) stepIndex += 1 },
            onGenerate = { scope.launch { runGeneration() } },
        )
    }
}

private fun niceName(s: WizardState): String {
    val parts = listOfNotNull(
        s.silhouette,
        s.colors.firstOrNull(),
        s.subtype ?: s.type,
    )
    return parts.joinToString(" ").ifBlank { "Studio piece" }
}

// ---------- Bottom bar ----------

@Composable
private fun WizardBottomBar(
    stepIndex: Int,
    totalSteps: Int,
    isFinal: Boolean,
    canAdvance: Boolean,
    hasReference: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onGenerate: () -> Unit,
) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(HemColors.Hairline))
        Row(
            Modifier
                .fillMaxWidth()
                .background(HemColors.Paper)
                .padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, HemColors.Ink.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) { Text("Back", style = HemType.body.copy(fontWeight = FontWeight.Medium)) }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${stepIndex + 1}",
                    style = HemType.serifSection.copy(color = HemColors.Bronze, fontSize = 22.sp),
                )
                Text(
                    "/$totalSteps",
                    style = HemType.bodyMuted.copy(fontSize = 14.sp),
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (isFinal) {
                val label = if (hasReference) "Generate variation · $GEN_COST" else "Generate · $GEN_COST"
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(HemColors.Ink)
                        .clickable(onClick = onGenerate)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(label, style = HemType.body.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
                }
            } else {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (canAdvance) HemColors.Ink else HemColors.Muted)
                        .clickable(enabled = canAdvance, onClick = onNext)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text("Next", style = HemType.body.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

// ---------- Start-from screen ----------

@Composable
private fun StartFromScreen(
    onBlank: () -> Unit,
    onPickTemplate: (Template) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(HemColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clickable(onClick = onBack).padding(6.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
            Eyebrow("START FROM")
        }
        Spacer(Modifier.height(HemSpace.sm))
        SerifDisplay("A new piece")
        Spacer(Modifier.height(HemSpace.xs))
        Text("Start blank, or lift a template.", style = HemType.bodyMuted)
        Spacer(Modifier.height(HemSpace.lg))

        Row(horizontalArrangement = Arrangement.spacedBy(HemSpace.sm)) {
            BigStartCard(
                title = "Blank canvas",
                subtitle = "Design from scratch",
                onClick = onBlank,
                modifier = Modifier.weight(1f),
            )
            BigStartCard(
                title = "Templates",
                subtitle = "Pre-filled starters",
                onClick = { /* scroll down; nothing to do */ },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(HemSpace.xl))
        Eyebrow("TEMPLATES · ${TEMPLATES.size}")
        Spacer(Modifier.height(HemSpace.sm))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(HemSpace.sm),
        ) {
            TEMPLATES.forEach { t ->
                TemplateCard(t = t, onClick = { onPickTemplate(t) })
            }
        }
        Spacer(Modifier.height(HemSpace.xl))
    }
}

@Composable
private fun BigStartCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(HemSpace.md),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = HemType.serifSection.copy(fontSize = 20.sp))
        Text(subtitle, style = HemType.bodyMuted.copy(fontSize = 13.sp))
    }
}

@Composable
private fun TemplateCard(t: Template, onClick: () -> Unit) {
    val swatch = runCatching {
        val hex = COLOR_PRESETS.firstOrNull { it.name == t.colors.first() }?.hex?.removePrefix("#") ?: "8A6B4A"
        Color(("FF$hex").toLong(16))
    }.getOrDefault(Color(0xFF8A6B4A))
    Column(
        Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(HemSpace.sm),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(swatch.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                t.glyph,
                style = HemType.serifSection.copy(color = swatch, fontSize = 36.sp),
            )
        }
        Spacer(Modifier.height(HemSpace.xs))
        Text(t.name, style = HemType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp))
        Text(t.type, style = HemType.bodyMuted.copy(fontSize = 11.sp))
    }
}

// ---------- Step 1 — Type + subtype + reference ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step1TypeAndReference(
    state: WizardState,
    referenceUploading: Boolean,
    onImportRef: () -> Unit,
    onRemoveRef: () -> Unit,
) {
    Column {
        Text("What are we making?", style = HemType.serifSection)
        Spacer(Modifier.height(HemSpace.md))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TYPES.forEach { t ->
                Chip(
                    text = t,
                    active = state.type == t,
                    onClick = {
                        if (state.type != t) {
                            state.type = t
                            state.subtype = null
                        }
                    },
                )
            }
        }
        val subs = SUBTYPES[state.type]
        if (!subs.isNullOrEmpty()) {
            Spacer(Modifier.height(HemSpace.md))
            Eyebrow("SUBCATEGORY")
            Spacer(Modifier.height(HemSpace.xs))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                subs.forEach { s ->
                    Chip(text = s, active = state.subtype == s, onClick = { state.subtype = s })
                }
            }
        }
        Spacer(Modifier.height(HemSpace.lg))
        Eyebrow("REFERENCE · OPTIONAL")
        Spacer(Modifier.height(HemSpace.xs))
        if (!state.referenceUrl.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(HemColors.CardCream),
                ) {
                    AsyncImage(
                        model = state.referenceUrl,
                        contentDescription = "Reference",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(HemSpace.sm))
                Text("Reference set", style = HemType.body)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(CircleShape)
                        .border(1.dp, HemColors.Ink.copy(alpha = 0.4f), CircleShape)
                        .clickable(onClick = onRemoveRef)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) { Text("×", style = HemType.body) }
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Transparent)
                    .border(
                        1.dp,
                        HemColors.Ink.copy(alpha = 0.4f),
                        RoundedCornerShape(12.dp),
                    )
                    .clickable(enabled = !referenceUploading, onClick = onImportRef)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (referenceUploading) "Uploading…" else "＋ Add reference photo",
                    style = HemType.body.copy(fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

// ---------- Chip step primitives ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSingleStep(
    title: String,
    subtitle: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    Column {
        Text(title, style = HemType.serifSection)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = HemType.bodyMuted)
        Spacer(Modifier.height(HemSpace.md))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { opt ->
                Chip(text = opt, active = selected == opt, onClick = { onSelect(opt) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipMultiStep(
    title: String,
    subtitle: String,
    options: List<String>,
    selected: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    max: Int,
) {
    Column {
        Text(title, style = HemType.serifSection)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = HemType.bodyMuted)
        Spacer(Modifier.height(HemSpace.md))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { opt ->
                val active = selected.contains(opt)
                Chip(text = opt, active = active, onClick = {
                    if (active) {
                        selected.remove(opt)
                    } else if (selected.size < max) {
                        selected.add(opt)
                    }
                })
            }
        }
    }
}

// ---------- Colors step ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorsStep(state: WizardState) {
    Column {
        Text("Colors", style = HemType.serifSection)
        Spacer(Modifier.height(4.dp))
        Text("Pick up to three. Two often reads cleaner.", style = HemType.bodyMuted)
        Spacer(Modifier.height(HemSpace.md))
        // Palette strip
        if (state.colors.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.colors.forEach { name ->
                    val col = resolveColor(name)
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(col)
                            .border(1.dp, HemColors.Ink.copy(alpha = 0.3f), CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(HemSpace.sm))
            if (state.colors.size == 3) {
                Text(
                    "Two colors read cleaner than three — try monochrome first.",
                    style = HemType.bodyMuted.copy(fontSize = 12.sp, color = HemColors.Bronze),
                )
                Spacer(Modifier.height(HemSpace.xs))
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            COLOR_PRESETS.forEach { p ->
                val active = state.colors.contains(p.name)
                ColorChip(
                    label = p.name,
                    swatch = Color(("FF${p.hex.removePrefix("#")}").toLong(16)),
                    active = active,
                    onClick = {
                        if (active) state.colors.remove(p.name)
                        else if (state.colors.size < 3) state.colors.add(p.name)
                    },
                )
            }
            // Custom colors already added
            state.colors.filter { it.startsWith("#") }.forEach { hex ->
                val col = resolveColor(hex)
                ColorChip(
                    label = hex.uppercase(),
                    swatch = col,
                    active = true,
                    onClick = { state.colors.remove(hex) },
                )
            }
        }
        Spacer(Modifier.height(HemSpace.md))
        CustomHexRow(onAdd = { hex ->
            if (state.colors.size < 3) state.colors.add(hex)
        })
    }
}

@Composable
private fun CustomHexRow(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val cleaned = "#" + text.uppercase().filter { it.isLetterOrDigit() }.take(6)
    val valid = cleaned.length == 7 && cleaned.drop(1).all { it in "0123456789ABCDEF" }
    val swatch = if (valid) resolveColor(cleaned) else HemColors.Hairline
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(swatch)
                .border(1.dp, HemColors.Ink.copy(alpha = 0.3f), CircleShape),
        )
        Spacer(Modifier.width(HemSpace.sm))
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(999.dp))
                .background(HemColors.CardCream)
                .border(1.dp, HemColors.Hairline, RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = HemColors.Ink, fontSize = 14.sp),
                cursorBrush = SolidColor(HemColors.Ink),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Ascii,
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (text.isEmpty()) {
                Text("Custom hex (e.g. B0743A)", style = HemType.bodyMuted.copy(fontSize = 14.sp))
            }
        }
        Spacer(Modifier.width(HemSpace.sm))
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (valid) HemColors.Ink else HemColors.Muted)
                .clickable(enabled = valid, onClick = {
                    onAdd(cleaned)
                    text = ""
                })
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text("+ Add", style = HemType.body.copy(color = Color.White, fontWeight = FontWeight.Medium))
        }
    }
}

private fun resolveColor(nameOrHex: String): Color {
    if (nameOrHex.startsWith("#")) {
        return runCatching {
            val hex = nameOrHex.removePrefix("#")
            Color(("FF$hex").toLong(16))
        }.getOrDefault(HemColors.Muted)
    }
    val preset = COLOR_PRESETS.firstOrNull { it.name == nameOrHex }
    return preset?.let {
        val hex = it.hex.removePrefix("#")
        Color(("FF$hex").toLong(16))
    } ?: HemColors.Muted
}

@Composable
private fun ColorChip(
    label: String,
    swatch: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.75f),
        label = "colorChipScale",
    )
    Row(
        Modifier
            .scale(scale)
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) HemColors.Ink else Color.Transparent)
            .border(1.dp, HemColors.Ink.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(swatch)
                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = HemType.body.copy(
                color = if (active) Color.White else HemColors.Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

// ---------- Fabric ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FabricStep(state: WizardState) {
    Column {
        Text("Fabric", style = HemType.serifSection)
        Spacer(Modifier.height(4.dp))
        Text("Pick up to two.", style = HemType.bodyMuted)
        Spacer(Modifier.height(HemSpace.md))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            fabricsFor(state.type).forEach { f ->
                val active = state.fabrics.contains(f)
                Chip(text = f, active = active, onClick = {
                    if (active) state.fabrics.remove(f)
                    else if (state.fabrics.size < 2) state.fabrics.add(f)
                })
            }
        }
        Spacer(Modifier.height(HemSpace.lg))
        Eyebrow("TEXTURE · OPTIONAL")
        Spacer(Modifier.height(HemSpace.xs))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TEXTURES.forEach { t ->
                Chip(
                    text = t,
                    active = state.texture == t,
                    onClick = { state.texture = if (state.texture == t) null else t },
                )
            }
        }
    }
}

// ---------- Occasion + Season ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OccasionSeasonStep(state: WizardState) {
    Column {
        Text("Where + when", style = HemType.serifSection)
        Spacer(Modifier.height(4.dp))
        Text("This tunes weight and formality.", style = HemType.bodyMuted)
        Spacer(Modifier.height(HemSpace.md))
        Eyebrow("OCCASION")
        Spacer(Modifier.height(HemSpace.xs))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OCCASIONS.forEach { o ->
                Chip(text = o, active = state.occasion == o, onClick = { state.occasion = o })
            }
        }
        Spacer(Modifier.height(HemSpace.lg))
        Eyebrow("SEASON")
        Spacer(Modifier.height(HemSpace.xs))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SEASONS.forEach { s ->
                Chip(text = s, active = state.season == s, onClick = { state.season = s })
            }
        }
    }
}

// ---------- Refinements ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RefinementsStep(text: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    Column {
        Text("Anything else Hem should know?", style = HemType.serifSection)
        Spacer(Modifier.height(4.dp))
        Text("Optional. Tap a suggestion or write your own.", style = HemType.bodyMuted)
        Spacer(Modifier.height(HemSpace.md))
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HemColors.CardCream)
                .border(1.dp, HemColors.Hairline, RoundedCornerShape(12.dp))
                .padding(HemSpace.md),
        ) {
            BasicTextField(
                value = text,
                onValueChange = onChange,
                textStyle = TextStyle(color = HemColors.Ink, fontSize = 14.sp),
                cursorBrush = SolidColor(HemColors.Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxSize(),
            )
            if (text.isEmpty()) {
                Text("e.g. cropped, boxy shoulders, subtle sheen…", style = HemType.bodyMuted.copy(fontSize = 14.sp))
            }
        }
        Spacer(Modifier.height(HemSpace.md))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PROMPT_ADDONS.forEach { p ->
                Chip(text = "+ $p", active = false, onClick = {
                    val next = if (text.isBlank()) p else "$text $p."
                    onChange(next)
                })
            }
        }
    }
}

// ---------- Preview / final ----------

@Composable
private fun PreviewStep(state: WizardState, prompt: String) {
    Column {
        Text("Preview", style = HemType.serifSection)
        Spacer(Modifier.height(4.dp))
        Text("Read it back before we sketch.", style = HemType.bodyMuted)
        Spacer(Modifier.height(HemSpace.md))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(HemColors.CardCream)
                .border(1.dp, HemColors.Hairline, RoundedCornerShape(16.dp))
                .padding(HemSpace.md),
        ) {
            Text(naturalSummary(state), style = HemType.serifQuote.copy(fontSize = 16.sp))
        }
        Spacer(Modifier.height(HemSpace.md))
        if (state.colors.isNotEmpty()) {
            Eyebrow("PALETTE")
            Spacer(Modifier.height(HemSpace.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.colors.forEach { c ->
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(resolveColor(c))
                            .border(1.dp, HemColors.Ink.copy(alpha = 0.3f), CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(HemSpace.md))
        }
        if (!state.referenceUrl.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(HemColors.CardCream),
                ) {
                    AsyncImage(
                        model = state.referenceUrl,
                        contentDescription = "Reference",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(HemSpace.sm))
                Text("Will be treated as a reference.", style = HemType.bodyMuted.copy(fontSize = 13.sp))
            }
            Spacer(Modifier.height(HemSpace.md))
        }
        var showFullPrompt by remember { mutableStateOf(false) }
        Box(
            Modifier
                .clickable { showFullPrompt = !showFullPrompt }
                .padding(vertical = 4.dp),
        ) {
            Text(
                if (showFullPrompt) "▾ Hide full prompt" else "▸ Show full prompt",
                style = HemType.bodyMuted.copy(fontSize = 12.sp),
            )
        }
        if (showFullPrompt) {
            Spacer(Modifier.height(4.dp))
            Text(prompt, style = HemType.bodyMuted.copy(fontSize = 12.sp))
        }
    }
}

private fun naturalSummary(s: WizardState): String {
    val sil = s.silhouette?.lowercase()
    val color = s.colors.joinToString(" and ") { it.lowercase().removePrefix("#") }
    val fabric = s.fabrics.joinToString(" and ") { it.lowercase() }
    val piece = s.subtype?.lowercase() ?: s.type?.lowercase() ?: "piece"
    val len = s.sleeveOrLength?.lowercase()?.let { "with $it sleeves or hem" }.orEmpty()
    val details = if (s.details.isNotEmpty()) " and ${s.details.joinToString(", ") { it.lowercase() }}" else ""
    val head = listOfNotNull("An", sil, color.ifBlank { null }, fabric.ifBlank { null }, piece).joinToString(" ")
    return "$head $len$details, for ${s.occasion.lowercase()} ${s.season.lowercase()} wear.".replace("  ", " ")
}

// ---------- Chip ----------

@Composable
private fun Chip(text: String, active: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "chipScale",
    )
    Box(
        Modifier
            .scale(scale)
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) HemColors.Ink else Color.Transparent)
            .border(1.dp, HemColors.Ink.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            style = HemType.body.copy(
                color = if (active) Color.White else HemColors.Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

// ---------- Loading + Result ----------

@Composable
private fun GeneratingScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .background(HemColors.Paper)
            .padding(HemSpace.gutter),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SerifDisplay("Sketching…")
        Spacer(Modifier.height(HemSpace.md))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(HemColors.Hairline),
        )
        Spacer(Modifier.height(HemSpace.sm))
        Text("Hem is drawing your piece.", style = HemType.bodyMuted)
    }
}

data class VariationResult(val url: String, val savedOk: Boolean)

@Composable
private fun GeneratedResultScreen(
    heroUrl: String,
    variations: List<VariationResult>,
    generatingVariations: Boolean,
    addingBusy: Boolean,
    addedOk: Boolean,
    errorText: String?,
    onTryAgain: () -> Unit,
    onPickVariation: (String) -> Unit,
    onSaveVariation: (String) -> Unit,
    onGenerateVariations: () -> Unit,
    onAddToStudio: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(HemColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clickable(onClick = onBack)
                    .padding(6.dp),
            ) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.width(4.dp))
            Eyebrow("YOUR PIECE")
        }
        Spacer(Modifier.height(HemSpace.md))
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(HemColors.CardCream),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(heroUrl).build(),
                contentDescription = "Generated piece",
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                onError = { Log.e("Studio", "coil err url=$heroUrl", it.result.throwable) },
                onSuccess = { Log.i("Studio", "coil ok url=$heroUrl") },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (variations.isNotEmpty() || generatingVariations) {
            Spacer(Modifier.height(HemSpace.md))
            Eyebrow("VARIATIONS")
            Spacer(Modifier.height(HemSpace.xs))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(HemSpace.sm),
            ) {
                if (generatingVariations && variations.isEmpty()) {
                    repeat(3) { VariationShimmerTile() }
                } else {
                    variations.forEach { v ->
                        VariationTile(
                            url = v.url,
                            isHero = v.url == heroUrl,
                            savedOk = v.savedOk,
                            onTap = { onPickVariation(v.url) },
                            onSave = { onSaveVariation(v.url) },
                        )
                    }
                }
            }
        }

        if (errorText != null) {
            Spacer(Modifier.height(HemSpace.sm))
            Text(errorText, style = HemType.bodyMuted.copy(color = Color(0xFFB0743A)))
        }
        if (addedOk) {
            Spacer(Modifier.height(HemSpace.sm))
            Text("Saved to Studio.", style = HemType.bodyMuted.copy(color = HemColors.Bronze))
        }
        Spacer(Modifier.height(HemSpace.lg))
        PrimaryButton(
            label = if (addingBusy) "Saving…" else if (addedOk) "Saved" else "Save current",
            onClick = onAddToStudio,
            enabled = !addingBusy && !addedOk,
        )
        Spacer(Modifier.height(HemSpace.sm))
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, HemColors.Ink.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                .clickable(enabled = !generatingVariations, onClick = onGenerateVariations),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (generatingVariations) "Sketching 3 alternatives…" else "Generate alternatives · ${GEN_COST * 3}",
                style = HemType.body.copy(fontWeight = FontWeight.Medium),
            )
        }
        Spacer(Modifier.height(HemSpace.sm))
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, HemColors.Ink.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                .clickable(onClick = onTryAgain),
            contentAlignment = Alignment.Center,
        ) {
            Text("Try again", style = HemType.body.copy(fontWeight = FontWeight.Medium))
        }
        Spacer(Modifier.height(HemSpace.sm))
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clickable(onClick = onDone),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Done",
                style = HemType.bodyMuted.copy(
                    fontWeight = FontWeight.Medium,
                    color = HemColors.Muted,
                ),
            )
        }
        Spacer(Modifier.height(HemSpace.xl))
    }
}

@Composable
private fun VariationTile(
    url: String,
    isHero: Boolean,
    savedOk: Boolean,
    onTap: () -> Unit,
    onSave: () -> Unit,
) {
    Box(Modifier.size(88.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(HemColors.CardCream)
                .border(
                    width = if (isHero) 2.dp else 1.dp,
                    color = if (isHero) HemColors.Bronze else HemColors.Hairline,
                    shape = RoundedCornerShape(10.dp),
                )
                .clickable(onClick = onTap),
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (savedOk) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(HemColors.Bronze),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "✓",
                        style = HemType.body.copy(
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(HemColors.CardCream)
                .border(1.dp, HemColors.Hairline, CircleShape)
                .clickable(enabled = !savedOk, onClick = onSave),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (savedOk) "✓" else "+",
                style = HemType.body.copy(
                    color = HemColors.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@Composable
private fun VariationShimmerTile() {
    Box(
        Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(HemColors.CardCream)
            .shimmer(),
    )
}

