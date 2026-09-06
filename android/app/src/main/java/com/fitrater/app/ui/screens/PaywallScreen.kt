package com.fitrater.app.ui.screens

import android.content.Context
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitrater.app.data.billing.RcBilling
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.PrimaryButton
import com.fitrater.app.ui.theme.SerifDisplay
import com.fitrater.app.util.ToastBus
import com.fitrater.app.util.openExternal
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.models.Period
import kotlinx.coroutines.launch

private const val TERMS_URL = "https://fitrater.ai/terms"
private const val PRIVACY_URL = "https://fitrater.ai/privacy"

/** A single subscription plan as rendered on the paywall. Prices always come from the store. */
private data class PaywallPlan(
    val id: String,
    val title: String,
    /** Store-formatted price line, e.g. "$59.99 / year". Null until the offering loads. */
    val priceLine: String?,
    /** Free-trial phrase from the Play subscription offer, e.g. "7 days free". Null when there is none. */
    val trialLine: String?,
    val pkg: Package?,
)

@Composable
fun PaywallScreen(
    onDone: () -> Unit,
    onDismiss: () -> Unit = onDone,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findPaywallActivity() }
    val scope = rememberCoroutineScope()

    var plans by remember { mutableStateOf(emptyPaywallPlans()) }
    var picked by remember { mutableStateOf("annual") }
    var loadingOffering by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val offering = runCatching { RcBilling.currentOffering() }.getOrNull()
        if (offering != null) {
            plans = listOf(
                paywallPlan("annual", "Annual", offering.annual),
                paywallPlan("monthly", "Monthly", offering.monthly),
            )
        }
        loadingOffering = false
    }

    val selected = plans.firstOrNull { it.id == picked }
    // Only ever promise a trial when the store actually attached a free phase to the offer.
    val trialLine = selected?.trialLine
    val buttonLabel = when {
        busy -> "Processing…"
        trialLine != null -> "Start free trial"
        else -> "Subscribe"
    }
    // Savings badge is derived from the real prices — never a hardcoded claim.
    val savingsBadge = remember(plans) { savingsBadge(plans) }

    Column(
        Modifier
            .fillMaxSize()
            .background(HemColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HemSpace.gutter, vertical = HemSpace.xl),
    ) {
        Spacer(Modifier.height(HemSpace.lg))
        // gold badge
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Brush.horizontalGradient(listOf(HemColors.GoldStart, HemColors.GoldEnd)))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                "FITSCORE PRO",
                style = HemType.smallLabel.copy(color = Color.White, letterSpacing = 2.sp),
            )
        }
        Spacer(Modifier.height(HemSpace.md))
        // The feature-specific hero lives on the credits sheet — that's what every
        // feature gate with a known context (try-on, letter) actually opens.
        SerifDisplay("Every feature.\nNothing held back.")
        Spacer(Modifier.height(HemSpace.lg))
        listOf(
            "i." to "Unlimited look scoring with Hem's notes",
            "ii." to "The Mirror — try any piece on your photo",
            "iii." to "Closet auto-tagging & the Sunday letter",
        ).forEach { (roman, body) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(
                    roman,
                    style = HemType.serifQuote.copy(color = HemColors.Bronze),
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(body, style = HemType.body)
            }
        }
        Spacer(Modifier.height(HemSpace.lg))
        plans.forEach { plan ->
            PlanCard(
                title = plan.title,
                price = when {
                    plan.priceLine == null && loadingOffering -> "Loading…"
                    plan.priceLine == null -> "Unavailable"
                    plan.trialLine != null -> "${plan.trialLine}, then ${plan.priceLine}"
                    else -> plan.priceLine
                },
                badge = if (plan.id == "annual") savingsBadge else null,
                selected = picked == plan.id,
                enabled = plan.pkg != null && !busy,
                onClick = { picked = plan.id },
            )
            Spacer(Modifier.height(HemSpace.sm))
        }
        Spacer(Modifier.height(HemSpace.lg))
        PrimaryButton(
            label = buttonLabel,
            enabled = selected?.pkg != null && activity != null && !busy,
            onClick = {
                val a = activity ?: return@PrimaryButton
                val pkg = selected?.pkg ?: return@PrimaryButton
                busy = true
                scope.launch {
                    val result = runCatching { RcBilling.purchase(a, pkg) }
                    busy = false
                    result
                        .onSuccess {
                            runCatching { RcBilling.refreshCustomerInfo() }
                            runCatching { Repo.markPaywallShown() }
                            ToastBus.post("You're Pro — welcome.")
                            onDone()
                        }
                        .onFailure { err ->
                            val msg = err.message ?: ""
                            if (msg.contains("cancel", ignoreCase = true)) {
                                ToastBus.post("Purchase canceled.")
                            } else {
                                ToastBus.post("Purchase failed — no charge.")
                            }
                        }
                }
            },
        )
        Spacer(Modifier.height(HemSpace.sm))
        Text(
            "Restore purchases",
            style = HemType.bodyMuted,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !busy) {
                    busy = true
                    scope.launch {
                        val result = RcBilling.restore()
                        busy = false
                        when (result) {
                            is RcBilling.RestoreResult.Failed ->
                                ToastBus.post("Couldn't reach the store — try again in a moment.")
                            is RcBilling.RestoreResult.Success -> {
                                val pro = result.customerInfo
                                    .entitlements[RcBilling.ENTITLEMENT_PRO]?.isActive == true
                                if (pro) {
                                    runCatching { Repo.markPaywallShown() }
                                    ToastBus.post("Purchases restored.")
                                    onDone()
                                } else {
                                    ToastBus.post("No purchases found on this account.")
                                }
                            }
                        }
                    }
                }
                .padding(vertical = HemSpace.sm),
        )
        Spacer(Modifier.height(HemSpace.md))
        LegalBlock(context)
        Spacer(Modifier.height(HemSpace.sm))
        Text(
            "Not now",
            style = HemType.bodyMuted.copy(fontSize = 13.sp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !busy, onClick = onDismiss)
                .padding(vertical = HemSpace.sm),
        )
        Spacer(Modifier.height(HemSpace.xl))
    }
}

