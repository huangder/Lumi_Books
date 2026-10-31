package com.huangder.lumibooks.edition

import kotlinx.coroutines.flow.StateFlow

/** Capability boundary supplied by the source set selected for this build. */
interface PremiumAccess {
    val unlocked: StateFlow<Boolean>
    fun isUnlocked(): Boolean
    fun canAddRemoteSource(count: Int): Boolean
    fun start()
}
