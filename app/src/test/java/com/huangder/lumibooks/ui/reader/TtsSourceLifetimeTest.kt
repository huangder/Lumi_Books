package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.tts.TtsPageContent
import com.huangder.lumibooks.tts.TtsPageSource
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsSourceLifetimeTest {
    @Test fun readerCanCloseBeforePlaybackWithoutReleasingParser() {
        var releases = 0
        val lifetime = TtsSourceLifetime { releases++ }
        val source = Source()
        val session = lifetime.retain(source)
        lifetime.releaseReader()
        assertEquals(0, releases)
        session.detachReader()
        assertEquals(1, source.detaches)
        session.close()
        session.close()
        lifetime.releaseReader()
        assertEquals(1, releases)
        assertEquals(1, source.closes)
    }

    @Test fun replacingSessionsKeepsResourcesUntilBothReaderAndPlaybackFinish() {
        var releases = 0
        val lifetime = TtsSourceLifetime { releases++ }
        val first = lifetime.retain(Source())
        val second = lifetime.retain(Source())
        first.close()
        lifetime.releaseReader()
        assertEquals(0, releases)
        second.close()
        assertEquals(1, releases)
    }

    @Test fun stoppingPlaybackLeavesTheOpenReaderUsable() {
        var releases = 0
        val lifetime = TtsSourceLifetime { releases++ }
        lifetime.retain(Source()).close()
        assertEquals(0, releases)
        lifetime.releaseReader()
        assertEquals(1, releases)
    }

    private class Source : TtsPageSource {
        var closes = 0
        var detaches = 0
        override suspend fun getPage(chapterIndex: Int, pageIndex: Int): TtsPageContent? = null
        override fun detachReader() { detaches++ }
        override fun close() { closes++ }
    }
}
