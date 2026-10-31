package com.huangder.lumibooks.ui.components

import com.huangder.lumibooks.ui.theme.AppPresentation
import com.huangder.lumibooks.ui.theme.AppSurfaceRole

/** Kept by the surface across recompositions, not recreated by each draw modifier. */
internal class GlassRefractionState {
    var failed: Boolean = false
        private set

    fun render(
        supported: Boolean,
        normalBlurPx: Float,
        fallbackBlurPx: Float,
        resetEffects: () -> Unit,
        blur: (Float) -> Unit,
        refract: () -> Unit,
        onFailure: (Exception) -> Unit
    ): Boolean {
        if (supported && !failed) {
            blur(normalBlurPx)
            try {
                refract()
                return true
            } catch (error: Exception) {
                failed = true
                // A rejected lens may already have changed both effect and padding.
                // Start again from vibrancy, rather than stacking blur on a partial lens.
                resetEffects()
                onFailure(error)
            }
        }
        blur(maxOf(normalBlurPx, fallbackBlurPx))
        return false
    }
}

internal fun liquidGlassBlurRadius(transparency: Float): Float =
    6f * (1f - transparency.coerceIn(0f, 1f))

internal fun liquidGlassFallbackBlurRadius(role: AppSurfaceRole, requestedDp: Float? = null): Float =
    maxOf(if (role == AppSurfaceRole.Overlay) AppPresentation.OverlayFallbackBlur.value else AppPresentation.ControlFallbackBlur.value,
        requestedDp ?: 0f)
