package com.huangder.lumibooks.ui.reader

/** Per-WebView document/configuration identity, independent of its changing page role. */
internal class EpubDocumentLifecycle {
    var configurationGeneration = 0L
        private set
    var layoutRevision = -1L
        private set
    private var configurationKey: String? = null
    private var stableRevision = -1L
    private var revealed = false
    private val commands = mutableSetOf<String>()

    fun beginDocument() {
        configurationGeneration++
        configurationKey = null
        layoutRevision = -1L
        stableRevision = -1L
        revealed = false
        commands.clear()
    }

    fun configure(key: String): Boolean {
        if (configurationKey == key) return false
        configurationKey = key
        configurationGeneration++
        layoutRevision = -1L
        stableRevision = -1L
        return true
    }

    fun configurationFailed(generation: Long) {
        if (generation != configurationGeneration) return
        configurationKey = null
        commands.clear()
    }

    fun issueCommand(key: String): Boolean = commands.add(key)

    /** A prepared neighbour is already visible as part of its page-turn animation. */
    fun markPresented() { revealed = true }

    fun observeLayout(generation: Long, revision: Long, stable: Boolean): Boolean {
        if (generation != configurationGeneration || revision < 0 || revision < layoutRevision) return false
        if (revision > layoutRevision) stableRevision = -1L
        layoutRevision = revision
        if (stable) stableRevision = revision
        return true
    }

    fun isCurrent(generation: Long, revision: Long): Boolean =
        generation == configurationGeneration && revision == layoutRevision

    fun isStable(generation: Long, revision: Long): Boolean =
        isCurrent(generation, revision) && stableRevision == revision

    fun claimReveal(generation: Long, revision: Long): Boolean {
        if (revealed || !isStable(generation, revision)) return false
        revealed = true
        return true
    }
}
