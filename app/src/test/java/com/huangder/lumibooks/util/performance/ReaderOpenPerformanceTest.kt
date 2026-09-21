package com.huangder.lumibooks.util.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderOpenPerformanceTest {
    @Test
    fun stageLedgerRecordsSuccessFailureAndIgnoresDuplicateCompletion() {
        val ledger = ReaderOpenStageLedger()

        assertTrue(ledger.begin(ReaderOpenStage.BOOK_RECORD, 100L))
        assertFalse(ledger.begin(ReaderOpenStage.BOOK_RECORD, 110L))
        assertEquals(
            ReaderOpenStageCompletion(ReaderOpenStage.BOOK_RECORD, 25L, "success"),
            ledger.finish(ReaderOpenStage.BOOK_RECORD, 125L, "success")
        )
        assertNull(ledger.finish(ReaderOpenStage.BOOK_RECORD, 130L, "failed"))

        assertTrue(ledger.begin(ReaderOpenStage.METADATA_PARSE, 200L))
        assertEquals(
            "failed",
            ledger.finish(ReaderOpenStage.METADATA_PARSE, 260L, "failed")?.result
        )
        assertTrue(ledger.activeStages().isEmpty())
    }

    @Test
    fun finishAllClosesEveryPendingStage() {
        val ledger = ReaderOpenStageLedger()
        ledger.begin(ReaderOpenStage.PAGINATION, 1_000L)
        ledger.begin(ReaderOpenStage.FIRST_FRAME, 1_500L)

        val completions = ledger.finishAll(2_000L, "cancelled")

        assertEquals(2, completions.size)
        assertTrue(completions.all { it.result == "cancelled" })
        assertTrue(ledger.activeStages().isEmpty())
    }

    @Test
    fun timeoutSnapshotContainsPendingStagesAndRendererState() {
        val attributes = readerOpenPendingAttributes(
            elapsedMs = 30_000L,
            activeStages = listOf(ReaderOpenStage.PAGINATION, ReaderOpenStage.FIRST_FRAME),
            state = ReaderOpenDiagnosticState(
                phase = "canvas_layout_pending",
                isLoading = true,
                pageReady = false,
                chapterIndex = 3,
                details = mapOf("renderMode" to "READER_LAYOUT")
            )
        )

        assertEquals(30_000L, attributes["elapsedMs"])
        assertEquals("FIRST_FRAME,PAGINATION", attributes["activeStages"])
        assertEquals("canvas_layout_pending", attributes["phase"])
        assertEquals(true, attributes["isLoading"])
        assertEquals("READER_LAYOUT", attributes["renderMode"])
    }
}
