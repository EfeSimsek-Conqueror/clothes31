package com.fitrater.app.util

import java.io.IOException

/**
 * Turns a [Throwable] into something worth showing a user.
 *
 * Library exceptions leak internals — Credential Manager surfaces strings like
 * "[16] reauth failed", Ktor surfaces host names and status lines. Those are useful in
 * Logcat and meaningless (or alarming) on screen. Messages we raise ourselves are already
 * written for the user, so those pass through.
 *
 * @param fallback shown when we have nothing better to say.
 */
fun Throwable.userMessage(fallback: String): String = when (this) {
    is IOException -> "No connection. Check your network and try again."
    // Our own deliberate, user-facing failures (e.g. HemService).
    is IllegalStateException -> message?.takeIf { it.isNotBlank() } ?: fallback
    else -> fallback
}
