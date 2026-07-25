package com.fitrater.app.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow

object Session {
    fun status(): StateFlow<SessionStatus> = Supa.client.auth.sessionStatus
}
