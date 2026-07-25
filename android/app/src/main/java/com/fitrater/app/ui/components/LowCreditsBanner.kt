package com.fitrater.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.util.CreditsBus
import com.fitrater.app.util.NudgeBus
import java.time.LocalDate

/**
 * Cream nudge banner that appears when the user has fewer than 30 credits left.
 * - Dismissible for the current calendar day (in-memory only, via [NudgeBus]).
 * - Tapping "SEE PACKS →" opens the credits sheet via [onOpenCredits].
 */
@Composable
fun LowCreditsBanner(onOpenCredits: () -> Unit) {
    val balanceBox by CreditsBus.balance.collectAsState()
    val balance = balanceBox ?: return // Wait for a real balance before nudging.
    if (balance >= 30) return
    val dismissed by NudgeBus.dismissedToday.collectAsState()
    if (dismissed == LocalDate.now()) return

    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(HemColors.Bronze.copy(alpha = 0.5f)))
        Row(
            Modifier
                .fillMaxWidth()
                .background(HemColors.CardCream)
                .padding(horizontal = HemSpace.gutter, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "LOW ON CREDITS",
                    style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 2.sp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Only $balance credits left. Top up for another week of Hem.",
                    style = HemType.body.copy(color = HemColors.Ink, fontSize = 13.sp),
                )
            }
            Spacer(Modifier.size(HemSpace.sm))
            Text(
                "SEE PACKS →",
                style = HemType.smallLabel.copy(color = HemColors.Bronze, letterSpacing = 2.sp),
                modifier = Modifier
                    .clickable(onClick = onOpenCredits)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Spacer(Modifier.size(HemSpace.xs))
            Box(
                Modifier
                    .size(28.dp)
                    .clickable { NudgeBus.dismissForToday() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = HemColors.Muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(HemColors.Bronze.copy(alpha = 0.5f)))
    }
}
