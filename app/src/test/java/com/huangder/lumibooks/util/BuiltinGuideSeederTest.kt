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
        assertTrue(BuiltinGuideSeeder.CONTENT_VERSION >= 4)
        assertEquals(
            mapOf(
                "zh-CN" to "builtin/lumi/covers/SC.webp",
                "zh-TW" to "builtin/lumi/covers/TC.webp",
                "zh-HK" to "builtin/lumi/covers/TC.webp",
                "zh-MO" to "builtin/lumi/covers/TC.webp",
                "en" to "builtin/lumi/covers/EN.webp",
                "ja" to "builtin/lumi/covers/JP.webp",
                "ko" to "builtin/lumi/covers/KR.webp"
            ),
            BuiltinGuideSeeder.GUIDE_MANIFEST.associate { it.language to it.coverAssetPath }
        )
        BuiltinGuideSeeder.GUIDE_MANIFEST.forEach { guide ->
            assertTrue(guide.fileName.endsWith(".epub"))
            assertTrue(guide.assetPath.startsWith("builtin/lumi/"))
            assertTrue(guide.coverAssetPath.startsWith("builtin/lumi/covers/"))
            assertTrue(guide.coverFileName.startsWith("builtin_guide_"))
            assertTrue(guide.bookId.startsWith("builtin-guide-"))
            assertTrue(guide.contentVersion >= BuiltinGuideSeeder.CONTENT_VERSION)
        }
    }
}