/** Store-truth disclosure required by Play: renewal terms + links to the two policies. */
@Composable
private fun LegalBlock(context: Context) {
    Text(
        "Subscription renews automatically. Cancel anytime in Google Play.",
        style = HemType.bodyMuted.copy(fontSize = 12.sp),
    )
    Spacer(Modifier.height(HemSpace.xs))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Terms of Use",
            style = HemType.bodyMuted.copy(
                fontSize = 12.sp,
                textDecoration = TextDecoration.Underline,
            ),
            modifier = Modifier
                .clickable { openExternal(context, TERMS_URL) }
                .padding(vertical = 4.dp),
        )
        Text(
            "·",
            style = HemType.bodyMuted.copy(fontSize = 12.sp),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Text(
            "Privacy Policy",
            style = HemType.bodyMuted.copy(
                fontSize = 12.sp,
                textDecoration = TextDecoration.Underline,
            ),
            modifier = Modifier
                .clickable { openExternal(context, PRIVACY_URL) }
                .padding(vertical = 4.dp),
        )
        Spacer(Modifier.width(HemSpace.xs))
    }
}

private fun emptyPaywallPlans(): List<PaywallPlan> = listOf(
    PaywallPlan("annual", "Annual", priceLine = null, trialLine = null, pkg = null),
    PaywallPlan("monthly", "Monthly", priceLine = null, trialLine = null, pkg = null),
)

private fun paywallPlan(id: String, title: String, pkg: Package?): PaywallPlan {
    if (pkg == null) return PaywallPlan(id, title, priceLine = null, trialLine = null, pkg = null)
    val product = pkg.product
    val price = product.price.formatted
    val period = periodLabel(product.period)
    return PaywallPlan(
        id = id,
        title = title,
        priceLine = if (period == null) price else "$price / $period",
        trialLine = freeTrialLabel(pkg),
        pkg = pkg,
    )
}

/**
 * The free-trial phrase for a package, straight from the Play subscription offer.
 * Returns null when the selected offer has no free phase — in that case the UI must
 * not mention a trial at all.
 */
private fun freeTrialLabel(pkg: Package): String? = runCatching {
    val phase = pkg.product.defaultOption?.freePhase ?: return@runCatching null
    val period = phase.billingPeriod
    val count = period.value
    if (count <= 0) return@runCatching null
    val unit = when (period.unit) {
        Period.Unit.DAY -> "day"
        Period.Unit.WEEK -> "week"
        Period.Unit.MONTH -> "month"
        Period.Unit.YEAR -> "year"
        else -> return@runCatching null
    }
    if (count == 1) "1 $unit free" else "$count ${unit}s free"
}.getOrNull()

private fun periodLabel(period: Period?): String? = when (period?.unit) {
    Period.Unit.DAY -> "day"
    Period.Unit.WEEK -> "week"
    Period.Unit.MONTH -> "month"
    Period.Unit.YEAR -> "year"
    else -> null
}

/** "SAVE 45%" — computed from the real annual vs. 12× monthly price, or null if we can't. */
private fun savingsBadge(plans: List<PaywallPlan>): String? {
    val annual = plans.firstOrNull { it.id == "annual" }?.pkg?.product?.price ?: return null
    val monthly = plans.firstOrNull { it.id == "monthly" }?.pkg?.product?.price ?: return null
    if (annual.currencyCode != monthly.currencyCode) return null
    val yearOfMonthly = monthly.amountMicros * 12.0
    if (yearOfMonthly <= 0.0) return null
    val pct = ((1.0 - annual.amountMicros / yearOfMonthly) * 100).toInt()
    return if (pct >= 5) "SAVE $pct%" else null
}

private fun Context.findPaywallActivity(): android.app.Activity? {
    var ctx: Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    badge: String? = null,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) HemColors.Bronze else HemColors.Ink.copy(alpha = 0.2f)
    val borderWidth = if (selected) 2.dp else 1.dp
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HemColors.CardCream)
            .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(HemSpace.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = HemType.body.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        color = if (enabled) HemColors.Ink else HemColors.Muted,
                    ),
                )
                Spacer(Modifier.height(2.dp))
                Text(price, style = HemType.bodyMuted)
            }
            if (badge != null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(HemColors.Bronze)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(badge, style = HemType.smallLabel.copy(color = Color.White, letterSpacing = 1.5.sp))
                }
            }
        }
    }
}
