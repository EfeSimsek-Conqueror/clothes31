package com.fitrater.app.ui.screens

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitrater.app.data.Supa
import com.fitrater.app.data.model.Outfit
import com.fitrater.app.data.model.Profile
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.data.repo.Stats
import com.fitrater.app.ui.components.SkeletonBar
import com.fitrater.app.ui.components.shimmer
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.SerifDisplay
import com.fitrater.app.ui.theme.SerifFamily
import com.fitrater.app.util.AppScope
import com.fitrater.app.util.CreditsBus
import com.fitrater.app.util.ToastBus
import com.fitrater.app.util.openExternal
import com.revenuecat.purchases.models.Period
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val Red = Color(0xFFB23A2A)

@Composable
fun YouSheetContent(
    onOpenCredits: () -> Unit,
    onOpenStyleProfile: () -> Unit,
    onOpenHelpPrivacy: () -> Unit,
    onOpenWeeklyLetter: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenManagePro: () -> Unit,
    onSignedOut: () -> Unit,
    isPro: Boolean = false,
) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf<Profile?>(null) }
    var stats by remember { mutableStateOf<Stats?>(null) }
    var latest by remember { mutableStateOf<Outfit?>(null) }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var uploadingAvatar by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        CreditsBus.refresh()
        val p = runCatching { Repo.currentProfile() }.getOrNull()
        profile = p
        avatarUrl = p?.avatar_url
        stats = runCatching { Repo.stats() }.getOrNull()
        latest = runCatching { Repo.outfits(1) }.getOrDefault(emptyList()).firstOrNull()
        loaded = true
    }

    val pickAvatarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploadingAvatar = true
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.buffered()?.use { it.readBytes() }
                        ?: error("Could not read image")
                }
                val url = withContext(Dispatchers.IO) { Repo.uploadAvatar(bytes) }
                Repo.updateAvatarUrl(url)
                // Cache-bust so Coil re-renders the new file on the same URL.
                avatarUrl = "$url?ts=${System.currentTimeMillis()}"
                ToastBus.post("Avatar updated")
            }.onFailure {
                Log.e("You", "avatar upload failed", it)
                ToastBus.post("Couldn't upload avatar: ${it.message ?: "error"}")
            }
            uploadingAvatar = false
        }
    }

    val email = Repo.userEmail
    val display = profile?.display_name ?: email?.substringBefore("@") ?: "You"
    val initial = display.firstOrNull()?.uppercaseChar()?.toString() ?: "•"

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
    ) {
        // 1. Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(HemColors.CardCream)
                    .border(1.dp, HemColors.Hairline, CircleShape)
                    .clickable(enabled = !uploadingAvatar) {
                        pickAvatarLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Text(initial, style = HemType.serifSection)
                }
                if (uploadingAvatar) {
                    Box(
                        Modifier.fillMaxSize().clip(CircleShape).background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("…", style = HemType.body.copy(color = Color.White))
                    }
                }
            }
            Spacer(Modifier.width(HemSpace.sm))
            Column(Modifier.weight(1f)) {
                SerifDisplay(display)
                if (email != null) {
                    Text(email, style = HemType.bodyMuted.copy(fontSize = 13.sp))
                }
            }
            if (profile?.is_pro == true) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, HemColors.Bronze, RoundedCornerShape(999.dp))
                        .background(HemColors.CardCream)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        "PRO",
                        style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 2.sp),
                    )
                }
            }
        }

        Spacer(Modifier.height(HemSpace.md))
        // Low credits nudge (persistent reminder in Pro-management context).
        com.fitrater.app.ui.components.LowCreditsBanner(onOpenCredits = onOpenCredits)

        Spacer(Modifier.height(HemSpace.lg))
        // 2. Stats — two rows.
        if (!loaded) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HemSpace.sm)) {
                repeat(3) { StatSkeletonCard(modifier = Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(HemSpace.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HemSpace.sm)) {
                repeat(3) { StatSkeletonCard(modifier = Modifier.weight(1f)) }
            }
        } else {
            val s = stats
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HemSpace.sm)) {
                StatCard(value = (s?.pieces ?: 0).toString(), label = "pieces", modifier = Modifier.weight(1f))
                StatCard(value = (s?.looks ?: 0).toString(), label = "looks", modifier = Modifier.weight(1f))
                StatCard(
                    value = s?.bestScore?.let { String.format(Locale.US, "%.1f", it) } ?: "–",
                    label = "best score",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(HemSpace.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HemSpace.sm)) {
                StatCard(value = (s?.creditsUsed ?: 0).toString(), label = "credits used", modifier = Modifier.weight(1f))
                StatCard(
                    value = "${s?.streakDays ?: 0}d",
                    label = "streak",
                    modifier = Modifier.weight(1f),
                )
                val delta = s?.monthDelta
                val deltaLabel = when {
                    delta == null -> "–"
                    delta >= 0 -> "+" + String.format(Locale.US, "%.1f", delta)
                    else -> String.format(Locale.US, "%.1f", delta)
                }
                StatCard(value = deltaLabel, label = "month rank", modifier = Modifier.weight(1f))
            }
        }

        // honesty picker removed Aug 2026 — single honest tone. The server now
        // normalises every tone to "honest", so the kind/honest/brutal segmented
        // control was promising a choice that no longer exists.

        // 5. Quote card
        val quote = latest?.hem_comment
        if (!quote.isNullOrBlank()) {
            Spacer(Modifier.height(HemSpace.lg))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HemColors.CardCream)
                    .border(1.dp, HemColors.Hairline, RoundedCornerShape(12.dp)),
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(HemColors.Bronze),
                )
                Column(Modifier.padding(HemSpace.md)) {
                    Text("“$quote”", style = HemType.serifQuote)
                    Text(
                        "— Hem",
                        style = HemType.bodyMuted.copy(fontStyle = FontStyle.Italic),
                    )
                }
            }
        }

        Spacer(Modifier.height(HemSpace.lg))
        // 6. Rows
        SheetHairline()
        SheetChevronRow("Buy credits") { onOpenCredits() }
        SheetHairline()
        SheetChevronRow("Weekly letter") { onOpenWeeklyLetter() }
        SheetHairline()
        SheetChevronRow("Style profile") { onOpenStyleProfile() }
        SheetHairline()
        SheetChevronRow("Appearance") { onOpenAppearance() }
        SheetHairline()
        SheetChevronRow("Manage Fitrater Pro") { onOpenManagePro() }
        SheetHairline()
        SheetChevronRow("Help & privacy") { onOpenHelpPrivacy() }
        SheetHairline()

        // Growth loop: one-shot Play rating reward.
        val ratedOk = profile?.rated_ok == true
        var rateBusy by remember { mutableStateOf(false) }
        // Persist the "already rated" flag FIRST — if that write fails the reward is not
        // granted, otherwise the user could collect it again on the next attempt.
        val grantRatingReward = {
            scope.launch {
                if (Repo.markRatedOk()) {
                    runCatching { Repo.addCredits(Supa.PLAY_RATING_REWARD, "play_rating") }
                    profile = profile?.copy(rated_ok = true)
                        ?: com.fitrater.app.data.model.Profile(rated_ok = true)
                    ToastBus.post("${Supa.PLAY_RATING_REWARD} credits added — thanks.")
                } else {
                    ToastBus.post("Couldn't record that just now — try again in a moment.")
                }
                rateBusy = false
            }
            Unit
        }
        RateOnPlayRow(
            alreadyRated = ratedOk,
            busy = rateBusy,
            onClick = {
                if (ratedOk || rateBusy) return@RateOnPlayRow
                val activity = context.findYouActivity()
                if (activity == null) {
                    ToastBus.post("Couldn't open the rating flow.")
                    return@RateOnPlayRow
                }
                rateBusy = true
                val manager = com.google.android.play.core.review.ReviewManagerFactory.create(activity)
                manager.requestReviewFlow().addOnCompleteListener { req ->
                    if (req.isSuccessful) {
                        manager.launchReviewFlow(activity, req.result).addOnCompleteListener {
                            grantRatingReward()
                        }
                    } else {
                        // Play unavailable (sideload, no Play Services) — grant anyway to unblock the reward.
                        grantRatingReward()
                    }
                }
            },
        )
        SheetHairline()

        Spacer(Modifier.height(HemSpace.xl))
        // 7. Sign out
        Box(
            Modifier
                .fillMaxWidth()
                .clickable {
                    AppScope.launch {
                        runCatching { Supa.client.auth.signOut() }
                        onSignedOut()
                    }
                }
                .padding(vertical = HemSpace.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Sign out",
                style = HemType.body.copy(
                    color = Red,
                    fontFamily = SerifFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        Spacer(Modifier.height(HemSpace.xl))
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(12.dp))
            .padding(vertical = HemSpace.md, horizontal = HemSpace.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = HemType.serifSection.copy(fontSize = 22.sp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = HemType.bodyMuted.copy(fontSize = 11.sp))
    }
}

@Composable
private fun StatSkeletonCard(modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(12.dp))
            .padding(vertical = HemSpace.md, horizontal = HemSpace.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.6f)
                .height(22.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(HemColors.Hairline)
                .shimmer(),
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth(0.9f)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(HemColors.Hairline)
                .shimmer(),
        )
    }
}

