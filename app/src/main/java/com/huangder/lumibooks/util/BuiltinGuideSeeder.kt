package com.huangder.lumibooks.util

import android.content.Context
import android.util.Log
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.repository.FolderRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Installs the bundled user guides as ordinary books exactly once per content version. */
@Singleton
class BuiltinGuideSeeder @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val dataStoreManager: DataStoreManager,
    private val bookRepository: BookRepository,
    private val folderRepository: FolderRepository
) {
    private val mutex = Mutex()

    suspend fun seed() {
        mutex.withLock {
            if (dataStoreManager.builtinGuidesSeededVersion.first() >= CONTENT_VERSION) return@withLock

            runCatching {
                val folder = folderRepository.getOrCreateRootFolder(FOLDER_NAME)
                val existingBooks = bookRepository.getAllBooks().first().associateBy { it.id }
                val booksDirectory = File(FileUtils.getBooksDirectory(context), FOLDER_NAME).apply { mkdirs() }
                val now = System.currentTimeMillis()

                GUIDE_MANIFEST.forEach { guide ->
                    val destination = File(booksDirectory, guide.fileName)
                    if (!destination.exists() || destination.length() == 0L) {
                        context.assets.open(guide.assetPath).use { input ->
                            destination.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    check(destination.length() > 0L) { "Bundled guide is empty: ${guide.assetPath}" }

                    if (existingBooks[guide.bookId] == null) {
                        bookRepository.insertBook(
                            Book(
                                id = guide.bookId,
                                title = guide.title,
                                author = "Lumi",
                                filePath = destination.absolutePath,
                                coverPath = null,
                                format = BookFormat.EPUB,
                                lastReadTime = 0L,
                                readingProgress = 0f,
                                createdAt = now
                            )
                        )
                    }
                    folderRepository.moveBooks(setOf(guide.bookId), folder.id)
                }
                dataStoreManager.markBuiltinGuidesSeeded(CONTENT_VERSION)
                Log.i(TAG, "Installed ${GUIDE_MANIFEST.size} bundled Lumi guides")
            }.onFailure { error ->
                Log.w(TAG, "Bundled guide installation deferred", error)
            }
        }
    }

    data class GuideManifest(
        val language: String,
        val title: String,
        val fileName: String,
        val assetPath: String,
        val bookId: String = "builtin-guide-$language"
    )

    companion object {
        private const val TAG = "BuiltinGuideSeeder"
        const val CONTENT_VERSION = 1
        const val FOLDER_NAME = "lumi"

        val GUIDE_MANIFEST = listOf(
            GuideManifest("zh-CN", "Lumi 使用教程（简体中文）", "guide_zh-CN.epub", "builtin/lumi/guide_zh-CN.epub"),
            GuideManifest("zh-TW", "Lumi 使用教學（繁體中文・中國台灣）", "guide_zh-TW.epub", "builtin/lumi/guide_zh-TW.epub"),
            GuideManifest("zh-HK", "Lumi 使用教學（繁體中文・中國香港）", "guide_zh-HK.epub", "builtin/lumi/guide_zh-HK.epub"),
            GuideManifest("zh-MO", "Lumi 使用教學（繁體中文・中國澳門）", "guide_zh-MO.epub", "builtin/lumi/guide_zh-MO.epub"),
            GuideManifest("en", "Lumi User Guide", "guide_en.epub", "builtin/lumi/guide_en.epub"),
            GuideManifest("ja", "Lumi 使い方ガイド", "guide_ja.epub", "builtin/lumi/guide_ja.epub"),
            GuideManifest("ko", "Lumi 사용 안내서", "guide_ko.epub", "builtin/lumi/guide_ko.epub")
        )
    }
}
