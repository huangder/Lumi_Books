package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationRangeEditorTest {
    private fun segment(
        start: Int,
        end: Int,
        type: String = "underline",
        color: String = "red",
        note: String = ""
    ) = AnnotationSegment(start, end, type, color, note)

    @Test
    fun replaceRangeLeavesNonOverlappingSegmentsUntouched() {
        val result = AnnotationRangeEditor.replaceRange(
            existing = listOf(segment(0, 5)),
            replacement = segment(10, 20, color = "blue")
        )

        assertEquals(
            listOf(segment(0, 5), segment(10, 20, color = "blue")),
            result
        )
    }

    @Test
    fun replaceRangeOverwritesOnlyOverlappingPartAndPreservesOuterColors() {
        val result = AnnotationRangeEditor.replaceRange(
            existing = listOf(segment(10, 20, color = "red")),
            replacement = segment(15, 30, color = "blue")
        )

        assertEquals(
            listOf(
                segment(10, 15, color = "red"),
                segment(15, 30, color = "blue")
            ),
            result
        )
    }

    @Test
    fun replaceRangeInsideExistingSplitsItIntoThreeSegments() {
        val result = AnnotationRangeEditor.replaceRange(
            existing = listOf(segment(10, 30, color = "red", note = "memo")),
            replacement = segment(15, 20, color = "blue")
        )

        assertEquals(
            listOf(
                segment(10, 15, color = "red", note = "memo"),
                segment(15, 20, color = "blue"),
                segment(20, 30, color = "red")
            ),
            result
        )
    }

    @Test
    fun replaceRangeCombinesCoverageAcrossMultipleExistingSegments() {
        val result = AnnotationRangeEditor.replaceRange(
            existing = listOf(
                segment(0, 10, color = "red"),
                segment(12, 18, color = "green"),
                segment(25, 30, color = "yellow")
            ),
            replacement = segment(5, 25, color = "blue")
        )

        assertEquals(
            listOf(
                segment(0, 5, color = "red"),
                segment(5, 25, color = "blue"),
                segment(25, 30, color = "yellow")
            ),
            result
        )
    }

    @Test
    fun replaceRangeDoesNotModifyAnotherAnnotationType() {
        val result = AnnotationRangeEditor.replaceRange(
            existing = listOf(
                segment(10, 20, type = "highlight", color = "yellow"),
                segment(10, 20, type = "underline", color = "red")
            ),
            replacement = segment(15, 25, type = "underline", color = "blue")
        )

        assertEquals(
            listOf(
                segment(10, 15, type = "underline", color = "red"),
                segment(10, 20, type = "highlight", color = "yellow"),
                segment(15, 25, type = "underline", color = "blue")
            ),
            result
        )
    }

    @Test
    fun replaceRangeFullyContainingExistingRemovesOldCoverage() {
        val result = AnnotationRangeEditor.replaceRange(
            existing = listOf(
                segment(10, 20, color = "red"),
                segment(22, 25, color = "green")
            ),
            replacement = segment(5, 30, color = "blue")
        )

        assertEquals(listOf(segment(5, 30, color = "blue")), result)
    }

    @Test
    fun replaceExactlySameRangeChangesColorWithoutDuplicate() {
        val result = AnnotationRangeEditor.replaceRange(
            existing = listOf(segment(10, 20, color = "red")),
            replacement = segment(10, 20, color = "blue")
        )

        assertEquals(listOf(segment(10, 20, color = "blue")), result)
    }

    @Test
    fun removeRangeLeavesNonOverlappingAndDifferentTypesUntouched() {
        val result = AnnotationRangeEditor.removeRange(
            existing = listOf(
                segment(0, 5),
                segment(10, 20, type = "highlight")
            ),
            targetStart = 10,
            targetEnd = 15,
            type = "underline"
        )

        assertEquals(
            listOf(segment(0, 5), segment(10, 20, type = "highlight")),
            result
        )
    }

    @Test
    fun removeRangeDeletesExactlyCoveredSegment() {
        val result = AnnotationRangeEditor.removeRange(
            existing = listOf(segment(10, 20)),
            targetStart = 10,
            targetEnd = 20,
            type = "underline"
        )

        assertEquals(emptyList<AnnotationSegment>(), result)
    }

    @Test
    fun removeRangeTrimsLeftOrRightIntersection() {
        assertEquals(
            listOf(segment(15, 20, note = "memo")),
            AnnotationRangeEditor.removeRange(
                listOf(segment(10, 20, note = "memo")),
                targetStart = 5,
                targetEnd = 15,
                type = "underline"
            )
        )
        assertEquals(
            listOf(segment(10, 15, note = "memo")),
            AnnotationRangeEditor.removeRange(
                listOf(segment(10, 20, note = "memo")),
                targetStart = 15,
                targetEnd = 25,
                type = "underline"
            )
        )
    }

    @Test
    fun removeRangeInsideSegmentSplitsItAndKeepsNoteOnlyOnLeft() {
        val result = AnnotationRangeEditor.removeRange(
            existing = listOf(segment(10, 30, note = "memo")),
            targetStart = 15,
            targetEnd = 20,
            type = "underline"
        )

        assertEquals(
            listOf(
                segment(10, 15, note = "memo"),
                segment(20, 30, note = "")
            ),
            result
        )
    }

    @Test
    fun removeRangeAcrossMultipleSegmentsTrimsAndDeletesAsNeeded() {
        val result = AnnotationRangeEditor.removeRange(
            existing = listOf(
                segment(0, 10, color = "red"),
                segment(12, 18, color = "green"),
                segment(20, 30, color = "blue")
            ),
            targetStart = 5,
            targetEnd = 25,
            type = "underline"
        )

        assertEquals(
            listOf(
                segment(0, 5, color = "red"),
                segment(25, 30, color = "blue")
            ),
            result
        )
    }

    @Test
    fun invalidOrEmptyRangesAreRejected() {
        val existing = listOf(segment(0, 5))
        assertEquals(existing, AnnotationRangeEditor.removeRange(existing, 3, 3, "underline"))
        assertEquals(existing, AnnotationRangeEditor.replaceRange(existing, segment(9, 9)))
    }
}
