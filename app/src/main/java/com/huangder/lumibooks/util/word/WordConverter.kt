package com.huangder.lumibooks.util.word

import org.apache.poi.EncryptedDocumentException
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.HWPFDocumentCore
import org.apache.poi.hwpf.OldWordFileFormatException
import org.apache.poi.hwpf.converter.WordToHtmlConverter
import org.apache.poi.hwpf.usermodel.Paragraph
import org.apache.poi.poifs.filesystem.FileMagic
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.w3c.dom.Element
import org.zwobble.mammoth.DocumentConverter
import org.zwobble.mammoth.images.ImageConverter
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import java.io.StringWriter

internal enum class WordFailure { INVALID, ENCRYPTED, UNSUPPORTED, LIMIT, IO, CONVERSION }

internal class WordException(val reason: WordFailure, cause: Throwable? = null) : IOException(reason.name, cause)

internal const val MAX_WORD_BYTES = 64L * 1024 * 1024
private const val MAX_EXPANDED_BYTES = 128L * 1024 * 1024
private const val MAX_XML_BYTES = 16L * 1024 * 1024
private const val MAX_DOCX_ENTRIES = 4096
private const val MAX_IMAGE_BYTES = 16 * 1024 * 1024

internal fun checkWordCancellation() {
    if (Thread.currentThread().isInterrupted) throw CancellationException("Word conversion cancelled")
}

internal data class WordMetadata(val title: String? = null, val author: String? = null)

internal object WordConverter {
    fun convert(source: File, extension: String, key: String, stage: File, output: File) {
        if (source.length() > MAX_WORD_BYTES) throw WordException(WordFailure.LIMIT)
        checkWordCancellation()
        val images = WordImages(stage)
        val result = try {
            when (extension) {
                "docx" -> docx(source, images, stage)
                "doc" -> doc(source, images)
                else -> throw WordException(WordFailure.UNSUPPORTED)
            }
        } catch (error: WordException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: EncryptedDocumentException) {
            throw WordException(WordFailure.ENCRYPTED, error)
        } catch (error: OldWordFileFormatException) {
            throw WordException(WordFailure.UNSUPPORTED, error)
        } catch (error: Exception) {
            // Mammoth and HWPF may wrap exceptions thrown by image callbacks.
            val cause = generateSequence<Throwable>(error) { it.cause }.firstOrNull {
                it is WordException || it is CancellationException
            }
            if (cause != null) throw cause
            throw WordException(WordFailure.INVALID, error)
        } catch (error: LinkageError) {
            throw WordException(WordFailure.CONVERSION, error)
        }
        checkWordCancellation()
        WordEpubWriter.write(result.first, result.second, images.files, key, output)
    }

    private fun docx(source: File, images: WordImages, stage: File): Pair<String, WordMetadata> {
        if (FileMagic.valueOf(source) == FileMagic.OLE2) {
            POIFSFileSystem(source, true).use { fs ->
                if (fs.root.hasEntry("EncryptedPackage")) throw WordException(WordFailure.ENCRYPTED)
            }
            throw WordException(WordFailure.INVALID)
        }
        val normalized = File(stage, "outline.docx")
        val (metadata, styles) = ZipFile(source).use { zip ->
            checkDocxArchive(zip)
            val directStyles = DocxStructure.normalizeDirectOutline(zip, normalized)
            DocxStructure.metadata(zip) to (directStyles + "\n" + DocxStructure.styleMap(zip))
        }
        val converter = DocumentConverter().idPrefix("word-").disableEmbeddedStyleMap().addStyleMap(styles)
            .imageConverter(ImageConverter.ImgElement { image ->
                val stream = try { image.inputStream } catch (_: IOException) { null }
                val path = stream?.use(images::writeDocx)
                if (path == null) emptyMap() else mapOf("src" to path)
            })
        return converter.convertToHtml(normalized.takeIf(File::isFile) ?: source).value to metadata
    }

    private fun checkDocxArchive(zip: ZipFile) {
        if (zip.size() > MAX_DOCX_ENTRIES) throw WordException(WordFailure.LIMIT)
        if (zip.getEntry("[Content_Types].xml") == null || zip.getEntry("_rels/.rels") == null) {
            throw WordException(WordFailure.INVALID)
        }
        val names = HashSet<String>()
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        for (entry in zip.entries().asSequence()) {
            val name = entry.name
            if (name.startsWith('/') || '\\' in name || ".." in name.split('/') || !names.add(name)) {
                throw WordException(WordFailure.INVALID)
            }
            if (entry.isDirectory) continue
            val limit = if (name.endsWith(".xml", true) || name.endsWith(".rels", true)) MAX_XML_BYTES else MAX_EXPANDED_BYTES
            if (entry.size > minOf(limit, MAX_EXPANDED_BYTES - total)) throw WordException(WordFailure.LIMIT)
            var expanded = 0L
            zip.getInputStream(entry).use { input ->
                while (true) {
                    checkWordCancellation()
                    val count = input.read(buffer)
                    if (count < 0) break
                    expanded += count
                    total += count
                    if (expanded > limit || total > MAX_EXPANDED_BYTES) throw WordException(WordFailure.LIMIT)
                }
            }
            if (expanded != entry.size) throw WordException(WordFailure.INVALID)
        }
    }

