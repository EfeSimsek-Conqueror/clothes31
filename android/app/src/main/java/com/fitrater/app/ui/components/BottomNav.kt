package com.fitrater.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fitrater.app.nav.Route
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemType

private data class NavItem(val label: String, val route: String)

@Composable
fun HemBottomNav(
    currentRoute: String,
    onSelect: (String) -> Unit,
    onCameraClick: () -> Unit,
) {
    val left = listOf(NavItem("Today", Route.Home), NavItem("Studio", Route.Studio))
    val right = listOf(NavItem("Journal", Route.Journal), NavItem("You", Route.You))
    Column(
        Modifier
            .fillMaxWidth()
            .background(HemColors.Paper),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(HemColors.Hairline),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            left.forEach { NavCell(Modifier.weight(1f), it, currentRoute == it.route) { onSelect(it.route) } }
            CameraFab(onCameraClick)
            right.forEach { NavCell(Modifier.weight(1f), it, currentRoute == it.route) { onSelect(it.route) } }
        }
    }
}

@Composable
private fun NavCell(modifier: Modifier, item: NavItem, active: Boolean, onClick: () -> Unit) {
    // Weighted rather than intrinsically sized: at the Large text setting the four
    // labels outgrow the row and the last one ("YOU") used to clip off the edge.
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = item.label.uppercase(),
            maxLines = 1,
            style = HemType.smallLabel.copy(
                color = if (active) HemColors.Ink else HemColors.Muted,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .width(20.dp)
                .height(2.dp)
                .background(if (active) HemColors.Bronze else Color.Transparent),
        )
    }
}

@Composable
private fun CameraFab(onClick: () -> Unit) {
    Box(
        Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(HemColors.Ink)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.PhotoCamera,
            contentDescription = "Score a look",
            tint = HemColors.OnInk,
            modifier = Modifier.size(26.dp),
        )
    }
}
