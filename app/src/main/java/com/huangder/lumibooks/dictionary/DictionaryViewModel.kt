package com.huangder.lumibooks.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DictionaryViewModel @Inject constructor(val repository: DictionaryRepository) : ViewModel() {
    val state = repository.state
    private val _lookup = MutableStateFlow<DictionaryLookupResult?>(null)
    val lookup = _lookup.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private var queryJob: Job? = null
    private var query = ""
    private var signature = ""

    init {
        viewModelScope.launch {
            state.collect { state ->
                val next = "${state.filterVersion}|" + state.items.mapNotNull { it.installed }
                    .joinToString { "${it.descriptor.confirmationKey}:${it.enabled}:${it.order}" }
                if (next != signature) { signature = next; if (query.isNotBlank()) search(query) }
            }
        }
    }
    fun search(value: String) {
        query = value
        queryJob?.cancel()
        _lookup.value = null
        queryJob = viewModelScope.launch {
            try { _lookup.value = repository.lookup(value) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                _lookup.value = DictionaryLookupResult(failedDictionaries = listOf("本地词典"))
            }
        }
    }
    fun stopSearch() { query = ""; queryJob?.cancel(); _lookup.value = null }
    fun action(block: suspend DictionaryRepository.() -> Unit) {
        viewModelScope.launch {
            try { _error.value = null; repository.block() }
            catch (error: Exception) { if (error is CancellationException) throw error; _error.value = error.message ?: "操作失败，请重试" }
        }
    }
}
