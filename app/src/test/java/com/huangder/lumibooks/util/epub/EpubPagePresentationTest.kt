package com.huangder.lumibooks.util.epub

import android.app.Application
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EpubPagePresentationTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun duokanHintsSurvivePackageParsingWithoutInventingFixedPagesOrPageSides() {
        openFixture(pages = listOf(
            Page("special", properties = "duokan-page-fullscreen-spread-left"),
            Page("fit", properties = "duokan-page-fitwindow"),
            Page("normal"),
            Page("standard", properties = "duokan-page-fullscreen page-spread-right rendition:layout-pre-paginated")
        )).use { session ->
            assertEquals(EpubDuokanPage.SPREAD_LEFT, session.pagePresentation(0).duokanPage)
            assertEquals(EpubRenditionLayout.REFLOWABLE, session.pagePresentation(0).renditionLayout)
            assertNull(session.pagePresentation(0).preferredSide)
            assertEquals(EpubDuokanPage.FIT_WINDOW, session.pagePresentation(1).duokanPage)
            assertNull(session.pagePresentation(2).duokanPage)
            assertEquals(EpubPageSide.RIGHT, session.pagePresentation(3).preferredSide)
            assertEquals(EpubRenditionLayout.PRE_PAGINATED, session.pagePresentation(3).renditionLayout)
            val native = session.nativeChapterSource(0)!!
            assertEquals(EpubDuokanPage.SPREAD_LEFT, native.duokanPage)
            assertEquals(ChapterRenderEngine.WEBVIEW, NativeEpubCompiler.compile(native).decision.engine)
        }
        assertNull(EpubDuokanPage.fromProperties(setOf("duokan-page-fullscreen-future")))
    }

    @Test fun itemPropertiesOverridePackageSpreadAndKeepDeclaredSides() {
        openFixture(
            pages = listOf(
                Page("left", properties = "page-spread-left rendition:spread-both"),
                Page("right", properties = "page-spread-right rendition:spread-none"),
                Page("center", properties = "rendition:page-spread-center rendition:spread-auto"),
                Page("inherited"),
                Page("portrait", properties = "rendition:spread-portrait")
            ),
            metadata = "<meta property=\"rendition:spread\">landscape</meta>"
        ).use { session ->
            assertEquals(EpubPageSide.LEFT, session.pagePresentation(0).preferredSide)
            assertEquals(EpubRenditionSpread.BOTH, session.pagePresentation(0).renditionSpread)
            assertEquals(EpubPageSide.RIGHT, session.pagePresentation(1).preferredSide)
            assertEquals(EpubRenditionSpread.NONE, session.pagePresentation(1).renditionSpread)
            assertEquals(EpubPageSide.CENTER, session.pagePresentation(2).preferredSide)
            assertEquals(EpubRenditionSpread.AUTO, session.pagePresentation(2).renditionSpread)
            assertNull(session.pagePresentation(3).preferredSide)
            assertEquals(EpubRenditionSpread.LANDSCAPE, session.pagePresentation(3).renditionSpread)
            assertEquals(EpubRenditionSpread.PORTRAIT, session.pagePresentation(4).renditionSpread)
            assertEquals(EpubPagePresentation(), session.pagePresentation(-1))
        }
    }

    @Test fun firstChapterAndCoverLikeFilenameDoNotInventACover() {
        openFixture(
            pages = listOf(
                Page("cover", body = "<img src=\"illustration.jpg\"/>"),
                Page("second", body = "<p>The first text chapter</p>"),
                Page("contents", body = "<a epub:type=\"cover\" href=\"cover.xhtml\">Cover</a>")
            ),
            extraManifest = "<item id=\"illustration\" href=\"illustration.jpg\" media-type=\"image/jpeg\"/>"
        ).use { session ->
            assertFalse(session.pagePresentation(0).isCover)
            assertFalse(session.pagePresentation(1).isCover)
            assertFalse(session.pagePresentation(2).isCover)
        }
    }

    @Test fun guideNavigationAndLegacyImageMetadataIdentifyRealCovers() {
        openFixture(
            pages = listOf(
                Page("ordinary"), Page("guide"), Page("landmark"),
                Page("image", body = "<svg><image xlink:href=\"jacket.jpg\"/></svg>")
            ),
            metadata = "<meta name=\"cover\" content=\"jacket\"/>",
            extraManifest = "<item id=\"jacket\" href=\"jacket.jpg\" media-type=\"image/jpeg\"/>",
            guide = "<guide><reference type=\"cover\" href=\"guide.xhtml\"/></guide>",
            navExtra = "<nav epub:type=\"landmarks\"><ol><li><a epub:type=\"cover\" href=\"landmark.xhtml\">Jacket</a></li></ol></nav>"
        ).use { session ->
            assertFalse(session.pagePresentation(0).isCover)
            assertTrue(session.pagePresentation(1).isCover)
            assertTrue(session.pagePresentation(2).isCover)
            assertTrue(session.pagePresentation(3).isCover)
        }
    }

    @Test fun epub3CoverImageAndDocumentSemanticsAreRecognized() {
        openFixture(
            pages = listOf(
                Page("image", body = "<img src=\"jacket.jpg\"/>"),
                Page("semantic", body = "<section epub:type=\"frontmatter cover\">Title</section>")
            ),
            extraManifest = "<item id=\"jacket\" href=\"jacket.jpg\" media-type=\"image/jpeg\" properties=\"cover-image\"/>"
        ).use { session ->
            assertTrue(session.pagePresentation(0).isCover)
            assertTrue(session.pagePresentation(1).isCover)
        }
    }

    @Test fun logicalChaptersUseTheirSpineMetadataAndCoverFragmentStaysInItsSection() {
        openFixture(
            pages = listOf(
                Page("sections", properties = "page-spread-left rendition:spread-none",
                    body = "<section id=\"first\"><p>First</p></section><section id=\"second\"><p>Second</p></section>"),
                Page("fixed", properties = "rendition:layout-pre-paginated page-spread-right",
                    head = "<meta name=\"viewport\" content=\"width=1600, height=900\"/>")
            ),
            guide = "<guide><reference type=\"cover\" href=\"sections.xhtml#first\"/></guide>",
            toc = "<li><a href=\"sections.xhtml#first\">First</a></li><li><a href=\"sections.xhtml#second\">Second</a></li><li><a href=\"fixed.xhtml\">Fixed</a></li>"
        ).use { session ->
            assertEquals(3, session.chapterCount)
            assertEquals("OPS/sections.xhtml#second", session.chapterHref(1))
            assertTrue(session.pagePresentation(0).isCover)
            assertFalse(session.pagePresentation(1).isCover)
            assertEquals(EpubPageSide.LEFT, session.pagePresentation(1).preferredSide)
            assertEquals(EpubRenditionSpread.NONE, session.pagePresentation(1).renditionSpread)
            val fixed = session.pagePresentation(2)
            assertEquals(EpubRenditionLayout.PRE_PAGINATED, fixed.renditionLayout)
            assertEquals(EpubPageSide.RIGHT, fixed.preferredSide)
            assertEquals(1600f, fixed.viewportWidth)
            assertEquals(900f, fixed.viewportHeight)
            assertFalse(fixed.isCover)
        }
    }

    @Test fun presentationCacheSupportsConcurrentCallersAndSvgFallback() {
        openFixture(pages = listOf(Page("svg", body = "<svg viewBox=\"0 0 1200 1800\"/>"))).use { session ->
            val executor = Executors.newFixedThreadPool(4)
            try {
                val results = executor.invokeAll((1..8).map { Callable { session.pagePresentation(0) } })
                    .map { it.get() }
                results.forEach { assertSame(results.first(), it) }
                assertEquals(1200f, results.first().viewportWidth)
                assertEquals(1800f, results.first().viewportHeight)
            } finally {
                executor.shutdownNow()
            }
        }
    }

    private data class Page(
        val id: String,
        val properties: String = "",
        val head: String = "",
        val body: String = "<p>Page content</p>"
    )

    private fun openFixture(
        pages: List<Page>,
        metadata: String = "",
        extraManifest: String = "",
        guide: String = "",
        navExtra: String = "",
        toc: String = pages.joinToString("") { "<li><a href=\"${it.id}.xhtml\">${it.id}</a></li>" }
    ): EpubRenderSession {
        val file = temporaryFolder.newFile("presentation-${System.nanoTime()}.epub")
        val entries = linkedMapOf(
            "META-INF/container.xml" to "<container><rootfiles><rootfile full-path=\"OPS/package.opf\"/></rootfiles></container>",
            "OPS/package.opf" to """
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata><title>Presentation fixture</title>$metadata</metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    ${pages.joinToString("") { "<item id=\"${it.id}\" href=\"${it.id}.xhtml\" media-type=\"application/xhtml+xml\"/>" }}
                    $extraManifest
                  </manifest>
                  <spine>${pages.joinToString("") { "<itemref idref=\"${it.id}\" properties=\"${it.properties}\"/>" }}</spine>
                  $guide
                </package>
            """.trimIndent(),
            "OPS/nav.xhtml" to "<html xmlns:epub=\"http://www.idpf.org/2007/ops\"><body><nav epub:type=\"toc\"><ol>$toc</ol></nav>$navExtra</body></html>"
        )
        pages.forEach {
            entries["OPS/${it.id}.xhtml"] = "<html xmlns:epub=\"http://www.idpf.org/2007/ops\"><head>${it.head}</head><body>${it.body}</body></html>"
        }
        writeEpub(file, entries)
        return EpubRenderSession.open(file.absolutePath)
    }
}
