package com.huangder.lumibooks.tts

import kotlinx.coroutines.flow.Flow
import com.huangder.lumibooks.domain.model.BookPositionUse

interface TtsSettingsStore {
    val ttsProsodySettings: Flow<TtsProsodySettings>
    val ttsProviderSelection: Flow<TtsProviderSelection>
    val externalTtsSettings: Flow<ExternalTtsSettings>
    val ttsStartChoice: Flow<TtsStartChoice?>

    fun externalTtsResumePosition(bookId: String): Flow<ExternalTtsResumePosition?>
    fun ttsListeningPosition(bookId: String): Flow<TtsListeningPosition?>
    suspend fun saveTtsListeningPosition(position: TtsListeningPosition)
    suspend fun saveBookPositionUse(bookId: String, use: BookPositionUse)
    suspend fun saveTtsStartChoice(choice: TtsStartChoice?)
    suspend fun saveTtsProsodySettings(settings: TtsProsodySettings)
    suspend fun saveTtsProviderSelection(selection: TtsProviderSelection)
    suspend fun saveExternalTtsResumePosition(position: ExternalTtsResumePosition)
    suspend fun clearExternalTtsResumePosition(bookId: String)
}
