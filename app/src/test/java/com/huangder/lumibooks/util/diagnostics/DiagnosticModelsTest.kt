package com.huangder.lumibooks.util.diagnostics

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiagnosticModelsTest {
    @Test
    fun eventRoundTripsThroughJson() {
        val original = DiagnosticEvent(
            timestamp = 42L,
            level = DiagnosticLevel.ERROR,
            category = "reader",
            event = "render_failed",
            sessionId = "s1",
            operationId = "op1",
            durationMs = 12L,
            attributes = mapOf("chapter" to "3"),
            exceptionType = "java.io.IOException"
        )
        val restored = DiagnosticEvent.fromJson(JSONObject(original.toJson().toString()))
        assertEquals(original, restored)
    }

    @Test
    fun unknownIssueTypeFallsBackToOther() {
        assertEquals(DiagnosticIssueType.OTHER, DiagnosticIssueType.fromId("not-a-type"))
        assertNull(DiagnosticIssueType.fromId(null).takeIf { it != DiagnosticIssueType.OTHER })
    }
}
