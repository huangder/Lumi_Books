package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.huangder.lumibooks.ui.theme.AppShapes
import com.huangder.lumibooks.ui.theme.AppSurfaceRole
import org.junit.Assert.*
import org.junit.Test

class GlassBackdropPolicyTest {
    private class Source : Backdrop {
        override val isCoordinatesDependent = false
        override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?,
            layerBlock: (GraphicsLayerScope.() -> Unit)?) = Unit
    }

    @Test fun actualPageSourcesWinOverGlobalArtwork() {
        val explicit = Source()
        val local = Source()
        val artwork = Source()
        assertSame(explicit, resolveGlassBackdrop(explicit, local, artwork))
        assertSame(local, resolveGlassBackdrop(null, local, artwork))
        assertSame(artwork, resolveGlassBackdrop(null, null, artwork))
        assertNull(resolveGlassBackdrop(null, null, null))
    }

    @Test fun continuousCornersSupportTheLensWithoutReplacingTheirOutline() {
        val shape = AppShapes.rounded(28.dp)
        assertTrue(supportsLiquidGlassLens(shape))
        assertFalse(supportsLiquidGlassHighlight(shape))
        assertTrue(supportsLiquidGlassLens(RoundedCornerShape(28.dp)))
        assertFalse(supportsLiquidGlassLens(G2ContinuousCornerShape(28f)))
    }

    @Test fun strongBlurIsReservedForTheRefractionFallback() {
        assertEquals(8f, liquidGlassFallbackBlurRadius(AppSurfaceRole.Control), 0f)
        assertEquals(12f, liquidGlassFallbackBlurRadius(AppSurfaceRole.Overlay), 0f)
        assertEquals(18f, liquidGlassFallbackBlurRadius(AppSurfaceRole.Overlay, 18f), 0f)
        assertEquals(12f, liquidGlassFallbackBlurRadius(AppSurfaceRole.Overlay, 0f), 0f)
    }

    @Test fun buttonsKeepTheirExistingBlurStrength() {
        assertEquals(6f, liquidGlassBlurRadius(0f), 0f)
        assertEquals(3f, liquidGlassBlurRadius(0.5f), 0f)
        assertEquals(0f, liquidGlassBlurRadius(1f), 0f)
    }

    @Test fun snapshotAlignmentUsesScreenCoordinatesAcrossWindowsAndImeOffsets() {
        val origin = Offset(12f, 96f)
        assertEquals(IntOffset(-108, -204), glassSnapshotOffset(origin, Offset(120f, 300f)))
        assertEquals(IntOffset(-108, -84), glassSnapshotOffset(origin, Offset(120f, 180f)))
        assertEquals(IntOffset.Zero, glassSnapshotOffset(origin, origin))
    }
}