@Composable
private fun SheetHairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(HemColors.Hairline))
}

/** Wrapper of context to Activity for the Play review manager. */
private fun android.content.Context.findYouActivity(): android.app.Activity? {
    var ctx: android.content.Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
private fun RateOnPlayRow(alreadyRated: Boolean, busy: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !alreadyRated && !busy, onClick = onClick)
            .padding(vertical = HemSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (alreadyRated) "Rate on Play Store" else "Rate on Play Store",
                style = HemType.body.copy(
                    fontWeight = FontWeight.Medium,
                    color = if (alreadyRated) HemColors.Muted else HemColors.Ink,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    busy -> "Opening…"
                    alreadyRated -> "Thank you — you already rated."
                    else -> "Earn ${Supa.PLAY_RATING_REWARD} credits — once."
                },
                style = HemType.bodyMuted.copy(fontSize = 13.sp),
            )
        }
        if (!alreadyRated) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = HemColors.Muted)
        }
    }
}

@Composable
private fun SheetChevronRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = HemSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = HemType.body.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = HemColors.Muted)
    }
}

// ---- Credits paywall sheet (RevenueCat-backed) ----

/** Fallback definition of the 4 consumable credit packs, keyed by product/lookup id.
 *  When the RC offering loads at runtime, each package's `product.id` is matched
 *  to one of these entries to figure out how many credits to grant on success. */
