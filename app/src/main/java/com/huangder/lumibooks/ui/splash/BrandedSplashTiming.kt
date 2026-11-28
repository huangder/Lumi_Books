package com.huangder.lumibooks.ui.splash

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal const val BRANDED_SPLASH_MIN_MS = 300L
internal const val BRANDED_SPLASH_MAX_MS = 1_100L

internal suspend fun awaitBrandedSplashExit(contentReady: Flow<Boolean>) {
    delay(BRANDED_SPLASH_MIN_MS)
    withTimeoutOrNull(BRANDED_SPLASH_MAX_MS - BRANDED_SPLASH_MIN_MS) {
        contentReady.first { it }
    }
}
