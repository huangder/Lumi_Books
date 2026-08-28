import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.huangder.lumibooks.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
}

val generatedFixtures = layout.buildDirectory.dir("generated/benchmark-fixtures")

fun writeTxtFixture(file: File, targetBytes: Int, title: String) {
    file.outputStream().buffered().writer(Charsets.UTF_8).use { writer ->
        var chapter = 1
        var written = 0
        while (written < targetBytes) {
            val block = "第${chapter}章 性能测试\n$title 的确定性正文段落，用于测量打开、分页和连续翻页。\n\n"
            writer.write(block)
            written += block.toByteArray(Charsets.UTF_8).size
            chapter++
        }
    }
}

fun ZipOutputStream.writeEntry(path: String, value: String, stored: Boolean = false) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    val entry = ZipEntry(path)
    if (stored) {
        val crc = CRC32().apply { update(bytes) }
        entry.method = ZipEntry.STORED
        entry.size = bytes.size.toLong()
        entry.compressedSize = bytes.size.toLong()
        entry.crc = crc.value
    }
    putNextEntry(entry)
    write(bytes)
    closeEntry()
}

fun writeEpubFixture(file: File, chapterCount: Int, title: String) {
    ZipOutputStream(file.outputStream().buffered()).use { zip ->
        zip.writeEntry("mimetype", "application/epub+zip", stored = true)
        zip.writeEntry(
            "META-INF/container.xml",
            """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"""
        )
        val manifest = (1..chapterCount).joinToString("") { index ->
            "<item id=\"c$index\" href=\"chapter$index.xhtml\" media-type=\"application/xhtml+xml\"/>"
        }
        val spine = (1..chapterCount).joinToString("") { index -> "<itemref idref=\"c$index\"/>" }
        zip.writeEntry(
            "OEBPS/content.opf",
            """<?xml version="1.0" encoding="UTF-8"?><package version="3.0" xmlns="http://www.idpf.org/2007/opf" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">lumi-$chapterCount</dc:identifier><dc:title>$title</dc:title><dc:creator>Lumi Benchmark</dc:creator><dc:language>zh-CN</dc:language></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>$manifest</manifest><spine>$spine</spine></package>"""
        )
        val nav = (1..chapterCount).joinToString("") { index ->
            "<li><a href=\"chapter$index.xhtml\">第${index}章</a></li>"
        }
        zip.writeEntry(
            "OEBPS/nav.xhtml",
            """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml"><body><nav epub:type="toc" xmlns:epub="http://www.idpf.org/2007/ops"><ol>$nav</ol></nav></body></html>"""
        )
        (1..chapterCount).forEach { index ->
            val paragraphs = (1..24).joinToString("") { paragraph ->
                "<p>第${index}章第${paragraph}段。确定性 EPUB 正文用于分页、跨章与预加载性能测量。</p>"
            }
            zip.writeEntry(
                "OEBPS/chapter$index.xhtml",
                """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>第${index}章</title></head><body><h1>第${index}章</h1>$paragraphs</body></html>"""
            )
        }
    }
}

val generateBenchmarkFixtures by tasks.registering {
    outputs.dir(generatedFixtures)
    doLast {
        val output = generatedFixtures.get().asFile.apply { mkdirs() }
        writeTxtFixture(File(output, "lumi_txt_1mb.txt"), 1 * 1024 * 1024, "Lumi TXT 1MB")
        writeTxtFixture(File(output, "lumi_txt_15mb.txt"), 15 * 1024 * 1024, "Lumi TXT 15MB")
        writeEpubFixture(File(output, "lumi_epub_regular.epub"), 24, "Lumi EPUB 24")
        writeEpubFixture(File(output, "lumi_epub_500.epub"), 500, "Lumi EPUB 500")
    }
}

android.sourceSets.getByName("main").assets.srcDir(generatedFixtures)
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) dependsOn(generateBenchmarkFixtures)
}
