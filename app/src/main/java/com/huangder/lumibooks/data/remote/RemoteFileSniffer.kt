package com.huangder.lumibooks.data.remote

import java.io.File

/**
 * Cheap content check before a download is imported: catalogs behind expired sessions often
 * answer with an HTML login page and a 200 status, which must not become a "book".
 */
object RemoteFileSniffer {
    fun matches(file: File, extension: String): Boolean {
        val head = ByteArray(512)
        val read = file.inputStream().use { it.read(head) }
        if (read <= 0) return false
        return matches(head.copyOf(read), extension)
    }

    fun matches(head: ByteArray, extension: String): Boolean = when (extension.lowercase()) {
        "epub", "cbz" -> head.startsWith(zipHeader)
        // Password-protected DOCX is also an OLE compound file. The converter reports the password error.
        "docx" -> head.startsWith(zipHeader) || head.startsWith(oleHeader)
        "doc" -> head.startsWith(oleHeader)
        "pdf" -> head.toString(Charsets.ISO_8859_1).take(1024).contains("%PDF-")
        "mobi", "azw", "azw3" -> head.size >= 68 && head.copyOfRange(60, 68).toString(Charsets.ISO_8859_1).let {
            it == "BOOKMOBI" || it == "TEXtREAd"
        }
        "txt", "md", "markdown" -> !looksLikeHtml(head) && head.none { it == 0.toByte() }
        else -> false
    }

    private fun looksLikeHtml(head: ByteArray): Boolean {
        val text = head.toString(Charsets.UTF_8).trimStart('﻿', ' ', '\n', '\r', '\t').lowercase()
        return text.startsWith("<!doctype html") || text.startsWith("<html")
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private val zipHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val oleHeader = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
}
