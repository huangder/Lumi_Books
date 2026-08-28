package com.huangder.lumibooks.benchmark

import android.content.ContentValues
import android.content.Intent
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

internal object BenchmarkBooks {
    const val PACKAGE_NAME = "com.huangder.lumibooks"

    data class Fixture(val asset: String, val title: String, val mimeType: String)

    val txtSmall = Fixture("lumi_txt_1mb.txt", "lumi_txt_1mb", "text/plain")
    val txtLarge = Fixture("lumi_txt_15mb.txt", "lumi_txt_15mb", "text/plain")
    val epubRegular = Fixture("lumi_epub_regular.epub", "Lumi EPUB 24", "application/epub+zip")
    val epubLarge = Fixture("lumi_epub_500.epub", "Lumi EPUB 500", "application/epub+zip")

    val all = listOf(txtSmall, txtLarge, epubRegular, epubLarge)

    fun prepareApp(device: UiDevice) {
        wakeAndDismissKeyguard(device)
        device.executeShellCommand(
            "am broadcast -a com.huangder.lumibooks.benchmark.PREPARE_APP " +
                "-p $PACKAGE_NAME"
        )
        ensureAppForeground(device)
    }

    fun verifyHomeReadyWithoutInput(device: UiDevice) {
        ensureAppForeground(device)
        device.waitForIdle(5_000)
        requireHomeForeground(device)
    }

    fun ensureImported(device: UiDevice, fixture: Fixture) {
        showLibrary(device)
        if (findBook(device, fixture) != null) return

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fixture.asset)
            put(MediaStore.MediaColumns.MIME_TYPE, fixture.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/LumiBenchmark")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        resolver.openOutputStream(uri)!!.use { output ->
            instrumentation.context.assets.open(fixture.asset).use { input -> input.copyTo(output) }
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        instrumentation.context.startActivity(
            Intent(Intent.ACTION_VIEW, uri)
                .setDataAndType(uri, fixture.mimeType)
                .setPackage(PACKAGE_NAME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), 30_000)
        device.waitForIdle(30_000)
        showLibrary(device)
        check(findBook(device, fixture) != null) {
            "Fixture was not imported: ${fixture.asset}"
        }
    }

    fun openFromBookshelf(
        device: UiDevice,
        fixture: Fixture,
        resetPosition: Boolean = false
    ) {
        showLibrary(device)
        if (resetPosition) resetReadingPosition(device, fixture)
        val book = findBook(device, fixture)
            ?: error("Book is not visible: ${fixture.title}")
        requireBookshelfForeground(device)
        book.click()
        device.waitForIdle()
    }

    fun clearReaderCache(device: UiDevice) {
        device.executeShellCommand(
            "am broadcast -a com.huangder.lumibooks.benchmark.CLEAR_READER_CACHE " +
                "-p $PACKAGE_NAME"
        )
    }

    fun showBookshelf(device: UiDevice) {
        showLibrary(device)
    }

    fun scrollHome(device: UiDevice) {
        swipeWithinTaggedSurface(device, HOME_SCREEN_TAG, upward = true)
        device.waitForIdle(2_000)
        requireHomeForeground(device)
    }

    fun scrollBookshelf(device: UiDevice) {
        swipeWithinTaggedSurface(device, BOOKSHELF_SCREEN_TAG, upward = true)
        device.waitForIdle(2_000)
        requireBookshelfForeground(device)
    }

    fun closeReader(device: UiDevice, fixture: Fixture) {
        requireAppForeground(device)
        check(
            device.hasObject(By.res(READER_CONTENT_READY_TAG)) ||
                device.hasObject(By.res(READER_CONTENT_LOADING_TAG))
        ) { "Refusing to navigate back outside the Lumi reader" }
        device.pressBack()
        dismissBlockingDialogs(device)
        showLibrary(device)
        check(findBook(device, fixture) != null) {
            "Bookshelf did not reappear after closing ${fixture.asset}"
        }
    }

    fun awaitReaderSettled(device: UiDevice, timeoutMs: Long = 10_000) {
        check(
            device.wait(
                Until.hasObject(By.res(READER_CONTENT_READY_TAG)),
                timeoutMs
            )
        ) { "Reader content did not draw within ${timeoutMs}ms" }
        device.waitForIdle(timeoutMs)
        // The marker is published after the measured first frame. Allow the retained cover
        // transition to finish so book_open_to_interactive is closed in the same trace.
        Thread.sleep(500)
    }

    fun turnNextPage(device: UiDevice) {
        val reader = requireTaggedSurface(
            device,
            READER_CONTENT_READY_TAG,
            "Refusing to turn a page outside the ready reader"
        )
        val bounds = reader.visibleBounds
        check(bounds.width() > MIN_INPUT_SURFACE_PX && bounds.height() > MIN_INPUT_SURFACE_PX) {
            "Reader input surface is not safely visible: $bounds"
        }
        device.click(bounds.left + bounds.width() * 4 / 5, bounds.centerY())
        device.waitForIdle(2_000)
        requireAppForeground(device)
    }

    private fun showLibrary(device: UiDevice) {
        ensureAppForeground(device)
        device.waitForIdle(5_000)
        dismissBlockingDialogs(device)
        if (device.hasObject(By.res(BOOKSHELF_SCREEN_TAG))) return

        repeat(3) {
            requireAppForeground(device)
            if (device.hasObject(By.res(BOOKSHELF_SCREEN_TAG))) return
            val bookshelf = device.findObject(By.res(BOOKSHELF_TAB_TAG))
            if (bookshelf != null) {
                requireAppForeground(device)
                bookshelf.click()
            } else if (
                device.hasObject(By.res(READER_CONTENT_READY_TAG)) ||
                device.hasObject(By.res(READER_CONTENT_LOADING_TAG))
            ) {
                requireAppForeground(device)
                device.pressBack()
            }
            device.waitForIdle(5_000)
            dismissBlockingDialogs(device)
            if (device.hasObject(By.res(BOOKSHELF_SCREEN_TAG))) return
        }

        error("Bookshelf screen did not appear through the tagged Lumi navigation item")
    }

