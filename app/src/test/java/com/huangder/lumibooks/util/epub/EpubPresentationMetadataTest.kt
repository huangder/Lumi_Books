package com.huangder.lumibooks.util.epub

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.*
import org.junit.Test

class EpubPresentationMetadataTest {
    @Test fun spreadPermissionUsesViewportOrientation() {
        assertFalse(EpubRenditionSpread.NONE.allowsSpread(true))
        assertFalse(EpubRenditionSpread.NONE.allowsSpread(false))
        assertTrue(EpubRenditionSpread.LANDSCAPE.allowsSpread(true))
        assertFalse(EpubRenditionSpread.LANDSCAPE.allowsSpread(false))
        assertFalse(EpubRenditionSpread.PORTRAIT.allowsSpread(true))
        assertTrue(EpubRenditionSpread.PORTRAIT.allowsSpread(false))
        for (spread in listOf(EpubRenditionSpread.AUTO, EpubRenditionSpread.BOTH)) {
            assertTrue(spread.allowsSpread(true))
            assertTrue(spread.allowsSpread(false))
        }
        assertEquals(EpubRenditionSpread.LANDSCAPE, EpubRenditionSpread.fromMetadata(" Landscape "))
        assertNull(EpubRenditionSpread.fromMetadata("unsupported"))
    }

    @Test fun viewportSupportsPublisherDimensionsAndSvgViewBoxFallback() {
        assertEquals(1200f to 800f, viewport("<html><head><meta name='viewport' content='height = 800; width = 1200'/></head></html>"))
        assertEquals(1024f to 768f, viewport("<html><head><meta name='VIEWPORT' content='width=1024px height=768px'/></head></html>"))
        assertEquals(1000f to 1600f, viewport("<svg viewBox='-10 -20 1000 1600'/>"))
        assertEquals(800f to 1200f, viewport("<html><body><svg width='800px' height='1200px'/></body></html>"))
        assertEquals(1200f to 800f, viewport("<html><body><div><svg viewBox='0 0 1200 800'/></div></body></html>"))
        assertEquals(900f to 1400f, viewport("<html><head><meta name='viewport' content='width=device-width, height=device-height'/></head><body><svg viewBox='0,0,900,1400'/></body></html>"))
    }

    @Test fun viewportPrefersValidMetaAndRejectsIconsAndInvalidDimensions() {
        assertEquals(1600f to 900f, viewport("<html><head><meta name='viewport' content='width=1600,height=900'/></head><body><svg viewBox='0 0 900 1600'/></body></html>"))
        for (content in listOf("width=device-width,height=900", "width=-10,height=900", "width=0,height=900", "width=Infinity,height=900")) {
            assertNull(viewport("<html><head><meta name='viewport' content='$content'/></head><body>Text</body></html>"))
        }
        assertNull(viewport("<html><body><p>Text</p><svg viewBox='0 0 24 24'/></body></html>"))
        assertNull(viewport("<html><body><svg viewBox='0 0 -1 100'/></body></html>"))
        assertNull(viewport("<html><body><svg width='100%' height='100%'/></body></html>"))
    }

    private fun viewport(source: String): Pair<Float, Float>? =
        EpubPresentationMetadata.viewport(Jsoup.parse(source, "", Parser.xmlParser()))
}
