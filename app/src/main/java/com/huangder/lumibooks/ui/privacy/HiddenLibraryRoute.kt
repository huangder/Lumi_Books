package com.huangder.lumibooks.ui.privacy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.ui.home.HomeViewModel

/**
 * Navigation destination of the hidden library. The app locks the session whenever it leaves
 * the foreground; coming back shows an opaque passcode panel before anything hidden is drawn.
 * [isCurrent] keeps the panel away while this page is only animating out or sits under the reader.
 */
@Composable
fun HiddenLibraryRoute(
    isCurrent: Boolean,
    onBack: () -> Unit,
    onOpenBook: (Book, Rect?) -> Unit,
    homeViewModel: HomeViewModel,
    lockViewModel: HiddenLibraryLockViewModel
) {
    val unlocked by lockViewModel.unlocked.collectAsState()
    val locked = !unlocked && isCurrent
    SecureWindowEffect()
    Box(Modifier.fillMaxSize()) {
        // While locked, TalkBack must not read the titles drawn under the opaque panel either.
        Box(Modifier.fillMaxSize().then(if (locked) Modifier.clearAndSetSemantics { } else Modifier)) {
            HiddenLibraryScreen(onBack = onBack, onOpenBook = onOpenBook, viewModel = homeViewModel)
        }
        HiddenLibraryGate(
            visible = locked,
            purpose = PasscodeGatePurpose.Unlock,
            onUnlocked = lockViewModel::unlockSession,
            onDismiss = onBack,
            opaque = true,
            viewModel = lockViewModel,
            modifier = Modifier.zIndex(5f)
        )
    }
}
