package com.fitrater.app.util

/**
 * In-memory hand-off for a freshly captured photo between the CameraCaptureScreen
 * and the ScoreSheet. Keeping the raw bytes in memory (rather than routing them
 * as a nav argument) avoids serialization + size limits.
 */
/** Survives nav-scoped composable destruction (used by VersusScreen). */
object VersusBus {
    @Volatile var aBytes: ByteArray? = null
    @Volatile var bBytes: ByteArray? = null
    fun clear() { aBytes = null; bBytes = null }
}

object CameraBus {
    @Volatile private var pending: ByteArray? = null
    @Volatile var slot: String? = null

    fun set(bytes: ByteArray) { pending = bytes }
    fun consume(): ByteArray? {
        val b = pending
        pending = null
        return b
    }
    /** Consume + also reset slot. */
    fun consumeWithSlot(): Pair<String?, ByteArray?> {
        val s = slot
        val b = pending
        slot = null
        pending = null
        return s to b
    }
}
