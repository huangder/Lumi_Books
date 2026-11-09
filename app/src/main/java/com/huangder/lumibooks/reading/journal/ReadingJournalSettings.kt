package com.huangder.lumibooks.reading.journal

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.huangder.lumibooks.data.local.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class JournalRetentionUnit { MONTH, YEAR, NEVER }

data class JournalRetention(val amount: Int = DEFAULT_AMOUNT, val unit: JournalRetentionUnit = JournalRetentionUnit.YEAR) {
    companion object {
        const val DEFAULT_AMOUNT = 1
        const val MAX_AMOUNT = 99
    }
}

data class ReadingJournalState(
    val enabled: Boolean = false,
    val consentVersion: Int = 0,
    val enabledAt: Long = 0L,
    val retention: JournalRetention = JournalRetention(),
    val lastCleanupAt: Long = 0L,
    val lastBackupAt: Long = 0L
)

/**
 * Journal preferences live in the shared settings store. Keys starting with
 * [LOCAL_KEY_PREFIX] are per device and stay out of backups; retention is portable.
 */
@Singleton
class ReadingJournalSettings @Inject constructor(@ApplicationContext private val context: Context) {
    val state: Flow<ReadingJournalState> = context.dataStore.data.map(::read)

    suspend fun current(): ReadingJournalState = read(context.dataStore.data.first())

    suspend fun enable(consentVersion: Int, retention: JournalRetention, now: Long = System.currentTimeMillis()) {
        context.dataStore.edit {
            it[ENABLED] = true
            it[CONSENT_VERSION] = consentVersion
            if ((it[ENABLED_AT] ?: 0L) <= 0L) it[ENABLED_AT] = now
            it.writeRetention(retention)
        }
    }

    suspend fun disable() {
        context.dataStore.edit { it[ENABLED] = false; it[ENABLED_AT] = 0L }
    }

    suspend fun setRetention(retention: JournalRetention) {
        context.dataStore.edit { it.writeRetention(retention) }
    }

    suspend fun markCleanup(now: Long) {
        context.dataStore.edit { it[LAST_CLEANUP_AT] = now }
    }

    suspend fun markBackup(now: Long = System.currentTimeMillis()) {
        context.dataStore.edit { it[LAST_BACKUP_AT] = now }
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.writeRetention(value: JournalRetention) {
        this[RETENTION_AMOUNT] = value.amount.coerceIn(1, JournalRetention.MAX_AMOUNT)
        this[RETENTION_UNIT] = value.unit.name
    }

    companion object {
        const val CONSENT_VERSION_CURRENT = 1
        const val LOCAL_KEY_PREFIX = "journal_local_"

        private val ENABLED = booleanPreferencesKey("${LOCAL_KEY_PREFIX}enabled")
        private val CONSENT_VERSION = intPreferencesKey("${LOCAL_KEY_PREFIX}consent_version")
        private val ENABLED_AT = longPreferencesKey("${LOCAL_KEY_PREFIX}enabled_at")
        private val LAST_CLEANUP_AT = longPreferencesKey("${LOCAL_KEY_PREFIX}last_cleanup_at")
        private val LAST_BACKUP_AT = longPreferencesKey("${LOCAL_KEY_PREFIX}last_backup_at")
        private val RETENTION_AMOUNT = intPreferencesKey("journal_retention_amount")
        private val RETENTION_UNIT = stringPreferencesKey("journal_retention_unit")

        internal fun read(preferences: Preferences) = ReadingJournalState(
            enabled = preferences[ENABLED] ?: false,
            consentVersion = preferences[CONSENT_VERSION] ?: 0,
            enabledAt = preferences[ENABLED_AT] ?: 0L,
            retention = JournalRetention(
                amount = (preferences[RETENTION_AMOUNT] ?: JournalRetention.DEFAULT_AMOUNT)
                    .coerceIn(1, JournalRetention.MAX_AMOUNT),
                unit = JournalRetentionUnit.entries.firstOrNull { it.name == preferences[RETENTION_UNIT] }
                    ?: JournalRetentionUnit.YEAR
            ),
            lastCleanupAt = preferences[LAST_CLEANUP_AT] ?: 0L,
            lastBackupAt = preferences[LAST_BACKUP_AT] ?: 0L
        )

        /** Marks a completed backup or cloud sync; failures never affect the backup itself. */
        suspend fun recordBackup(context: Context, now: Long = System.currentTimeMillis()) {
            runCatching { context.dataStore.edit { it[LAST_BACKUP_AT] = now } }
        }

        /** Retention cutoff for restores and merges, read without an injected instance. */
        suspend fun retentionCutoff(context: Context, now: Long): Long? =
            ReadingJournalRetentionPolicy.cutoff(read(context.dataStore.data.first()).retention, now)
    }
}
