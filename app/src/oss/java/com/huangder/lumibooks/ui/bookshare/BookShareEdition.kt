package com.huangder.lumibooks.ui.bookshare

import android.content.Context
import androidx.compose.runtime.Composable
import com.huangder.lumibooks.ui.excerpt.ExcerptDocument

internal object BookShareEdition {
    val styles = listOf(BookShareStyle.SIMPLE)
    fun prepare(context: Context, data: BookShareSnapshot, kind: BookShareKind, style: BookShareStyle): ExcerptDocument? = null
}
@Composable internal fun rememberBookShareExportPermission(): (BookShareStyle, () -> Unit) -> Unit =
    { _, action -> action() }
