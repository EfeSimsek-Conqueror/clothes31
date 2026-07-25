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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitrater.app.data.model.ClosetItem
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.SerifDisplay

/**
 * Piece detail bottom sheet — shows a Studio piece with iteration history and edit CTA.
 * Extracted from StudioScreen so it can be reused from Home's "Latest" card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PieceDetailSheet(
    item: ClosetItem,
    imageUrl: String?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
) {
    var chain by remember(item.id) { mutableStateOf<List<ClosetItem>>(emptyList()) }
    var chainUrls by remember(item.id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var current by remember(item.id) { mutableStateOf(item) }
    var wornCount by remember(item.id) { mutableStateOf(0) }

    LaunchedEffect(current.id) {
        val id = current.id ?: return@LaunchedEffect
        wornCount = runCatching { Repo.tryOnCountForPiece(id) }.getOrDefault(0)
    }

    LaunchedEffect(current.id) {
        val list = mutableListOf<ClosetItem>()
        var cursor: ClosetItem? = current
        val visited = mutableSetOf<String>()
        while (cursor?.parent_id != null && cursor.parent_id !in visited) {
            visited.add(cursor.parent_id!!)
            val p = runCatching { Repo.closetItemById(cursor!!.parent_id!!) }.getOrNull() ?: break
            list.add(0, p)
            cursor = p
        }
        val kids = current.id?.let { runCatching { Repo.closetChildren(it) }.getOrDefault(emptyList()) }.orEmpty()
        list.addAll(kids)
        chain = list
        val urls = mutableMapOf<String, String>()
        list.forEach { c ->
            val id = c.id ?: return@forEach
            val direct = c.image_url
            if (!direct.isNullOrBlank()) { urls[id] = direct; return@forEach }
            val path = c.image_path ?: return@forEach
            runCatching { Repo.signedClosetUrl(path) }.getOrNull()?.let { urls[id] = it }
        }
        chainUrls = urls
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HemColors.Paper,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
        ) {
            val isImported = current.image_path?.startsWith("references/") == true
            val effectiveUrl = current.id?.let { chainUrls[it] } ?: imageUrl
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isImported) HemColors.CardCream else HemColors.Ink.copy(alpha = 0.08f))
                        .border(1.dp, HemColors.Hairline, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        if (isImported) "IMPORTED" else "GENERATED",
                        style = HemType.bodyMuted.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(HemSpace.sm))
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(HemColors.CardCream),
                contentAlignment = Alignment.Center,
            ) {
                if (!effectiveUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = effectiveUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("👕", style = HemType.serifDisplay.copy(fontSize = 96.sp, color = HemColors.Muted))
                }
            }
            Spacer(Modifier.height(HemSpace.md))
            SerifDisplay(current.name ?: "Piece")
            Spacer(Modifier.height(HemSpace.xs))
            val meta = listOfNotNull(current.subcategory ?: current.category, current.color_hex ?: current.color).joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, style = HemType.bodyMuted)

            if (wornCount > 0) {
                Spacer(Modifier.height(HemSpace.xs))
                Text(
                    "WORN $wornCount TIME${if (wornCount == 1) "" else "S"}",
                    style = HemType.eyebrow.copy(color = HemColors.Bronze, letterSpacing = 2.sp, fontSize = 10.sp),
                )
            }

            if (chain.isNotEmpty()) {
                Spacer(Modifier.height(HemSpace.lg))
                Eyebrow("HISTORY · ${chain.size + 1}")
                Spacer(Modifier.height(HemSpace.sm))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(HemSpace.xs),
                ) {
                    (chain + current).forEach { c ->
                        val isCurrent = c.id == current.id
                        val u = c.id?.let { chainUrls[it] } ?: c.image_url
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(HemColors.CardCream)
                                .border(
                                    if (isCurrent) 2.dp else 1.dp,
                                    if (isCurrent) HemColors.Bronze else HemColors.Hairline,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable {
                                    if (!isCurrent) {
                                        current = c
                                    }
                                },
                        ) {
                            if (!u.isNullOrBlank()) {
                                AsyncImage(
                                    model = u,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(HemSpace.lg))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(HemColors.Ink)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "✎ Edit piece",
                    style = HemType.body.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
            Spacer(Modifier.height(HemSpace.xl))
        }
    }
}
