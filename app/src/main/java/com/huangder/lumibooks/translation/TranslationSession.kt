package com.huangder.lumibooks.translation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/** One dictionary sheet session. All calls and state transitions run on the owning UI scope. */
class TranslationSession(private val scope: CoroutineScope, private val source: TranslationSource) {
    private val mutableState = MutableStateFlow<TranslationState>(TranslationState.Hidden)
    val state = mutableState.asStateFlow()
    private var configuration: TranslationConfiguration? = null
    private var text: String? = null
    private var request: Job? = null
    private var generation = 0L

    init {
        scope.launch {
            source.configuration.collect { next ->
                if (next != configuration) {
                    configuration = next
                    launchTranslation()
                }
            }
        }
    }

    fun search(selectedText: String) {
        if (text == selectedText) return
        text = selectedText
        launchTranslation()
    }

    fun retry() {
        if (mutableState.value is TranslationState.Failure || mutableState.value == TranslationState.Manual)
            launchTranslation(manual = true)
    }

    fun stop() {
        text = null
        launchTranslation()
    }

    private fun launchTranslation(manual: Boolean = false) {
        request?.cancel()
        val currentGeneration = ++generation
        val selectedText = text
        val config = configuration
        if (selectedText.isNullOrBlank() || config == null || !config.hasToken ||
            config.settings.model.isBlank() || config.settings.baseUrl.isBlank() || config.settings.targetLanguage.isBlank()) {
            mutableState.value = TranslationState.Hidden
            return
        }
        if (!config.enabled && !manual) {
            mutableState.value = TranslationState.Manual
            return
        }
        mutableState.value = TranslationState.Loading
        request = scope.launch {
            val result = try {
                TranslationState.Success(source.translate(selectedText, config))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                TranslationState.Failure((error as? TranslationException)?.error ?: TranslationError.SERVICE)
            }
            coroutineContext.ensureActive()
            if (generation == currentGeneration) mutableState.value = result
        }
    }
}
