package com.huangder.lumibooks.ui.reader.engine

import android.os.Build
import android.text.Layout
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import kotlin.math.abs

/**
 * Maps text offsets to the horizontal coordinates used by a justified Layout.
 *
 * On some Android 15 implementations Editor still exposes the unexpanded
 * cursor positions even though TextLine draws the line with extra spacing.
 * The correction is deliberately derived from the Layout itself: if the
 * native line already reaches the paragraph edge, no correction is applied.
 * This keeps devices whose Editor is already correct on their native path.
 */
internal class ReaderLineGeometry(
    private val layout: Layout,
    private val text: CharSequence,
    private val justificationMode: Int = if (Build.VERSION.SDK_INT >= 35) {
        layout.getJustificationMode()
    } else {
        Layout.JUSTIFICATION_MODE_NONE
    },
    private val forceLastLineJustification: Boolean = false
) {
    data class HorizontalRange(val left: Float, val right: Float)

    private data class LineMetrics(
        val lineStart: Int,
        val rawLineEnd: Int,
        val contentEnd: Int,
        val baseStart: Float,
        val advances: FloatArray,
        val stretchUnitsBefore: IntArray,
        val unitCount: Int,
        val denominator: Int,
        val extraPerUnit: Float,
        val targetEnd: Float,
        val justified: Boolean,
        val nativeLineWidth: Float,
        /** Character rectangles in Layout coordinates, four floats per UTF-16 unit. */
        val characterBounds: FloatArray?,
        /** True when the vendor already reports the expanded positions in bounds. */
        val boundsAreExpanded: Boolean
    )

    private data class AdvanceMeasurement(
        val advances: FloatArray,
        val characterBounds: FloatArray?,
        val boundsWidth: Float?,
        /** True when character-boundary deltas grow across the line. */
        val boundsShowCumulativeExpansion: Boolean
    )

    private val metricsCache = HashMap<Int, LineMetrics>()

    fun horizontalRange(line: Int, start: Int, end: Int): HorizontalRange? {
        if (line !in 0 until layout.lineCount || start >= end) return null
        return try {
            val metrics = lineMetrics(line)
            val segmentStart = maxOf(start, metrics.lineStart)
            val segmentEnd = minOf(end, metrics.contentEnd)
            if (segmentStart >= segmentEnd) return null

            // Android 14+ exposes the actual shaped rectangles, including the
            // inter-character spacing applied by TextLine. Prefer those for a
            // justified selection so the painted range follows the glyphs rather
            // than a second approximation of the advances.
            if (metrics.justified && metrics.boundsAreExpanded) {
                characterBoundsRange(metrics, segmentStart, segmentEnd)?.let { return it }
            }

            val left = position(metrics, segmentStart)
            val right = if (segmentEnd >= metrics.contentEnd) {
                if (metrics.justified) {
                    metrics.targetEnd
                } else {
                    nativeLineEnd(metrics, line)
                }
            } else {
                position(metrics, segmentEnd)
            }
            if (!left.isFinite() || !right.isFinite()) {
                nativeRange(line, segmentStart, segmentEnd)
            } else {
                HorizontalRange(minOf(left, right), maxOf(left, right))
            }
        } catch (_: RuntimeException) {
            nativeRange(line, start, end)
        }
    }

    /** Returns a caret coordinate in Layout coordinates, or null if Layout is unavailable. */
    fun horizontalPosition(offset: Int, trailing: Boolean = false): Float? {
        if (layout.lineCount == 0 || text.isEmpty()) return null
        val safe = offset.coerceIn(0, text.length)
        return try {
            // Layout assigns a paragraph boundary to the following line. For a
            // selection end that boundary is the trailing edge of the previous
            // line; using the following line returns its leading x (usually 0).
            val line = lineForOffset(safe).let { candidate ->
                if (trailing && candidate > 0 && safe > 0 &&
                    layout.getLineStart(candidate) == safe
                ) candidate - 1 else candidate
            }
            val metrics = lineMetrics(line)
            val native = nativePosition(safe)
            val corrected = if (!metrics.justified) {
                native
            } else {
                position(metrics, safe, native)
            }
            corrected?.takeIf { it.isFinite() } ?: native
        } catch (_: RuntimeException) {
            nativePosition(safe)
        }
    }

    fun lineRange(line: Int): HorizontalRange? {
        if (line !in 0 until layout.lineCount) return null
        return try {
            val metrics = lineMetrics(line)
            if (metrics.contentEnd <= metrics.lineStart) return null
            val right = if (metrics.justified) {
                metrics.targetEnd
            } else {
                nativeLineEnd(metrics, line)
            }
            HorizontalRange(minOf(metrics.baseStart, right), maxOf(metrics.baseStart, right))
        } catch (_: RuntimeException) {
            null
        }
    }

    /** Maps a touch x coordinate through the same expansion as the drawn line. */
    fun offsetForHorizontal(line: Int, x: Float): Int? {
        if (line !in 0 until layout.lineCount) return null
        return try {
            val metrics = lineMetrics(line)
            val contentEnd = metrics.contentEnd
            if (contentEnd <= metrics.lineStart || !x.isFinite()) return null
            if (!metrics.justified) {
                return layout.getOffsetForHorizontal(line, x)
                    .coerceIn(metrics.lineStart, contentEnd)
            }
            // Editor/Layout choose a caret by the midpoint between adjacent
            // carets, rather than treating a character's whole advance as its
            // leading edge. The old interval test returned the character's
            // start even at its trailing edge, making the final character
            // impossible to select and biasing taps one character to the left.
            val caretCount = contentEnd - metrics.lineStart + 1
            if (caretCount <= 1) return metrics.lineStart
            val carets = FloatArray(caretCount)
            for (i in 0 until caretCount) {
                carets[i] = position(metrics, metrics.lineStart + i)
            }
            val increasing = carets.last() >= carets.first()
            if (increasing) {
                if (x <= carets.first()) return metrics.lineStart
                for (i in 0 until caretCount - 1) {
                    val midpoint = (carets[i] + carets[i + 1]) * 0.5f
                    if (x < midpoint) return metrics.lineStart + i
                }
                return contentEnd
            }
            if (x >= carets.first()) return metrics.lineStart
            for (i in 0 until caretCount - 1) {
                val midpoint = (carets[i] + carets[i + 1]) * 0.5f
                if (x > midpoint) return metrics.lineStart + i
            }
            return contentEnd
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun position(metrics: LineMetrics, offset: Int, native: Float? = null): Float {
        val safeOffset = offset.coerceIn(metrics.lineStart, metrics.rawLineEnd)
        val nativePosition = native ?: nativePosition(safeOffset)
        val base = nativePosition ?: run {
            val local = (safeOffset - metrics.lineStart).coerceIn(0, metrics.advances.size)
            metrics.baseStart + metrics.advances.take(local).sum()
        }
        if (!metrics.justified || safeOffset <= metrics.lineStart) return base

        if (metrics.boundsAreExpanded && safeOffset <= metrics.contentEnd) {
            characterBoundary(metrics, safeOffset)?.let { return it }
        }

        // Whitespace after contentEnd and the line break are not part of the
        // expansion. Keep their native position so a selection including them
        // cannot move beyond the visible line.
        if (safeOffset == metrics.contentEnd) return metrics.targetEnd
        if (safeOffset > metrics.contentEnd) return nativePosition ?: metrics.targetEnd

        val local = (safeOffset - metrics.lineStart).coerceIn(0, metrics.stretchUnitsBefore.lastIndex)
        val ownUnits = metrics.stretchUnitsBefore[local]
        val ownTotal = metrics.stretchUnitsBefore[(metrics.contentEnd - metrics.lineStart)
            .coerceIn(0, metrics.stretchUnitsBefore.lastIndex)]
        val unitsBefore = if (ownTotal > 0 && metrics.unitCount != ownTotal) {
            (ownUnits.toFloat() * metrics.unitCount / ownTotal).toInt()
        } else {
            ownUnits
        }
        return base + unitsBefore.coerceIn(0, metrics.denominator) * metrics.extraPerUnit
    }

    private fun lineMetrics(line: Int): LineMetrics = metricsCache.getOrPut(line) {
        require(line in 0 until layout.lineCount)
        val lineStart = layout.getLineStart(line).coerceIn(0, text.length)
        val rawLineEnd = layout.getLineEnd(line).coerceIn(lineStart, text.length)
        val contentEnd = readerLineContentEnd(text, lineStart, rawLineEnd)
        val measurement = fillAdvances(line, lineStart, rawLineEnd, contentEnd)
        val advances = measurement.advances

        val paragraphDirection = layout.getParagraphDirection(line)
        val paragraphLeft = layout.getParagraphLeft(line).toFloat()
        val paragraphRight = layout.getParagraphRight(line).toFloat()
        val isLtr = paragraphDirection != Layout.DIR_RIGHT_TO_LEFT
        val indent = leadingMargin(line)
        val baseStart = nativePosition(lineStart)
            ?: (paragraphLeft + if (isLtr) indent else 0f)

        // `getLineWidth()` is not a reliable source for the unexpanded width on
        // Android 15 vendor layouts. ColorOS can report the final paragraph width
        // there while its primary-horizontal caret positions are still native.
        // The caret span is the only value that describes the coordinate system
        // used by Editor, so prefer it and use getLineWidth only as a fallback.
        val measuredNativeLineWidth = nativeLineWidth(line, contentEnd, rawLineEnd, advances)
        val nativeEnd = nativePosition(contentEnd)
        val wrappedBoundary = contentEnd == rawLineEnd && line < layout.lineCount - 1
        // At a wrapped boundary ColorOS returns the following line's leading
        // caret (usually x=0). Do not treat that as this line's width; use the
        // sum of the character advances instead.
        val nativeEndForSpan = nativeEnd?.takeUnless {
            wrappedBoundary && abs(it - baseStart) < measuredNativeLineWidth * 0.5f
        }
        val nativeHorizontalSpan = abs(
            (nativeEndForSpan ?: (baseStart + measuredNativeLineWidth)) - baseStart
        ).takeIf { it.isFinite() && it > 0.01f } ?: measuredNativeLineWidth
        val ownUnitCount = countUnits(lineStart, contentEnd, advances)
        val unitCount = if (justificationMode == Layout.JUSTIFICATION_MODE_INTER_CHARACTER) {
            lineLetterSpacingUnitCount(line, ownUnitCount)
        } else {
            ownUnitCount
        }
        val denominator = when (justificationMode) {
            Layout.JUSTIFICATION_MODE_INTER_CHARACTER -> (unitCount - 1).coerceAtLeast(0)
            Layout.JUSTIFICATION_MODE_INTER_WORD -> unitCount
            else -> 0
        }

        val endsWithParagraphBreak = readerLineEndsParagraph(text, lineStart, rawLineEnd)
        // Only continuation lines may be stretched. forceLastLineJustification
        // covers the page's final line when the paragraph keeps flowing on the
        // next page; it must not bypass the paragraph-break guard for the rest of
        // the page, or every paragraph-final line here would be stretched.
        val canJustify = isLtr && justificationMode != Layout.JUSTIFICATION_MODE_NONE &&
            contentEnd > lineStart &&
            shouldJustifyReaderLine(
                lineIndex = line,
                lineCount = layout.lineCount,
                endsWithParagraphBreak = endsWithParagraphBreak,
                pageEndsMidParagraph = forceLastLineJustification
            )

        // ParagraphRight includes the available line width after alignment and
        // leading margins. For RTL the reader currently does not justify, but
        // retaining the native direction keeps this helper safe for mixed text.
        val targetEnd = if (isLtr) paragraphRight else paragraphLeft
        val targetWidth = if (isLtr) targetEnd - baseStart else baseStart - targetEnd
        // Some OEMs expose already-expanded character bounds while their
        // cursor helpers remain native. Treat a bounds span that reaches the
        // paragraph edge as the effective native width to avoid double spacing.
        // Glyph bounds are narrower than logical advances because of side
        // bearings. They should only be considered expanded when their span is
        // measurably beyond the native caret span; a percentage-sized tolerance
        // hides the small but cumulative ColorOS expansion on long lines.
        val boundsTolerance = maxOf(2f, layout.paint.textSize * 0.02f)
        val boundsAreExpanded = targetWidth > 0f && measurement.boundsWidth?.let { width ->
            // fillCharacterBounds() reports glyph ink bounds. A steadily
            // changing side-bearing delta is not sufficient evidence that the
            // vendor included justification: line-boundary carets and mixed
            // fonts produce the same pattern. Only accept bounds as expanded
            // when their complete visible span reaches the paragraph width (or
            // is measurably wider than the native caret span).
            width >= targetWidth - boundsTolerance ||
                width > nativeHorizontalSpan + boundsTolerance
        } == true
        val nativeLineWidth = if (boundsAreExpanded) {
            targetWidth
        } else {
            nativeHorizontalSpan
        }
        val extraTotal = targetWidth - nativeLineWidth
        // If a vendor already exposes expanded character bounds, the bounds are
        // used directly below and no second spacing estimate is applied.
        val extraPerUnit = if (canJustify && denominator > 0 && extraTotal > 0f) {
            extraTotal / denominator
        } else {
            0f
        }
        val justified = canJustify &&
            (extraPerUnit > 0f || boundsAreExpanded) &&
            targetEnd.isFinite() && baseStart.isFinite()

        // Keep the exact target edge instead of accumulating rounded spacing.
        // This is also the endpoint used for a selection ending at the line.
        val resolvedTargetEnd = if (justified) targetEnd else {
            if (isLtr) baseStart + nativeLineWidth else baseStart - nativeLineWidth
        }

        LineMetrics(
            lineStart = lineStart,
            rawLineEnd = rawLineEnd,
            contentEnd = contentEnd,
            baseStart = baseStart,
            advances = advances,
            stretchUnitsBefore = buildStretchUnitsBefore(lineStart, rawLineEnd, contentEnd, advances),
            unitCount = unitCount.coerceAtLeast(0),
            denominator = denominator,
            extraPerUnit = extraPerUnit,
            targetEnd = resolvedTargetEnd,
            justified = justified,
            nativeLineWidth = nativeLineWidth,
            characterBounds = measurement.characterBounds,
            boundsAreExpanded = boundsAreExpanded
        )
    }

    private fun nativeLineWidth(
        line: Int,
        contentEnd: Int,
        rawLineEnd: Int,
        advances: FloatArray
    ): Float {
        // Primary-horizontal deltas are the unexpanded advances used by Editor.
        // getLineWidth() may already contain OEM justification spacing on Android
        // 15, so prefer the per-character sum whenever it is available.
        val contentLength = (contentEnd - layout.getLineStart(line)).coerceIn(0, advances.size)
        val advanceSum = advances.take(contentLength).sum()
        if (advanceSum.isFinite() && advanceSum > 0.01f) {
            return advanceSum
        }
        val measured = layout.getLineWidth(line)
        if (measured.isFinite() && measured >= 0f) {
            val trailingLength = (rawLineEnd - contentEnd).coerceIn(0, advances.size)
            val trailingWidth = advances.takeLast(trailingLength).sum()
            return (measured - trailingWidth).coerceAtLeast(0f)
        }
        return advanceSum.coerceAtLeast(0f)
    }

    private fun fillAdvances(
        line: Int,
        start: Int,
        end: Int,
        contentEnd: Int
    ): AdvanceMeasurement {
        val output = FloatArray((end - start).coerceAtLeast(0))
        if (start >= end) return AdvanceMeasurement(output, null, null, false)

        // Bounds describe visible glyphs and may be narrower than their logical
        // advances. Use primary-horizontal deltas for line measurement and unit
        // counting; bounds are retained separately for selection painting.
        for (i in output.indices) {
            val offset = start + i
            if (offset >= contentEnd && (text[offset] == '\n' || text[offset] == '\r')) {
                // A paragraph break has no horizontal advance. On some layouts
                // the next line's primary position would otherwise be mistaken
                // for the width of this line break.
                output[i] = 0f
                continue
            }
            val left = runCatching { layout.getPrimaryHorizontal(offset) }.getOrNull()
            val right = if (offset + 1 == contentEnd && contentEnd == end && line < layout.lineCount - 1) {
                // A wrapped line's content end is also the next line's start.
                // Its primary horizontal is commonly 0; measure only this glyph
                // for the native advance and resolve the line endpoint separately.
                runCatching { abs(layout.paint.measureText(text, offset, offset + 1)) }.getOrNull()
                    ?.let { measured -> (left ?: 0f) + measured }
            } else {
                runCatching { layout.getPrimaryHorizontal(offset + 1) }.getOrNull()
            }
            val delta = if (left != null && right != null && left.isFinite() && right.isFinite()) {
                abs(right - left)
            } else {
                runCatching { abs(layout.paint.measureText(text, offset, offset + 1)) }.getOrDefault(0f)
            }
            output[i] = if (delta.isFinite()) delta else 0f
        }

        var boundsValid = false
        var boundsLeft = Float.POSITIVE_INFINITY
        var boundsRight = Float.NEGATIVE_INFINITY
        var characterBounds: FloatArray? = null
        if (Build.VERSION.SDK_INT >= 34) {
            val bounds = FloatArray((end - start) * 4)
            try {
                layout.fillCharacterBounds(start, end, bounds, 0)
                boundsValid = bounds.all(Float::isFinite)
                if (boundsValid) {
                    for (i in output.indices) {
                        val width = abs(bounds[i * 4 + 2] - bounds[i * 4])
                        if (!width.isFinite() || width < 0f) {
                            boundsValid = false
                            break
                        }
                        val offset = start + i
                        if (offset < contentEnd && width > 0.01f) {
                            boundsLeft = minOf(boundsLeft, bounds[i * 4])
                            boundsRight = maxOf(boundsRight, bounds[i * 4 + 2])
                        }
                    }
                    if (boundsValid) characterBounds = bounds
                }
            } catch (_: RuntimeException) {
                boundsValid = false
            }
        }
        if (boundsValid) {
            val hasVisibleGlyph = output.any { it > 0.01f }
            val hasNonWhitespace = (start until end).any { !text[it].isWhitespace() }
            if (hasVisibleGlyph || !hasNonWhitespace) {
                val boundsWidth = if (boundsLeft.isFinite() && boundsRight.isFinite()) {
                    (boundsRight - boundsLeft).takeIf { it.isFinite() && it >= 0f }
                } else {
                    null
                }
                var boundsShowCumulativeExpansion = false
                if (characterBounds != null && contentEnd - start >= 3) {
                    var firstDelta = Float.NaN
                    var lastDelta = Float.NaN
                    var previousDelta = Float.NaN
                    var increasingSteps = 0
                    var samples = 0
                    for (offset in start until contentEnd) {
                        val local = offset - start
                        val boundLeft = characterBounds[local * 4]
                        val nativeLeft = nativePosition(offset)
                        if (!boundLeft.isFinite() || nativeLeft == null) continue
                        val delta = boundLeft - nativeLeft
                        if (!delta.isFinite()) continue
                        if (firstDelta.isNaN()) firstDelta = delta
                        if (!previousDelta.isNaN() && delta > previousDelta + 0.5f) increasingSteps++
                        previousDelta = delta
                        lastDelta = delta
                        samples++
                    }
                    // A glyph side bearing is roughly constant; justification
                    // creates a steadily growing offset toward the line end.
                    boundsShowCumulativeExpansion = samples >= 3 &&
                        (lastDelta - firstDelta) > maxOf(2f, layout.paint.textSize * 0.02f) &&
                        increasingSteps >= (samples - 1) / 3
                }
                return AdvanceMeasurement(
                    output,
                    characterBounds,
                    boundsWidth,
                    boundsShowCumulativeExpansion
                )
            }
        }
        // Vendor Layouts may reject custom ImageSpan/ReplacementSpan bounds. The
        // primary-horizontal values above remain the native fallback in that case.
        return AdvanceMeasurement(output, null, null, false)
    }

    private fun characterBoundary(metrics: LineMetrics, offset: Int): Float? {
        val bounds = metrics.characterBounds ?: return null
        val local = (offset - metrics.lineStart).coerceIn(0, bounds.size / 4)
        if (local >= bounds.size / 4) {
            return characterBoundary(metrics, metrics.contentEnd - 1, right = true)
        }
        val left = bounds[local * 4]
        val right = bounds[local * 4 + 2]
        if (!left.isFinite() || !right.isFinite()) return null
        if (abs(right - left) <= 0.01f) return null
        // A caret before a character uses its leading edge. For the end of the
        // content use the trailing edge of the final visible character.
        return if (offset >= metrics.contentEnd) {
            characterBoundary(metrics, metrics.contentEnd - 1, right = true)
        } else {
            left
        }
    }

    private fun characterBoundary(metrics: LineMetrics, offset: Int, right: Boolean): Float? {
        val bounds = metrics.characterBounds ?: return null
        val local = (offset - metrics.lineStart).coerceIn(0, bounds.size / 4 - 1)
        if (local < 0 || bounds.isEmpty()) return null
        val value = bounds[local * 4 + if (right) 2 else 0]
        return value.takeIf(Float::isFinite)
    }

    private fun characterBoundsRange(
        metrics: LineMetrics,
        start: Int,
        end: Int
    ): HorizontalRange? {
        val bounds = metrics.characterBounds ?: return null
        var left = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var visible = false
        for (offset in start until end) {
            if (offset >= metrics.contentEnd) break
            val local = offset - metrics.lineStart
            if (local < 0 || local * 4 + 3 >= bounds.size) continue
            val boundLeft = bounds[local * 4]
            val boundRight = bounds[local * 4 + 2]
            if (!boundLeft.isFinite() || !boundRight.isFinite()) continue
            if (abs(boundRight - boundLeft) > 0.01f) {
                left = minOf(left, boundLeft, boundRight)
                right = maxOf(right, boundLeft, boundRight)
                visible = true
            }
        }
        if (!visible || !left.isFinite() || !right.isFinite() || right <= left) return null
        val selectedEnd = end.coerceAtMost(metrics.contentEnd)
        val finalRight = if (selectedEnd >= metrics.contentEnd) {
            maxOf(right, characterBoundary(metrics, selectedEnd, right = true) ?: right)
        } else {
            right
        }
        return HorizontalRange(left, finalRight)
    }

    private fun countUnits(start: Int, end: Int, advances: FloatArray): Int {
        if (justificationMode == Layout.JUSTIFICATION_MODE_NONE) return 0
        var count = 0
        var index = start
        while (index < end) {
            val local = index - start
            val codePoint = Character.codePointAt(text, index)
            val charCount = Character.charCount(codePoint).coerceAtMost(end - index)
            val visible = text[index] != '\n' && text[index] != '\r'
            val hasAdvance = (0 until charCount).any { advances.getOrNull(local + it)?.let { w -> w > 0.01f } == true }
            val type = Character.getType(codePoint)
            val isCombining = type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt()
            val unit = when (justificationMode) {
                Layout.JUSTIFICATION_MODE_INTER_WORD -> text[index] == ' '
                Layout.JUSTIFICATION_MODE_INTER_CHARACTER -> visible && hasAdvance && !isCombining
                else -> false
            }
            if (unit) count++
            index += charCount
        }
        return count
    }

    private fun buildStretchUnitsBefore(
        start: Int,
        end: Int,
        contentEnd: Int,
        advances: FloatArray
    ): IntArray {
        val result = IntArray((end - start).coerceAtLeast(0) + 1)
        var index = start
        while (index < end) {
            val local = index - start
            val codePoint = Character.codePointAt(text, index)
            val charCount = Character.charCount(codePoint).coerceAtMost(end - index)
            val visible = index < contentEnd && text[index] != '\n' && text[index] != '\r'
            val hasAdvance = (0 until charCount).any { advances.getOrNull(local + it)?.let { w -> w > 0.01f } == true }
            val type = Character.getType(codePoint)
            val isCombining = type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt()
            val unit = when (justificationMode) {
                Layout.JUSTIFICATION_MODE_INTER_WORD -> index < contentEnd && text[index] == ' '
                Layout.JUSTIFICATION_MODE_INTER_CHARACTER -> visible && hasAdvance && !isCombining
                else -> false
            }
            repeat(charCount) { result[local + it + 1] = result[local] + if (unit) 1 else 0 }
            index += charCount
        }
        return result
    }

    private fun lineLetterSpacingUnitCount(line: Int, fallback: Int): Int {
        if (Build.VERSION.SDK_INT < 35) return fallback
        return runCatching { layout.getLineLetterSpacingUnitCount(line, false) }
            .getOrNull()
            ?.takeIf { it > 0 }
            ?: fallback
    }

    private fun lineForOffset(offset: Int): Int {
        if (text.isEmpty()) return 0
        return if (offset >= text.length) {
            (layout.lineCount - 1).coerceAtLeast(0)
        } else {
            layout.getLineForOffset(offset.coerceAtLeast(0))
        }
    }

    /** Returns this line's trailing edge instead of the following line's x=0 caret. */
    private fun nativeContentEndPosition(
        line: Int,
        lineStart: Int,
        rawLineEnd: Int,
        contentEnd: Int
    ): Float? {
        if (contentEnd == rawLineEnd && contentEnd > lineStart && line < layout.lineCount - 1) {
            return runCatching { layout.getLineRight(line) }
                .getOrNull()
                ?.takeIf(Float::isFinite)
        }
        return nativePosition(contentEnd)
    }

    private fun nativePosition(offset: Int): Float? = runCatching {
        layout.getPrimaryHorizontal(offset.coerceIn(0, text.length))
    }.getOrNull()?.takeIf(Float::isFinite)

    private fun safeLineRight(line: Int, fallback: Float): Float = runCatching {
        layout.getLineRight(line)
    }.getOrNull()?.takeIf(Float::isFinite) ?: fallback

    private fun nativeLineEnd(metrics: LineMetrics, line: Int): Float {
        val fallback = metrics.baseStart + metrics.nativeLineWidth
        // A trimmed line must not inherit the width of its trailing spaces.
        if (metrics.contentEnd < metrics.rawLineEnd) return fallback
        return safeLineRight(line, fallback)
    }

    private fun nativeRange(line: Int, start: Int, end: Int): HorizontalRange? {
        val safeStart = maxOf(start, layout.getLineStart(line))
        val safeEnd = minOf(end, layout.getLineEnd(line))
        val left = nativePosition(safeStart) ?: return null
        val right = nativePosition(safeEnd) ?: return null
        return HorizontalRange(minOf(left, right), maxOf(left, right))
    }

    private fun leadingMargin(line: Int): Float {
        if (text !is Spanned) return 0f
        val start = layout.getLineStart(line)
        if (start >= text.length) return 0f
        val firstLine = line == 0 || (start > 0 && text[start - 1] == '\n')
        return text.getSpans(
            start,
            (start + 1).coerceAtMost(text.length),
            LeadingMarginSpan::class.java
        ).sumOf { it.getLeadingMargin(firstLine).toDouble() }.toFloat()
    }
}
