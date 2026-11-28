package com.huangder.lumibooks.ui.bookshelf.spine

import java.util.concurrent.ConcurrentHashMap

/** Tracks the first-visit animation and scroll position of each shelf during this process. */
internal object SpineShelfSession {
    private val dropped = ConcurrentHashMap.newKeySet<String>()
    private val scroll = ConcurrentHashMap<String, Pair<Int, Int>>()

    fun dropPlayed(scene: String): Boolean = scene in dropped

    fun markDropPlayed(scene: String) {
        dropped += scene
    }

    fun scrollOf(scene: String): Pair<Int, Int> = scroll[scene] ?: (0 to 0)

    fun saveScroll(scene: String, index: Int, offset: Int) {
        scroll[scene] = index to offset
    }
}
