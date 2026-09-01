package com.huangder.lumibooks.ui.welcome

import android.content.Context
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.util.LaunchThemeController
import com.huangder.lumibooks.util.WelcomeLaunchSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Resolves the synchronous state needed before any welcome UI is allowed to draw. */
internal fun resolveWelcomeLaunchSnapshot(
    context: Context,
    dataStoreManager: DataStoreManager
): WelcomeLaunchSnapshot {
    val snapshot = LaunchThemeController.welcomeSnapshot(context)
    if (LaunchThemeController.hasWelcomeSnapshot(context)) return snapshot

    // One-time migration for installs that predate the SharedPreferences launch mirror.
    return runBlocking {
        WelcomeLaunchSnapshot(
            completedInstallTime = dataStoreManager.completedWelcomeInstallTime.first(),
            splashEnabled = dataStoreManager.splashEnabled.first(),
            hasCompletedLanguageSetup = dataStoreManager.hasCompletedWelcomeLanguageSetup.first()
        )
    }.also { hydrated ->
        LaunchThemeController.updateWelcomeSnapshot(
            context = context,
            completedInstallTime = hydrated.completedInstallTime,
            splashEnabled = hydrated.splashEnabled,
            hasCompletedLanguageSetup = hydrated.hasCompletedLanguageSetup
        )
    }
}
