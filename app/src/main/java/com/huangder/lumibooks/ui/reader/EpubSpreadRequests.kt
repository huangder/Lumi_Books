package com.huangder.lumibooks.ui.reader

/** Keep the opening locator through first layout, but never replay it after explicit navigation. */
internal class EpubSpreadOpeningRequest(private val opening: EpubNavigationRequest) {
    private var externalNavigationStarted = false

    fun resolve(request: EpubNavigationRequest?): EpubNavigationRequest? {
        if (request != null) externalNavigationStarted = true
        return request ?: opening.takeUnless { externalNavigationStarted }
    }
}
