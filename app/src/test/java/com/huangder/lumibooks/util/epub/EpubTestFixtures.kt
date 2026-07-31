package com.huangder.lumibooks.util.epub

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun writeEpub(file: File, entries: Map<String, String>) {
    ZipOutputStream(file.outputStream().buffered()).use { zip ->
        entries.forEach { (entryPath, content) ->
            zip.putNextEntry(ZipEntry(entryPath))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
}

internal fun epub3Entries(): Map<String, String> = linkedMapOf(
    "mimetype" to "application/epub+zip",
    "META-INF/container.xml" to """
        <?xml version="1.0" encoding="UTF-8"?>
        <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles><rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles>
        </container>
    """.trimIndent(),
    "OPS/package.opf" to """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>Fixture EPUB 3</dc:title>
            <dc:creator>Fixture Author</dc:creator>
            <meta property="rendition:layout">reflowable</meta>
            <meta property="rendition:orientation">auto</meta>
            <meta property="rendition:spread">landscape</meta>
            <meta property="rendition:flow">paginated</meta>
          </metadata>
          <manifest>
            <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            <item id="chapter1" href="Text/chapter%201.xhtml" media-type="application/xhtml+xml"/>
            <item id="chapter2" href="Text/chapter2.xhtml" media-type="application/xhtml+xml"/>
            <item id="style" href="Styles/book.css" media-type="text/css"/>
          </manifest>
          <spine page-progression-direction="rtl">
            <itemref idref="chapter1"/>
            <itemref idref="chapter2" properties="rendition:layout-pre-paginated"/>
          </spine>
        </package>
    """.trimIndent(),
    "OPS/nav.xhtml" to """
        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
          <body><nav epub:type="toc"><ol>
            <li><a href="Text/chapter%201.xhtml#start">Part One</a><ol>
              <li><a href="Text/chapter2.xhtml">Fixed Page</a></li>
            </ol></li>
          </ol></nav></body>
        </html>
    """.trimIndent(),
    "OPS/Text/chapter 1.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><body><p id="start">One</p></body></html>""",
    "OPS/Text/chapter2.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><body><svg viewBox="0 0 1200 1600"/></body></html>""",
    "OPS/Styles/book.css" to "p { color: red; }"
)

internal fun epub2Entries(): Map<String, String> = linkedMapOf(
    "META-INF/container.xml" to """
        <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles>
        </container>
    """.trimIndent(),
    "OEBPS/content.opf" to """
        <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Fixture EPUB 2</dc:title></metadata>
          <manifest>
            <item id="c1" href="chapter.xhtml" media-type="application/xhtml+xml"/>
            <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
          </manifest>
          <spine toc="ncx"><itemref idref="c1"/></spine>
        </package>
    """.trimIndent(),
    "OEBPS/toc.ncx" to """
        <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap>
          <navPoint id="n1"><navLabel><text>Chapter One</text></navLabel><content src="chapter.xhtml#one"/></navPoint>
        </navMap></ncx>
    """.trimIndent(),
    "OEBPS/chapter.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><body><p id="one">One</p></body></html>"""
)
