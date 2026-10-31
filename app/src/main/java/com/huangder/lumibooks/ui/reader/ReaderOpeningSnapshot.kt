package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalView
import com.huangder.lumibooks.data.local.ReaderPreferencesSnapshot
import com.huangder.lumibooks.domain.model.Book
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Window traits that decide how a settled page is drawn. A saved page only stands in for the
 * reader when the next opening happens in the same window, theme and app font.
 */
internal data class ReaderOpeningDisplay(
    val width: Int,
    val height: Int,
    val density: Float,
    val fontScale: Float,
    val locale: String,
    val appIsDark: Boolean,
    val globalFontMode: String
)
internal fun readerOpeningDisplay(
    view: View,
    density: Float,
    fontScale: Float,
    appIsDark: Boolean,
    globalFontMode: String
): ReaderOpeningDisplay? {
    if (view.width <= 0 || view.height <= 0) return null
    return ReaderOpeningDisplay(
        width = view.width,
        height = view.height,
        density = density,
        fontScale = fontScale,
        locale = view.resources.configuration.locales[0]?.toLanguageTag().orEmpty(),
        appIsDark = appIsDark,
        globalFontMode = globalFontMode
    )
}

/** Brightness and one-time hints change while reading without changing the page pixels. */
internal fun ReaderPreferencesSnapshot.openingSnapshotFingerprint(): String = copy(
    brightness = 0f,
    txtEncodingHintShown = false,
    epubLayoutHintShown = false,
    mobiLayoutHintShown = false,
    readerFirstOpenHintAcknowledgementCount = 0
).toString()

