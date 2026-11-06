package com.huangder.lumibooks.ui.bookshelf.spine

import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Process-lifetime shelf state. A cold start (the app removed from recents) draws a new seed, so
 * poses are re-rolled and the books fall again; returning from the reader keeps both. Each
 * folder is its own scene: its books fall the first time it is opened in this process.
 */
internal object SpineShelfSession {
    val seed: Long = Random.nextLong()

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
