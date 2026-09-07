package com.huangder.lumibooks.ui.splash

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.welcome.readWelcomeInstallState
import com.huangder.lumibooks.ui.welcome.resolveWelcomeLaunchSnapshot
import com.huangder.lumibooks.ui.welcome.WelcomeActivity
import com.huangder.lumibooks.util.LaunchThemeController

abstract class BaseLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(createDestinationIntent())
        finish()
        overridePendingTransition(0, 0)
    }

    protected abstract fun createDestinationIntent(): Intent
}

class SplashLaunchActivity : BaseLauncherActivity() {
    override fun createDestinationIntent(): Intent = welcomeOrMainIntent()
}

class DirectLaunchActivity : BaseLauncherActivity() {
    override fun createDestinationIntent(): Intent = welcomeOrMainIntent()
}

/**
 * Resolve the launcher route before any welcome Activity can draw a frame.
 *
 * Some launchers cache the previously enabled alias for a while after the splash setting is
 * changed. Both aliases therefore need the same guard; checking only the direct alias still
 * lets a stale splash alias show the welcome screen for one frame on affected devices.
 */
private fun Activity.welcomeOrMainIntent(): Intent {
    val welcomeLaunch = resolveWelcomeLaunchSnapshot(
        context = this,
        dataStoreManager = DataStoreManager(applicationContext)
    )
    val shouldShowWelcome = readWelcomeInstallState()
        .shouldShowWelcome(welcomeLaunch.completedInstallTime)
    return if (shouldShowWelcome) {
        Intent(this, WelcomeActivity::class.java)
    } else {
        // Use the persisted preference rather than the alias that happened to be cached by the
        // launcher. This also prevents a stale splash alias from re-enabling the Compose splash.
        LaunchThemeController.mainIntent(this, welcomeLaunch.splashEnabled)
    }
}
