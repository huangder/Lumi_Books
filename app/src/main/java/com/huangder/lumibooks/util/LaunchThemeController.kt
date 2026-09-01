package com.huangder.lumibooks.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.domain.model.DEFAULT_APP_ACCENT_HEX
import com.huangder.lumibooks.domain.model.AppIconStyle

data class LaunchThemeSnapshot(
    val iconStyle: String = AppIconStyle.LUMI_2.storedValue,
    val appTheme: String = "lumi",
    val appAccentColor: String = DEFAULT_APP_ACCENT_HEX,
    val globalFontMode: String = "system",
    val liquidGlassTransparency: Float = 0.55f,
    val liquidGlassHdrHighlightEnabled: Boolean = false,
    val cardOutlinesEnabled: Boolean = false,
    val darkMode: String = "system",
    val motionPreference: String = "standard",
    val eInkModeEnabled: Boolean = false,
    val predictiveBackEnabled: Boolean = true
)

data class WelcomeLaunchSnapshot(
    val completedInstallTime: Long = 0L,
    val splashEnabled: Boolean = true,
    val hasCompletedLanguageSetup: Boolean = false
)

object LaunchThemeController {
    const val EXTRA_SPLASH_ENABLED = "com.huangder.lumibooks.extra.SPLASH_ENABLED"

    private val launcherSwitchLock = Any()

    private const val STATE_PREFERENCES = "launch_theme_state"
    private const val PENDING_SPLASH_ENABLED = "pending_splash_enabled"
    private const val SPLASH_ENABLED_SNAPSHOT = "splash_enabled_snapshot"
    private const val APP_ICON_STYLE = "app_icon_style"
    private const val APP_THEME = "app_theme"
    private const val APP_ACCENT_COLOR = "app_accent_color"
    private const val GLOBAL_FONT_MODE = "global_font_mode"
    private const val LIQUID_GLASS_TRANSPARENCY = "liquid_glass_transparency"
    private const val LIQUID_GLASS_HDR_HIGHLIGHT_ENABLED = "liquid_glass_hdr_highlight_enabled"
    private const val CARD_OUTLINES_ENABLED = "card_outlines_enabled"
    private const val DARK_MODE = "dark_mode"
    private const val MOTION_PREFERENCE = "motion_preference"
    private const val E_INK_MODE_ENABLED = "e_ink_mode_enabled"
    private const val PREDICTIVE_BACK_ENABLED = "predictive_back_enabled"
    private const val COMPLETED_WELCOME_INSTALL_TIME = "completed_welcome_install_time"
    private const val HAS_COMPLETED_WELCOME_LANGUAGE_SETUP = "has_completed_welcome_language_setup"

