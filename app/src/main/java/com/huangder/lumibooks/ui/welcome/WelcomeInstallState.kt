package com.huangder.lumibooks.ui.welcome

import android.content.Context
import java.io.File

internal data class WelcomeInstallState(
    val firstInstallTime: Long,
    val lastUpdateTime: Long
) {
    val installMarker: Long
        get() = lastUpdateTime

    val isUpdate: Boolean
        get() = firstInstallTime > 0L && lastUpdateTime > firstInstallTime

    fun shouldShowWelcome(completedInstallTime: Long): Boolean {
        return installMarker <= 0L || completedInstallTime != installMarker
    }
}

internal fun Context.readWelcomeInstallState(): WelcomeInstallState {
    return try {
        packageManager.getPackageInfo(packageName, 0).let { packageInfo ->
            WelcomeInstallState(
                firstInstallTime = packageInfo.firstInstallTime,
                lastUpdateTime = packageInfo.lastUpdateTime
            )
        }
    } catch (_: Exception) {
        WelcomeInstallState(
            firstInstallTime = 0L,
            lastUpdateTime = File(applicationInfo.sourceDir).lastModified()
        )
    }
}
