package com.huangder.lumibooks.ui.bookshare

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.huangder.lumibooks.ui.excerpt.ExcerptDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun ShareDocumentPreview(document: ExcerptDocument, modifier: Modifier = Modifier, onFailure: () -> Unit) {
    LazyColumn(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
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
            val current = bitmap
            if (current != null) Image(current.asImageBitmap(), null, Modifier.fillMaxWidth().aspectRatio(document.width.toFloat() / height))
            else Box(Modifier.fillMaxWidth().aspectRatio(document.width.toFloat() / height), Alignment.Center) { CircularProgressIndicator() }
        }
    }
}
