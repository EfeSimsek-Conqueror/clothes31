package com.fitrater.app.ui.screens.subpages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitrater.app.data.Supa
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType

private val FAQS = listOf(
    "What are credits?" to
        "Credits fuel every score, Studio generation, try-on, versus, and decode. New accounts start with ${Supa.SIGNUP_CREDITS}. Buy packs anytime or unlock unlimited with Pro.",
    "What happens after my trial?" to
        "Your Pro trial lasts 7 days on the annual plan. Until it ends you get ${Supa.TRIAL_DAILY_CAP} credits/day. After that you're billed and unlock the full monthly cap.",
    "Can I cancel?" to
        "Yes — from Google Play → Payments & subscriptions. Access continues until the current period ends and nothing rolls over. No penalty, no dark patterns.",
    "How is my data used?" to
        "Photos and prompts are processed by our AI providers to score and generate your looks. Nothing is used to train third-party models. Export or delete anytime under Help & privacy.",
    "How does Hem talk to me?" to
        "One voice: honest and constructive. Hem tells you what's working, what isn't, and the one change that would lift the look — no flattery, no pile-on.",
    "How does Try-on work?" to
        "Upload a photo of yourself plus a garment (or paste a link) and Hem renders it on you. Costs ${Supa.TRYON_COST} credits per try.",
)

@Composable
fun FaqScreen(onClose: () -> Unit) {
    SubpageScaffold(eyebrow = "SUPPORT", title = "FAQ", onClose = onClose) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            FAQS.forEach { (q, a) ->
                FaqRow(q, a)
                Spacer(Modifier.height(HemSpace.sm))
            }
            Spacer(Modifier.height(HemSpace.xxl))
        }
    }
}

@Composable
private fun FaqRow(question: String, answer: String) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HemColors.CardCream)
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(12.dp))
            .clickable { open = !open }
            .padding(HemSpace.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                question,
                style = HemType.body.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            Text(if (open) "–" else "+", style = HemType.serifSection.copy(fontSize = 22.sp))
        }
        if (open) {
            Spacer(Modifier.height(HemSpace.xs))
            Text(answer, style = HemType.body.copy(lineHeight = 22.sp))
        }
    }
}
