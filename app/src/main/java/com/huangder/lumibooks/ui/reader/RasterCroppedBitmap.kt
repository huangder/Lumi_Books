package com.huangder.lumibooks.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun RasterCroppedBitmap(
    bitmap: Bitmap,
    crop: RasterHorizontalCrop,
    fitToViewport: Boolean,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    onDrawn: () -> Unit = {}
) {
    val image = bitmap.asImageBitmap()
    Canvas(modifier) {
        val source = crop.sourceRect(bitmap.width, bitmap.height)
        val ratio = source.width().toFloat() / source.height().coerceAtLeast(1)
        val drawWidth = if (fitToViewport) min(size.width, size.height * ratio) else size.width
        val drawHeight = if (fitToViewport) min(size.height, drawWidth / ratio) else size.height
        drawImage(
            image = image,
            srcOffset = IntOffset(source.left, source.top),
            srcSize = IntSize(source.width(), source.height()),
            dstOffset = IntOffset(((size.width - drawWidth) / 2f).roundToInt(), ((size.height - drawHeight) / 2f).roundToInt()),
            dstSize = IntSize(drawWidth.roundToInt().coerceAtLeast(1), drawHeight.roundToInt().coerceAtLeast(1)),
            colorFilter = colorFilter
        )
        onDrawn()
    }
}
