package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.ui.components.AppDialogAction
import com.huangder.lumibooks.ui.components.AppDialogActionRole

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.huangder.lumibooks.R
import com.huangder.lumibooks.tts.TtsListeningPosition
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.kyant.backdrop.Backdrop

internal fun shouldOfferListeningResume(
    position: TtsListeningPosition?,
    bookId: String,
    chapterCount: Int,
    readingChapter: Int,
    readingPage: Int,
    visibleStart: Int? = null,
    visibleEnd: Int? = null,
    allowPageFallback: Boolean = true,
    visibleCharacters: List<ReaderVisibleCharacters> = emptyList(),
    visibleChapters: Set<Int> = emptySet()
): Boolean {
    if (position == null || position.bookId != bookId ||
        position.chapterIndex !in 0 until chapterCount
    ) return false
    if (position.chapterIndex in visibleChapters) return false
    if (visibleCharacters.any { it.chapter == position.chapterIndex }) {
        return visibleCharacters.none { it.chapter == position.chapterIndex &&
            position.characterOffset in it.start until it.end }
    }
    if (position.chapterIndex != readingChapter) return true
    if (visibleStart != null && visibleEnd != null && visibleEnd > visibleStart) {
        return position.characterOffset !in visibleStart until visibleEnd
    }
    return !allowPageFallback || position.pageIndex != readingPage
}

/** Wait for the saved reading position to draw before offering a listening jump. */
@Composable
internal fun ObserveListeningResume(
    viewModel: ReaderViewModel,
    ready: Boolean,
    readerActive: Boolean,
    readingChapter: Int,
    readingPage: Int,
    visibleStart: Int? = null,
    visibleEnd: Int? = null,
    allowPageFallback: Boolean = true,
    visibleCharacters: List<ReaderVisibleCharacters> = emptyList(),
    visibleChapters: Set<Int> = emptySet(),
    visibleCharactersState: State<List<ReaderVisibleCharacters>>? = null
) {
    val owner = LocalLifecycleOwner.current
    var foreground by remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!foreground) viewModel.hideListeningReader()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            viewModel.hideListeningReader()
        }
    }
    LaunchedEffect(ready, foreground, readerActive) {
        if (ready && foreground && readerActive) {
            // Read the latest viewport only when this check runs. Observing every
            // scrolled line in composition would recompose the owning reader screen.
            val currentVisibleCharacters = visibleCharactersState?.value ?: visibleCharacters
            viewModel.checkListeningResume(readingChapter, readingPage, visibleStart, visibleEnd,
                allowPageFallback, currentVisibleCharacters, visibleChapters)
        } else if (!foreground || !readerActive) {
            viewModel.hideListeningReader()
        }
    }
}

@Composable
internal fun ReaderListeningDialog(
    titleText: String,
    confirmText: String,
    dismissText: String,
    paperColor: Color,
    contentColor: Color,
    eInkMode: Boolean,
    backdrop: Backdrop?,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    body: @Composable (Color) -> Unit
) {
    val surface = if (eInkMode) Color.White else paperColor.copy(alpha = 1f)
    val ink = listeningDialogContentColor(surface, contentColor, eInkMode)
    val useGlass = LocalAppTheme.current == "liquid_glass" && !eInkMode
    val title: @Composable () -> Unit = {
        Text(titleText, color = ink,
            fontWeight = FontWeight.SemiBold)
    }
    val text: @Composable () -> Unit = { body(ink) }
    val confirm: @Composable () -> Unit = {
        if (useGlass) {
            LiquidGlassTextButton(text = confirmText,
                onClick = onConfirm, tintedColor = ink, contentColor = surface)
        } else {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(
                containerColor = ink, contentColor = surface)) {
                Text(confirmText)
            }
        }
    }
    val dismiss: @Composable () -> Unit = {
        if (useGlass) {
            LiquidGlassTextButton(text = dismissText,
                onClick = onDismiss, contentColor = ink)
        } else {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(
                contentColor = ink)) {
                Text(dismissText)
            }
        }
    }
    if (useGlass) {
        LiquidGlassAlertDialog(onDismissRequest = onDismissRequest, backdrop = backdrop,
            contentScrimColor = surface.copy(alpha = 0.64f), transparencyOverride = 0.35f,
            title = title, text = text,
 actions = listOf(
     AppDialogAction(
         confirmText,
         onConfirm,
         tintedColor = ink,
         contentColor = surface
     ),
     AppDialogAction(
         dismissText,
         onDismiss,
         AppDialogActionRole.Cancel,
         contentColor = ink
     )
 ))
    } else {
        AlertDialog(onDismissRequest = onDismissRequest, containerColor = surface,
            titleContentColor = ink, textContentColor = ink,
            title = title, text = text, confirmButton = confirm, dismissButton = dismiss)
    }
}

internal fun listeningDialogContentColor(paper: Color, content: Color, eInkMode: Boolean): Color {
    if (eInkMode) return Color.Black
    val contrast = (maxOf(paper.luminance(), content.luminance()) + 0.05f) /
        (minOf(paper.luminance(), content.luminance()) + 0.05f)
    return if (contrast >= 4.5f) content.copy(alpha = 1f)
    else if (paper.luminance() < 0.18f) Color.White else Color.Black
}
