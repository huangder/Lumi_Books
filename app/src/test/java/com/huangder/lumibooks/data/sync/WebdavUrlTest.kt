package com.huangder.lumibooks.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class WebdavUrlTest {

    @Test
    fun `appends encoded path segments while preserving base path and query`() {
        assertEquals(
            "https://dav.example.com/root/%E9%98%85%E8%AF%BB%20%E5%90%8C%E6%AD%A5/books/A%23B.epub?token=abc",
            WebdavUrl.append(
                "https://dav.example.com/root/?token=abc",
                "阅读 同步",
                "books",
                "A#B.epub"
            )
        )
    }

    @Test
    fun `nested sync path creates one segment per level`() {
        assertEquals(
            "https://dav.example.com/dav/Lumi/Books/manifest.json",
            WebdavUrl.append(
                "https://dav.example.com/dav/",
                "/Lumi/Books/",
                "manifest.json"
            )
        )
    }

    @Test
    fun `base path without trailing slash keeps its last segment`() {
        assertEquals(
            "https://dav.example.com/remote.php/dav/files/user/LumiBooks",
            WebdavUrl.append(
                "https://dav.example.com/remote.php/dav/files/user",
                "LumiBooks"
            )
        )
    }

    @Test
    fun `encoded href segment is not encoded twice`() {
        assertEquals(
            "https://dav.example.com/dav/assets/%E9%98%85%E8%AF%BB%20%E5%B0%81%E9%9D%A2.jpg",
            WebdavUrl.appendEncoded(
                "https://dav.example.com/dav/assets",
                "%E9%98%85%E8%AF%BB%20%E5%B0%81%E9%9D%A2.jpg"
            )
        )
    }
}
