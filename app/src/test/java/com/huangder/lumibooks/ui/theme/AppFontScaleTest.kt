package com.huangder.lumibooks.ui.theme

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.domain.model.AppFontSize
import org.junit.Assert.*
import org.junit.Test

class AppFontScaleTest {
    @Test fun sixLevelsHaveStableStorageAndIncreasingScales() {
        assertEquals(listOf(.8f, .9f, 1f, 1.1f, 1.2f, 1.35f), AppFontSize.entries.map { it.scale })
        AppFontSize.entries.forEach { assertEquals(it, AppFontSize.fromStoredValue(it.storedValue)) }
        listOf(null, "", "unknown", "135").forEach {
            assertEquals(AppFontSize.STANDARD, AppFontSize.fromStoredValue(it))
        }
    }

    @Test fun standardRetainsOriginalSystemDensity() {
        val system = Density(3f, 2f)
        assertSame(system, appFontDensity(system, AppFontSize.STANDARD))
    }

    @Test fun onlyTextScalesAndConversionsAreReversible() {
        val system = Density(3f, 1.5f)
        AppFontSize.entries.forEach { size ->
            val scaled = appFontDensity(system, size)
            assertEquals(with(system) { 24.dp.toPx() }, with(scaled) { 24.dp.toPx() }, .001f)
            assertEquals(with(system) { (16.sp * size.scale).toPx() }, with(scaled) { 16.sp.toPx() }, .001f)
            assertEquals(16f, with(scaled) { 16.sp.toDp().toSp().value }, .001f)
        }
    }

    @Test fun preservesNonlinearSystemFontConversions() {
        val system = object : Density {
            override val density = 3f
            override val fontScale = 2f
            override fun TextUnit.toDp(): Dp = (value * 1.4f + 3f).dp
            override fun Dp.toSp(): TextUnit = ((value - 3f) / 1.4f).sp
        }
        val scaled = appFontDensity(system, AppFontSize.EXTRA_LARGE)
        assertEquals(with(system) { (16.sp * 1.35f).toPx() }, with(scaled) { 16.sp.toPx() }, .001f)
        assertEquals(16f, with(scaled) { 16.sp.toDp().toSp().value }, .001f)
    }
}
