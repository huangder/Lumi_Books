package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ContinuousReaderRecompositionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun scrollingOnlyRecomposesReturnUiWhenTheTargetChangesSides() {
        val anchor = mutableStateOf<ContinuousViewportAnchor?>(ContinuousViewportAnchor(0, 0, 0))
        val visible = mutableStateOf(listOf(ReaderVisibleCharacters(0, 0, 1000)))
        var compositions = 0
        var displayedSide: ReaderReturnSide? = null
        compose.setContent {
            val side = rememberContinuousReturnSide(0, 2000, 0, anchor, visible).value
            SideEffect { compositions++; displayedSide = side }
        }
        compose.waitForIdle()
        assertEquals(ReaderReturnSide.RIGHT, displayedSide)
        val initialCompositions = compositions

        // Both pixel offsets and visible line ranges change throughout a fling.
        repeat(10) { step ->
            compose.runOnIdle {
                anchor.value = ContinuousViewportAnchor(0, step * 50, -step * 20)
                visible.value = listOf(ReaderVisibleCharacters(0, step * 50, 1000 + step * 50))
            }
            compose.waitForIdle()
        }
        assertEquals("Scrolling towards the target must not recompose the surrounding UI",
            initialCompositions, compositions)

        compose.runOnIdle {
            anchor.value = ContinuousViewportAnchor(0, 1900, -10)
            visible.value = listOf(ReaderVisibleCharacters(0, 1900, 2500))
        }
        compose.waitForIdle()
        assertNull("A visible return target hides the button", displayedSide)
        assertEquals(initialCompositions + 1, compositions)

        compose.runOnIdle {
            anchor.value = ContinuousViewportAnchor(0, 2500, -10)
            visible.value = listOf(ReaderVisibleCharacters(0, 2500, 4000))
        }
        compose.waitForIdle()
        assertEquals(ReaderReturnSide.LEFT, displayedSide)
        assertEquals(initialCompositions + 2, compositions)
    }

    @Test fun readerWithoutReturnTargetDoesNotObserveScrollingPosition() {
        val anchor = mutableStateOf<ContinuousViewportAnchor?>(null)
        val visible = mutableStateOf<List<ReaderVisibleCharacters>>(emptyList())
        var compositions = 0
        compose.setContent {
            val side = rememberContinuousReturnSide(null, null, 0, anchor, visible).value
            SideEffect { compositions++; assertNull(side) }
        }
        compose.waitForIdle()
        val initialCompositions = compositions
        compose.runOnIdle {
            anchor.value = ContinuousViewportAnchor(2, 5000, -20)
            visible.value = listOf(ReaderVisibleCharacters(2, 5000, 6000))
        }
        compose.waitForIdle()
        assertEquals(initialCompositions, compositions)
    }
}
