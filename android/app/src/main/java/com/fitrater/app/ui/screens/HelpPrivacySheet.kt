package com.fitrater.app.ui.screens

import android.content.Intent
import android.util.Log
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import android.content.Context
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.SerifDisplay
import com.fitrater.app.util.ToastBus
import com.fitrater.app.util.openExternal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Red = Color(0xFFB23A2A)

@Composable
fun HelpPrivacySheetContent(
    onClose: () -> Unit,
    onSignedOut: () -> Unit,
    onOpenFaq: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var deleteStep by remember { mutableStateOf(0) } // 0=idle, 1=confirm, 2=deleting
    var typedConfirm by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("HELP & PRIVACY")
                Spacer(Modifier.height(HemSpace.xs))
                SerifDisplay("Your data, on demand.")
            }
            Text(
                "×",
                style = HemType.serifSection.copy(fontSize = 28.sp),
                modifier = Modifier.clickable(onClick = onClose).padding(8.dp),
            )
        }

        Spacer(Modifier.height(HemSpace.lg))
        HairlineRow()
        ActionRow(
            title = "FAQ",
            subtitle = "Answers to the most common questions.",
            onClick = { onOpenFaq() },
        )
        HairlineRow()
        ActionRow(
            title = "Contact support",
            subtitle = "efe@cloudgeng.com — usually within a day.",
            onClick = {
                runCatching {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:efe@cloudgeng.com?subject=" +
                            android.net.Uri.encode("Fitrater support"))
                    }
                    context.startActivity(intent)
                }.onFailure { ToastBus.post("No mail app installed.") }
            },
        )
        HairlineRow()
        ActionRow(
            title = "Rate on Play Store",
            subtitle = "One tap. It helps a lot.",
            onClick = {
                val uri = android.net.Uri.parse("market://details?id=com.fitrater.app")
                val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }.onFailure {
                    // No Play Store app — fall back to the web listing in a Custom Tab.
                    openExternal(context, "https://play.google.com/store/apps/details?id=com.fitrater.app")
                }
            },
        )
        HairlineRow()

        Spacer(Modifier.height(HemSpace.lg))
        Eyebrow("LEGAL")
        Spacer(Modifier.height(HemSpace.xs))
        HairlineRow()
        ActionRow(
            title = "Privacy policy",
            subtitle = "fitrater.ai/privacy",
            onClick = { openUrl(context, "https://fitrater.ai/privacy") },
        )
        HairlineRow()
        ActionRow(
            title = "Terms of service",
            subtitle = "fitrater.ai/terms",
            onClick = { openUrl(context, "https://fitrater.ai/terms") },
        )
        HairlineRow()
        ActionRow(
            title = "Open-source licenses",
            subtitle = "The libraries that power Fitrater.",
            onClick = { onOpenLicenses() },
        )
        HairlineRow()

        Spacer(Modifier.height(HemSpace.lg))
        Eyebrow("YOUR DATA")
        Spacer(Modifier.height(HemSpace.xs))
        HairlineRow()
        ActionRow(
            title = "Export my data",
            subtitle = if (exporting) "Preparing your archive…" else "Everything Hem has, as JSON.",
            enabled = !exporting,
            onClick = {
                if (exporting) return@ActionRow
                exporting = true
                scope.launch {
                    runCatching {
                        val payload = withContext(Dispatchers.IO) { Repo.exportUserData() }
                        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                        val stamp = java.text.SimpleDateFormat(
                            "yyyyMMdd-HHmmss", java.util.Locale.US,
                        ).format(java.util.Date())
                        val file = File(dir, "fitrater-data-$stamp.json")
                        withContext(Dispatchers.IO) { file.writeText(payload) }
                        val uri = FileProvider.getUriForFile(
                            context, "com.fitrater.app.fileprovider", file,
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(intent, "Export Fitrater data"),
                        )
                    }.onFailure {
                        Log.e("HelpPrivacy", "export failed", it)
                        ToastBus.post("Export failed: ${it.message ?: "error"}")
                    }
                    exporting = false
                }
            },
        )
        HairlineRow()
        ActionRow(
            title = "Delete my account",
            subtitle = "This can't be undone.",
            danger = true,
            onClick = { deleteStep = 1 },
        )
        HairlineRow()

        Spacer(Modifier.height(HemSpace.xl))
        Text(
            "Questions? efe@cloudgeng.com",
            style = HemType.bodyMuted.copy(fontSize = 13.sp),
        )
        Spacer(Modifier.height(HemSpace.xl))
    }

    if (deleteStep >= 1) {
        DeleteConfirmDialog(
            typed = typedConfirm,
            onTypedChange = { typedConfirm = it },
            busy = deleteStep == 2,
            onDismiss = {
                if (deleteStep != 2) {
                    deleteStep = 0
                    typedConfirm = ""
                }
            },
            onConfirm = {
                if (typedConfirm.trim().equals("DELETE", ignoreCase = false) && deleteStep == 1) {
                    deleteStep = 2
                    scope.launch {
                        runCatching { Repo.deleteAllUserData() }
                            .onFailure { Log.e("HelpPrivacy", "delete failed", it) }
                        ToastBus.post("Account deleted")
                        deleteStep = 0
                        typedConfirm = ""
                        onSignedOut()
                    }
                }
            },
        )
    }
}

/** Custom Tab, not ACTION_VIEW — every fitrater.ai URL is an autoVerify'd App Link for us. */
private fun openUrl(context: Context, url: String) {
    openExternal(context, url)
}

@Composable
private fun HairlineRow() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(HemColors.Hairline))
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = HemSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = HemType.body.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (danger) Red else HemColors.Ink,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = HemType.bodyMuted.copy(fontSize = 13.sp))
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = HemColors.Muted)
    }
}

@Composable
private fun DeleteConfirmDialog(
    typed: String,
    onTypedChange: (String) -> Unit,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HemColors.Paper,
        title = { Text("Delete account?", style = HemType.serifSection) },
        text = {
            Column {
                Text(
                    "This wipes every look, piece, and note. Type DELETE below to confirm.",
                    style = HemType.body,
                )
                Spacer(Modifier.height(HemSpace.sm))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(HemColors.CardCream)
                        .border(1.dp, HemColors.Hairline, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = typed,
                        onValueChange = onTypedChange,
                        textStyle = TextStyle(color = HemColors.Ink, fontSize = 15.sp),
                        cursorBrush = SolidColor(Red),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (typed.isEmpty()) {
                        Text("DELETE", style = HemType.bodyMuted.copy(fontSize = 15.sp))
                    }
                }
            }
        },
        confirmButton = {
            val enabled = !busy && typed.trim() == "DELETE"
            Text(
                if (busy) "DELETING…" else "DELETE",
                style = HemType.label.copy(color = if (enabled) Red else HemColors.Muted),
                modifier = Modifier
                    .clickable(enabled = enabled, onClick = onConfirm)
                    .padding(HemSpace.sm),
            )
        },
        dismissButton = {
            Text(
                "CANCEL",
                style = HemType.label.copy(color = HemColors.Ink),
                modifier = Modifier
                    .clickable(enabled = !busy, onClick = onDismiss)
                    .padding(HemSpace.sm),
            )
        },
    )
}
