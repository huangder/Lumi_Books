package com.huangder.lumibooks.ui.navigation

/**
 * Routes whose glass controls would sample the navigation backdrop that records them: the
 * reader, and the hidden library (its header and passcode panel sit directly in the NavHost).
 * Recording either would build a RenderNode cycle and overflow the RenderThread stack.
 */
private val routesWithoutNavigationBackdrop = setOf(Screen.Reader.route, Screen.HiddenLibrary.route)

/** Outgoing destinations stay visible during a pop animation after currentRoute changes. */
internal fun navigationBackdropCaptureAllowed(currentRoute: String?, visibleRoutes: List<String?>): Boolean =
    currentRoute !in routesWithoutNavigationBackdrop && visibleRoutes.none { it in routesWithoutNavigationBackdrop }
