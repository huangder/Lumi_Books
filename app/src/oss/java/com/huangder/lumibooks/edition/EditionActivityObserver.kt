package com.huangder.lumibooks.edition

import javax.inject.Inject

/** Optional edition-specific handling of activity recorded by this installation. */
class EditionActivityObserver @Inject constructor() {
    suspend fun prepare() = Unit

    fun record(readingDuration: Long, listeningDuration: Long) = Unit
}
