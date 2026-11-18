package com.huangder.lumibooks.data.sync

import android.app.Application
import com.huangder.lumibooks.domain.model.WebdavConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WebdavSyncChangesTest {
    private val server = MockWebServer()
    private lateinit var changes: WebdavSyncChanges
    private lateinit var config: WebdavConfig
    private val manifest = SyncManifest()

    @Before fun setup() {
        server.start()
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("webdav_change_baselines", 0).edit().clear().commit()
        changes = WebdavSyncChanges(context, WebdavClient())
        config = WebdavConfig(
            enabled = true, serverUrl = server.url("/").toString(),
            syncProfileAndSettings = false, syncLibraryOrganization = false, syncReadingData = false
        ).normalized()
    }

    @After fun close() { server.shutdown() }

    @Test fun `unchanged data only probes manifest and uses conditional requests`() = runBlocking {
        val key = changes.key(config)
        changes.record(key, "local", manifest, null)
        server.enqueue(MockResponse().setBody(manifest.toJson()).setHeader("ETag", "\"v1\""))
        assertTrue(changes.unchanged(key, "local", config, "password"))
        assertEquals("GET", server.takeRequest().method)
        // Simulate a fresh process: no state payload needs to be retained or downloaded again.
        changes = WebdavSyncChanges(RuntimeEnvironment.getApplication(), WebdavClient())
        server.enqueue(MockResponse().setResponseCode(304))
        assertTrue(changes.unchanged(key, "local", config, "password"))
        assertEquals("\"v1\"", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test fun `local changes require synchronization without a remote probe`() = runBlocking {
        val key = changes.key(config)
        changes.record(key, "before", manifest, null)
        assertFalse(changes.unchanged(key, "after", config, "password"))
        assertEquals(0, server.requestCount)
    }

    @Test fun `remote edit or deletion requires synchronization`() = runBlocking {
        val key = changes.key(config)
        changes.record(key, "local", manifest, null)
        val modified = manifest.copy(data = mapOf("book" to SyncFileEntry("book.json", "", 0, 20)))
        server.enqueue(MockResponse().setBody(modified.toJson()))
        assertFalse(changes.unchanged(key, "local", config, "password"))
        server.enqueue(MockResponse().setResponseCode(404))
        assertFalse(changes.unchanged(key, "local", config, "password"))
    }

    @Test fun `probe failure does not advance successful baseline`() = runBlocking {
        val key = changes.key(config)
        changes.record(key, "local", manifest, null)
        server.enqueue(MockResponse().setResponseCode(503))
        try {
            changes.unchanged(key, "local", config, "password")
            fail("Expected failure")
        } catch (expected: WebdavException) { assertEquals(503, expected.statusCode) }
        server.enqueue(MockResponse().setBody(manifest.toJson()))
        assertTrue(changes.unchanged(key, "local", config, "password"))
    }

    @Test fun `state changes are checked independently of manifest`() = runBlocking {
        config = config.copy(syncReadingData = true)
        val state = com.huangder.lumibooks.data.backup.PortableSnapshot(
            createdAt = 1, sourceDeviceId = "device", preferences = emptyList(), books = emptyList(),
            folders = emptyList(), bookFolderLinks = emptyList(), tags = emptyList(), bookTagLinks = emptyList(),
            readingRecords = emptyList(), bookmarks = emptyList(), notes = emptyList(),
            tombstones = emptyList(), assets = emptyList()
        )
        val key = changes.key(config)
        changes.record(key, "local", manifest, webdavStateFingerprint(state))
        server.enqueue(MockResponse().setBody(manifest.toJson()))
        server.enqueue(MockResponse().setBody(state.copy(createdAt = 999, sourceDeviceId = "other").toJson()))
        assertTrue(changes.unchanged(key, "local", config, "password"))
        server.enqueue(MockResponse().setBody(manifest.toJson()))
        server.enqueue(MockResponse().setResponseCode(404))
        assertFalse(changes.unchanged(key, "local", config, "password"))
    }

    @Test fun `destination and selection changes invalidate baseline but last sync time does not`() {
        assertEquals(changes.key(config), changes.key(config.copy(lastSyncTime = 100)))
        assertNotEquals(changes.key(config), changes.key(config.copy(syncPath = "Other")))
        assertNotEquals(changes.key(config), changes.key(config.copy(syncReadingData = true)))
    }

    @Test fun `manifest fingerprint ignores object key ordering`() {
        assertEquals(syncJsonFingerprint("{\"a\":1,\"b\":2}"), syncJsonFingerprint("{\"b\":2,\"a\":1}"))
    }
}
