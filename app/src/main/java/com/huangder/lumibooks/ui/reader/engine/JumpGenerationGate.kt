package com.huangder.lumibooks.ui.reader.engine

internal class JumpGenerationGate {
    private var nextGeneration = 0L

    var activeGeneration: Long? = null
        private set

    val isSettling: Boolean
        get() = activeGeneration != null

    fun begin(): Long {
        nextGeneration = if (nextGeneration == Long.MAX_VALUE) 1L else nextGeneration + 1L
        return nextGeneration.also { activeGeneration = it }
    }

    fun resolve(generation: Long): Boolean {
        if (activeGeneration != generation) return false
        activeGeneration = null
        return true
    }

    fun clear() {
        activeGeneration = null
    }
}

internal class ReaderPositionRequestTracker {
    private var lastChapterIndex: Int? = null
    private var lastPageIndex: Int? = null

    fun observe(chapterIndex: Int, pageIndex: Int): Boolean {
        val changed = lastChapterIndex != chapterIndex || lastPageIndex != pageIndex
        lastChapterIndex = chapterIndex
        lastPageIndex = pageIndex
        return changed
    }
}
