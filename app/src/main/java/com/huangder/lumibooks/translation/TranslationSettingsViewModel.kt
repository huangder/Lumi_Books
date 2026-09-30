package com.huangder.lumibooks.translation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TranslationSettingsAction(
    val busy: Boolean = false,
    val saved: Boolean = false,
    val testTranslation: String? = null,
    val error: TranslationError? = null
)

sealed interface TranslationModelCatalog {
    data object Idle : TranslationModelCatalog
    data object Loading : TranslationModelCatalog
    data class Ready(val models: List<String>) : TranslationModelCatalog
    data class Failure(val error: TranslationError) : TranslationModelCatalog
}

@HiltViewModel
class TranslationSettingsViewModel @Inject constructor(private val repository: TranslationRepository) : ViewModel() {
    val configuration = repository.configuration.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val mutableAction = MutableStateFlow(TranslationSettingsAction())
    val action = mutableAction.asStateFlow()
    private var job: Job? = null
    private val mutableModels = MutableStateFlow<TranslationModelCatalog>(TranslationModelCatalog.Idle)
    val models = mutableModels.asStateFlow()
    private var modelsJob: Job? = null

    fun fetchModels(settings: TranslationSettings, token: String) {
        modelsJob?.cancel()
        mutableModels.value = TranslationModelCatalog.Loading
        modelsJob = viewModelScope.launch {
            mutableModels.value = try { TranslationModelCatalog.Ready(repository.models(settings, token)) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                TranslationModelCatalog.Failure((error as? TranslationException)?.error ?: TranslationError.SERVICE)
            }
        }
    }

    fun connectionEdited() {
        edited()
        modelsJob?.cancel()
        mutableModels.value = TranslationModelCatalog.Idle
    }

    fun save(settings: TranslationSettings, token: String) = runAction {
        repository.save(settings, token)
        TranslationSettingsAction(saved = true)
    }

    fun test(settings: TranslationSettings, token: String) = runAction {
        TranslationSettingsAction(testTranslation = repository.test(settings, token))
    }

    fun edited() { job?.cancel(); mutableAction.value = TranslationSettingsAction() }

    private fun runAction(block: suspend () -> TranslationSettingsAction) {
        job?.cancel()
        mutableAction.value = TranslationSettingsAction(busy = true)
        job = viewModelScope.launch {
            mutableAction.value = try { block() } catch (error: Exception) {
                if (error is CancellationException) throw error
                TranslationSettingsAction(error = (error as? TranslationException)?.error ?: TranslationError.STORAGE)
            }
        }
    }
}
