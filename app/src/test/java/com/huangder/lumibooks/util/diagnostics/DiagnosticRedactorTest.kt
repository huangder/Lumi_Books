package com.huangder.lumibooks.util.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRedactorTest {
    private val redactor = DiagnosticRedactor.forTesting("test-salt")

    @Test
    fun removesSecretsAndPersonalIdentifiers() {
        val value = "Bearer abc123 token=secret https://example.com/api?token=x a@b.example 192.168.1.1"
        val output = redactor.sanitize(value)
        assertFalse(output.contains("abc123"))
        assertFalse(output.contains("secret"))
        assertFalse(output.contains("?token=x"))
        assertFalse(output.contains("a@b.example"))
        assertFalse(output.contains("192.168.1.1"))
    }

    @Test
    fun stableHashCorrelatesIdentifiersWithoutExposingValue() {
        assertEquals(redactor.hashIdentifier("book-1"), redactor.hashIdentifier("book-1"))
        assertNotEquals("book-1", redactor.hashIdentifier("book-1"))
        assertNotEquals(redactor.hashIdentifier("book-1"), redactor.hashIdentifier("book-2"))
    }

    @Test
    fun stackTraceKeepsUsefulContextButIsBounded() {
        val stack = (1..1000).joinToString("\n") { "at com.example.Reader.line$it(Reader.kt:$it)" }
        val output = redactor.sanitizeStackTrace(stack)
        assertTrue(output.contains("Reader.kt"))
        assertTrue(output.length <= 8192)
    }
}
