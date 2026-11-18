package com.huangder.lumibooks.replacement

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.util.UUID
import org.json.JSONArray

/** Definitions are shared; activation and execution order belong exclusively to each book. */
internal object SharedReplacementRules {
    const val LOCAL_KEY_PREFIX = "local_replacement_library_"
    private const val LEGACY_PREFIX = "local_replacement_rules_"
    private val libraryKey = stringPreferencesKey("${LOCAL_KEY_PREFIX}definitions_v1")
    private fun bookEnabledKey(bookId: String) = booleanPreferencesKey("${LOCAL_KEY_PREFIX}enabled_$bookId")
    private fun selectedKey(bookId: String) = stringSetPreferencesKey("${LOCAL_KEY_PREFIX}selected_$bookId")
    private fun orderKey(bookId: String) = stringPreferencesKey("${LOCAL_KEY_PREFIX}order_$bookId")

    /** One atomic migration imports even books that have not been opened since the upgrade. */
    fun migrate(preferences: MutablePreferences) {
        // Keep an encoded empty library as the migration marker, so deleted rules never reappear.
        if (preferences[libraryKey] != null) return
        val library = mutableListOf<ReplacementRule>()
        val legacy = preferences.asMap().entries.filter { it.key.name.startsWith(LEGACY_PREFIX) }
            .sortedBy { it.key.name }
        for ((key, value) in legacy) {
            val bookId = key.name.removePrefix(LEGACY_PREFIX)
            val book = BookReplacementRules.decode(value as? String)
            val selected = mutableSetOf<String>()
            val order = mutableListOf<String>()
            for (rule in book.rules) {
                val definition = rule.copy(enabled = false)
                val shared = library.firstOrNull {
                    // Two identical stages in the same book may intentionally run twice.
                    it.id !in order && it.copy(id = "") == definition.copy(id = "")
                } ?: run {
                    var id = rule.id
                    var suffix = 0
                    // Copied legacy IDs may refer to different rules in different books.
                    while (library.any { it.id == id }) {
                        id = UUID.nameUUIDFromBytes("${key.name}\u0000${rule.id}\u0000${suffix++}"
                            .toByteArray(Charsets.UTF_8)).toString()
                    }
                    definition.copy(id = id).also(library::add)
                }
                if (rule.enabled) selected += shared.id
                order += shared.id
            }
            preferences[bookEnabledKey(bookId)] = book.enabled
            preferences[selectedKey(bookId)] = selected
            preferences[orderKey(bookId)] = JSONArray(order.distinct()).toString()
        }
        preferences[libraryKey] = BookReplacementRules(rules = library).encode()
        // Leave the legacy values intact for data compatibility; only the new keys are read now.
    }

    fun read(preferences: Preferences, bookId: String): BookReplacementRules {
        val selected = preferences[selectedKey(bookId)].orEmpty()
        val rules = BookReplacementRules.decode(preferences[libraryKey]).rules
        val order = runCatching { JSONArray(preferences[orderKey(bookId)] ?: "[]") }.getOrDefault(JSONArray())
        val byId = rules.associateBy(ReplacementRule::id)
        val orderedIds = (0 until order.length()).map { order.optString(it) }.distinct()
        val orderedSet = orderedIds.toSet()
        val ordered = orderedIds.mapNotNull(byId::get) + rules.filter { it.id !in orderedSet }
        return BookReplacementRules(
            enabled = preferences[bookEnabledKey(bookId)] == true,
            rules = ordered.map {
                it.copy(enabled = it.id in selected)
            }
        )
    }

    /** Called inside DataStore.edit so simultaneous book edits cannot overwrite each other. */
    fun update(
        preferences: MutablePreferences,
        bookId: String,
        transform: (BookReplacementRules) -> BookReplacementRules
    ) {
        migrate(preferences)
        val next = transform(read(preferences, bookId))
        val rules = next.rules.distinctBy(ReplacementRule::id)
        val previous = BookReplacementRules.decode(preferences[libraryKey]).rules
        val byId = rules.associateBy(ReplacementRule::id)
        val previousIds = previous.mapTo(mutableSetOf()) { it.id }
        // A book's reorder does not shuffle the library shown to newly opened books.
        val definitions = previous.mapNotNull { byId[it.id] } + rules.filter { it.id !in previousIds }
        preferences[libraryKey] = BookReplacementRules(rules = definitions.map { it.copy(enabled = false) }).encode()
        preferences[bookEnabledKey(bookId)] = next.enabled
        preferences[selectedKey(bookId)] = rules.filter(ReplacementRule::enabled).mapTo(mutableSetOf()) { it.id }
        preferences[orderKey(bookId)] = JSONArray(rules.map(ReplacementRule::id)).toString()

        // Deletion is shared, including selections in other books. It never changes their master switch.
        val ids = rules.mapTo(mutableSetOf()) { it.id }
        val selections = preferences.asMap().keys.filter { it.name.startsWith("${LOCAL_KEY_PREFIX}selected_") }
        for (key in selections) {
            val selectionKey = stringSetPreferencesKey(key.name)
            val current = preferences[selectionKey].orEmpty()
            if (current.any { it !in ids }) preferences[selectionKey] = current.intersect(ids)
        }
    }
}
