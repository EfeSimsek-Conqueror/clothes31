package com.fitrater.app.util

/**
 * Build-time feature flags for surfaces that are wired in the UI but not yet
 * backed end-to-end. Keeping them here means the UI stays in the tree (and keeps
 * compiling) while never promising the user something the backend can't deliver.
 */
object Flags {
    /**
     * Push notifications. There is no FCM registration and no server-side
     * scheduler behind the notification preferences, so the toggles must stay
     * hidden until that infrastructure ships. Flip to `true` once both exist.
     */
    const val PUSH_ENABLED = false

    /**
     * Scoring v4 — the nine-axis brief dial.
     *
     * When true the score request carries `client_features: ["axes_v4","xray_v4"]`
     * and the server returns the axes, the rules ledger and the lever on top of
     * the v3 envelope. When false none of that is asked for: the same call comes
     * back as a pure v3 envelope graded on base weights, the score sheet's dials
     * still send their `intake` (the server uses it for weights either way), and
     * every v4 section on the detail screen hides itself because the fields are
     * absent. That is the degradation path if the Android UI ever has to ship a
     * release behind the server — flip this one constant, nothing else.
     */
    const val SCORING_V4 = true
}
