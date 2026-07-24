package com.huangder.lumibooks.ui.welcome

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
