package com.fitrater.app.ui.screens.subpages

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitrater.app.data.Supa
import com.fitrater.app.data.billing.RcBilling
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.PrimaryButton
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.EntitlementInfo
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun ManageProScreen(onClose: () -> Unit, onOpenPlans: () -> Unit) {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<CustomerInfo?>(null) }
    var spentThisMonth by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        info = runCatching { RcBilling.refreshCustomerInfo() }.getOrNull()
        spentThisMonth = runCatching { Repo.creditsSpentThisMonth() }.getOrDefault(0)
        loaded = true
    }

    val ent: EntitlementInfo? = info?.entitlements?.get(RcBilling.ENTITLEMENT_PRO)
    val isPro = ent?.isActive == true

    SubpageScaffold(
        eyebrow = if (isPro) "MEMBERSHIP" else "FITRATER PRO",
        title = if (isPro) "You're Pro" else "Fitrater Pro",
        onClose = onClose,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            if (!loaded) {
                Text("Loading membership…", style = HemType.bodyMuted)
            } else if (!isPro) {
                NotProContent(onOpenPlans = onOpenPlans)
            } else {
                ProContent(
                    ent = ent!!,
                    spentThisMonth = spentThisMonth,
                    onManageSubscription = {
                        val uri = Uri.parse(
                            "https://play.google.com/store/account/subscriptions" +
                                "?sku=${ent.productIdentifier}&package=com.fitrater.app",
                        )
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                )
            }
            Spacer(Modifier.height(HemSpace.xxl))
        }
    }
}

@Composable
private fun NotProContent(onOpenPlans: () -> Unit) {
    Text(
        "Unlimited scoring, deeper Studio, Hem's full memory.",
        style = HemType.body,
    )
    Spacer(Modifier.height(HemSpace.lg))
    Eyebrow("WHAT YOU GET")
    Spacer(Modifier.height(HemSpace.sm))
    val benefits = listOf(
        "i.  Unlimited scoring",
        "ii.  Studio without cap",
        "iii.  Try-on priority",
        "iv.  Unlimited try-on",
        "v.  Monthly Sunday letters",
        "vi.  Full Hem memory",
    )
    Column {
        benefits.forEach { line ->
            Text(
                line,
                style = HemType.serifQuote.copy(fontSize = 17.sp, fontStyle = FontStyle.Italic),
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
    Spacer(Modifier.height(HemSpace.xl))
    PrimaryButton(label = "See plans", onClick = onOpenPlans)
}

@Composable
private fun ProContent(
    ent: EntitlementInfo,
    spentThisMonth: Int,
    onManageSubscription: () -> Unit,
) {
    // Trial banner
    if (ent.periodType?.name == "INTRO") {
        val end = ent.expirationDate?.let { formatDate(it) } ?: "soon"
        val days = ent.expirationDate?.let { daysUntil(it) } ?: 0
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(HemColors.Bronze.copy(alpha = 0.15f))
                .border(1.dp, HemColors.Bronze, RoundedCornerShape(12.dp))
                .padding(HemSpace.md),
        ) {
            Column {
                Text(
                    "TRIAL ENDS ${end.uppercase()}",
                    style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 2.sp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$days days remaining · ${Supa.TRIAL_DAILY_CAP} credits/day",
                    style = HemType.body,
                )
            }
        }
        Spacer(Modifier.height(HemSpace.lg))
    }

    Eyebrow("PLAN")
    Spacer(Modifier.height(HemSpace.sm))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(14.dp))
            .padding(HemSpace.md),
    ) {
        PlanRowKV("Product", ent.productIdentifier)
        SubHairline()
        PlanRowKV(
            "Renews",
            if (ent.willRenew) formatDate(ent.expirationDate) else "Cancels ${formatDate(ent.expirationDate)}",
        )
        SubHairline()
        PlanRowKV("Auto-renew", if (ent.willRenew) "Yes" else "No")
    }

    Spacer(Modifier.height(HemSpace.lg))
    Eyebrow("USAGE THIS MONTH")
    Spacer(Modifier.height(HemSpace.sm))
    val cap = if (ent.productIdentifier.contains("annual", ignoreCase = true)) {
        Supa.SUB_ANNUAL_MONTHLY_CAP
    } else Supa.SUB_MONTHLY_CAP
    val pct = (spentThisMonth.toFloat() / cap.toFloat()).coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$spentThisMonth / $cap credits",
            style = HemType.body.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        Text(
            "${(pct * 100).toInt()}%",
            style = HemType.bodyMuted.copy(fontSize = 13.sp),
        )
    }
    Spacer(Modifier.height(HemSpace.xs))
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(HemColors.Hairline),
    ) {
        Box(
            Modifier
                .fillMaxWidth(pct)
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(HemColors.Bronze),
        )
    }

    Spacer(Modifier.height(HemSpace.xl))
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, HemColors.Ink.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
            .clickable(onClick = onManageSubscription),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            "Manage subscription",
            style = HemType.body.copy(fontWeight = FontWeight.Medium, color = HemColors.Ink),
        )
    }
}

@Composable
private fun PlanRowKV(k: String, v: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = HemSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(k, style = HemType.bodyMuted, modifier = Modifier.weight(1f))
        Text(v, style = HemType.body.copy(fontWeight = FontWeight.SemiBold))
    }
}

private fun formatDate(date: java.util.Date?): String {
    if (date == null) return "—"
    val fmt = SimpleDateFormat("MMM d, yyyy", Locale.US)
    fmt.timeZone = TimeZone.getDefault()
    return fmt.format(date)
}

private fun daysUntil(date: java.util.Date): Int {
    val diff = date.time - System.currentTimeMillis()
    return (diff / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(0)
}