private data class CreditPackSpec(val productId: String, val name: String, val credits: Int, val popular: Boolean = false)

private val CREDIT_PACK_SPECS = listOf(
    CreditPackSpec("credits_100", "Starter", 150),
    CreditPackSpec("credits_500", "Popular", 500, popular = true),
    CreditPackSpec("credits_1500", "Pro Pack", 1200),
    CreditPackSpec("credits_5000", "Mega", 3000),
)

/** Presentation model for a single row in the credits sheet. */
private data class PackRow(
    val spec: CreditPackSpec,
    val pkg: com.revenuecat.purchases.Package?, // null if offering hasn't loaded / this product missing
    val priceLabel: String,
)

private data class PlanRow(
    val id: String,
    val label: String,
    val subline: String,
    val pkg: com.revenuecat.purchases.Package?,
    val priceLabel: String,
)

@Composable
fun CreditsSheetContent(onClose: () -> Unit, paywallContext: String? = null) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()

    val balanceBox by CreditsBus.balance.collectAsState()
    val balance = balanceBox ?: 0
    var packs by remember { mutableStateOf<List<PackRow>>(emptyList()) }
    var plans by remember { mutableStateOf<List<PlanRow>>(emptyList()) }
    var isPro by remember { mutableStateOf(false) }
    var loadingOffering by remember { mutableStateOf(true) }
    var processingProductId by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }

    // Load balance + RC offering once.
    LaunchedEffect(Unit) {
        CreditsBus.refresh()
        val offering = runCatching { com.fitrater.app.data.billing.RcBilling.currentOffering() }.getOrNull()
        val info = runCatching { com.fitrater.app.data.billing.RcBilling.refreshCustomerInfo() }.getOrNull()
        isPro = com.fitrater.app.data.billing.RcBilling.isPro(info)

        // Only ever render what the store actually sells right now. A row we can't
        // price is a row we can't charge for, so showing it just gives the user a
        // dead card — and the set changes without an app update as products come
        // and go in Play Console.
        packs = CREDIT_PACK_SPECS.mapNotNull { spec ->
            // Exact-match on product id. Substring match is unsafe here —
            // "credits_500" is a prefix of "credits_5000", which would let
            // Popular pick up Mega's price/product.
            val pkg = offering?.availablePackages?.firstOrNull { it.product.id == spec.productId }
            val price = pkg?.product?.price?.formatted ?: return@mapNotNull null
            PackRow(spec = spec, pkg = pkg, priceLabel = price)
        }
        plans = listOfNotNull(
            planRow("weekly", "Weekly", "cancel anytime", offering?.weekly),
            planRow("monthly", "Monthly", "1,200 credits / month", offering?.monthly),
            planRow("annual", "Annual", annualSubline(offering?.annual), offering?.annual),
        )
        loadingOffering = false
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HemSpace.gutter, vertical = HemSpace.md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "CREDITS",
                    style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 2.sp),
                )
                Spacer(Modifier.height(HemSpace.xs))
                SerifDisplay("Top up your closet.")
            }
            Text(
                "×",
                style = HemType.serifSection.copy(fontSize = 28.sp),
                modifier = Modifier.clickable(onClick = onClose).padding(8.dp),
            )
        }

        // Feature-specific hero, when the paywall was opened from a locked feature.
        val heroTitle = when (paywallContext) {
            "tryon" -> "Try-on is Pro."
            "letter" -> "The Sunday Letter is Pro."
            else -> null
        }
        val heroBody = when (paywallContext) {
            "tryon" -> "Wear any Studio piece on your own photo. Unlimited on Pro."
            "letter" -> "A short Sunday letter — what you wore, what worked, what to try."
            else -> null
        }
        if (heroTitle != null && heroBody != null) {
            Spacer(Modifier.height(HemSpace.lg))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HemColors.CardCream)
                    .border(1.dp, HemColors.Bronze.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(HemSpace.md),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    "✦",
                    style = HemType.serifSection.copy(color = HemColors.Bronze, fontSize = 22.sp),
                )
                Spacer(Modifier.width(HemSpace.sm))
                Column(Modifier.weight(1f)) {
                    Text(heroTitle, style = HemType.serifSection.copy(fontSize = 20.sp))
                    Spacer(Modifier.height(2.dp))
                    Text(heroBody, style = HemType.bodyMuted)
                }
            }
        }

        Spacer(Modifier.height(HemSpace.lg))
        // Current balance + Pro badge
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(HemColors.CardCream)
                .border(1.dp, HemColors.Bronze.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(HemSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(HemColors.Bronze.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = HemColors.Bronze)
            }
            Spacer(Modifier.width(HemSpace.md))
            Column(Modifier.weight(1f)) {
                Text("Current balance", style = HemType.bodyMuted.copy(fontSize = 12.sp))
                Text(
                    "$balance credits",
                    style = HemType.serifSection.copy(fontSize = 24.sp),
                )
            }
            if (isPro) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(HemColors.Bronze)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("PRO", style = HemType.smallLabel.copy(color = Color.White, letterSpacing = 2.sp))
                }
            }
        }

        Spacer(Modifier.height(HemSpace.lg))
        packs.forEach { row ->
            PackCard(
                row = row,
                busy = processingProductId == row.spec.productId,
                enabled = row.pkg != null && processingProductId == null,
                onClick = {
                    val a = activity
                    val pkg = row.pkg
                    if (a == null || pkg == null) return@PackCard
                    processingProductId = row.spec.productId
                    scope.launch {
                        val result = runCatching { com.fitrater.app.data.billing.RcBilling.purchase(a, pkg) }
                        processingProductId = null
                        result
                            .onSuccess {
                                runCatching { Repo.addCredits(row.spec.credits, kind = "iap_${row.spec.productId}") }
                                runCatching { Repo.markPaywallShown() }
                                // Repo.addCredits already triggers CreditsBus.refreshAsync().
                                ToastBus.post("Added ${row.spec.credits} credits.")
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
            Spacer(Modifier.height(HemSpace.md))
        }

        if (plans.isNotEmpty()) {
            Spacer(Modifier.height(HemSpace.md))
            Text(
                "PRO INSTEAD",
                style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 2.sp),
            )
            Spacer(Modifier.height(HemSpace.xs))
            Text(
                "Unlimited monthly refills, priority scoring, Sunday letter.",
                style = HemType.bodyMuted,
            )
            Spacer(Modifier.height(HemSpace.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HemSpace.md)) {
                plans.forEach { plan ->
                    PlanTile(
                        plan = plan,
                        busy = processingProductId == plan.id,
                        enabled = plan.pkg != null && processingProductId == null,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val a = activity
                            val pkg = plan.pkg
                            if (a == null || pkg == null) return@PlanTile
                            processingProductId = plan.id
                            scope.launch {
                                val result = runCatching { com.fitrater.app.data.billing.RcBilling.purchase(a, pkg) }
                                processingProductId = null
                                result
                                    .onSuccess {
                                        isPro = com.fitrater.app.data.billing.RcBilling.isPro(it.customerInfo)
                                        runCatching { Repo.markPaywallShown() }
                                        ToastBus.post("You're Pro — welcome.")
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
                }
            }
        }

        if (loadingOffering) {
            Spacer(Modifier.height(HemSpace.md))
            Text("Loading store…", style = HemType.bodyMuted.copy(fontSize = 12.sp))
        } else if (packs.isEmpty() && plans.isEmpty()) {
            // Nothing purchasable came back — say so rather than leaving a blank sheet.
            Spacer(Modifier.height(HemSpace.md))
            Text(
                "The store isn't reachable right now. Your credits are safe — try again in a moment.",
                style = HemType.bodyMuted,
            )
        }

        Spacer(Modifier.height(HemSpace.lg))
        Text(
            if (restoring) "Restoring…" else "Restore purchases",
            style = HemType.bodyMuted,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !restoring) {
                    restoring = true
                    scope.launch {
                        val result = com.fitrater.app.data.billing.RcBilling.restore()
                        restoring = false
                        when (result) {
                            is com.fitrater.app.data.billing.RcBilling.RestoreResult.Failed ->
                                ToastBus.post("Couldn't reach the store — try again in a moment.")
                            is com.fitrater.app.data.billing.RcBilling.RestoreResult.Success -> {
                                val pro = result.customerInfo.entitlements
                                    .get(com.fitrater.app.data.billing.RcBilling.ENTITLEMENT_PRO)
                                    ?.isActive == true
                                if (pro) {
                                    isPro = true
                                    ToastBus.post("Purchases restored.")
                                } else {
                                    ToastBus.post("No purchases found on this account.")
                                }
                            }
                        }
                    }
                }
                .padding(vertical = HemSpace.sm),
        )

        Spacer(Modifier.height(HemSpace.sm))
        // Play requires the renewal terms and both policy links next to the plans.
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
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                ),
                modifier = Modifier
                    .clickable { openExternal(context, "https://fitrater.ai/terms") }
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
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                ),
                modifier = Modifier
                    .clickable { openExternal(context, "https://fitrater.ai/privacy") }
                    .padding(vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(HemSpace.xl))
    }
}

/** Null when the store has no priced package for this plan, so the caller can drop the tile. */
private fun planRow(
    id: String,
    label: String,
    subline: String,
    pkg: com.revenuecat.purchases.Package?,
): PlanRow? {
    val price = pkg?.product?.price?.formatted ?: return null
    return PlanRow(id, label, subline, pkg, price)
}

/** Only ever promises a trial when the store attached a free phase to the annual offer. */
private fun annualSubline(pkg: com.revenuecat.purchases.Package?): String {
    val trial = planTrialLabel(pkg) ?: return "750 credits / mo"
    return "750 credits / mo · $trial"
}

private fun planTrialLabel(pkg: com.revenuecat.purchases.Package?): String? = runCatching {
    val phase = pkg?.product?.defaultOption?.freePhase ?: return@runCatching null
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
    "$count-$unit trial"
}.getOrNull()

private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx: android.content.Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
private fun PackCard(row: PackRow, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val pack = row.spec
    val borderColor = when {
        pack.popular -> HemColors.Bronze
        else -> HemColors.Hairline
    }
    val borderWidth = if (pack.popular) 2.dp else 1.dp
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HemColors.CardCream)
            .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(HemSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pack.name,
                    style = HemType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                )
                if (pack.popular) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(HemColors.Bronze)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "BEST VALUE",
                            style = HemType.smallLabel.copy(color = Color.White, letterSpacing = 1.5.sp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${"%,d".format(pack.credits)} credits",
                style = HemType.bodyMuted,
            )
        }
        if (busy) {
            Text("Processing…", style = HemType.bodyMuted.copy(fontSize = 13.sp))
        } else {
            Text(row.priceLabel, style = HemType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp))
        }
    }
}

@Composable
private fun PlanTile(plan: PlanRow, busy: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val borderColor = HemColors.Hairline
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(HemColors.CardCream)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(HemSpace.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(plan.label, style = HemType.body.copy(fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(2.dp))
        Text(plan.subline, style = HemType.bodyMuted.copy(fontSize = 12.sp))
        Spacer(Modifier.height(4.dp))
        if (busy) {
            Text("Processing…", style = HemType.bodyMuted.copy(fontSize = 12.sp))
        } else {
            Text(plan.priceLabel, style = HemType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp))
        }
    }
}

@Composable
fun PlaceholderSheetContent(message: String, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(HemSpace.gutter),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Note",
                style = HemType.serifSection,
                modifier = Modifier.weight(1f),
            )
            Text(
                "×",
                style = HemType.serifSection.copy(fontSize = 26.sp),
                modifier = Modifier.clickable(onClick = onClose).padding(6.dp),
            )
        }
        Spacer(Modifier.height(HemSpace.md))
        Text(message, style = HemType.body)
        Spacer(Modifier.height(HemSpace.xl))
    }
}
