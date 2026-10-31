package com.huangder.lumibooks.privacy

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory unlock state of the hidden library. It is never persisted: a new process starts
 * locked, and the app locks it again whenever it leaves the foreground or the user leaves
 * the hidden pages.
 */
@Singleton
class HiddenLibrarySession @Inject constructor() {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    fun unlock() {
        _unlocked.value = true
    }

    fun lock() {
        _unlocked.value = false
    }
}
