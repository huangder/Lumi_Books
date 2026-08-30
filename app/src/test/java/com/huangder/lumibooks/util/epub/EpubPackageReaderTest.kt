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
    fun acceptsStandardExternalDoctypeInNcx() {
        val file = temporaryFolder.newFile("fixture2-doctype.epub")
        val entries = epub2Entries().toMutableMap()
        entries["OEBPS/toc.ncx"] = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN"
                "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
              <navMap>
                <navPoint id="n1" playOrder="1">
                  <navLabel><text>Chapter One</text></navLabel>
                  <content src="chapter.xhtml#one"/>
                </navPoint>
              </navMap>
            </ncx>
        """.trimIndent()
        writeEpub(file, entries)

        val pkg = EpubPackageReader.read(file.absolutePath)

        assertEquals("Fixture EPUB 2", pkg.title)
        assertEquals(1, pkg.spine.size)
        assertEquals(1, pkg.navigation.size)
        assertEquals("Chapter One", pkg.navigation.single().title)
        assertEquals("OEBPS/chapter.xhtml#one", pkg.navigation.single().href)
    }

    @Test
    fun reconcilesRootEntriesReferencedFromNestedOpfAndNcx() {
        val file = temporaryFolder.newFile("fixture2-root-entries.epub")
        val entries = epub2Entries().toMutableMap()
        entries["OEBPS/content.opf"] = """
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Root entries</dc:title></metadata>
              <manifest>
                <item id="c1" href="Text/:first.xhtml" media-type="application/xhtml+xml"/>
                <item id="c2" href="Text/::second.xhtml" media-type="application/xhtml+xml"/>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
              </manifest>
              <spine toc="ncx"><itemref idref="c1"/><itemref idref="c2"/></spine>
            </package>
        """.trimIndent()
        entries["OEBPS/toc.ncx"] = """
            <?xml version="1.0"?>
            <!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN"
                "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap>
              <navPoint><navLabel><text>First</text></navLabel><content src="Text/:first.xhtml"/></navPoint>
              <navPoint><navLabel><text>Second</text></navLabel><content src="Text/::second.xhtml"/></navPoint>
            </navMap></ncx>
        """.trimIndent()
        entries.remove("OEBPS/chapter.xhtml")
        entries["Text/:first.xhtml"] = "<html><body>First</body></html>"
        entries["Text/::second.xhtml"] = "<html><body>Second</body></html>"
        writeEpub(file, entries)

        val pkg = EpubPackageReader.read(file.absolutePath)

        assertEquals(
            listOf("Text/:first.xhtml", "Text/::second.xhtml"),
            pkg.spine.map { it.manifestItem.fullPath }
        )
        assertEquals(
            listOf("Text/:first.xhtml", "Text/::second.xhtml"),
            pkg.navigation.map { it.href }
        )
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

    @Test
    fun rejectsInternalEntityDeclarationsInNcx() {
        val file = temporaryFolder.newFile("ncx-internal-entity.epub")
        val entries = epub2Entries().toMutableMap()
        entries["OEBPS/toc.ncx"] = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE ncx [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/">
              <navMap><navPoint id="n1"><navLabel><text>&xxe;</text></navLabel>
                <content src="chapter.xhtml"/></navPoint></navMap>
            </ncx>
        """.trimIndent()
        writeEpub(file, entries)

        assertThrows(IllegalArgumentException::class.java) {
            EpubPackageReader.read(file.absolutePath)
        }
    }

}
