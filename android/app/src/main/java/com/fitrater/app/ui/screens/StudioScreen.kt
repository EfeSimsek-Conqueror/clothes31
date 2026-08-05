package com.fitrater.app.ui.screens

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitrater.app.data.model.ClosetItem
import com.fitrater.app.data.model.ClosetItemInsert
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.ui.components.StudioEmptyIllustration
import com.fitrater.app.ui.components.shimmer
import com.fitrater.app.ui.theme.EyebrowRow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.SerifDisplay
import com.fitrater.app.data.Supa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StudioScreen(
    onCreateNew: () -> Unit,
    onOpenEdit: (item: ClosetItem, referenceUrl: String?) -> Unit = { _, _ -> },
    onCreateCoverTemplate: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credits by com.fitrater.app.util.CreditsBus.balance.collectAsState()
    var items by remember { mutableStateOf<List<ClosetItem>>(emptyList()) }
    var imageUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selected by remember { mutableStateOf<ClosetItem?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val list = runCatching { Repo.closetItems() }.getOrDefault(emptyList())
        items = list
        val urls = mutableMapOf<String, String>()
        list.forEach { it ->
            val id = it.id ?: return@forEach
            val direct = it.image_url
            if (!direct.isNullOrBlank()) { urls[id] = direct; return@forEach }
            val path = it.image_path ?: return@forEach
            val signed = runCatching { Repo.signedClosetUrl(path) }.getOrNull()
            if (!signed.isNullOrBlank()) urls[id] = signed
        }
        imageUrls = urls
    }

    LaunchedEffect(Unit) {
        com.fitrater.app.util.CreditsBus.refresh()
        refresh()
        loaded = true
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        error = null
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.buffered()?.use { it.readBytes() }
                        ?: error("Could not read image")
                }
                val path = Repo.uploadClosetPhoto(bytes)
                val uid = Repo.userId ?: error("Not signed in")
                Repo.insertClosetItem(
                    ClosetItemInsert(
                        user_id = uid,
                        name = "New piece",
                        category = "top",
                        image_path = path,
                    ),
                )
                refresh()
            }.onSuccess {
                Log.i("Studio", "closet import success")
            }.onFailure {
                Log.e("Studio", "closet import failed", it)
                error = it.message ?: "Import failed"
                com.fitrater.app.util.ToastBus.post("Upload failed: ${it.message ?: "error"}")
            }
            busy = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(HemColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SerifDisplay("Studio", modifier = Modifier.weight(1f))
            if (credits != null) CreditsPill(credits!!)
        }
        Spacer(Modifier.height(HemSpace.xs))
        Text("Design a piece, then wear it.", style = HemType.bodyMuted)
        Spacer(Modifier.height(HemSpace.lg))
        if (!loaded) {
            // Skeleton row of piece tiles while closetItems loads.
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(HemSpace.sm),
            ) {
                repeat(4) {
                    Box(
                        Modifier
                            .width(160.dp)
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(HemColors.CardCream)
                            .shimmer(),
                    )
                }
            }
        } else if (items.isEmpty()) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                StudioEmptyIllustration()
            }
            Spacer(Modifier.height(HemSpace.md))
            Text("Your studio is empty.", style = HemType.serifSection)
            Spacer(Modifier.height(HemSpace.xs))
            Text(
                "Import a piece you already own, or design one from a template.",
                style = HemType.bodyMuted,
            )
            Spacer(Modifier.height(HemSpace.lg))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HemSpace.sm)) {
                StudioEmptyCard(
                    glyph = "↑",
                    title = "Import a piece",
                    subtitle = "From your camera roll",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!busy) {
                            pickLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }
                    },
                )
                StudioEmptyCard(
                    glyph = "✦",
                    title = "Create new",
                    subtitle = "Design with a template",
                    modifier = Modifier.weight(1f),
                    onClick = onCreateNew,
                )
            }
        } else {
            val ordering = listOf("top", "bottom", "outerwear", "dress", "shoes", "accessory")
            val grouped = items.groupBy { (it.category ?: "other").lowercase() }
            val orderedKeys = ordering.filter { grouped.containsKey(it) } +
                grouped.keys.filter { it !in ordering }
            orderedKeys.forEach { cat ->
                val list = grouped[cat].orEmpty()
                if (list.isEmpty()) return@forEach
                EyebrowRow("${prettyCategoryPlural(cat)} · ${list.size}")
                Spacer(Modifier.height(HemSpace.sm))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(HemSpace.sm),
                ) {
                    list.forEach { it ->
                        val url = it.id?.let { id -> imageUrls[id] }
                        PieceCard(it, imageUrl = url, onClick = { selected = it })
                    }
                }
                Spacer(Modifier.height(HemSpace.md))
            }
        }
        Spacer(Modifier.height(HemSpace.lg))
        if (error != null) {
            Text(error!!, style = HemType.bodyMuted.copy(color = HemColors.Bronze))
            Spacer(Modifier.height(HemSpace.sm))
        }
        // The empty state already offers Import and Create as its own two cards — showing
        // these as well gave four cards for two actions.
        if (items.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HemSpace.sm)) {
                StudioActionCard(
                    title = "+ Create New",
                    subtitle = "Guided AI design",
                    modifier = Modifier.weight(1f),
                    onClick = onCreateNew,
                )
                StudioActionCard(
                    title = if (busy) "Uploading…" else "↑ Import",
                    subtitle = "Photo or icon",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!busy) {
                            pickLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }
                    },
                )
            }
            Spacer(Modifier.height(HemSpace.sm))
            StudioActionCard(
                title = "✦ Design a cover",
                subtitle = "Save a magazine template · ${Supa.COVER_TEMPLATE_COST} credits",
                modifier = Modifier.fillMaxWidth(),
                onClick = onCreateCoverTemplate,
            )
        }
        Spacer(Modifier.height(HemSpace.xl))
    }

    val sel = selected
    if (sel != null) {
        PieceDetailSheet(
            item = sel,
            imageUrl = sel.id?.let { imageUrls[it] },
            onDismiss = { selected = null },
            onEdit = {
                val url = sel.id?.let { imageUrls[it] }
                selected = null
                onOpenEdit(sel, url)
            },
        )
    }
}

