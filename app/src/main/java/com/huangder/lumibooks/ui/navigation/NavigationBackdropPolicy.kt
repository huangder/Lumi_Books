package com.huangder.lumibooks.ui.navigation

/** Outgoing destinations stay visible during a pop animation after currentRoute changes. */
internal fun navigationBackdropCaptureAllowed(currentRoute: String?, visibleRoutes: List<String?>): Boolean =
    currentRoute != Screen.Reader.route && visibleRoutes.none { it == Screen.Reader.route }
