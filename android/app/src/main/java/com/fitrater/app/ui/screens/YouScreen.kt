package com.fitrater.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitrater.app.data.Supa
import com.fitrater.app.data.model.Profile
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.Hairline
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.SerifDisplay
import com.fitrater.app.util.CreditsBus
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun YouScreen() {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<Profile?>(null) }
    var honesty by remember { mutableStateOf("honest") }
    var morning by remember { mutableStateOf(true) }
    var weekly by remember { mutableStateOf(true) }
    var wrapped by remember { mutableStateOf(true) }
    val creditsBox by CreditsBus.balance.collectAsState()
    val credits = creditsBox ?: 0
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        CreditsBus.refresh()
        val p = runCatching { Repo.currentProfile() }.getOrNull()
        profile = p
        p?.honesty?.let { honesty = it }
        val push = runCatching { Repo.pushSettings() }.getOrNull()
        push?.morning_stylist?.let { morning = it }
        push?.weekly_task?.let { weekly = it }
        push?.wrapped?.let { wrapped = it }
        loaded = true
    }

    val email = Repo.userEmail
    val display = profile?.display_name ?: email?.substringBefore("@") ?: "You"
    val initial = display.firstOrNull()?.uppercaseChar()?.toString() ?: "•"

    Column(
        Modifier
            .fillMaxSize()
            .background(HemColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(HemColors.CardCream)
                    .border(1.dp, HemColors.Hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val avatar = profile?.avatar_url
                if (!avatar.isNullOrBlank()) {
                    AsyncImage(model = avatar, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape))
                } else {
                    Text(initial, style = HemType.serifSection)
                }
            }
            Spacer(Modifier.padding(horizontal = HemSpace.sm))
            Column {
                SerifDisplay(display)
                if (email != null) {
                    Text(email, style = HemType.bodyMuted)
                }
            }
        }
        Spacer(Modifier.height(HemSpace.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(HemColors.CardCream)
                    .border(1.dp, HemColors.Bronze.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("✦ $credits credits", style = HemType.body.copy(fontWeight = FontWeight.SemiBold))
            }
        }

        Spacer(Modifier.height(HemSpace.lg))
        Eyebrow("HONESTY")
        Spacer(Modifier.height(HemSpace.sm))
        Row(Modifier.fillMaxWidth()) {
            listOf("kind", "honest", "brutal").forEach { option ->
                val selected = honesty == option
                Box(
                    Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) HemColors.Ink else Color.Transparent)
                        .border(1.dp, HemColors.Ink.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                        .clickable {
                            honesty = option
                            scope.launch { runCatching { Repo.updateHonesty(option) } }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option.replaceFirstChar { it.uppercase() },
                        style = HemType.body.copy(
                            color = if (selected) Color.White else HemColors.Ink,
                            fontSize = 14.sp,
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(HemSpace.lg))
        Eyebrow("NOTIFICATIONS")
        Spacer(Modifier.height(HemSpace.sm))
        Hairline()
        SettingRow(title = "Morning stylist", subtitle = "8:00 AM · your timezone") {
            Switch(
                checked = morning,
                onCheckedChange = {
                    morning = it
                    scope.launch { runCatching { Repo.upsertPushSettings(morning, weekly, wrapped) } }
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = HemColors.Ink,
                    checkedThumbColor = Color.White,
                ),
            )
        }
        Hairline()
        SettingRow(title = "Weekly task", subtitle = "Sundays — a small challenge") {
            Switch(
                checked = weekly,
                onCheckedChange = {
                    weekly = it
                    scope.launch { runCatching { Repo.upsertPushSettings(morning, weekly, wrapped) } }
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = HemColors.Ink,
                    checkedThumbColor = Color.White,
                ),
            )
        }
        Hairline()
        SettingRow(title = "Wrapped", subtitle = "End of month recap") {
            Switch(
                checked = wrapped,
                onCheckedChange = {
                    wrapped = it
                    scope.launch { runCatching { Repo.upsertPushSettings(morning, weekly, wrapped) } }
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = HemColors.Ink,
                    checkedThumbColor = Color.White,
                ),
            )
        }
        Hairline()

        Spacer(Modifier.height(HemSpace.lg))
        val tags = profile?.style_tags.orEmpty()
        if (tags.isNotEmpty()) {
            Eyebrow("YOUR VIBE")
            Spacer(Modifier.height(HemSpace.sm))
            Text(tags.joinToString(" · "), style = HemType.body)
            Spacer(Modifier.height(HemSpace.lg))
        }

        Text(
            "Sign out",
            style = HemType.body.copy(color = HemColors.Bronze),
            modifier = Modifier
                .clickable {
                    scope.launch { runCatching { Supa.client.auth.signOut() } }
                }
                .padding(vertical = HemSpace.md),
        )
        Text(
            "v0.1.0 · made with love",
            style = HemType.bodyMuted.copy(fontSize = 12.sp),
            modifier = Modifier.fillMaxWidth().padding(vertical = HemSpace.md),
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = HemSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = HemType.body.copy(fontWeight = FontWeight.SemiBold))
            Text(subtitle, style = HemType.bodyMuted.copy(fontSize = 13.sp))
        }
        trailing?.invoke()
    }
}
