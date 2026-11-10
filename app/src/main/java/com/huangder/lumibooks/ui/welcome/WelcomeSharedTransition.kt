package com.huangder.lumibooks.ui.welcome

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/**
 * Scopes for the welcome flow's shared-element hand-off: the introduction's large "Filmy"
 * flies to the top-left corner of the feature stories (and back). Null outside the flow or
 * when motion is off, in which case the modifier is a no-op.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal val LocalWelcomeSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
internal val LocalWelcomeAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.welcomeCodename(): Modifier {
    val shared = LocalWelcomeSharedScope.current ?: return this
    val animated = LocalWelcomeAnimatedScope.current ?: return this
    return with(shared) {
        this@welcomeCodename.sharedBounds(
            sharedContentState = rememberSharedContentState("welcome-codename"),
            animatedVisibilityScope = animated,
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Fit, Alignment.Center)
        )
    }
}
