package com.fitrater.app.util

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory copy of user appearance prefs — persisted server-side in profiles.
 * Screens/animations read from these; a full apply-live pass will land later.
 */
object AppPrefs {
    val theme = MutableStateFlow("system") // "system" | "light" | "dark"
    val textScale = MutableStateFlow(1.0)
    val reduceMotion = MutableStateFlow(false)
}
