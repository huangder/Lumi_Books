package com.huangder.lumibooks.tts

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExternalTtsAudioCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun createKey_isStableAndChangesWithSynthesisInputs() {
        val cache = ExternalTtsAudioCache(temporaryFolder.newFolder("cache"))
        val settings = ExternalTtsConfig.defaults(ExternalTtsProtocol.MIMO_CHAT)

        val first = cache.createKey(settings, "同一句话")
        val again = cache.createKey(settings, "同一句话")
        val changedVoice = cache.createKey(settings.copy(voice = "other"), "同一句话")

        assertEquals(first, again)
        assertNotEquals(first, changedVoice)
        assertTrue(first.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun store_commitsCompletePcmAndReadsFromExactFrame() = runBlocking {
        val cache = ExternalTtsAudioCache(temporaryFolder.newFolder("cache"))
        val settings = ExternalTtsConfig.defaults(ExternalTtsProtocol.MIMO_CHAT)
        val key = cache.createKey(settings, "resume")
        val chunks = listOf(
            byteArrayOf(0, 1, 2, 3),
            byteArrayOf(4, 5, 6, 7)
        )

        assertTrue(cache.store(key, chunks.asFlow(), maxBytes = 1024))
        val resumed = requireNotNull(cache.open(key, startFrame = 2)).chunks.toList()

        assertTrue(cache.contains(key))
        assertEquals(4L, cache.frameCount(key))
        assertArrayEquals(byteArrayOf(4, 5, 6, 7), resumed.single())
    }

    @Test
    fun clear_doesNotDeleteAnActiveRead() = runBlocking {
        val cache = ExternalTtsAudioCache(temporaryFolder.newFolder("cache"))
        val key = cache.createKey(
            ExternalTtsConfig.defaults(ExternalTtsProtocol.MIMO_CHAT),
            "active"
        )
        cache.store(key, listOf(ByteArray(32_768) { 1 }).asFlow(), 65_536)
        val activeRead = requireNotNull(cache.open(key))

        activeRead.chunks.collect { cache.clear() }

        assertTrue(cache.contains(key))
    }

    @Test
    fun clear_doesNotDeleteAReadPinnedBeforeCollection() = runBlocking {
        val cache = ExternalTtsAudioCache(temporaryFolder.newFolder("cache"))
        val key = cache.createKey(
            ExternalTtsConfig.defaults(ExternalTtsProtocol.MIMO_CHAT),
            "pinned"
        )
        cache.store(key, listOf(ByteArray(32_768) { 1 }).asFlow(), 65_536)
        val pinnedRead = requireNotNull(cache.open(key))

        cache.clear()

        assertTrue(cache.contains(key))
        assertEquals(32_768, pinnedRead.chunks.toList().sumOf { it.size })
    }

    @Test
    fun closingAnUncollectedReadReleasesItsPin() = runBlocking {
        val cache = ExternalTtsAudioCache(temporaryFolder.newFolder("cache"))
        val key = cache.createKey(
            ExternalTtsConfig.defaults(ExternalTtsProtocol.MIMO_CHAT),
            "closed"
        )
        cache.store(key, listOf(ByteArray(8) { 1 }).asFlow(), 1024)
        val pinnedRead = requireNotNull(cache.open(key))

        pinnedRead.close()
        cache.clear()

        assertFalse(cache.contains(key))
    }

    @Test
    fun trimToLimit_evictsLeastRecentlyUsedEntry() = runBlocking {
        val cacheDir = temporaryFolder.newFolder("cache")
        val cache = ExternalTtsAudioCache(cacheDir)
        val settings = ExternalTtsConfig.defaults(ExternalTtsProtocol.OPENAI_SPEECH)
        val oldest = cache.createKey(settings, "old")
        val newest = cache.createKey(settings, "new")
        cache.store(oldest, listOf(ByteArray(8) { 1 }).asFlow(), 1024)
        Thread.sleep(5)
        cache.store(newest, listOf(ByteArray(8) { 2 }).asFlow(), 1024)

        cache.trimToLimit(8)

        assertFalse(cache.contains(oldest))
        assertTrue(cache.contains(newest))
    }
}
