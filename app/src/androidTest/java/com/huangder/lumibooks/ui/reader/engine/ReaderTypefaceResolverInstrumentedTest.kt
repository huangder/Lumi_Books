package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.huangder.lumibooks.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderTypefaceResolverInstrumentedTest {
    @Test
    fun fileBackedTypefaceIsNotRewrappedForWeight() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = requireNotNull(ResourcesCompat.getFont(context, R.font.lxgw_wenkai))
        val resolved = resolveReaderTypeface(context, "kaiti", null, 400)

        assertSame(original, resolved.typeface)
        assertTrue(resolved.typeface !== Typeface.MONOSPACE)
    }

    @Test
    fun platformTypefaceAppliesRequestedWeight() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolved = resolveReaderTypeface(context, "sans_serif", null, 700)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            assertEquals(700, resolved.typeface.weight)
        }
        assertTrue(!resolved.fakeBold)
    }

    @Test
    fun invalidCustomPathFallsBackToDefaultTypeface() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolved = resolveReaderTypeface(
            context = context,
            fontType = "custom:missing",
            customFontPath = "/path/that/does/not/exist.ttf",
            weight = 400
        )

        assertSame(Typeface.DEFAULT, resolved.typeface)
        assertTrue(!resolved.fakeBold)
    }
}
