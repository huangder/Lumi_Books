package com.huangder.lumibooks.ui.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AuthorizedFoldersScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private fun label(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun capture(name: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/authorized-folders/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test
    fun swipeDeletesOnlyTheSelectedCardAndLastRemovalShowsEmptyState() {
        val first = AuthorizedFolderEntry("first", "Novels", "primary:Books/Novels")
        val second = AuthorizedFolderEntry("second", "Comics", "primary:Comics")
        var state by mutableStateOf(AuthorizedFoldersUiState(listOf(first, second), loading = false))
        val removed = mutableListOf<String>()
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", darkTheme = false) {
                CompositionLocalProvider(
                    LocalMotionEnabled provides false,
                    LocalLiquidGlassCapability provides LiquidGlassCapability(false, false)
                ) {
                    view = LocalView.current
                    Surface(color = AppColors.PageBg) {
                        AuthorizedFoldersContent(state, onBack = {}, onRemove = { folder ->
                            removed += folder.uri
                            state = state.copy(folders = state.folders.filterNot { it.uri == folder.uri })
                        })
                    }
                }
            }
        }
        compose.onNodeWithText(first.path).assertIsDisplayed()
        compose.onNodeWithText(second.path).assertIsDisplayed()
        capture("expanded")
        compose.onNodeWithText(first.name).performTouchInput { swipeLeft() }
        compose.onNode(hasContentDescription(label(R.string.delete)) and isEnabled()).assertIsDisplayed()
        capture("swipe-delete")
        compose.onNode(hasContentDescription(label(R.string.delete)) and isEnabled()).performClick()
        compose.onNodeWithText(first.name).assertDoesNotExist()
        compose.onNodeWithText(second.name).assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(first.uri), removed) }
        compose.onNodeWithText(second.name).performTouchInput { swipeLeft() }
        compose.onNode(hasContentDescription(label(R.string.delete)) and isEnabled()).performClick()
        compose.onNodeWithText(label(R.string.authorized_folders_empty)).assertIsDisplayed()
        capture("empty")
        compose.runOnIdle { assertEquals(listOf(first.uri, second.uri), removed) }
    }

    @Test
    fun longListScrollsUnderTheToolbarAndKeepsBackReachable() {
        val entries = (1..12).map { AuthorizedFolderEntry("uri-$it", "Folder $it", "primary:Books/Folder $it") }
        var backed = false
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", darkTheme = true) {
                CompositionLocalProvider(
                    LocalMotionEnabled provides false,
                    LocalLiquidGlassCapability provides LiquidGlassCapability(false, false)
                ) {
                    view = LocalView.current
                    Surface(color = AppColors.PageBg) {
                        AuthorizedFoldersContent(AuthorizedFoldersUiState(entries, loading = false), onBack = { backed = true }, onRemove = {})
                    }
                }
            }
        }
        compose.onNodeWithText("Folder 12").performScrollTo().assertIsDisplayed()
        capture("collapsed-dark")
        compose.onNodeWithContentDescription(label(R.string.back)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, backed) }
    }
}
