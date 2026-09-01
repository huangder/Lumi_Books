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
    override fun createDestinationIntent(): Intent = Intent(this, WelcomeActivity::class.java)
}

class DirectLaunchActivity : BaseLauncherActivity() {
    override fun createDestinationIntent(): Intent {
        val welcomeLaunch = resolveWelcomeLaunchSnapshot(
            context = this,
            dataStoreManager = DataStoreManager(applicationContext)
        )
        val shouldShowWelcome = readWelcomeInstallState()
            .shouldShowWelcome(welcomeLaunch.completedInstallTime)
        return if (shouldShowWelcome) {
            Intent(this, WelcomeActivity::class.java)
        } else {
            LaunchThemeController.mainIntent(this, splashEnabled = false)
        }
    }
}
