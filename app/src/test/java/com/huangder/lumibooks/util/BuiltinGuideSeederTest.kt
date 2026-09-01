package com.huangder.lumibooks.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinGuideSeederTest {
    @Test
    fun manifestContainsEverySupportedGuideLanguageOnce() {
        val languages = BuiltinGuideSeeder.GUIDE_MANIFEST.map { it.language }
        assertEquals(setOf("zh-CN", "zh-TW", "zh-HK", "zh-MO", "en", "ja", "ko"), languages.toSet())
        assertEquals(languages.size, languages.toSet().size)
        assertEquals(languages.size, BuiltinGuideSeeder.GUIDE_MANIFEST.map { it.bookId }.toSet().size)
    }

    @Test
    fun manifestUsesStableFilesInsideLumiFolder() {
        assertEquals("lumi", BuiltinGuideSeeder.FOLDER_NAME)
        assertEquals("builtin/lumi/folder_cover.png", BuiltinGuideSeeder.FOLDER_COVER_ASSET_PATH)
        assertTrue(BuiltinGuideSeeder.FOLDER_COVER_VERSION >= 1)
        BuiltinGuideSeeder.GUIDE_MANIFEST.forEach { guide ->
            assertTrue(guide.fileName.endsWith(".epub"))
            assertTrue(guide.assetPath.startsWith("builtin/lumi/"))
            assertTrue(guide.bookId.startsWith("builtin-guide-"))
        }
    }
}
