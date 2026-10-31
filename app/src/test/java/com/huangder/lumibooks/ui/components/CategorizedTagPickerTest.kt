package com.huangder.lumibooks.ui.components

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.entity.TagGroupEntity
import com.huangder.lumibooks.domain.model.ManagedTag
import com.huangder.lumibooks.domain.model.TagTypes
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategorizedTagPickerTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = RuntimeEnvironment.getApplication().getString(id)
    private val catalog = TagPickerCatalog(
        listOf(TagGroupEntity("topics", TagTypes.ANNOTATION, "Topics", "topics", 1)),
        listOf(ManagedTag("Alpha", "Alpha", null, 0), ManagedTag("Beta", "Beta", "topics", 0))
    )

    @Test fun choosesAcrossCategoriesAndOnlyCommitsOnConfirm() {
        var saved: List<String>? = null
        var dismissed = false
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", darkTheme = false) {
                CompositionLocalProvider(LocalMotionEnabled provides false) {
                    AnnotationTagSheet(emptyList(), emptyList(), { saved = it }, { dismissed = true }, categoryCatalog = catalog)
                }
            }
        }
        compose.onNodeWithText("Alpha").assertDoesNotExist()
        compose.onNodeWithText(label(R.string.tag_picker_uncategorized)).performClick()
        compose.onNodeWithText("Alpha").performClick()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithContentDescription(label(R.string.back)).performClick()
        compose.onNodeWithText("Topics").performClick()
        compose.onNodeWithText("Beta").performClick()
        compose.onNodeWithContentDescription(label(R.string.back)).performClick()
        compose.onAllNodesWithText("1 tags · 1 selected").assertCountEquals(2)
        compose.onNodeWithText(label(R.string.confirm)).performClick()
        compose.waitUntil { dismissed }
        compose.runOnIdle { assertEquals(listOf("Alpha", "Beta"), saved) }
    }

    @Test fun cancelInsideCategoryDiscardsDraftAndClosesWholeTask() {
        var saves = 0
        var dismissed = false
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", darkTheme = false) {
                CompositionLocalProvider(LocalMotionEnabled provides false) {
                    AnnotationTagSheet(emptyList(), emptyList(), { saves++ }, { dismissed = true }, categoryCatalog = catalog)
                }
            }
        }
        compose.onNodeWithText("Topics").performClick()
        compose.onNodeWithText("Beta").performClick()
        compose.onNodeWithText(label(R.string.cancel)).performClick()
        compose.runOnIdle { assertTrue("Cancel must finish the entire selection task", dismissed); assertEquals(0, saves) }
    }

    @Test fun removedCategoryMakesItsLabelsAvailableUnderUncategorized() {
        val data = mutableStateOf(catalog)
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", darkTheme = false) {
                CompositionLocalProvider(LocalMotionEnabled provides false) {
                    AnnotationTagSheet(listOf("Beta"), emptyList(), {}, {}, categoryCatalog = data.value)
                }
            }
        }
        compose.onNodeWithText("Topics").performClick()
        compose.onNodeWithText("Beta").assertIsOn()
        compose.runOnIdle { data.value = catalog.copy(groups = emptyList()) }
        compose.onNodeWithText("Beta").assertIsOn()
        compose.onNodeWithText("Alpha").assertIsDisplayed()
    }

    @Test fun saveFailureLeavesDraftOpenForRetry() {
        var attempts = 0
        var dismissed = false
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", darkTheme = false) {
                CompositionLocalProvider(LocalMotionEnabled provides false) {
                    AnnotationTagSheet(listOf("Alpha"), emptyList(), {
                        attempts++
                        if (attempts == 1) error("disk write failed")
                    }, { dismissed = true }, categoryCatalog = catalog)
                }
            }
        }
        compose.onNodeWithText(label(R.string.confirm)).performClick()
        compose.onNodeWithText(label(R.string.tag_picker_save_failed)).assertIsDisplayed()
        compose.runOnIdle { assertFalse(dismissed) }
        compose.onNodeWithText(label(R.string.confirm)).performClick()
        compose.waitUntil { dismissed }
        compose.runOnIdle { assertEquals(2, attempts) }
    }
}
