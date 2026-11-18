package com.huangder.lumibooks.data.sync

import android.app.Application
import androidx.room.Room
import com.huangder.lumibooks.data.backup.PortableAsset
import com.huangder.lumibooks.data.backup.PortablePreference
import com.huangder.lumibooks.data.backup.PortableSnapshot
import com.huangder.lumibooks.data.backup.PortableSnapshotManager
import com.huangder.lumibooks.data.backup.assetReferenceIds
import com.huangder.lumibooks.data.backup.isReaderThemePreference
import com.huangder.lumibooks.data.backup.mapAssetReferences
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.domain.model.CustomFontPreset
import com.huangder.lumibooks.domain.model.ReaderBackgroundPreset
import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import com.huangder.lumibooks.domain.model.ReaderLayoutTarget
import com.huangder.lumibooks.domain.model.ReaderThemeSettings
import com.huangder.lumibooks.domain.model.ReaderThemeSuites
import com.huangder.lumibooks.domain.model.WebdavConfig
import com.huangder.lumibooks.mineru.MineruTokenStore
import com.huangder.lumibooks.translation.TranslationTokenStore
import com.huangder.lumibooks.tts.ExternalTtsTokenStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WebdavReaderThemeSyncTest {
    @Test
    fun `local change check ignores sync bookkeeping but tracks settings and asset content`() = runBlocking {
        withSync {
            val files = saveTheme()
            val before = sync.localFingerprint(config)
            prefs.updateWebdavLastSyncTime(12345)
            assertEquals(before, sync.localFingerprint(config))
            prefs.saveFontType("serif")
            assertNotEquals(before, sync.localFingerprint(config))
            val changedSettings = sync.localFingerprint(config)
            files.first().writeText("changed background content")
            assertNotEquals(changedSettings, sync.localFingerprint(config))
            assertEquals(0, assetReads.get())
        }
    }

    @Test
    fun `sync keeps nested theme assets across devices and repeated runs`() = runBlocking {
        withSync {
            val originals = saveTheme()
            val removed = PortableAsset("0".repeat(64), "book_cover", "deleted", "${"0".repeat(64)}.jpg", "0".repeat(64), 1)
            val pendingUpload = "$ASSET_PATH${"1".repeat(64)}.jpg"
            remote["$ASSET_PATH${removed.fileName}"] = byteArrayOf(0)
            remote[pendingUpload] = byteArrayOf(1)
            val emptyCloud = manager.capture(includeBookFiles = false, includePreferences = false).snapshot
            setCloud(emptyCloud.copy(assets = listOf(removed)))
            val first = sync.sync(config, "password")
            assertEquals(4, first.uploadedAssets)
            assertFalse(first.readerThemeDeferred)
            assertFalse(remote.containsKey("$ASSET_PATH${removed.fileName}"))
            assertTrue(remote.containsKey(pendingUpload))
            val state = cloud()
            assertEquals(4, state.readerThemeAssetsOrNull()?.size)
            assertTrue(state.preferences.any { "\\/\\/" in it.value && it.assetReferenceIds().isNotEmpty() })
            assertTrue(state.preferences.none { "processedValue" in it.value })
            assertThemeReadable()
            assertEquals(0, sync.sync(config, "password").uploadedAssets)

            prefs.clearAll()
            originals.forEach { check(it.delete()) }
            val restored = sync.sync(config, "password")
            assertEquals(4, restored.downloadedAssets)
            assertFalse(restored.readerThemeDeferred)
            assertThemeReadable()
            val repeated = sync.sync(config, "password")
            assertEquals(0, repeated.uploadedAssets)
            assertEquals(0, repeated.downloadedAssets)
            assertEquals(state.assets.toSet(), cloud().assets.toSet())
            assertTrue(state.assets.all { "$ASSET_PATH${it.fileName}" in remote.keys })
        }
    }

    @Test
    fun `old cloud paths and orphaned asset references repair from original files`() = runBlocking {
        withSync {
            saveTheme()
            val captured = manager.capture(includeBookFiles = false)
            val state = captured.snapshot
            val oldCatalog = state.preferences.single { it.key == "custom_reader_backgrounds" }
                .mapAssetReferences { id -> "asset://${id.take(64)}-${"b".repeat(16)}" }
            val rawByKey = captured.originalPreferences.associateBy { it.key }
            setCloud(state.copy(
                preferences = state.preferences.map { entry ->
                    when (entry.key) {
                        oldCatalog.key -> oldCatalog.copy(updatedAt = CLOUD_TIME)
                        "custom_fonts", "custom_font_path" -> rawByKey.getValue(entry.key).copy(updatedAt = CLOUD_TIME)
                        else -> entry
                    }
                },
                assets = emptyList()
            ))
            val recovered = sync.sync(config, "password")
            assertFalse(recovered.readerThemeDeferred)
            assertEquals(4, recovered.uploadedAssets)
            assertThemeReadable()
            assertEquals(4, cloud().readerThemeAssetsOrNull()?.size)

            val declared = cloud().assets.first()
            val expectedBytes = remote.remove("$ASSET_PATH${declared.fileName}")!!
            assertEquals(1, sync.sync(config, "password").uploadedAssets)
            assertArrayEquals(expectedBytes, remote["$ASSET_PATH${declared.fileName}"])
        }
    }

    @Test
    fun `missing cloud file defers only theme settings until the original is available`() = runBlocking {
        withSync {
            val originals = saveTheme()
            sync.sync(config, "password")
            val state = cloud()
            val missingAsset = state.assets.first { it.kind == "reader_background" }
            val missingPath = "$ASSET_PATH${missingAsset.fileName}"
            val bytes = remote.remove(missingPath)!!
            setCloud(state.copy(preferences = state.preferences.map { entry ->
                if (isReaderThemePreference(entry.key)) entry.copy(updatedAt = CLOUD_TIME) else entry
            } + PortablePreference("nickname", PortablePreference.TYPE_STRING, "From cloud", CLOUD_TIME, "remote")))
            prefs.clearAll()
            originals.forEach { check(it.delete()) }
            prefs.saveFontType("monospace")
            val before = cloud()
            val result = sync.sync(config, "password")
            assertTrue(result.readerThemeDeferred)
            assertEquals("monospace", prefs.fontType.first())
            assertEquals("From cloud", prefs.nickname.first())
            assertEquals(before.preferences.filter { isReaderThemePreference(it.key) },
                cloud().preferences.filter { isReaderThemePreference(it.key) })
            assertTrue(cloud().assets.any { it.id == missingAsset.id })

            remote[missingPath] = bytes
            assertFalse(sync.sync(config, "password").readerThemeDeferred)
            assertThemeReadable()
        }
    }

    @Test
    fun `settings disabled skips themes even when cloud references a missing file`() = runBlocking {
        withSync {
            saveTheme()
            sync.sync(config, "password")
            remote.remove("$ASSET_PATH${cloud().assets.first().fileName}")
            assetReads.set(0)
            val before = cloud()
            val disabled = config.copy(syncProfileAndSettings = false, syncLibraryOrganization = true)
            assertFalse(sync.sync(disabled, "password").readerThemeDeferred)
            assertEquals(0, assetReads.get())
            assertEquals(before.preferences, cloud().preferences)
            assertThemeReadable()
        }
    }

    @Test
    fun `sync keeps a theme edited after snapshot capture`() = runBlocking {
        withSync {
            saveTheme()
            sync.sync(config, "password")
            val state = cloud()
            setCloud(state.copy(preferences = state.preferences.map { entry ->
                if (isReaderThemePreference(entry.key)) entry.copy(updatedAt = CLOUD_TIME) else entry
            } + PortablePreference("nickname", PortablePreference.TYPE_STRING, "Updated", CLOUD_TIME, "remote")))
            beforeApply = {
                prefs.updateReaderThemeSuite(
                    ReaderThemeSuites.DAY_ID, ReaderLayoutTarget.READER_LAYOUT,
                    ReaderThemeSettings(fontType = "serif", fontSize = 22f)
                )
            }
            assertFalse(sync.sync(config, "password").readerThemeDeferred)
            val day = prefs.readerThemeSuiteState.first().suites.single { it.id == ReaderThemeSuites.DAY_ID }
            assertEquals("serif", day.settings.fontType)
            assertEquals(22f, day.settings.fontSize, 0f)
            assertEquals("Updated", prefs.nickname.first())
        }
    }

    private suspend fun withSync(test: suspend Fixture.() -> Unit) {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            MockWebServer().use { server ->
                val fixture = Fixture(context, db, server)
                server.start()
                fixture.prefs.clearAll()
                fixture.test()
            }
        } finally {
            db.close()
        }
    }

    private class Fixture(context: Application, db: AppDatabase, server: MockWebServer) {
        private val root = context.filesDir
        val prefs = DataStoreManager(context)
        var beforeApply: suspend () -> Unit = {}
        val manager = PortableSnapshotManager(
            context, db, prefs, SyncIdentityStore(db.syncStateDao()), MineruTokenStore(context),
            ExternalTtsTokenStore(context), TranslationTokenStore(context), beforeApply = { beforeApply() }
        )
        val sync = WebdavPortableStateSync(context, WebdavClient(), manager)
        val remote = ConcurrentHashMap<String, ByteArray>()
        val assetReads = AtomicInteger()
        val config get() = WebdavConfig(
            enabled = true, serverUrl = serverUrl(), username = "test",
            syncBookFiles = false, syncLibraryOrganization = false, syncReadingData = false
        )
        private val serverUrl = { server.url("/").toString() }

        init {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    if (path.startsWith(ASSET_PATH) && request.method in setOf("GET", "HEAD")) {
                        assetReads.incrementAndGet()
                    }
                    return when (request.method) {
                        "MKCOL" -> MockResponse().setResponseCode(201)
                        "GET" -> remote[path]?.let { MockResponse().setBody(Buffer().write(it)) }
                            ?: MockResponse().setResponseCode(404)
                        "HEAD" -> MockResponse().setResponseCode(if (remote.containsKey(path)) 200 else 404)
                        "PUT" -> {
                            remote[path] = request.body.readByteArray()
                            MockResponse().setResponseCode(201)
                        }
                        "PROPFIND" -> MockResponse().setResponseCode(207).setBody(assetListing())
                        "DELETE" -> {
                            remote.remove(path)
                            MockResponse().setResponseCode(204)
                        }
                        else -> MockResponse().setResponseCode(405)
                    }
                }
            }
        }

        private fun assetListing(): String = remote.keys.filter { it.startsWith(ASSET_PATH) }
            .joinToString(separator = "", prefix = "<d:multistatus xmlns:d=\"DAV:\">", postfix = "</d:multistatus>") {
                "<d:response><d:href>$it</d:href><d:propstat><d:prop><d:resourcetype/>" +
                    "</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
            }

        fun cloud(): PortableSnapshot = PortableSnapshot.fromJson(remote.getValue(STATE_PATH).decodeToString())

        fun setCloud(snapshot: PortableSnapshot) {
            remote[STATE_PATH] = snapshot.toJson().encodeToByteArray()
        }

        suspend fun saveTheme(): List<File> {
            val lightImage = file("reader_backgrounds/light.image", "light image")
            val darkImage = file("theme_imports/zip/background_dark.jpg", "dark image")
            val lightFont = file("fonts/custom_light.ttf", "light font")
            val darkFont = file("theme_imports/zip/font_dark.otf", "dark font")
            val blur = file("reader_backgrounds/light.blurred-10.jpg", "local blur")
            prefs.saveCustomReaderBackgrounds(listOf(
                ReaderBackgroundPreset("light", ReaderBackgroundType.IMAGE, lightImage.path,
                    processedValue = blur.path, processedBlurDp = 10f),
                ReaderBackgroundPreset("dark", ReaderBackgroundType.IMAGE, darkImage.path)
            ))
            prefs.saveCustomFonts(listOf(CustomFontPreset("light", lightFont.path), CustomFontPreset("dark", darkFont.path)))
            val light = ReaderThemeSettings(backgroundSelection = "custom:light", fontType = "custom:light")
            val dark = ReaderThemeSettings(backgroundSelection = "custom:dark", fontType = "custom:dark")
            val suites = ReaderThemeSuites.defaults().map {
                if (it.id == ReaderThemeSuites.DAY_ID) it.copy(settings = light, lightSettings = light, darkSettings = dark)
                else it
            }
            prefs.saveReaderThemeSuiteState(suites, ReaderThemeSuites.DAY_ID, applyActiveSuite = true)
            return listOf(lightImage, darkImage, lightFont, darkFont, blur)
        }

        suspend fun assertThemeReadable() {
            val backgrounds = prefs.customReaderBackgrounds.first().associateBy { it.id }
            val fonts = prefs.customFonts.first().associateBy { it.id }
            for (mode in listOf("light", "dark")) {
                assertEquals("$mode image", File(backgrounds.getValue(mode).value).readText())
                assertEquals("$mode font", File(fonts.getValue(mode).path).readText())
            }
            val day = prefs.readerThemeSuiteState.first().suites.single { it.id == ReaderThemeSuites.DAY_ID }
            assertEquals("custom:light", day.settings.backgroundSelection)
            assertEquals("custom:dark", day.darkSettings?.backgroundSelection)
            assertEquals("custom:light", day.settings.fontType)
            assertEquals("custom:dark", day.darkSettings?.fontType)
        }

        private fun file(path: String, body: String) = File(root, path).apply {
            parentFile?.mkdirs()
            writeText(body)
        }
    }

    companion object {
        private const val STATE_PATH = "/LumiBooks/state-v1/state.json"
        private const val ASSET_PATH = "/LumiBooks/state-v1/assets/"
        private const val CLOUD_TIME = Long.MAX_VALUE - 1
    }
}
