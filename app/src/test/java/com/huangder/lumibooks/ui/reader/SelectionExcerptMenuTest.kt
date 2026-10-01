package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SelectionExcerptMenuTest {
    @get:Rule val compose = createComposeRule()

    @Test fun highlightExcerptIsAccessibleAfterColors() = verify(null)
    @Test fun underlineExcerptIsAccessibleAfterColors() = verify(0)

    private fun verify(underlineMode: Int?) {
        var excerptClicks = 0
        var removeClicks = 0
        val context = RuntimeEnvironment.getApplication()
        val excerpt = context.getString(R.string.excerpt_title)
        val remove = context.getString(if (underlineMode == null) R.string.menu_remove_highlight else R.string.menu_remove_underline)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                    Box(Modifier.width(300.dp).height(64.dp)) {
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                        SelectionAnnotationRow(currentColor = null, underlineMode = underlineMode,
                            removeLabel = remove, menuText = Color.Black, dividerColor = Color.Gray,
                            onColorChange = {}, onExcerpt = { excerptClicks++ }, onRemove = { removeClicks++ })
                        }
                    }
                }
            }
        }
        compose.onNodeWithText(excerpt).performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText(remove).performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, excerptClicks)
            assertEquals(0, removeClicks)
        }
    }
}