/** Everything that must still match for a stored page to replace the opening placeholder. */
internal fun readerOpeningSnapshotKey(
    book: Book,
    preferencesFingerprint: String,
    display: ReaderOpeningDisplay,
    appStamp: Long
): String {
    val source = listOf(
        OPENING_SNAPSHOT_VERSION, appStamp, book.id, book.format.name, book.filePath,
        book.sourceLastModified, book.readingProgress, book.locatorJson, preferencesFingerprint, display
    ).joinToString("\u0001")
    return MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

private const val OPENING_SNAPSHOT_VERSION = 1

/**
 * Holds the reader's newest settled page until the reader leaves. A page is handed out only
 * if the reader still shows the same identity; anything else would make the next opening jump.
 */
internal class ReaderOpeningSnapshotSink {
    private var page: Bitmap? = null
    private var pageIdentity: Any? = null
    private var closed = false
    var currentIdentity: Any? = null

    fun hasPage(identity: Any): Boolean = page != null && pageIdentity == identity

    fun offer(identity: Any, bitmap: Bitmap) {
        if (closed || identity != currentIdentity) {
            bitmap.recycle()
            return
        }
        page?.takeIf { it !== bitmap }?.recycle()
        page = bitmap
        pageIdentity = identity
    }

    /** Ownership of the returned bitmap moves to the caller; later offers are discarded. */
    fun takeMatching(): Bitmap? {
        closed = true
        val result = page?.takeIf { pageIdentity != null && pageIdentity == currentIdentity }
        if (result == null) page?.recycle()
        page = null
        pageIdentity = null
        return result
    }

    fun release() {
        closed = true
        page?.recycle()
        page = null
        pageIdentity = null
    }
}

internal val LocalReaderOpeningSnapshotSink = staticCompositionLocalOf<ReaderOpeningSnapshotSink?> { null }

private const val OPENING_SNAPSHOT_SETTLE_MS = 600L

/**
 * Copies the window once a page has stayed unobstructed for a moment. Quick flips never wait
 * long enough to pay for a copy, and an overlay or a page change cancels the pending one.
 */
@Composable
internal fun ReaderOpeningSnapshotCapture(enabled: Boolean, identity: Any) {
    val sink = LocalReaderOpeningSnapshotSink.current ?: return
    val view = LocalView.current
    SideEffect { sink.currentIdentity = identity }
    LaunchedEffect(sink, identity, enabled) {
        if (!enabled || sink.hasPage(identity)) return@LaunchedEffect
        delay(OPENING_SNAPSHOT_SETTLE_MS)
        withFrameNanos { }
        val bitmap = captureReaderWindow(view) ?: return@LaunchedEffect
        sink.offer(identity, bitmap)
    }
}

private suspend fun captureReaderWindow(view: View): Bitmap? {
    val window = view.context.findReaderActivity()?.window ?: return null
    if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return null
    val origin = IntArray(2).also(view::getLocationInWindow)
    val rect = Rect(origin[0], origin[1], origin[0] + view.width, origin[1] + view.height)
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    return suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(window, rect, bitmap, { result ->
                if (result == PixelCopy.SUCCESS && continuation.isActive) {
                    continuation.resume(bitmap)
                } else {
                    bitmap.recycle()
                    if (continuation.isActive) continuation.resume(null)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (error: IllegalArgumentException) {
            bitmap.recycle()
            continuation.resume(null)
        }
    }
}

private tailrec fun Context.findReaderActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.findReaderActivity()
    else -> null
}

/**
 * Remembers the page each recently closed book showed, so the next hero opening can grow into
 * it. The newest page stays decoded in memory; older ones are read back from the cache. The key
 * includes the install time, so a page drawn by another build is never reused.
 */
@Singleton
class ReaderOpeningSnapshotStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private class Entry(val bookId: String, val key: String, val page: Bitmap)

    @Volatile private var recent: Entry? = null
    private val writes = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val directory: File get() = File(context.cacheDir, DIRECTORY_NAME)
    private val appStamp: Long by lazy {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }
            .getOrDefault(0L)
    }

    /** Call off the main thread: the first call reads the package's install time. */
    internal fun keyFor(book: Book, preferences: ReaderPreferencesSnapshot, display: ReaderOpeningDisplay): String =
        readerOpeningSnapshotKey(book, preferences.openingSnapshotFingerprint(), display, appStamp)

    fun cached(bookId: String, key: String): Bitmap? =
        recent?.takeIf { it.bookId == bookId && it.key == key }?.page

    suspend fun load(bookId: String, key: String): Bitmap? {
        cached(bookId, key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val stem = fileStem(bookId)
            val pageFile = File(directory, "$stem.png")
            val stored = runCatching { File(directory, "$stem.key").readText() }.getOrNull()
            if (stored != key || !pageFile.isFile) return@withContext null
            runCatching { decodeHardware(pageFile) }.getOrNull()?.also { page ->
                pageFile.setLastModified(System.currentTimeMillis())
                recent = Entry(bookId, key, page)
            }
        }
    }

    /** Takes ownership of [page]. The software pixels are released once they are on disk. */
    fun save(bookId: String, key: String, page: Bitmap) {
        scope.launch {
            try {
                runCatching { page.copy(Bitmap.Config.HARDWARE, false) }.getOrNull()
                    ?.let { recent = Entry(bookId, key, it) }
                writes.withLock {
                    val target = directory.apply { mkdirs() }
                    val stem = fileStem(bookId)
                    val keyFile = File(target, "$stem.key")
                    val pageFile = File(target, "$stem.png")
                    val temporary = File(target, "$stem.png.tmp")
                    keyFile.delete()
                    temporary.outputStream().buffered().use { page.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    if (!temporary.renameTo(pageFile)) {
                        pageFile.delete()
                        if (!temporary.renameTo(pageFile)) return@withLock
                    }
                    keyFile.writeText(key)
                    trim(target)
                }
            } catch (error: Exception) {
                android.util.Log.w(TAG, "Opening page was not saved", error)
            } finally {
                page.recycle()
            }
        }
    }

    /** Hidden and deleted books must not leave a readable page behind. */
    fun delete(bookIds: Collection<String>) {
        if (bookIds.isEmpty()) return
        if (recent?.bookId?.let(bookIds::contains) == true) recent = null
        scope.launch {
            writes.withLock {
                bookIds.forEach { id ->
                    val stem = fileStem(id)
                    File(directory, "$stem.png").delete()
                    File(directory, "$stem.key").delete()
                }
            }
        }
    }

    private fun trim(target: File) {
        val pages = target.listFiles { file -> file.extension == "png" }.orEmpty()
            .sortedByDescending(File::lastModified)
        var total = 0L
        pages.forEachIndexed { index, file ->
            total += file.length()
            if (index >= MAX_PAGES || total > MAX_BYTES) {
                file.delete()
                File(target, file.nameWithoutExtension + ".key").delete()
            }
        }
    }

    private fun fileStem(bookId: String): String =
        MessageDigest.getInstance("SHA-1").digest(bookId.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun decodeHardware(file: File): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
            }
        } else {
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.HARDWARE
            })
        }

    private companion object {
        const val TAG = "ReaderOpeningPage"
        const val DIRECTORY_NAME = "reader_opening_pages"
        const val MAX_PAGES = 24
        const val MAX_BYTES = 64L * 1024 * 1024
    }
}
