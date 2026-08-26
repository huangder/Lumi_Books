package com.huangder.lumibooks.ui.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverSearchEngineTest {

    @Test
    fun buildsImageSearchUrlsForEveryEngine() {
        val query = "活着 封面"

        assertEquals(
            "https://www.bing.com/images/search?q=%E6%B4%BB%E7%9D%80+%E5%B0%81%E9%9D%A2",
            CoverSearchEngine.BING.buildImageSearchUrl(query)
        )
        assertEquals(
            "https://m.baidu.com/s?word=%E6%B4%BB%E7%9D%80+%E5%B0%81%E9%9D%A2&tn=vsearch&pd=image_content",
            CoverSearchEngine.BAIDU.buildImageSearchUrl(query)
        )
        assertEquals(
            "https://www.google.com/search?tbm=isch&q=%E6%B4%BB%E7%9D%80+%E5%B0%81%E9%9D%A2",
            CoverSearchEngine.GOOGLE.buildImageSearchUrl(query)
        )
    }

    @Test
    fun identifiesSupportedSearchEngineHosts() {
        assertEquals(CoverSearchEngine.BING, CoverSearchEngine.fromUrl("https://cn.bing.com/images/search?q=book"))
        assertEquals(CoverSearchEngine.BAIDU, CoverSearchEngine.fromUrl("https://image.baidu.com/search/index?word=book"))
        assertEquals(CoverSearchEngine.GOOGLE, CoverSearchEngine.fromUrl("https://www.google.com.hk/search?q=book"))
        assertNull(CoverSearchEngine.fromUrl("https://example.com/search?q=book"))
    }

    @Test
    fun readsTheEngineSpecificQueryParameter() {
        assertEquals(
            "活着 封面",
            CoverSearchEngine.BAIDU.queryFromUrl(
                "https://m.baidu.com/s?word=%E6%B4%BB%E7%9D%80+%E5%B0%81%E9%9D%A2&tn=vsearch&pd=image_content"
            )
        )
    }
}
