package com.fitrater.app.ui.screens.subpages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType

private val DEPS = listOf(
    "Jetpack Compose" to "Apache License 2.0 · Google",
    "CameraX" to "Apache License 2.0 · Google",
    "Coil" to "Apache License 2.0 · Coil Contributors",
    "supabase-kt" to "MIT · jan-tennert",
    "kotlinx-serialization" to "Apache License 2.0 · JetBrains",
    "kotlinx-coroutines" to "Apache License 2.0 · JetBrains",
    "Google Play Billing" to "Google Play Terms · Google",
    "RevenueCat" to "MIT · RevenueCat Inc.",
)

@Composable
fun LicensesScreen(onClose: () -> Unit) {
    SubpageScaffold(eyebrow = "LEGAL", title = "Open source licenses", onClose = onClose) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Fitrater ships with the following open-source libraries — thank you to their authors.",
                style = HemType.bodyMuted,
            )
            Spacer(Modifier.height(HemSpace.md))
            DEPS.forEach { (name, license) ->
                Column(Modifier.fillMaxWidth().padding(vertical = HemSpace.sm)) {
                    Text(name, style = HemType.body.copy(fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(2.dp))
                    Text(license, style = HemType.bodyMuted.copy(fontSize = 13.sp))
                }
                SubHairline()
            }
            Spacer(Modifier.height(HemSpace.xxl))
        }
    }
}