    private fun doc(source: File, images: WordImages): Pair<String, WordMetadata> {
        POIFSFileSystem(source, true).use { fs ->
            val root = fs.root
            if (root.hasEntry("EncryptedPackage")) throw WordException(WordFailure.ENCRYPTED)
            if (!root.hasEntry("WordDocument")) throw WordException(WordFailure.INVALID)
            root.createDocumentInputStream("WordDocument").use { fib ->
                fib.skip(11)
                val flags = fib.read()
                if (flags >= 0 && flags and 0x01 != 0) throw WordException(WordFailure.ENCRYPTED)
            }
            HWPFDocument(root).use { document ->
                val dom = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
                val converter = object : WordToHtmlConverter(dom) {
                    override fun processParagraph(doc: HWPFDocumentCore, parent: Element, level: Int, paragraph: Paragraph, bullet: String?) {
                        checkWordCancellation()
                        super.processParagraph(doc, parent, level, paragraph, bullet)
                        val outline = paragraph.lvl
                        val element = parent.lastChild as? Element
                        if (outline in 0..5 && element?.tagName == "p" && element.textContent.isNotBlank()) {
                            // Android's DOM implementation does not implement renameNode on every API level.
                            val heading = dom.createElement("h${outline + 1}")
                            while (element.hasChildNodes()) heading.appendChild(element.firstChild)
                            parent.replaceChild(heading, element)
                        }
                    }
                }
                converter.setPicturesManager { content, type, _, _, _ ->
                    if (images.supports(type.extension)) content.inputStream().use { images.write(type.extension, it) }.orEmpty()
                    else ""
                }
                converter.processDocument(document)
                val html = StringWriter()
                TransformerFactory.newInstance().newTransformer().transform(DOMSource(dom), StreamResult(html))
                val info = document.summaryInformation
                return html.toString() to WordMetadata(info?.title, info?.author)
            }
        }
    }
}

private class WordImages(private val stage: File) {
    val files = linkedMapOf<String, File>()
    private var bytes = 0L

    fun supports(type: String): Boolean = extension(type) != null

    fun writeDocx(input: InputStream): String? {
        val stream = input.buffered()
        stream.mark(64)
        val prefix = ByteArray(32)
        var length = 0
        while (length < prefix.size) {
            val count = stream.read(prefix, length, prefix.size - length)
            if (count <= 0) break
            length += count
        }
        stream.reset()
        val type = when (FileMagic.valueOf(prefix.copyOf(length))) {
            FileMagic.JPEG -> "jpg"
            FileMagic.PNG -> "png"
            FileMagic.GIF -> "gif"
            FileMagic.BMP -> "bmp"
            else -> if (length >= 12 && prefix.decodeToString(0, 4) == "RIFF" && prefix.decodeToString(8, 12) == "WEBP") "webp" else return null
        }
        return write(type, stream)
    }

    fun write(type: String, input: InputStream): String? {
        val extension = extension(type) ?: return null
        checkWordCancellation()
        if (files.size >= 1024) throw WordException(WordFailure.LIMIT)
        val path = "images/image-${files.size + 1}.$extension"
        val file = File(stage, path).also { it.parentFile?.mkdirs() }
        val count = file.outputStream().buffered().use { output ->
            copyWordBytes(input, output, minOf(MAX_IMAGE_BYTES.toLong(), MAX_WORD_BYTES - bytes))
        }
        if (count == 0L) return null
        bytes += count
        files[path] = file
        return path
    }

    private fun extension(type: String): String? = when (type.lowercase()) {
        "jpeg", "jpg", "pjpeg" -> "jpg"
        "png", "gif", "webp", "bmp" -> type.lowercase()
        else -> null
    }
}

internal fun copyWordBytes(input: InputStream, output: java.io.OutputStream, limit: Long): Long {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        checkWordCancellation()
        val count = input.read(buffer)
        if (count < 0) return total
        total += count
        if (total > limit) throw WordException(WordFailure.LIMIT)
        output.write(buffer, 0, count)
    }
}
