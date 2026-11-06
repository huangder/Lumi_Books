package com.huangder.lumibooks.ui.bookshare

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.animation.ShimmerBox
import com.huangder.lumibooks.ui.animation.VisualMotion
import com.huangder.lumibooks.ui.excerpt.ExcerptDocument
import com.huangder.lumibooks.ui.statistics.insightsMotion
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tiled preview of a rendered share image. Tiles join into one rounded "print" with shimmer
 * placeholders that fade into the rendered bitmap.
 */
@Composable internal fun ShareDocumentPreview(document: ExcerptDocument, modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 16.dp), onFailure: () -> Unit) {
    val motion = insightsMotion()
    LazyColumn(modifier, contentPadding = contentPadding, horizontalAlignment = Alignment.CenterHorizontally) {
        val tileCount = (document.height + 1199) / 1200
        items(tileCount, key = { it }) { index ->
            val height = minOf(1200, document.height - index * 1200)
            val bitmap by produceState<Bitmap?>(null, document, index) {
                try { value = withContext(Dispatchers.Default) { document.render(index * 1200, height) } }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { onFailure() }
                catch (_: OutOfMemoryError) { onFailure() }
            }
            DisposableEffect(bitmap) {
                val owned = bitmap
                onDispose { owned?.recycle() }
            }
            val radius = 18.dp
            val shape = AppRoundedCornerShape(
                topStart = if (index == 0) radius else 0.dp, topEnd = if (index == 0) radius else 0.dp,
                bottomEnd = if (index == tileCount - 1) radius else 0.dp, bottomStart = if (index == tileCount - 1) radius else 0.dp)
            val tile = Modifier.fillMaxWidth().aspectRatio(document.width.toFloat() / height).clip(shape)
            val current = bitmap
            val alpha by animateFloatAsState(if (current != null) 1f else 0f,
                if (motion) VisualMotion.tween(260) else androidx.compose.animation.core.snap(), label = "shareTile")
            Box(tile.background(AppColors.BgGray)) {
                if (current == null || alpha < 1f) {
                    if (motion) ShimmerBox(Modifier.fillMaxSize()) else Box(Modifier.fillMaxSize())
                }
                if (current != null) Image(current.asImageBitmap(), null, Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha })
            }
        }
    }
}
