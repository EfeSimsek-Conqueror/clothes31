package com.fitrater.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/**
 * In-memory dismissal tracker for the LowCredits nudge banner.
 * If the app is force-quit and re-launched, the nudge will reappear —
 * this is intentional (soft nudge, not a hard silence).
 */
object NudgeBus {
    private val _dismissedToday = MutableStateFlow<LocalDate?>(null)
    val dismissedToday: StateFlow<LocalDate?> = _dismissedToday

    fun dismissForToday() {
        _dismissedToday.value = LocalDate.now()
    }
}
