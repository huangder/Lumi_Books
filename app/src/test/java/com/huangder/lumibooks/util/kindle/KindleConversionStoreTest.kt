package com.huangder.lumibooks.util.kindle

import com.huangder.lumibooks.util.MobiTestFixtures
import com.huangder.lumibooks.util.epub.EpubPackageReader
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KindleConversionStoreTest {
    @get:Rule val directory = TemporaryFolder()

    private fun book(name: String = "test.azw3", version: Int = 8, compression: Int = 1): File {
        val header = MobiTestFixtures.buildRecord0(textRecordCount = 1, compression = compression)
        MobiTestFixtures.be32(version).copyInto(header, 36)
        return MobiTestFixtures.writeMobiFile(File(directory.root, name), listOf("text".toByteArray()), record0 = header)
    }

    private fun resources(target: String): Int {
        File(target, "content.opf").writeText("""<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>测试书</dc:title><dc:creator>作者</dc:creator><dc:identifier id="id">fixture</dc:identifier></metadata><manifest><item id="c" href="part00000.html" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c"/></spine></package>""")
        File(target, "part00000.html").writeText("""<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Chapter</title></head><body><h1 id="chapter">你好</h1><p>World</p></body></html>""")
        return 0
    }

    @Test fun probeUsesContentAndSeparatesClassicKf8AndHuff() {
        assertFalse(KindleProbe.read(book("classic.azw3", 6)).needsReconstruction)
        assertTrue(KindleProbe.read(book("new.mobi", 8)).needsReconstruction)
        assertTrue(KindleProbe.read(book("huff.azw", 6, 17480)).needsReconstruction)
    }

    @Test fun encryptionAndInvalidRecordOffsetsFailBeforeConversion() {
        val encrypted = book()
        val bytes = encrypted.readBytes()
        val start = java.nio.ByteBuffer.wrap(bytes, 78, 4).int
        bytes[start + 13] = 1
        encrypted.writeBytes(bytes)
        assertEquals(KindleFailure.ENCRYPTED, assertThrows(KindleException::class.java) {
            KindleProbe.read(encrypted)
        }.failure)
        bytes[start + 13] = 0
        MobiTestFixtures.be32(bytes.size + 1).copyInto(bytes, 86)
        encrypted.writeBytes(bytes)
        assertEquals(KindleFailure.INVALID, assertThrows(KindleException::class.java) {
            KindleProbe.read(encrypted)
        }.failure)
    }

    @Test fun publishesEpubOnceAndPinsItAcrossCacheClear() {
        var conversions = 0
        val source = book()
        val store = KindleConversionStore(File(directory.root, "cache"), { _, output -> conversions++; resources(output) })
        val first = store.acquire(source)
        val second = store.acquire(source)
        assertEquals(1, conversions)
        assertEquals(first.file, second.file)
        assertEquals("测试书", EpubPackageReader.read(first.file.path).title)
        ZipFile(first.file).use { zip ->
            assertEquals("mimetype", zip.entries().nextElement().name)
            assertEquals(java.util.zip.ZipEntry.STORED, zip.getEntry("mimetype").method)
        }
        store.clear()
        assertTrue(first.file.isFile)
        first.close()
        assertTrue(second.file.isFile)
        second.close()
        assertFalse(second.file.exists())
        store.acquire(source).use { assertTrue(it.file.isFile) }
        assertEquals(2, conversions)
    }

    @Test fun contentChangeWithSameSizeAndTimestampCreatesNewCacheIdentity() {
        val source = book()
        val store = KindleConversionStore(File(directory.root, "cache"), { _, output -> resources(output) })
        val first = store.acquire(source)
        val modified = source.lastModified()
        val bytes = source.readBytes().apply { this[lastIndex] = 'X'.code.toByte() }
        source.writeBytes(bytes); source.setLastModified(modified)
        store.acquire(source).use { second -> assertNotEquals(first.file, second.file) }
        first.close()
    }

    @Test fun failedConversionLeavesNoValidOrTemporaryPublicationAndCanRetry() {
        var fail = true
        val cache = File(directory.root, "cache")
        val source = book()
        val store = KindleConversionStore(cache, { _, output -> if (fail) 6 else resources(output) })
        assertEquals(KindleFailure.CONVERSION, assertThrows(KindleException::class.java) { store.acquire(source) }.failure)
        assertTrue(cache.listFiles().orEmpty().isEmpty())
        fail = false
        store.acquire(source).use { assertTrue(it.file.isFile) }
    }

    @Test fun missingSpineResourceIsRejectedAndCorruptCacheIsRebuilt() {
        var omit = true
        val source = book()
        val store = KindleConversionStore(File(directory.root, "cache"), { _, output ->
            resources(output)
            if (omit) File(output, "part00000.html").delete()
            0
        })
        assertEquals(KindleFailure.CONVERSION, assertThrows(KindleException::class.java) { store.acquire(source) }.failure)
        omit = false
        val path = store.acquire(source).use { it.file }
        path.writeText("broken zip")
        store.acquire(source).use { assertEquals("测试书", EpubPackageReader.read(it.file.path).title) }
    }

    @Test fun simultaneousReadersShareOneConversion() {
        val count = AtomicInteger()
        val source = book()
        val store = KindleConversionStore(File(directory.root, "cache"), { _, output -> count.incrementAndGet(); resources(output) })
        val executor = Executors.newFixedThreadPool(3)
        try {
            val futures = (0..2).map { executor.submit<KindleEpubLease> { store.acquire(source) } }
            val leases = futures.map { it.get(20, TimeUnit.SECONDS) }
            assertEquals(1, count.get())
            assertEquals(1, leases.map { it.file.path }.distinct().size)
            leases.forEach { it.close() }
        } finally { executor.shutdownNow() }
    }
}
