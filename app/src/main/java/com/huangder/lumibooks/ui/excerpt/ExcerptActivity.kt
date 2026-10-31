package com.huangder.lumibooks.ui.excerpt

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.ui.components.LiquidGlassColumnSheetContainer
import com.huangder.lumibooks.ui.components.ConfigurableBottomSheetBackHandler
import com.huangder.lumibooks.ui.components.animateBottomSheetIn
import com.huangder.lumibooks.ui.components.animateBottomSheetOut
import com.huangder.lumibooks.ui.components.materialBottomSheetMotion
import com.huangder.lumibooks.ui.components.adaptiveSheetFrame
import com.huangder.lumibooks.ui.theme.*
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ExcerptRequest(
    val bookTitle: String,
    val author: String,
    val chapter: String,
    val text: String,
    val note: String = "",
    val coverPath: String? = null,
    val backgroundColor: Int = 0xFFF5F0E8.toInt(),
    val textColor: Int = 0xFF35312D.toInt(),
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromNote(book: Book, note: Note, chapter: String) = ExcerptRequest(
            book.title, book.author, chapter, note.selectedText, note.note, book.coverPath
        )
        fun fromBookmark(book: Book, bookmark: Bookmark, chapter: String) = ExcerptRequest(
            book.title, book.author, chapter, bookmark.title.ifBlank { chapter },
            bookmark.remark, book.coverPath
        )
    }
    internal fun toJson() = JSONObject().apply {
        put("title", bookTitle); put("author", author); put("chapter", chapter)
        put("text", text); put("note", note); put("cover", coverPath)
        put("background", backgroundColor); put("foreground", textColor); put("created", createdAt)
    }
}

object ExcerptLauncher {
    // Keep potentially long excerpts out of Binder's activity transaction.
    suspend fun open(context: Context, request: ExcerptRequest) {
        val token = UUID.randomUUID().toString()
        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "excerpts").apply { mkdirs() }
            directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 7 * 86_400_000L }
                ?.forEach { it.delete() }
            File(directory, "$token.json").writeText(request.toJson().toString())
        }
        context.startActivity(Intent(context, ExcerptActivity::class.java).putExtra("excerpt", token))
    }
    internal fun read(context: Context, intent: Intent): ExcerptRequest {
        val token = UUID.fromString(intent.getStringExtra("excerpt")).toString()
        val json = JSONObject(File(context.cacheDir, "excerpts/$token.json").readText())
        return ExcerptRequest(
            json.getString("title"), json.getString("author"), json.getString("chapter"),
            json.getString("text"), json.optString("note"),
            json.optString("cover").takeIf { it.isNotBlank() },
            json.getInt("background"), json.getInt("foreground"), json.getLong("created")
        )
    }
}

@AndroidEntryPoint
class ExcerptActivity : ComponentActivity() {
    @Inject lateinit var dataStoreManager: DataStoreManager
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val launchTheme = com.huangder.lumibooks.util.LaunchThemeController.themeSnapshot(this)
        setContent {
            val theme by dataStoreManager.launchThemeSnapshot.collectAsState(initial = launchTheme)
            val dark = when (theme.darkMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            EBookReaderTheme(
                liquidGlassStyle = theme.liquidGlassStyle,
                reduceTransparencyEnabled = theme.reduceTransparencyEnabled,
                darkTheme = dark,
                dynamicColor = theme.appTheme == "material3",
                appTheme = theme.appTheme,
                appAccentColor = theme.appAccentColor,
                appFontSize = theme.appFontSize,
                globalFontMode = theme.globalFontMode,
                liquidGlassTransparency = theme.liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = theme.liquidGlassHdrHighlightEnabled,
                eInkMode = theme.eInkModeEnabled,
                motionPreference = com.huangder.lumibooks.ui.theme.MotionPreference.fromStoredValue(theme.motionPreference),
                menuAnimationStyle = com.huangder.lumibooks.domain.model.MenuAnimationStyle.fromStoredValue(theme.menuAnimationStyle),
                lumiBackgroundScene = com.huangder.lumibooks.ui.theme.LumiBackgroundScene.SECONDARY
            ) {
                var request by remember { mutableStateOf<ExcerptRequest?>(null) }
                LaunchedEffect(Unit) {
                    try {
                        request = withContext(Dispatchers.IO) { ExcerptLauncher.read(this@ExcerptActivity, intent) }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        Toast.makeText(this@ExcerptActivity, R.string.excerpt_failed, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                val backdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
                val activeBackdrop = backdrop.takeIf { LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current }
                com.huangder.lumibooks.ui.components.LiquidGlassDialogHost(backdrop = activeBackdrop) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.matchParentSize()
                            .then(if (activeBackdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                            .background(AppColors.WindowBg))
                        com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop(activeBackdrop) {
                            request?.let { ExcerptPreview(it, onClose = { finish() }, modifier = Modifier.fillMaxSize().safeDrawingPadding()) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExcerptSheet(request: ExcerptRequest, onClose: () -> Unit, glassBackdrop: Backdrop? = null) {
    val offset = remember { Animatable(1f) }
    var closing by remember { mutableStateOf(false) }
    val animateSheet = LocalMotionEnabled.current && !LocalEInkMode.current
    val backProgress = ConfigurableBottomSheetBackHandler { closing = true }
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.86f
    LaunchedEffect(animateSheet) {
        if (animateSheet) offset.animateBottomSheetIn() else offset.snapTo(0f)
    }
    LaunchedEffect(closing, animateSheet) {
        if (closing) {
            if (animateSheet) offset.animateBottomSheetOut() else offset.snapTo(1f)
            onClose()
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(AppColors.Scrim.copy(alpha = 0.20f * (1f - offset.value.coerceIn(0f, 1f))))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { closing = true })
        LiquidGlassColumnSheetContainer(
            modifier = adaptiveSheetFrame(maxHeight = maxHeight).materialBottomSheetMotion(offset.value, backProgress),
            fallbackColor = AppColors.CardBg,
            shape = AppRoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            backdrop = glassBackdrop
        ) {
            ExcerptPreview(request, onClose = { closing = true },
                modifier = Modifier.fillMaxWidth().height(maxHeight), handleBack = false)
        }
    }
}


internal object ExcerptImages {
    fun write(context: Context, document: ExcerptDocument, share: Boolean, fileNamePrefix: String = "LUMI_excerpt_"): Uri {
        if (share) {
            val directory = File(context.cacheDir, "excerpts").apply { mkdirs() }
            val file = File(directory, "$fileNamePrefix${UUID.randomUUID()}.png")
            try {
                file.outputStream().use { document.writePng(it) }
                return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (error: Exception) { file.delete(); throw error }
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${fileNamePrefix}${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LUMIbooks")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        try {
            checkNotNull(resolver.openOutputStream(uri)).use { document.writePng(it) }
            if (Build.VERSION.SDK_INT >= 29) resolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null)
            return uri
        } catch (error: Exception) { resolver.delete(uri, null, null); throw error }
    }
}