private fun prettyCategoryPlural(cat: String): String = when (cat) {
    "top" -> "TOPS"
    "bottom" -> "BOTTOMS"
    "outerwear" -> "OUTERWEAR"
    "dress" -> "DRESSES"
    "shoes" -> "SHOES"
    "accessory" -> "ACCESSORIES"
    else -> cat.uppercase()
}

@Composable
private fun CreditsPill(credits: Int) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Bronze.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✦", style = HemType.body.copy(color = HemColors.Bronze))
        Spacer(Modifier.width(6.dp))
        Text("$credits", style = HemType.body.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun PieceCard(item: ClosetItem, imageUrl: String? = null, onClick: () -> Unit = {}) {
    val tint = runCatching {
        val hex = (item.color_hex ?: item.color)?.removePrefix("#") ?: "8A6B4A"
        Color(("FF$hex").toLong(16))
    }.getOrDefault(Color(0xFF8A6B4A))
    Column(
        Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = 0.15f))
            .clickable { onClick() }
            .padding(HemSpace.sm),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(tint.copy(alpha = 0.5f), tint),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val img = imageUrl ?: item.image_url
            if (!img.isNullOrBlank()) {
                AsyncImage(
                    model = img,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("👕", style = HemType.serifDisplay.copy(color = HemColors.OnScrim, fontSize = 48.sp))
            }
        }
        Spacer(Modifier.height(HemSpace.xs))
        Text(
            item.name ?: item.category ?: "Piece",
            style = HemType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        )
        val subtitle = item.subcategory ?: item.category ?: ""
        if (subtitle.isNotBlank()) {
            Text(subtitle, style = HemType.bodyMuted.copy(fontSize = 12.sp))
        }
    }
}

@Composable
private fun StudioEmptyCard(
    glyph: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .height(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(HemSpace.md),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(glyph, style = HemType.serifDisplay.copy(color = HemColors.Bronze, fontSize = 30.sp))
        Column {
            Text(title, style = HemType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp))
            Text(subtitle, style = HemType.bodyMuted.copy(fontSize = 12.sp))
        }
    }
}

@Composable
private fun StudioActionCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Column(
        modifier
            .height(120.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(HemSpace.md),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = HemType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp))
        Text(subtitle, style = HemType.bodyMuted.copy(fontSize = 13.sp))
    }
}
