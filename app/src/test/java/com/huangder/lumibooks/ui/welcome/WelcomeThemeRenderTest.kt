package com.huangder.lumibooks.ui.welcome

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w369dp-h822dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WelcomeThemeRenderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    private fun verify(theme: String, dark: Boolean = false, ink: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                EBookReaderTheme(
                    appTheme = theme,
                    darkTheme = dark,
                    dynamicColor = theme == "material3",
                    eInkMode = ink,
                    motionPreference = MotionPreference.REDUCED
                ) {
                    WelcomeScreen(
                        isNewInstallation = false,
                        shouldShowLanguageSetup = false,
                        initialLanguage = "zh-CN",
                        initialEInkMode = ink,
                        isEInkMode = ink,
                        isDark = dark,
                        isLiquidGlass = theme == "liquid_glass" && !ink,
                        onFinished = {},
                        onOpenSponsor = {},
                        onLanguageSetupComplete = { _, _ -> },
                        onEnableLiquidGlass = { error("An update must not offer theme setup") }
                    )
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.welcome_start_using)).performClick()
        val features = listOf(
            Triple("excerpt", R.string.welcome_excerpt_title, R.string.welcome_excerpt_description),
            Triple("g2", R.string.welcome_g2_title, R.string.welcome_g2_description),
            Triple("dictionary", R.string.welcome_dictionary_title, R.string.welcome_dictionary_description)
        )
        for ((name, title, description) in features) {
            compose.onNodeWithText(context.getString(title)).assertIsDisplayed()
            compose.onNodeWithContentDescription(context.getString(description)).assertExists()
            compose.onNodeWithText(context.getString(R.string.welcome_enable_liquid_glass)).assertDoesNotExist()
            val next = compose.onNodeWithText(context.getString(R.string.welcome_next)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.welcome_previous)).assertIsDisplayed()
            val bounds = next.fetchSemanticsNode().boundsInRoot
            assertTrue("Navigation must stay on screen", bounds.bottom <= view.height)
            save("$theme-${if (ink) "ink" else if (dark) "dark" else "light"}-$fontScale-$name")
            next.performClick()
        }
        compose.onNodeWithText(context.getString(R.string.welcome_support_title)).assertIsDisplayed()
    }

    private fun save(name: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val dir = File("build/reports/welcome-features").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun lumiLight() = verify("lumi")
    @Test fun lumiDark() = verify("lumi", dark = true)
    @Test fun materialLight() = verify("material3")
    @Test fun materialDark() = verify("material3", dark = true)
    @Test fun glassLight() = verify("liquid_glass")
    @Test fun glassDark() = verify("liquid_glass", dark = true)
    @Test fun inkKeepsNavigationReadable() = verify("lumi", ink = true)
    @Test fun largeTextKeepsNavigationAccessible() = verify("lumi", fontScale = 2f)
}
