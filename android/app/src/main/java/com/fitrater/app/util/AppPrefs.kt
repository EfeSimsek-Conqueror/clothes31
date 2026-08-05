package com.fitrater.app.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Appearance preferences.
 *
 * Mirrored in two places on purpose: [SharedPreferences] locally so a cold start can
 * paint the right theme on the very first frame (no session, no network), and
 * server-side in `profiles` so the choice follows the user across devices. Local is
 * what the UI renders from; the server copy is folded in once a profile loads.
 */
object AppPrefs {
    private const val FILE = "fitrater_appearance"
    private const val KEY_THEME = "theme"
    private const val KEY_TEXT_SCALE = "text_scale"
    private const val KEY_REDUCE_MOTION = "reduce_motion"

    /** "system" | "light" | "dark" */
    val theme = MutableStateFlow("system")
    val textScale = MutableStateFlow(1.0)
    val reduceMotion = MutableStateFlow(false)

    private var prefs: SharedPreferences? = null

    /** Call from `Activity.onCreate` before `setContent`. Reads are synchronous. */
    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        theme.value = p.getString(KEY_THEME, "system") ?: "system"
        textScale.value = p.getFloat(KEY_TEXT_SCALE, 1f).toDouble()
        reduceMotion.value = p.getBoolean(KEY_REDUCE_MOTION, false)
    }

    fun setTheme(value: String) {
        theme.value = value
        prefs?.edit()?.putString(KEY_THEME, value)?.apply()
    }

    fun setTextScale(value: Double) {
        textScale.value = value
        prefs?.edit()?.putFloat(KEY_TEXT_SCALE, value.toFloat())?.apply()
    }

    fun setReduceMotion(value: Boolean) {
        reduceMotion.value = value
        prefs?.edit()?.putBoolean(KEY_REDUCE_MOTION, value)?.apply()
    }

    /** Fold a server-side profile into the local copy. */
    fun hydrateFromProfile(theme: String?, textScale: Double?, reduceMotion: Boolean?) {
        theme?.let { setTheme(it) }
        textScale?.let { setTextScale(it) }
        reduceMotion?.let { setReduceMotion(it) }
    }
}
