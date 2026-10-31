package com.huangder.lumibooks.ui.excerpt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.StoredExcerptPreferences
import com.huangder.lumibooks.edition.PremiumAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class ExcerptViewModel internal constructor(
    private val storedPreferences: Flow<StoredExcerptPreferences?>,
    private val isPremium: () -> Boolean,
    private val savePreferences: suspend (StoredExcerptPreferences) -> Unit
) : ViewModel() {
    @Inject
    constructor(dataStoreManager: DataStoreManager, premiumAccess: PremiumAccess) : this(
        storedPreferences = dataStoreManager.excerptPreferences,
        isPremium = premiumAccess::isUnlocked,
        savePreferences = dataStoreManager::saveExcerptPreferences
    )

    private val _selection = MutableStateFlow<ExcerptSelection?>(null)
    internal val selection: StateFlow<ExcerptSelection?> = _selection.asStateFlow()
    private val commands = Channel<Command>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            // Reopening waits for prior saves, so it cannot restore an older selection.
            for (command in commands) {
                when (command) {
                    Command.Open -> {
                        val stored = try {
                            storedPreferences.first()?.toExcerptSelection()
                        } catch (_: IOException) {
                            null
                        }
                        _selection.value = initialExcerptSelection(isPremium(), stored)
                    }
                    is Command.Save -> if (isPremium()) {
                        try {
                            savePreferences(command.selection.toStoredPreferences())
                        } catch (error: IOException) {
                            android.util.Log.e("ExcerptViewModel", "Could not save excerpt preferences", error)
                        }
                    }
                }
            }
        }
    }

    fun openSession() {
        _selection.value = null
        commands.trySend(Command.Open)
    }

    internal fun select(selection: ExcerptSelection) {
        if (_selection.value == null) return
        val normalized = selection.normalized()
        _selection.value = normalized
        if (isPremium()) commands.trySend(Command.Save(normalized))
    }

    private sealed interface Command {
        data object Open : Command
        data class Save(val selection: ExcerptSelection) : Command
    }
}
