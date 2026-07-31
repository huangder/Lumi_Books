package com.huangder.lumibooks.ui.bookshelf

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.components.ConfigurableActivityBack
import com.huangder.lumibooks.ui.components.LiquidGlassDialogHost
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BookshelfCategoryBooksActivity : ComponentActivity() {
    private var systemDarkMode by mutableStateOf(false)

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        systemDarkMode = resources.configuration.isNightModeEnabled()
        val target = intent.toCategoryTarget() ?: run {
            finish()
            return
        }

        setContent {
            val appTheme by dataStoreManager.appTheme.collectAsState(initial = "lumi")
            val globalFontMode by dataStoreManager.globalFontMode.collectAsState(initial = "default")
            val liquidGlassTransparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = 0.55f)
            val liquidGlassHdrHighlightEnabled by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = false)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = "system")
            val eInkMode by dataStoreManager.eInkModeEnabled.collectAsState(initial = false)
            val predictiveBackEnabled by dataStoreManager.predictiveBackEnabled.collectAsState(initial = true)
            val isDark = if (eInkMode) false else when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> systemDarkMode
            }
            val effectiveTheme = if (eInkMode && appTheme == "liquid_glass") "lumi" else appTheme

            EBookReaderTheme(
                darkTheme = isDark,
                dynamicColor = effectiveTheme == "material3",
                appTheme = effectiveTheme,
                liquidGlassTransparency = liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = liquidGlassHdrHighlightEnabled && !eInkMode,
                eInkMode = eInkMode,
                globalFontMode = globalFontMode
            ) {
                ConfigurableActivityBack(
                    predictiveBackEnabled = predictiveBackEnabled,
                    onBack = { finish() }
                )
                Surface(modifier = Modifier.fillMaxSize(), color = AppColors.WindowBg) {
                    LiquidGlassDialogHost(modifier = Modifier.fillMaxSize()) {
                        BookshelfCategoryBooksRoute(
                            selectedTarget = target,
                            onBack = { finish() },
                            onOpenBook = { book ->
                                startActivity(
                                    Intent(this@BookshelfCategoryBooksActivity, MainActivity::class.java)
                                        .putExtra(MainActivity.EXTRA_OPEN_BOOK_ID, book.id)
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemDarkMode = newConfig.isNightModeEnabled()
    }

    private fun Configuration.isNightModeEnabled(): Boolean =
        (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    companion object {
        private const val EXTRA_KIND = "bookshelf_category_kind"
        private const val EXTRA_TITLE = "bookshelf_category_title"
        private const val EXTRA_TAG_ID = "bookshelf_category_tag_id"

        internal fun createIntent(context: Context, target: BookshelfCategoryTarget): Intent {
            val kind = when (target) {
                is BookshelfCategoryTarget.All -> "all"
                is BookshelfCategoryTarget.Downloaded -> "downloaded"
                is BookshelfCategoryTarget.Pdf -> "pdf"
                is BookshelfCategoryTarget.Txt -> "txt"
                is BookshelfCategoryTarget.Favorites -> "favorites"
                is BookshelfCategoryTarget.Tag -> "tag"
            }
            return Intent(context, BookshelfCategoryBooksActivity::class.java)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_TITLE, target.title)
                .apply {
                    if (target is BookshelfCategoryTarget.Tag) {
                        putExtra(EXTRA_TAG_ID, target.id)
                    }
                }
        }

        private fun Intent.toCategoryTarget(): BookshelfCategoryTarget? {
            val title = getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: return null
            return when (getStringExtra(EXTRA_KIND)) {
                "all" -> BookshelfCategoryTarget.All(title)
                "downloaded" -> BookshelfCategoryTarget.Downloaded(title)
                "pdf" -> BookshelfCategoryTarget.Pdf(title)
                "txt" -> BookshelfCategoryTarget.Txt(title)
                "favorites" -> BookshelfCategoryTarget.Favorites(title)
                "tag" -> getStringExtra(EXTRA_TAG_ID)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { BookshelfCategoryTarget.Tag(it, title) }
                else -> null
            }
        }
    }
}
