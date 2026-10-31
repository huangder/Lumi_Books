package com.huangder.lumibooks.util.epub

import android.net.Uri
import java.io.File
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the actual logical-chapter response, including the final TOC anchor. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpubTutorialDocumentTest {
    @Test
    fun simplifiedChineseTocLessonIsReachableAndIncludesAdvancedRules() {
        val tutorial = File("src/main/assets/builtin/lumi/guide_zh-CN.epub")
        EpubRenderSession.open(tutorial.absolutePath).use { session ->
            val nav = Jsoup.parse(requireNotNull(session.read("OEBPS/nav.xhtml")).bytes.toString(Charsets.UTF_8))
            val link = requireNotNull(nav.selectFirst("a[href='content.xhtml#txt-toc-rules']"))
            assertEquals("TXT 目录规则", link.text())
            val destination = requireNotNull(session.resolveInternalLink(0, link.attr("href")))
            val response = requireNotNull(session.assetLoader.shouldInterceptRequest(
                Uri.parse(session.chapterUrl(destination.first, destination.second))
            ))
            val document = Jsoup.parse(response.data.bufferedReader().use { it.readText() })
            val lesson = requireNotNull(document.getElementById("txt-toc-rules"))
            assertNotNull(lesson.getElementById("txt-toc-quickstart"))
            assertEquals(7, lesson.select("h3").count { !it.text().startsWith("快速上手") })
            for (topic in listOf("章节正则", "卷正则", "章节标题模板", "卷标题模板", "兼容规则", "符号开头章节", "保存并应用")) {
                assertTrue("Missing tutorial topic: $topic", lesson.text().contains(topic))
            }
            assertTrue(lesson.select("pre code").any { it.text().contains("^第\\s*") })
        }
    }

    @Test
    fun finalTutorialChapterRetainsDocumentShell() {
        val tutorial = File("src/main/assets/builtin/lumi/guide_zh-CN.epub")
        EpubRenderSession.open(tutorial.absolutePath).use { session ->
            for (index in 0 until session.chapterCount) {
                val response = requireNotNull(session.assetLoader.shouldInterceptRequest(Uri.parse(session.chapterUrl(index))))
                val output = response.data.bufferedReader().use { it.readText() }
                val parsed = Jsoup.parse(output, "", Parser.xmlParser())
                assertEquals("chapter $index: one root", 1, parsed.childrenSize())
                assertNotNull("chapter $index: publisher CSS", parsed.selectFirst("html > head > link[rel=stylesheet]"))
                assertNotNull("chapter $index: body script", parsed.selectFirst("html > body > script#lumi-reader-script"))
                val report = File("build/reports/epub-diagnostics").apply { mkdirs() }
                File(report, "tutorial-$index.xhtml").writeText(output)
            }
            val chapter = session.chapterCount - 1
            val response = requireNotNull(session.assetLoader.shouldInterceptRequest(
                Uri.parse(session.chapterUrl(chapter))
            ))
            val output = response.data.bufferedReader().use { it.readText() }
            val report = File("build/reports/epub-diagnostics").apply { mkdirs() }
            File(report, "tutorial-final.xhtml").writeText(output)
            File(report, "tutorial-full.xhtml").writeBytes(
                requireNotNull(session.read("OEBPS/content.xhtml")).bytes
            )
            File(report, "style.css").writeBytes(requireNotNull(session.read("OEBPS/style.css")).bytes)
            println("Final tutorial chapter=$chapter mime=${response.mimeType}")
            val document = Jsoup.parse(output, "", Parser.xmlParser())
            assertNotNull("Final chapter must preserve the publisher stylesheet",
                document.selectFirst("html > head > link[rel=stylesheet]"))
            assertEquals("Script must belong to the document body", "body",
                document.getElementById("lumi-reader-script")?.parent()?.normalName())
            assertEquals("A logical chapter must retain one html root", 1,
                document.children().count { it.normalName() == "html" })
        }
    }
}
