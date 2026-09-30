package com.huangder.lumibooks.translation

import com.huangder.lumibooks.data.local.DataStoreManager
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface TranslationSource {
    val configuration: Flow<TranslationConfiguration>
    suspend fun translate(text: String, configuration: TranslationConfiguration): String
}

@Singleton
class TranslationRepository @Inject constructor(
    private val store: DataStoreManager,
    private val tokens: TranslationTokenStore,
    private val client: TranslationHttpClient
) : TranslationSource {
    private val mutex = Mutex()
    override val configuration: Flow<TranslationConfiguration> = store.translationSettings.map { settings ->
        mutex.withLock { TranslationConfiguration(settings, tokens.hasToken(settings.credentialScope)) }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    override suspend fun translate(text: String, configuration: TranslationConfiguration): String {
        val token = withContext(Dispatchers.IO) { mutex.withLock {
            if (store.translationSettings.first() != configuration.settings)
                throw CancellationException("Translation configuration changed")
            tokens.read(configuration.settings.credentialScope)
        } }
            ?: throw TranslationException(TranslationError.MISSING_KEY)
        return client.translate(configuration.settings, token, text)
    }

    suspend fun save(settings: TranslationSettings, newToken: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val normalized = settings.normalized()
            val token = newToken.trim()
            if (token.any { it.isISOControl() }) throw TranslationException(TranslationError.CONFIGURATION)
            if (normalized.enabled) {
                validateTranslationSettings(normalized)
                if (token.isBlank() && !tokens.hasToken(normalized.credentialScope))
                    throw TranslationException(TranslationError.MISSING_KEY)
            }
            if (token.isNotBlank()) tokens.save(token, normalized.credentialScope)
            val previous = store.translationSettings.first()
            store.saveTranslationSettings(normalized.copy(credentialVersion =
                if (token.isNotBlank()) UUID.randomUUID().toString() else previous.credentialVersion))
        }
    }

    suspend fun test(settings: TranslationSettings, newToken: String): String {
        return client.translate(settings, resolveDraftToken(settings, newToken), "The morning light fell softly across the open book.")
    }

    suspend fun models(settings: TranslationSettings, newToken: String): List<String> =
        client.models(settings, resolveDraftToken(settings, newToken))

    private suspend fun resolveDraftToken(settings: TranslationSettings, newToken: String): String {
        val token = newToken.trim().takeIf { it.isNotBlank() }
            ?: withContext(Dispatchers.IO) { mutex.withLock { tokens.read(settings.credentialScope) } }
            ?: throw TranslationException(TranslationError.MISSING_KEY)
        if (token.any { it.isISOControl() }) throw TranslationException(TranslationError.CONFIGURATION)
        return token
    }
}
