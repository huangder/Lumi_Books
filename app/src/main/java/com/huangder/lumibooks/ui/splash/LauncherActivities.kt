package com.huangder.lumibooks.ui.splash

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.huangder.lumibooks.ui.welcome.WelcomeActivity

abstract class BaseLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, WelcomeActivity::class.java))
        finish()
        overridePendingTransition(0, 0)
    }
}

class SplashLauncherActivity : BaseLauncherActivity()

class DirectLauncherActivity : BaseLauncherActivity()
