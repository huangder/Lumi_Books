package com.huangder.lumibooks.benchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.dao.BookDao
import com.huangder.lumibooks.util.cache.ReaderCacheStore
import com.huangder.lumibooks.util.epub.EpubRenderMode
import com.huangder.lumibooks.ui.reader.ReaderPositionFlow
import com.huangder.lumibooks.ui.reader.ReaderPositionLocator
import com.huangder.lumibooks.ui.settings.BundledPolicy
import com.huangder.lumibooks.domain.model.ReaderPageTransition
import com.huangder.lumibooks.domain.model.BookshelfLayout
import com.huangder.lumibooks.domain.model.LiquidGlassSamplingQuality
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ReaderBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CLEAR_READER_CACHE) {
            ReaderCacheStore.get(context).clear()
        }
        if (intent.action == ACTION_PREPARE_APP) {
            val installMarker = context.packageManager
                .getPackageInfo(context.packageName, 0)
                .lastUpdateTime
            runBlocking {
                DataStoreManager(context).apply {
                    completeWelcomeFlow(installMarker)
                    saveWelcomeLanguageSetupCompleted()
                    acceptPolicyVersions(BundledPolicy.TERMS.version, BundledPolicy.PRIVACY.version)
                    // The bundled announcement would otherwise cover the first screen.
                    acknowledgeNotice(com.huangder.lumibooks.domain.model.EARLY_DONOR_NOTICE_ID)
                    saveStartupScreen(DataStoreManager.STARTUP_SCREEN_HOME)
                    saveEInkModeEnabled(false)
                    saveSplashEnabled(intent.getBooleanExtra("splash", false))
                }
            }
        }
        if (intent.action == ACTION_CONFIGURE_READER) {
            val title = intent.getStringExtra(EXTRA_BOOK_TITLE)?.takeIf { it in fixtureTitles } ?: return
            val transition = ReaderPageTransition.fromKey(intent.getStringExtra("transition"))
            val mode = EpubRenderMode.fromStorage(intent.getStringExtra("render_mode")) ?: return
            val resume = intent.getBooleanExtra("resume", false)
            val bookDao = EntryPointAccessors.fromApplication(
                context.applicationContext,
                BenchmarkDatabaseEntryPoint::class.java
            ).bookDao()
            resultCode = runBlocking {
                val book = bookDao.getAllBooks().first().singleOrNull { it.title == title }
                    ?: return@runBlocking 0
                DataStoreManager(context).apply {
                    savePageTransition(transition.key)
                    saveRenderMode(book.id, mode)
                    if (book.format == "EPUB") markEpubLayoutHintShown(book.id)
                }
                val locator = if (resume && book.format == "EPUB") {
                    ReaderPositionLocator(
                        chapterIndex = if (title == "Lumi EPUB 500") 250 else 12,
                        chapterFraction = 0.5f,
                        flow = if (transition == ReaderPageTransition.CONTINUOUS) {
                            ReaderPositionFlow.CONTINUOUS
                        } else ReaderPositionFlow.PAGED
                    )
                } else null
                bookDao.updateBook(book.copy(readingProgress = if (resume) 0.5f else 0f, locatorJson = locator?.toJson()))
                1
            }
        }
        if (intent.action == ACTION_PREPARE_BOOKSHELF) {
            val quality = LiquidGlassSamplingQuality.entries.singleOrNull {
                it.storedValue == intent.getStringExtra("glass_quality")
            } ?: return
            val bookDao = EntryPointAccessors.fromApplication(
                context.applicationContext,
                BenchmarkDatabaseEntryPoint::class.java
            ).bookDao()
            resultCode = runBlocking {
                val books = bookDao.getAllBooks().first()
                val fixture = books.singleOrNull { it.title == "lumi_txt_1mb" } ?: return@runBlocking 0
                val existingIds = books.mapTo(mutableSetOf()) { it.id }
                for (index in 1..80) {
                    val id = "lumi-benchmark-shelf-$index"
                    if (id in existingIds) continue
                    bookDao.insertBook(fixture.copy(
                        id = id,
                        title = "Lumi shelf fixture $index",
                        lastReadTime = 0L,
                        readingProgress = 0f,
                        locatorJson = null,
                        sourceUri = null,
                        sourceDocumentKey = null,
                        sourceSha256 = null
                    ))
                }
                DataStoreManager(context).apply {
                    saveAppTheme("liquid_glass")
                    saveLiquidGlassSamplingQuality(quality)
                    saveBookshelfLayoutMode(BookshelfLayout.Grid)
                }
                1
            }
        }
        if (intent.action == ACTION_RESET_READING_POSITION) {
            val title = intent.getStringExtra(EXTRA_BOOK_TITLE) ?: return
            val bookDao = EntryPointAccessors.fromApplication(
                context.applicationContext,
                BenchmarkDatabaseEntryPoint::class.java
            ).bookDao()
            runBlocking {
                bookDao.getAllBooks().first()
                    .filter { it.title == title || it.title.startsWith("$title (") }
                    .forEach { book ->
                        bookDao.updateBook(
                            book.copy(readingProgress = 0f, locatorJson = null)
                        )
                    }
            }
        }
    }

    companion object {
        const val ACTION_CLEAR_READER_CACHE =
            "com.huangder.lumibooks.benchmark.CLEAR_READER_CACHE"
        const val ACTION_PREPARE_APP =
            "com.huangder.lumibooks.benchmark.PREPARE_APP"
        const val ACTION_RESET_READING_POSITION =
            "com.huangder.lumibooks.benchmark.RESET_READING_POSITION"
        const val EXTRA_BOOK_TITLE = "book_title"
        private const val ACTION_CONFIGURE_READER = "com.huangder.lumibooks.benchmark.CONFIGURE_READER"
        private const val ACTION_PREPARE_BOOKSHELF = "com.huangder.lumibooks.benchmark.PREPARE_BOOKSHELF"
        private val fixtureTitles = setOf("Lumi EPUB 500", "Lumi EPUB 24", "lumi_txt_15mb", "lumi_txt_1mb")
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface BenchmarkDatabaseEntryPoint {
    fun bookDao(): BookDao
}
