package com.huangder.lumibooks.util

import com.huangder.lumibooks.domain.model.AppIconStyle

internal object LauncherComponentNames {
    const val LUMI_2_SPLASH =
        "com.huangder.lumibooks.ui.splash.SplashLauncherActivity"
    const val LUMI_2_DIRECT =
        "com.huangder.lumibooks.ui.splash.DirectLauncherActivity"
    const val CLASSIC_SPLASH =
        "com.huangder.lumibooks.ui.splash.ClassicSplashLauncherActivity"
    const val CLASSIC_DIRECT =
        "com.huangder.lumibooks.ui.splash.ClassicDirectLauncherActivity"
}

internal fun launcherComponentStates(
    style: String?,
    splashEnabled: Boolean
): Map<String, Boolean> {
    val normalizedStyle = AppIconStyle.normalize(style)
    return linkedMapOf(
        LauncherComponentNames.LUMI_2_SPLASH to
            (normalizedStyle == AppIconStyle.LUMI_2.storedValue && splashEnabled),
        LauncherComponentNames.LUMI_2_DIRECT to
            (normalizedStyle == AppIconStyle.LUMI_2.storedValue && !splashEnabled),
        LauncherComponentNames.CLASSIC_SPLASH to
            (normalizedStyle == AppIconStyle.CLASSIC.storedValue && splashEnabled),
        LauncherComponentNames.CLASSIC_DIRECT to
            (normalizedStyle == AppIconStyle.CLASSIC.storedValue && !splashEnabled)
    )
}
