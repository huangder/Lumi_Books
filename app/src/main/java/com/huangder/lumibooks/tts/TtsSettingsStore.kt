package com.huangder.lumibooks.tts

import kotlinx.coroutines.flow.Flow

interface TtsSettingsStore {
    val ttsSpeechRate: Flow<Float>
    val ttsPitch: Flow<Float>
    val ttsProviderSelection: Flow<TtsProviderSelection>
    val externalTtsSettings: Flow<ExternalTtsSettings>

    fun externalTtsResumePosition(bookId: String): Flow<ExternalTtsResumePosition?>
    suspend fun saveTtsSpeechRate(rate: Float)
    suspend fun saveTtsPitch(pitch: Float)
    suspend fun saveTtsProviderSelection(selection: TtsProviderSelection)
    suspend fun saveExternalTtsResumePosition(position: ExternalTtsResumePosition)
    suspend fun clearExternalTtsResumePosition(bookId: String)
}
