package com.huangder.lumibooks.util.parser

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.huangder.lumibooks.util.cache.ReaderCacheStore
import java.io.File
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TxtContentUriInstrumentedTest {
    private lateinit var context: Context
    private lateinit var source: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ReaderCacheStore.get(context).clear()
        source = File(context.cacheDir, "txt-content-uri-stability.txt").apply {
            writeText(
                buildString {
                    repeat(40) { chapter ->
                        append("第${chapter + 1}章 标题$chapter\n")
                        append("这是正文$chapter，包含多字节字符😀。\n\n")
                    }
                },
                Charsets.UTF_8
            )
        }
    }

    @After
    fun tearDown() {
        ReaderCacheStore.get(context).clear()
        source.delete()
    }

    @Test
    fun repeatedContentUriOpenPreservesSourceBoundariesAndByteAnchor() {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            source
        ).toString()
        val sourceHash = sha256(source.readBytes())
        var expectedSignature: List<Triple<String, Long?, Long?>>? = null
        var byteAnchor: Long? = null

        repeat(10) {
            val parser = TxtParser(context)
            val content = parser.parse(uri)
            val signature = content.chapters.indices.map { chapterIndex ->
                Triple(
                    content.chapters[chapterIndex].title,
                    parser.getChapterByteRange(chapterIndex)?.first,
                    parser.getChapterByteRange(chapterIndex)?.second
                )
            }
            if (expectedSignature == null) {
                expectedSignature = signature
                byteAnchor = parser.characterOffsetToByte(17, 9)
            }
            val anchor = requireNotNull(byteAnchor)
            val restored = requireNotNull(parser.byteToCharacterPosition(anchor))

            assertEquals(expectedSignature, signature)
            assertEquals(anchor, parser.characterOffsetToByte(restored.first, restored.second))
            assertTrue(sourceHash.contentEquals(sha256(source.readBytes())))
            parser.close()
        }
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