    private fun dismissBlockingDialogs(device: UiDevice) {
        repeat(3) {
            val confirmation = CONFIRM_LABELS.firstNotNullOfOrNull { label ->
                device.findObject(By.text(label))
            } ?: return
            requireAppForeground(device)
            confirmation.click()
            device.waitForIdle(5_000)
        }
    }

    private fun findBook(device: UiDevice, fixture: Fixture): UiObject2? {
        requireBookshelfForeground(device)
        device.findObject(By.textContains(fixture.title))?.let { return it }
        repeat(6) {
            swipeWithinTaggedSurface(device, BOOKSHELF_SCREEN_TAG, upward = true)
            device.waitForIdle(1_000)
            requireBookshelfForeground(device)
            device.findObject(By.textContains(fixture.title))?.let { return it }
        }
        repeat(6) {
            swipeWithinTaggedSurface(device, BOOKSHELF_SCREEN_TAG, upward = false)
            device.waitForIdle(1_000)
            requireBookshelfForeground(device)
            device.findObject(By.textContains(fixture.title))?.let { return it }
        }
        return null
    }

    private fun swipeWithinTaggedSurface(device: UiDevice, tag: String, upward: Boolean) {
        val surface = requireTaggedSurface(
            device,
            tag,
            "Refusing to swipe without the expected Lumi surface: $tag"
        )
        val bounds = surface.visibleBounds
        check(bounds.width() > MIN_INPUT_SURFACE_PX && bounds.height() > MIN_INPUT_SURFACE_PX) {
            "Lumi input surface is not safely visible: tag=$tag bounds=$bounds"
        }
        val x = bounds.centerX()
        val upperY = bounds.top + bounds.height() / 4
        val lowerY = bounds.bottom - bounds.height() / 4
        val startY = if (upward) lowerY else upperY
        val endY = if (upward) upperY else lowerY
        device.swipe(x, startY, x, endY, 12)
    }

    private fun requireTaggedSurface(
        device: UiDevice,
        tag: String,
        errorMessage: String
    ): UiObject2 {
        requireAppForeground(device)
        return device.findObject(By.res(tag)) ?: error(errorMessage)
    }

    fun requireAppForeground(device: UiDevice) {
        check(device.currentPackageName == PACKAGE_NAME) {
            "Refusing to send input outside Lumi; foreground=${device.currentPackageName}"
        }
    }

    private fun requireBookshelfForeground(device: UiDevice) {
        requireAppForeground(device)
        check(device.hasObject(By.res(BOOKSHELF_SCREEN_TAG))) {
            "Refusing to scroll outside the Lumi bookshelf"
        }
    }

    private fun requireHomeForeground(device: UiDevice) {
        requireAppForeground(device)
        check(device.hasObject(By.res(HOME_SCREEN_TAG))) {
            "Refusing to scroll outside the Lumi home screen"
        }
    }

    private fun ensureAppForeground(device: UiDevice) {
        wakeAndDismissKeyguard(device)
        if (device.currentPackageName == PACKAGE_NAME && hasKnownAppSurface(device)) return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val launchIntent = Intent().setClassName(PACKAGE_NAME, "$PACKAGE_NAME.MainActivity")
        instrumentation.context.startActivity(
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        val deadline = SystemClock.elapsedRealtime() + APP_FOREGROUND_TIMEOUT_MS
        while (
            (device.currentPackageName != PACKAGE_NAME || !hasKnownAppSurface(device)) &&
            SystemClock.elapsedRealtime() < deadline
        ) {
            Thread.sleep(100)
        }
        check(device.currentPackageName == PACKAGE_NAME && hasKnownAppSurface(device)) {
            "Lumi did not reach a known foreground surface; foreground=${device.currentPackageName}"
        }
        requireAppForeground(device)
    }

    private fun hasKnownAppSurface(device: UiDevice): Boolean =
        device.hasObject(By.res(HOME_SCREEN_TAG)) ||
            device.hasObject(By.res(BOOKSHELF_SCREEN_TAG)) ||
            device.hasObject(By.res(READER_CONTENT_LOADING_TAG)) ||
            device.hasObject(By.res(READER_CONTENT_READY_TAG))

    private fun wakeAndDismissKeyguard(device: UiDevice) {
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
    }

    private fun resetReadingPosition(device: UiDevice, fixture: Fixture) {
        device.executeShellCommand(
            "am broadcast -a $ACTION_RESET_READING_POSITION -p $PACKAGE_NAME " +
                "--es book_title '${fixture.title.replace("'", "'\\''")}'"
        )
    }

    private val CONFIRM_LABELS = listOf("收到", "Got it", "確認", "OK", "확인")
    private const val HOME_SCREEN_TAG = "home_screen"
    private const val BOOKSHELF_TAB_TAG = "bookshelf_tab"
    private const val BOOKSHELF_SCREEN_TAG = "bookshelf_screen"
    private const val READER_CONTENT_LOADING_TAG = "reader_content_loading"
    private const val READER_CONTENT_READY_TAG = "reader_content_ready"
    private const val MIN_INPUT_SURFACE_PX = 100
    private const val APP_FOREGROUND_TIMEOUT_MS = 30_000L
    private const val ACTION_RESET_READING_POSITION =
        "com.huangder.lumibooks.benchmark.RESET_READING_POSITION"
}
