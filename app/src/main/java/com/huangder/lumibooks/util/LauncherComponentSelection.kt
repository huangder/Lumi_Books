package com.huangder.lumibooks.util

import com.huangder.lumibooks.domain.model.AppIconStyle

internal object LauncherComponentNames {
    const val LUMI_CHAN_SPLASH = "com.huangder.lumibooks.ui.splash.LumiChanSplashLauncherActivity"
    const val LUMI_CHAN_DIRECT = "com.huangder.lumibooks.ui.splash.LumiChanDirectLauncherActivity"
    const val LUMI_2_SPLASH =
        "com.huangder.lumibooks.ui.splash.SplashLauncherActivity"
    const val LUMI_2_DIRECT =
        "com.huangder.lumibooks.ui.splash.DirectLauncherActivity"
    const val LUMI_2_DARK_SPLASH =
        "com.huangder.lumibooks.ui.splash.Lumi2DarkSplashLauncherActivity"
    const val LUMI_2_DARK_DIRECT =
        "com.huangder.lumibooks.ui.splash.Lumi2DarkDirectLauncherActivity"
    const val LUMI_2_AUTO_SPLASH =
        "com.huangder.lumibooks.ui.splash.Lumi2AutoSplashLauncherActivity"
    const val LUMI_2_AUTO_DIRECT =
        "com.huangder.lumibooks.ui.splash.Lumi2AutoDirectLauncherActivity"
    const val CLASSIC_SPLASH =
        "com.huangder.lumibooks.ui.splash.ClassicSplashLauncherActivity"
    const val CLASSIC_DIRECT =
        "com.huangder.lumibooks.ui.splash.ClassicDirectLauncherActivity"

    /** Manifest `android:enabled` values; only the Lumi 2 splash entry is enabled by default. */
    val manifestDefaults: Map<String, Boolean> = mapOf(
        LUMI_CHAN_SPLASH to false,
        LUMI_CHAN_DIRECT to false,
        LUMI_2_SPLASH to true,
        LUMI_2_DIRECT to false,
        LUMI_2_DARK_SPLASH to false,
        LUMI_2_DARK_DIRECT to false,
        LUMI_2_AUTO_SPLASH to false,
        LUMI_2_AUTO_DIRECT to false,
        CLASSIC_SPLASH to false,
        CLASSIC_DIRECT to false
    )
}

internal fun launcherComponentStates(
    style: String?,
    splashEnabled: Boolean,
    easterEggUnlocked: Boolean = false
): Map<String, Boolean> {
    val selectedStyle = AppIconStyle.fromStoredValue(style, easterEggUnlocked)
    // Auto keeps one component identity; Android resolves its icon's -night resource.
    // Do not toggle launcher components in response to a system theme change.
    fun entry(style: AppIconStyle, splash: Boolean) =
        selectedStyle == style && splashEnabled == splash
    return linkedMapOf(
        LauncherComponentNames.LUMI_CHAN_SPLASH to entry(AppIconStyle.LUMI_CHAN, true),
        LauncherComponentNames.LUMI_CHAN_DIRECT to entry(AppIconStyle.LUMI_CHAN, false),
        LauncherComponentNames.LUMI_2_SPLASH to entry(AppIconStyle.LUMI_2, true),
        LauncherComponentNames.LUMI_2_DIRECT to entry(AppIconStyle.LUMI_2, false),
        LauncherComponentNames.LUMI_2_DARK_SPLASH to entry(AppIconStyle.LUMI_2_DARK, true),
        LauncherComponentNames.LUMI_2_DARK_DIRECT to entry(AppIconStyle.LUMI_2_DARK, false),
        LauncherComponentNames.LUMI_2_AUTO_SPLASH to entry(AppIconStyle.LUMI_2_AUTO, true),
        LauncherComponentNames.LUMI_2_AUTO_DIRECT to entry(AppIconStyle.LUMI_2_AUTO, false),
        LauncherComponentNames.CLASSIC_SPLASH to entry(AppIconStyle.CLASSIC, true),
        LauncherComponentNames.CLASSIC_DIRECT to entry(AppIconStyle.CLASSIC, false)
    )
}
