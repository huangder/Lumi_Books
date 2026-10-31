package com.huangder.lumibooks.ui.excerpt

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import androidx.core.content.FileProvider
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, shadows = [ExcerptImagesTest.ShareUriShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExcerptImagesTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val document get() = ExcerptDocument(20, android.graphics.Color.WHITE) { it.drawColor(android.graphics.Color.WHITE) }

    @Test fun galleryUsesDefaultExcerptPrefixAndCustomReportPrefix() {
        val provider = Robolectric.setupContentProvider(RecordingImagesProvider::class.java, "media")
        ExcerptImages.write(context, document, false)
        ExcerptImages.write(context, document, false, "LUMI_report_")
        assertTrue(provider.names[0].startsWith("LUMI_excerpt_"))
        assertTrue(provider.names[1].startsWith("LUMI_report_"))
        assertTrue(provider.names.all { it.endsWith(".png") })
    }

    @Test fun shareFilesUseTheSamePrefixesAndContainValidPngs() {
        for (prefix in listOf("LUMI_excerpt_", "LUMI_report_")) {
            val uri = if (prefix == "LUMI_excerpt_") ExcerptImages.write(context, document, true)
                else ExcerptImages.write(context, document, true, prefix)
            assertTrue(uri.lastPathSegment!!.startsWith(prefix))
            assertEquals("${context.packageName}.fileprovider", uri.authority)
            val signature = checkNotNull(ShareUriShadow.lastFile).inputStream().use { stream -> ByteArray(8).also { stream.read(it) } }
            assertArrayEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10), signature)
        }
    }

    // FileProvider's Android-only canonical path checks use '/' and fail on a Windows JVM.
    // Intercept only URI creation; the real exporter still writes the file and PNG bytes.
    @Implements(FileProvider::class)
    class ShareUriShadow {
        companion object {
            var lastFile: File? = null
            @JvmStatic
            @Implementation
            fun getUriForFile(context: Context, authority: String, file: File): Uri {
                lastFile = file
                return Uri.Builder().scheme("content").authority(authority).appendPath(file.name).build()
            }
        }
    }

    class RecordingImagesProvider : ContentProvider() {
        val names = mutableListOf<String>()
        private val files = mutableMapOf<Uri, File>()
        override fun onCreate() = true
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            names += checkNotNull(values?.getAsString(MediaStore.Images.Media.DISPLAY_NAME))
            val result = Uri.withAppendedPath(uri, names.size.toString())
            files[result] = File.createTempFile("report-image", ".png")
            return result
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
            ParcelFileDescriptor.open(checkNotNull(files[uri]), ParcelFileDescriptor.MODE_READ_WRITE)
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 1
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun getType(uri: Uri) = "image/png"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    }
}
