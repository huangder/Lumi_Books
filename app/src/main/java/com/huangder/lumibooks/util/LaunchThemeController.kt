package com.huangder.lumibooks.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.huangder.lumibooks.MainActivity

object LaunchThemeController {
    const val EXTRA_SPLASH_ENABLED = "com.huangder.lumibooks.extra.SPLASH_ENABLED"

    private const val STATE_PREFERENCES = "launch_theme_state"
    private const val PENDING_SPLASH_ENABLED = "pending_splash_enabled"
    private const val SPLASH_LAUNCHER = "com.huangder.lumibooks.ui.splash.SplashLauncherActivity"
    private const val DIRECT_LAUNCHER = "com.huangder.lumibooks.ui.splash.DirectLauncherActivity"

    fun deferSplashEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PENDING_SPLASH_ENABLED, enabled)
            .apply()
    }

    fun applyPendingSplashSetting(context: Context) {
        val preferences = context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)
        if (!preferences.contains(PENDING_SPLASH_ENABLED)) return

        val enabled = preferences.getBoolean(PENDING_SPLASH_ENABLED, true)
        if (setSplashEnabled(context, enabled)) {
            preferences.edit().remove(PENDING_SPLASH_ENABLED).apply()
        }
    }

    private fun setSplashEnabled(context: Context, enabled: Boolean): Boolean {
        val packageManager = context.packageManager
        val enabledComponent = ComponentName(
            context,
            if (enabled) SPLASH_LAUNCHER else DIRECT_LAUNCHER
        )
        val disabledComponent = ComponentName(
            context,
            if (enabled) DIRECT_LAUNCHER else SPLASH_LAUNCHER
        )

        val splashIsEnabled = isComponentEnabled(
            packageManager.getComponentEnabledSetting(ComponentName(context, SPLASH_LAUNCHER)),
            manifestDefault = true
        )
        val directIsEnabled = isComponentEnabled(
            packageManager.getComponentEnabledSetting(ComponentName(context, DIRECT_LAUNCHER)),
            manifestDefault = false
        )
        if (splashIsEnabled == enabled && directIsEnabled != enabled) return true

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.setComponentEnabledSettings(
                    listOf(
                        PackageManager.ComponentEnabledSetting(
                            enabledComponent,
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP
                        ),
                        PackageManager.ComponentEnabledSetting(
                            disabledComponent,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    )
                )
            } else {
                packageManager.setComponentEnabledSetting(
                    enabledComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                packageManager.setComponentEnabledSetting(
                    disabledComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            true
        }.onFailure { error ->
            Log.e("LaunchThemeController", "Failed to switch launcher component", error)
        }.getOrDefault(false)
    }

    private fun isComponentEnabled(state: Int, manifestDefault: Boolean): Boolean = when (state) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> manifestDefault
        else -> false
    }

    fun mainIntent(context: Context, splashEnabled: Boolean): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_SPLASH_ENABLED, splashEnabled)
}
