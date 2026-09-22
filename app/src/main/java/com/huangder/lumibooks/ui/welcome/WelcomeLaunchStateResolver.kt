package com.huangder.lumibooks.ui.welcome

import com.huangder.lumibooks.util.diagnostics.StartupTrace
import android.content.Context
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.util.LaunchThemeController
import com.huangder.lumibooks.util.WelcomeLaunchSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Resolves the synchronous state needed before any welcome UI is allowed to draw. */
internal fun resolveWelcomeLaunchSnapshot(
    context: Context,
    dataStoreManager: DataStoreManager,
    installState: WelcomeInstallState = context.readWelcomeInstallState()
): WelcomeLaunchSnapshot {
    // Check completeness before reading: the background mirror may be hydrated concurrently.
    val cached = if (LaunchThemeController.hasWelcomeSnapshot(context)) {
        LaunchThemeController.welcomeSnapshot(context)
    } else {
        null
    }
    StartupTrace.event("welcome_resolve", mapOf("cacheComplete" to (cached != null), "cached" to cached?.toString(), "firstInstallTime" to installState.firstInstallTime, "installMarker" to installState.installMarker))
    return resolveWelcomeLaunchSnapshot(installState, cached) {
        runBlocking { dataStoreManager.welcomeLaunchSnapshot.first() }.also { hydrated ->
            StartupTrace.event("welcome_persisted_read", mapOf("snapshot" to hydrated.toString()))
            LaunchThemeController.updateWelcomeSnapshot(
                context = context,
                completedInstallTime = hydrated.completedInstallTime,
                splashEnabled = hydrated.splashEnabled,
                hasCompletedLanguageSetup = hydrated.hasCompletedLanguageSetup
            )
        }
    }.also { resolved ->
        StartupTrace.event("welcome_route_decision", mapOf("source" to if (cached != null && !installState.shouldShowWelcome(cached.completedInstallTime)) "cache" else "datastore", "snapshot" to resolved.toString(), "destination" to if (installState.shouldShowWelcome(resolved.completedInstallTime)) "WelcomeActivity" else "MainActivity"))
    }
}

/** A mirror may skip welcome, but must never authorize showing it without checking DataStore. */
internal fun resolveWelcomeLaunchSnapshot(
    installState: WelcomeInstallState,
    cached: WelcomeLaunchSnapshot?,
    readPersisted: () -> WelcomeLaunchSnapshot
): WelcomeLaunchSnapshot {
    if (cached != null && !installState.shouldShowWelcome(cached.completedInstallTime)) {
        return cached
    }
    // Includes old installations without a mirror and stale mirrors after process death.
    // Resolve before creating any welcome content; don't render welcome as a loading state.
    return readPersisted()
}
