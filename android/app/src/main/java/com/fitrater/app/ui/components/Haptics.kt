package com.fitrater.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Wraps a click gesture with a haptic pulse. Feels like a real object
 * — tick for chips, thunk for primary presses.
 */
@Composable
fun Modifier.pressHaptic(
    type: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
    onClick: () -> Unit,
): Modifier {
    val haptic: HapticFeedback = LocalHapticFeedback.current
    return this.clickable {
        haptic.performHapticFeedback(type)
        onClick()
    }
}

/** Fire a single haptic pulse on demand — used for one-shot reveals. */
@Composable
fun rememberHapticPulse(): (HapticFeedbackType) -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) {
        { type -> haptic.performHapticFeedback(type) }
    }
}
