package com.fitrater.app.util

import com.fitrater.app.data.repo.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Single source of truth for the user's current credit balance.
 * Every screen that shows credits observes [balance]. Every spend/grant
 * path in Repo calls [refresh] so all live UIs update immediately.
 */
object CreditsBus {
    private val _balance = MutableStateFlow<Int?>(null)
    val balance: StateFlow<Int?> = _balance

    /** Re-read credits from the DB and publish. Idempotent. */
    suspend fun refresh() {
        val n = runCatching { Repo.credits() }.getOrNull()
        _balance.value = n
    }

    /** Fire-and-forget refresh from any composable / callback scope. */
    fun refreshAsync() {
        AppScope.launch { refresh() }
    }

    /** Called when the user signs out — clear the cached balance. */
    fun clear() { _balance.value = null }
}
