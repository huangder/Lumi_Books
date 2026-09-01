package com.huangder.lumibooks.util

import android.content.Context
import android.util.Log
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.FolderNameValidator
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
            val seededVersion = dataStoreManager.builtinGuidesSeededVersion.first()
            if (seededVersion >= CONTENT_VERSION) {
                installDefaultFolderCoverForExistingGuides()
                return@withLock
            }

            runCatching {
                val folder = folderRepository.getOrCreateRootFolder(FOLDER_NAME)
                val existingBooks = bookRepository.getAllBooks().first().associateBy { it.id }
                val booksDirectory = File(FileUtils.getBooksDirectory(context), FOLDER_NAME).apply { mkdirs() }
                val now = System.currentTimeMillis()

                installDefaultFolderCover(folder)

                GUIDE_MANIFEST.forEach { guide ->
                    val destination = File(booksDirectory, guide.fileName)
                    if (seededVersion < guide.contentVersion || !destination.exists() || destination.length() == 0L) {
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

    private suspend fun installDefaultFolderCoverForExistingGuides() {
        if (dataStoreManager.builtinGuidesFolderCoverVersion.first() >= FOLDER_COVER_VERSION) return
        val folder = folderRepository.getAllFolders().first().firstOrNull { candidate ->
            candidate.parentId == null &&
                FolderNameValidator.normalized(candidate.name) == FolderNameValidator.normalized(FOLDER_NAME)
        }
        if (folder != null) {
            installDefaultFolderCover(folder)
        } else {
            // A user may have removed the guide folder. Do not recreate it just to restore a cover.
            dataStoreManager.markBuiltinGuidesFolderCoverSeeded(FOLDER_COVER_VERSION)
        }
    }

    private suspend fun installDefaultFolderCover(folder: com.huangder.lumibooks.domain.model.LibraryFolder) {
        if (folder.coverPath == null) {
            val coverPath = copyDefaultFolderCover(folder.id)
            check(folderRepository.updateFolderCover(folder.id, coverPath)) {
                "Unable to save bundled guide folder cover"
            }
        }
        dataStoreManager.markBuiltinGuidesFolderCoverSeeded(FOLDER_COVER_VERSION)
    }

    private fun copyDefaultFolderCover(folderId: String): String {
        val destination = File(
            FileUtils.getCoversDirectory(context),
            "folder_custom_${folderId}_builtin_lumi.png"
        )
        if (!destination.exists() || destination.length() == 0L) {
            context.assets.open(FOLDER_COVER_ASSET_PATH).use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        }
        check(destination.length() > 0L) { "Bundled guide folder cover is empty" }
        return destination.absolutePath
    }

    data class GuideManifest(
        val language: String,
        val title: String,
        val fileName: String,
        val assetPath: String,
        val bookId: String = "builtin-guide-$language",
        val contentVersion: Int = 1
    )

    companion object {
        private const val TAG = "BuiltinGuideSeeder"
        const val CONTENT_VERSION = 2
        const val FOLDER_COVER_VERSION = 1
        const val FOLDER_NAME = "lumi"
        const val FOLDER_COVER_ASSET_PATH = "builtin/lumi/folder_cover.png"

        val GUIDE_MANIFEST = listOf(
            GuideManifest("zh-CN", "Lumi 使用教程（简体中文）", "guide_zh-CN.epub", "builtin/lumi/guide_zh-CN.epub", contentVersion = 2),
            GuideManifest("zh-TW", "Lumi 使用教學（繁體中文・中國台灣）", "guide_zh-TW.epub", "builtin/lumi/guide_zh-TW.epub"),
            GuideManifest("zh-HK", "Lumi 使用教學（繁體中文・中國香港）", "guide_zh-HK.epub", "builtin/lumi/guide_zh-HK.epub"),
            GuideManifest("zh-MO", "Lumi 使用教學（繁體中文・中國澳門）", "guide_zh-MO.epub", "builtin/lumi/guide_zh-MO.epub"),
            GuideManifest("en", "Lumi User Guide", "guide_en.epub", "builtin/lumi/guide_en.epub"),
            GuideManifest("ja", "Lumi 使い方ガイド", "guide_ja.epub", "builtin/lumi/guide_ja.epub"),
            GuideManifest("ko", "Lumi 사용 안내서", "guide_ko.epub", "builtin/lumi/guide_ko.epub")
        )
    }
}