    fun deferSplashEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PENDING_SPLASH_ENABLED, enabled)
            .putBoolean(SPLASH_ENABLED_SNAPSHOT, enabled)
            .apply()
    }

    fun splashEnabledSnapshot(context: Context): Boolean =
        context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(SPLASH_ENABLED_SNAPSHOT, true)

    fun iconStyleSnapshot(context: Context): String =
        AppIconStyle.normalize(
            context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)
                .getString(APP_ICON_STYLE, AppIconStyle.LUMI_2.storedValue)
        )

    fun themeSnapshot(context: Context): LaunchThemeSnapshot {
        val preferences = context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)
        return LaunchThemeSnapshot(
            iconStyle = AppIconStyle.normalize(
                preferences.getString(APP_ICON_STYLE, AppIconStyle.LUMI_2.storedValue)
            ),
            appTheme = preferences.getString(APP_THEME, "lumi") ?: "lumi",
            appAccentColor = preferences.getString(APP_ACCENT_COLOR, DEFAULT_APP_ACCENT_HEX)
                ?: DEFAULT_APP_ACCENT_HEX,
            globalFontMode = preferences.getString(GLOBAL_FONT_MODE, "system") ?: "system",
            liquidGlassTransparency = preferences.getFloat(LIQUID_GLASS_TRANSPARENCY, 0.55f),
            liquidGlassHdrHighlightEnabled = preferences.getBoolean(
                LIQUID_GLASS_HDR_HIGHLIGHT_ENABLED,
                false
            ),
            cardOutlinesEnabled = preferences.getBoolean(CARD_OUTLINES_ENABLED, false),
            darkMode = preferences.getString(DARK_MODE, "system") ?: "system",
            motionPreference = preferences.getString(MOTION_PREFERENCE, "standard") ?: "standard",
            eInkModeEnabled = preferences.getBoolean(E_INK_MODE_ENABLED, false),
            predictiveBackEnabled = preferences.getBoolean(PREDICTIVE_BACK_ENABLED, true)
        )
    }

    fun welcomeSnapshot(context: Context): WelcomeLaunchSnapshot {
        val preferences = context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)
        return WelcomeLaunchSnapshot(
            completedInstallTime = preferences.getLong(COMPLETED_WELCOME_INSTALL_TIME, 0L),
            splashEnabled = preferences.getBoolean(SPLASH_ENABLED_SNAPSHOT, true),
            hasCompletedLanguageSetup = preferences.getBoolean(
                HAS_COMPLETED_WELCOME_LANGUAGE_SETUP,
                false
            )
        )
    }

    /**
     * Returns whether the welcome snapshot was written by the current launch-state format.
     *
     * Older installs can have the DataStore values but none of these mirror keys. Callers can
     * use this to perform a one-time migration without blocking every normal cold start.
     */
    fun hasWelcomeSnapshot(context: Context): Boolean {
        val preferences = context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)
        return preferences.contains(COMPLETED_WELCOME_INSTALL_TIME) &&
            preferences.contains(SPLASH_ENABLED_SNAPSHOT) &&
            preferences.contains(HAS_COMPLETED_WELCOME_LANGUAGE_SETUP)
    }

    fun updateThemeSnapshot(context: Context, snapshot: LaunchThemeSnapshot) {
        context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(APP_ICON_STYLE, AppIconStyle.normalize(snapshot.iconStyle))
            .putString(APP_THEME, snapshot.appTheme)
            .putString(APP_ACCENT_COLOR, snapshot.appAccentColor)
            .putString(GLOBAL_FONT_MODE, snapshot.globalFontMode)
            .putFloat(LIQUID_GLASS_TRANSPARENCY, snapshot.liquidGlassTransparency)
            .putBoolean(
                LIQUID_GLASS_HDR_HIGHLIGHT_ENABLED,
                snapshot.liquidGlassHdrHighlightEnabled
            )
            .putBoolean(CARD_OUTLINES_ENABLED, snapshot.cardOutlinesEnabled)
            .putString(DARK_MODE, snapshot.darkMode)
            .putString(MOTION_PREFERENCE, snapshot.motionPreference)
            .putBoolean(E_INK_MODE_ENABLED, snapshot.eInkModeEnabled)
            .putBoolean(PREDICTIVE_BACK_ENABLED, snapshot.predictiveBackEnabled)
            .apply()
    }

    fun updateIconStyleSnapshot(context: Context, style: String) {
        context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(APP_ICON_STYLE, AppIconStyle.normalize(style))
            .apply()
    }

    fun updateWelcomeCompletedInstallTime(context: Context, installTime: Long) {
        context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putLong(COMPLETED_WELCOME_INSTALL_TIME, installTime)
            .apply()
    }

    fun updateWelcomeSnapshot(
        context: Context,
        completedInstallTime: Long,
        splashEnabled: Boolean,
        hasCompletedLanguageSetup: Boolean
    ) {
        context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putLong(COMPLETED_WELCOME_INSTALL_TIME, completedInstallTime)
            .putBoolean(SPLASH_ENABLED_SNAPSHOT, splashEnabled)
            .putBoolean(HAS_COMPLETED_WELCOME_LANGUAGE_SETUP, hasCompletedLanguageSetup)
            .apply()
    }

    fun updateWelcomeLanguageSetup(context: Context, completed: Boolean) {
        context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(HAS_COMPLETED_WELCOME_LANGUAGE_SETUP, completed)
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
        return setLauncherComponents(context, iconStyleSnapshot(context), enabled)
    }

    /** Apply the selected icon/splash pair without restarting the running application. */
    fun applyIconStyle(context: Context, style: String): Boolean =
        setLauncherComponents(context, style, splashEnabledSnapshot(context))

    /** Reconcile persisted launcher preferences after an install, upgrade, or process restart. */
    fun synchronizeLauncherComponents(context: Context): Boolean =
        setLauncherComponents(context, iconStyleSnapshot(context), splashEnabledSnapshot(context))

    private fun setLauncherComponents(
        context: Context,
        style: String,
        splashEnabled: Boolean
    ): Boolean = synchronized(launcherSwitchLock) {
        val packageManager = context.packageManager
        val desired = launcherComponentStates(style, splashEnabled)
        val manifestDefaults = mapOf(
            LauncherComponentNames.LUMI_2_SPLASH to true,
            LauncherComponentNames.LUMI_2_DIRECT to false,
            LauncherComponentNames.CLASSIC_SPLASH to false,
            LauncherComponentNames.CLASSIC_DIRECT to false
        )

        val alreadySynchronized = desired.all { (name, shouldBeEnabled) ->
            val current = isComponentEnabled(
                packageManager.getComponentEnabledSetting(ComponentName(context, name)),
                manifestDefaults.getValue(name)
            )
            current == shouldBeEnabled
        }
        if (alreadySynchronized) return@synchronized true

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.setComponentEnabledSettings(
                    desired.map { (name, shouldBeEnabled) ->
                        PackageManager.ComponentEnabledSetting(
                            ComponentName(context, name),
                            if (shouldBeEnabled) {
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            } else {
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            },
                            PackageManager.DONT_KILL_APP
                        )
                    }
                )
            } else {
                // Disable every alternative first so the launcher never observes two styles.
                desired.filterValues { !it }.keys.forEach { name ->
                    packageManager.setComponentEnabledSetting(
                        ComponentName(context, name),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
                desired.filterValues { it }.keys.forEach { name ->
                    packageManager.setComponentEnabledSetting(
                        ComponentName(context, name),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
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
