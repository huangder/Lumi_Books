package com.huangder.lumibooks.ui.excerpt

import android.Manifest
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.components.LiquidGlassColumnSheetContainer
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.ConfigurableBottomSheetBackHandler
import com.huangder.lumibooks.ui.components.animateBottomSheetIn
import com.huangder.lumibooks.ui.components.animateBottomSheetOut
import com.huangder.lumibooks.ui.components.materialBottomSheetMotion
import com.kyant.backdrop.Backdrop
import com.huangder.lumibooks.data.local.DataStoreManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
                darkTheme = dark,
                dynamicColor = theme.appTheme == "material3",
                appTheme = theme.appTheme,
                appAccentColor = theme.appAccentColor,
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
                Box(Modifier.fillMaxSize().background(AppColors.PageBg).safeDrawingPadding()) {
                    request?.let { ExcerptPreview(it, onClose = { finish() }) }
                }
            }
        }
    }
}

@Composable
fun ExcerptSheet(request: ExcerptRequest, onClose: () -> Unit, glassBackdrop: Backdrop? = null) {
    val offset = remember { Animatable(1f) }
    var closing by remember { mutableStateOf(false) }
    val backProgress = ConfigurableBottomSheetBackHandler { closing = true }
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.86f
    LaunchedEffect(Unit) { offset.animateBottomSheetIn() }
    LaunchedEffect(closing) {
        if (closing) { offset.animateBottomSheetOut(); onClose() }
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(AppColors.Scrim.copy(alpha = 0.20f * (1f - offset.value.coerceIn(0f, 1f))))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { closing = true })
        LiquidGlassColumnSheetContainer(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .heightIn(max = maxHeight).materialBottomSheetMotion(offset.value, backProgress),
            fallbackColor = AppColors.CardBg,
            shape = AppRoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            backdrop = glassBackdrop
        ) {
            ExcerptPreview(request, onClose = { closing = true },
                modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight), handleBack = false)
        }
    }
}

@Composable
fun ExcerptPreview(request: ExcerptRequest, onClose: () -> Unit, modifier: Modifier = Modifier.fillMaxSize(), handleBack: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var style by rememberSaveable(request) { mutableIntStateOf(0) }
    var document by remember(request) { mutableStateOf<ExcerptDocument?>(null) }
    var failed by remember(request, style) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val dark = LocalIsDarkTheme.current
    val glass = LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current
    BackHandler(enabled = handleBack, onBack = onClose)
    LaunchedEffect(request, style) {
        try {
            document = withContext(Dispatchers.Default) {
                ExcerptRenderer.prepare(context, request, ExcerptStyle.entries[style]).also { it.previewFirstTile }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            failed = true
        } catch (_: OutOfMemoryError) { failed = true }
    }
    fun export(share: Boolean) {
        val current = document ?: return
        if (busy) return
        busy = true
        scope.launch {
            try {
                val uri = withContext(Dispatchers.IO) { ExcerptImages.write(context, current, share) }
                if (share) context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, "excerpt", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, context.getString(R.string.excerpt_share)))
                else Toast.makeText(context, R.string.excerpt_saved, Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Toast.makeText(context, if (share) R.string.excerpt_share_failed else R.string.excerpt_save_failed, Toast.LENGTH_LONG).show()
            } catch (_: OutOfMemoryError) {
                Toast.makeText(context, R.string.excerpt_failed, Toast.LENGTH_LONG).show()
            } finally { busy = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) export(false)
        else Toast.makeText(context, R.string.excerpt_save_failed, Toast.LENGTH_SHORT).show()
    }
    Column(modifier.padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.excerpt_title), fontSize = 24.sp, fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
            LiquidGlassSurface(shape = CircleShape, fallbackColor = AppColors.BgGray,
                contentScrimColor = AppColors.CardBg.copy(alpha = if (dark) 0.60f else 0.72f),
                onClick = onClose, controlEdge = true, modifier = Modifier.size(44.dp)) {
                Icon(AppIcons.X, stringResource(R.string.close), tint = AppColors.TextPrimary, modifier = Modifier.size(20.dp))
            }
        }
        BoxWithConstraints(Modifier.weight(1f, fill = false).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val current = document
            if (current != null) {
                val previewHeight = minOf(maxHeight, maxWidth * (current.height.toFloat() / current.width))
                val animatedHeight by animateDpAsState(previewHeight,
                    animationSpec = tween(300, easing = FastOutSlowInEasing), label = "excerptHeight")
                // Tiles avoid the GPU texture limit even when the exported image is very tall.
                AnimatedContent(targetState = current,
                    modifier = Modifier.fillMaxWidth().height(animatedHeight).clip(AppRoundedCornerShape(12.dp)),
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                    label = "excerptStyle") { shown ->
                LazyColumn(state = rememberLazyListState(), modifier = Modifier.fillMaxSize()) {
                    items((shown.height + 1199) / 1200) { index ->
                        val top = index * 1200
                        val tileHeight = minOf(1200, shown.height - top)
                        val tile by produceState<Bitmap?>(if (index == 0) shown.previewFirstTile else null, shown, index) {
                            if (index != 0) value = withContext(Dispatchers.Default) { shown.render(top, tileHeight) }
                        }
                        Box(Modifier.fillMaxWidth().aspectRatio(shown.width.toFloat() / tileHeight)
                            .background(Color(shown.backgroundColor))) {
                            tile?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize()) }
                        }
                    }
                }
                }
            } else Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                if (failed) Text(stringResource(R.string.excerpt_failed), color = AppColors.TextSecondary)
                else CircularProgressIndicator(color = AppColors.TextPrimary)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 34.dp).navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)) {
            val actions = listOf(
                Triple(AppIcons.ArrowsLeftRight, R.string.excerpt_style, { style = (style + 1) % ExcerptStyle.entries.size }),
                Triple(AppIcons.DownloadSimple, R.string.excerpt_save, {
                    if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else export(false)
                }),
                Triple(AppIcons.ShareNetwork, R.string.excerpt_share, { export(true) })
            )
            actions.forEachIndexed { index, (icon, label, action) ->
                val enabled = !busy && (index == 0 || document != null)
                val shadow = Modifier.dropShadow(CircleShape, Shadow(
                    radius = 24.dp, spread = 1.dp, offset = DpOffset.Zero,
                    color = Color.Black.copy(alpha = if (dark) 0.15f else 0.07f)))
                LiquidGlassSurface(shape = CircleShape, fallbackColor = AppColors.CardBg,
                    contentScrimColor = if (glass) AppColors.CardBg.copy(alpha = if (dark) 0.6f else 0.72f) else Color.Transparent,
                    enabled = enabled, onClick = action, controlEdge = true,
                    modifier = Modifier.size(52.dp), decorationModifier = shadow) {
                    if (busy && index == 1) CircularProgressIndicator(Modifier.size(22.dp), color = AppColors.TextPrimary, strokeWidth = 2.dp)
                    else Icon(icon, stringResource(label), tint = AppColors.TextPrimary.copy(alpha = if (enabled) 1f else 0.38f), modifier = Modifier.size(25.dp))
                }
            }
        }
    }
}

internal object ExcerptImages {
    fun write(context: Context, document: ExcerptDocument, share: Boolean): Uri {
        if (share) {
            val directory = File(context.cacheDir, "excerpts").apply { mkdirs() }
            val file = File(directory, "${UUID.randomUUID()}.png")
            try {
                file.outputStream().use { document.writePng(it) }
                return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (error: Exception) { file.delete(); throw error }
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "LUMI_excerpt_${System.currentTimeMillis()}.png")
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
