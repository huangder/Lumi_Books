package com.huangder.lumibooks.util.epub

import java.io.ByteArrayOutputStream

object EpubPathResolver {
    fun normalize(path: String): String? {
        val clean = path.substringBefore('#').substringBefore('?')
        val decoded = decodePercent(clean).replace('\\', '/')
        if (decoded.isBlank() || decoded.startsWith('/') || decoded.contains('\u0000')) return null
        if (SCHEME_REGEX.containsMatchIn(decoded)) return null

        val segments = ArrayDeque<String>()
        for (segment in decoded.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty()) return null else segments.removeLast()
                else -> segments.addLast(segment)
            }
        }
        return segments.joinToString("/").takeIf { it.isNotEmpty() }
    }

    fun resolve(baseDocumentPath: String, reference: String): String? {
        val cleanReference = reference.substringBefore('#').substringBefore('?')
        if (cleanReference.isBlank()) return normalize(baseDocumentPath)
        if (cleanReference.startsWith('/') || SCHEME_REGEX.containsMatchIn(cleanReference)) return null
        val baseDirectory = baseDocumentPath.substringBeforeLast('/', "")
        return normalize(if (baseDirectory.isEmpty()) cleanReference else "$baseDirectory/$cleanReference")
    }

    fun fragment(reference: String): String? = reference.substringAfter('#', "")
        .takeIf { it.isNotEmpty() }
        ?.let(::decodePercent)

    private fun decodePercent(value: String): String {
        val output = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] != '%' || index + 2 >= value.length) {
                output.append(value[index++])
                continue
            }
            val bytes = ByteArrayOutputStream()
            while (index + 2 < value.length && value[index] == '%') {
                val byte = value.substring(index + 1, index + 3).toIntOrNull(16) ?: break
                bytes.write(byte)
                index += 3
            }
            if (bytes.size() == 0) output.append(value[index++])
            else output.append(bytes.toByteArray().toString(Charsets.UTF_8))
        }
        return output.toString()
    }

    private val SCHEME_REGEX = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
}
