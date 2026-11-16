package com.huangder.lumibooks.data.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WebdavSyncLogStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun keepsOnlyTheThreeMostRecentRunsNewestFirst() = runBlocking {
        val store = WebdavSyncLogStore(temp.newFolder("logs"))
        for (i in 1..4) {
            store.beginRun(if (i % 2 == 0) WebdavSyncTrigger.AUTO else WebdavSyncTrigger.MANUAL, now = i * 1000L)
            store.log(WebdavLogLevel.INFO, "run $i", now = i * 1000L + 1)
            store.endRun(success = i != 3, now = i * 1000L + 2)
        }

        val runs = store.recentRuns()

        assertEquals(listOf(4000L, 3000L, 2000L), runs.map { it.startedAt })
        assertEquals(WebdavSyncTrigger.AUTO, runs[0].trigger)
        assertEquals(false, runs[1].success)
        assertEquals("run 2", runs[2].entries.single().message)
        assertEquals(3, temp.root.resolve("logs").listFiles()!!.size)
    }

    @Test
    fun unfinishedRunHasNoResult() = runBlocking {
        val store = WebdavSyncLogStore(temp.newFolder("logs"))
        store.beginRun(WebdavSyncTrigger.MANUAL, now = 10L)
        store.log(WebdavLogLevel.ERROR, "boom", now = 11L)

        val run = store.recentRuns().single()

        assertNull(run.success)
        assertNull(run.finishedAt)
        assertEquals(WebdavLogLevel.ERROR, run.entries.single().level)
    }

    @Test
    fun entriesOutsideARunAreIgnored() = runBlocking {
        val store = WebdavSyncLogStore(temp.newFolder("logs"))
        store.log(WebdavLogLevel.INFO, "orphan")
        assertTrue(store.recentRuns().isEmpty())
    }

    @Test
    fun sanitizerStripsCredentialsButKeepsHost() {
        val cleaned = sanitizeWebdavLogMessage(
            "PUT https://alice:s3cret@dav.example.com/books/a.epub failed password=hunter2 Authorization: Basic YWxpY2U6czNjcmV0"
        )
        assertTrue(cleaned.contains("https://dav.example.com/books/a.epub"))
        assertFalse(cleaned.contains("s3cret"))
        assertFalse(cleaned.contains("hunter2"))
        assertFalse(cleaned.contains("YWxpY2U6czNjcmV0"))
    }

    @Test
    fun placementClassification() {
        assertEquals(WebdavBookPlacement.BOTH, webdavBookPlacement(hasLocalFile = true, hasRemoteFile = true))
        assertEquals(WebdavBookPlacement.LOCAL_ONLY, webdavBookPlacement(hasLocalFile = true, hasRemoteFile = false))
        assertEquals(WebdavBookPlacement.REMOTE_ONLY, webdavBookPlacement(hasLocalFile = false, hasRemoteFile = true))
        assertEquals(WebdavBookPlacement.MISSING, webdavBookPlacement(hasLocalFile = false, hasRemoteFile = false))
    }
}
