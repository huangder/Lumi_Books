package com.huangder.lumibooks.util.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubPackageReaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun readsEpub3ManifestSpineNavigationAndRenditionMetadata() {
        val file = temporaryFolder.newFile("fixture3.epub")
        writeEpub(file, epub3Entries())

        val pkg = EpubPackageReader.read(file.absolutePath)

        assertEquals("Fixture EPUB 3", pkg.title)
        assertEquals("Fixture Author", pkg.author)
        assertEquals(EpubPageProgressionDirection.RTL, pkg.pageProgressionDirection)
        assertEquals(EpubRenditionLayout.REFLOWABLE, pkg.renditionLayout)
        assertEquals("auto", pkg.renditionOrientation)
        assertEquals("landscape", pkg.renditionSpread)
        assertEquals("paginated", pkg.renditionFlow)
        assertEquals(2, pkg.spine.size)
        assertEquals("OPS/Text/chapter 1.xhtml", pkg.spine[0].manifestItem.fullPath)
        assertEquals(EpubRenditionLayout.PRE_PAGINATED, pkg.spine[1].renditionLayout)
        assertEquals(listOf(1, 2), pkg.navigation.map { it.level })
        assertEquals("OPS/Text/chapter 1.xhtml#start", pkg.navigation[0].href)
    }

    @Test
    fun readsEpub2NcxAndKeepsLegacyDefaults() {
        val file = temporaryFolder.newFile("fixture2.epub")
        writeEpub(file, epub2Entries())

        val pkg = EpubPackageReader.read(file.absolutePath)

        assertEquals("Fixture EPUB 2", pkg.title)
        assertEquals("\u672A\u77E5\u4F5C\u8005", pkg.author)
        assertEquals(EpubPageProgressionDirection.DEFAULT, pkg.pageProgressionDirection)
        assertEquals(1, pkg.navigation.size)
        assertEquals("Chapter One", pkg.navigation.single().title)
        assertEquals("OEBPS/chapter.xhtml#one", pkg.navigation.single().href)
        assertFalse(pkg.spine.single().renditionLayout == EpubRenditionLayout.PRE_PAGINATED)
    }

    @Test
    fun rejectsDoctypeAndExternalEntities() {
        val file = temporaryFolder.newFile("doctype.epub")
        val entries = epub2Entries().toMutableMap()
        entries["META-INF/container.xml"] = """
            <?xml version="1.0"?>
            <!DOCTYPE container [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>
        """.trimIndent()
        writeEpub(file, entries)

        assertThrows(Exception::class.java) { EpubPackageReader.read(file.absolutePath) }
    }

}
