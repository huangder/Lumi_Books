package com.huangder.lumibooks

import android.app.Application
import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.huangder.lumibooks.util.LaunchThemeController
import com.huangder.lumibooks.util.LocaleHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EBookReaderApp : Application(), Application.ActivityLifecycleCallbacks {

    private var startedActivityCount = 0

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(base))
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount++
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        if (startedActivityCount == 0 && !activity.isChangingConfigurations) {
            LaunchThemeController.applyPendingSplashSetting(this)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
