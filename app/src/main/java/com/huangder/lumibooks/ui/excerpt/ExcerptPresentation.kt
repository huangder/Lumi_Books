package com.huangder.lumibooks.ui.excerpt

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.AppSheetHeader
import com.huangder.lumibooks.ui.components.CollapsingPageScaffold
import com.huangder.lumibooks.ui.theme.AppSpace

/** Fullscreen navigation and reader sheets deliberately retain separate presentation contracts. */
enum class ExcerptPresentation { FULLSCREEN, SHEET }

@Composable
internal fun ExcerptPreviewLayout(
    presentation: ExcerptPresentation,
    onClose: () -> Unit,
    content: @Composable ColumnScope.(Dp) -> Unit
) {
    val title = stringResource(R.string.excerpt_title)
    if (presentation == ExcerptPresentation.SHEET) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewportHeight = maxHeight
            Column(Modifier.fillMaxSize().padding(horizontal = AppSpace.md)) {
                AppSheetHeader(title, onClose,
                    modifier = Modifier.padding(top = AppSpace.md, bottom = AppSpace.sm))
                content(viewportHeight)
            }
        }
    } else {
        CollapsingPageScaffold(title = title, onBack = onClose) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val viewportHeight = maxHeight
                Column(Modifier.fillMaxSize().padding(horizontal = AppSpace.md)) {
                    content(viewportHeight)
                }
            }
        }
    }
}
