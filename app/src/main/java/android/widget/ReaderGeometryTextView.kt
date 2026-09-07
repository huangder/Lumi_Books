package android.widget

import android.content.Context
import android.text.Layout
import android.util.AttributeSet

/**
 * TextView bridge for reader selection geometry.
 *
 * Android's Editor calls the package-private getOffsetAtCoordinate() while a
 * selection handle is being dragged. Keeping this bridge in android.widget
 * makes the method an actual framework override, so OEM Editors continue to
 * use their normal popup/gesture flow while the reader supplies corrected
 * coordinates for justified lines.
 */
internal open class ReaderGeometryTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextView(context, attrs, defStyleAttr) {
    /** Layout-relative line/x to offset; null preserves the platform mapping. */
    var readerOffsetMapper: ((Layout, Int, Float) -> Int?)? = null

    /**
     * Hidden framework entry point used by Editor.HandleView during dragging.
     * This must keep the exact (int, float) signature used by TextView.Editor.
     */
    @Suppress("unused")
    fun getOffsetAtCoordinate(line: Int, x: Float): Int {
        val textLayout = layout ?: return -1
        val localX = x - totalPaddingLeft + scrollX
        val mapped = readerOffsetMapper?.invoke(textLayout, line, localX)
        if (mapped != null) return mapped
        return textLayout.getOffsetForHorizontal(line, localX)
    }
}
