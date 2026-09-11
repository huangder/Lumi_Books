package com.huangder.lumibooks.tts

import kotlinx.coroutines.flow.Flow

interface TtsSettingsStore {
    val ttsProsodySettings: Flow<TtsProsodySettings>
    val ttsProviderSelection: Flow<TtsProviderSelection>
    val externalTtsSettings: Flow<ExternalTtsSettings>

    fun externalTtsResumePosition(bookId: String): Flow<ExternalTtsResumePosition?>
    suspend fun saveTtsProsodySettings(settings: TtsProsodySettings)
    suspend fun saveTtsProviderSelection(selection: TtsProviderSelection)
    suspend fun saveExternalTtsResumePosition(position: ExternalTtsResumePosition)
    suspend fun clearExternalTtsResumePosition(bookId: String)
}
