package com.huangder.lumibooks.ui.reader

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.huangder.lumibooks.R
import com.huangder.lumibooks.util.epub.EpubRenderSession
import com.huangder.lumibooks.util.epub.EpubResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

internal data class EpubImagePreviewRequest(
    val source: String,
    val altText: String,
    val leftPx: Float,
    val topPx: Float,
    val rightPx: Float,
    val bottomPx: Float,
    val naturalWidth: Int,
    val naturalHeight: Int
)

@Composable
internal fun EpubImagePreviewOverlay(
    session: EpubRenderSession,
    request: EpubImagePreviewRequest,
    progress: Float,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var resource by remember(request.source) { mutableStateOf<EpubResource?>(null) }
    var loadFinished by remember(request.source) { mutableStateOf(false) }
    var saveRequestToken by remember(request.source) { mutableIntStateOf(0) }
    var saving by remember(request.source) { mutableStateOf(false) }
    var userScale by remember(request.source) { mutableStateOf(1f) }
    var userPan by remember(request.source) { mutableStateOf(Offset.Zero) }

    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
    DisposableEffect(imageLoader) {
        onDispose { imageLoader.shutdown() }
    }

    LaunchedEffect(session, request.source) {
        resource = withContext(Dispatchers.IO) { session.readImageUrl(request.source) }
        loadFinished = true
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            saveRequestToken++
        } else {
            Toast.makeText(context, R.string.epub_image_storage_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(saveRequestToken) {
        if (saveRequestToken <= 0 || saving) return@LaunchedEffect
        val image = resource ?: return@LaunchedEffect
        saving = true
        val saved = withContext(Dispatchers.IO) { saveEpubImageToGallery(context, image) }
        saving = false
        Toast.makeText(
            context,
            if (saved) R.string.epub_image_saved_to_gallery else R.string.epub_image_save_failed,
            Toast.LENGTH_SHORT
        ).show()
    }

    BackHandler(onBack = onDismissRequest)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewportHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val maxImageWidthPx = viewportWidthPx * 0.86f
        val maxImageHeightPx = viewportHeightPx * 0.62f
        val sourceWidthPx = (request.rightPx - request.leftPx).coerceAtLeast(1f)
        val sourceHeightPx = (request.bottomPx - request.topPx).coerceAtLeast(1f)
        val aspectRatio = when {
            request.naturalWidth > 0 && request.naturalHeight > 0 ->
                request.naturalWidth.toFloat() / request.naturalHeight.toFloat()
            sourceHeightPx > 0f -> sourceWidthPx / sourceHeightPx
            else -> 1f
        }.coerceIn(0.08f, 12f)
        val availableRatio = maxImageWidthPx / maxImageHeightPx
        val targetWidthPx: Float
        val targetHeightPx: Float
        if (aspectRatio >= availableRatio) {
            targetWidthPx = maxImageWidthPx
            targetHeightPx = targetWidthPx / aspectRatio
        } else {
            targetHeightPx = maxImageHeightPx
            targetWidthPx = targetHeightPx * aspectRatio
        }
        val targetCenterX = viewportWidthPx / 2f
        val targetCenterY = viewportHeightPx * 0.45f
        val sourceCenterX = (request.leftPx + request.rightPx) / 2f
        val sourceCenterY = (request.topPx + request.bottomPx) / 2f
        val openingScaleX = (sourceWidthPx / targetWidthPx).coerceIn(0.02f, 1.4f)
        val openingScaleY = (sourceHeightPx / targetHeightPx).coerceIn(0.02f, 1.4f)
        val p = progress.coerceIn(0f, 1f)
        val easedButtonProgress = ((p - 0.48f) / 0.52f).coerceIn(0f, 1f)
        val effectiveUserScale = 1f + (userScale - 1f) * p
        val maxPanX = targetWidthPx * (userScale - 1f).coerceAtLeast(0f) * 0.55f
        val maxPanY = targetHeightPx * (userScale - 1f).coerceAtLeast(0f) * 0.55f
        val clampedPan = Offset(
            userPan.x.coerceIn(-maxPanX, maxPanX),
            userPan.y.coerceIn(-maxPanY, maxPanY)
        )
        val imageWidth = with(density) { targetWidthPx.toDp() }
        val imageHeight = with(density) { targetHeightPx.toDp() }
        val targetOffsetX = targetCenterX - targetWidthPx / 2f
        val targetOffsetY = targetCenterY - targetHeightPx / 2f
        val buttonSizePx = with(density) { 58.dp.toPx() }
        val buttonGapPx = with(density) { 30.dp.toPx() }
        val buttonCenterY = min(
            viewportHeightPx - buttonSizePx,
            targetCenterY + targetHeightPx / 2f + buttonGapPx + buttonSizePx / 2f
        )

        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                )
        )

        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = targetOffsetX + (sourceCenterX - targetCenterX) * (1f - p) + clampedPan.x * p
                    translationY = targetOffsetY + (sourceCenterY - targetCenterY) * (1f - p) + clampedPan.y * p
                    scaleX = (openingScaleX + (1f - openingScaleX) * p) * effectiveUserScale
                    scaleY = (openingScaleY + (1f - openingScaleY) * p) * effectiveUserScale
                    alpha = p.coerceIn(0.08f, 1f)
                }
                .size(imageWidth, imageHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFDFDFDF))
                .border(0.5.dp, Color.White.copy(alpha = 0.34f), RoundedCornerShape(14.dp))
                .pointerInput(request.source) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (userScale * zoom).coerceIn(1f, 4f)
                        userScale = nextScale
                        userPan = if (nextScale <= 1.01f) Offset.Zero else userPan + pan
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            contentAlignment = Alignment.Center
        ) {
            val image = resource
            if (image != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(image.bytes)
                        .crossfade(false)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = request.altText.ifBlank {
                        context.getString(R.string.epub_image_preview)
                    },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!loadFinished) {
                CircularProgressIndicator(
                    color = Color(0xFFFF5A63),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(34.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = stringResource(R.string.epub_image_load_failed),
                    tint = Color.DarkGray.copy(alpha = 0.72f),
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = targetCenterX - buttonSizePx / 2f
                    translationY = buttonCenterY - buttonSizePx / 2f + (1f - easedButtonProgress) * buttonGapPx
                    alpha = easedButtonProgress
                    scaleX = 0.82f + 0.18f * easedButtonProgress
                    scaleY = 0.82f + 0.18f * easedButtonProgress
                }
                .size(58.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(enabled = resource != null && !saving) {
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        saveRequestToken++
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (saving) {
                CircularProgressIndicator(
                    color = Color(0xFFFF5A63),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(25.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = stringResource(R.string.epub_image_save_to_gallery),
                    tint = if (resource != null) Color(0xFFFF5A63) else Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

private fun saveEpubImageToGallery(context: Context, resource: EpubResource): Boolean {
    val extension = extensionForImage(resource.mediaType, resource.path)
    val displayName = "LumiBooks_${System.currentTimeMillis()}.$extension"
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, resource.mediaType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/LumiBooks")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create MediaStore item")
            try {
                resolver.openOutputStream(uri, "w")?.use { it.write(resource.bytes) }
                    ?: error("Unable to open MediaStore output")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        } else {
            @Suppress("DEPRECATION")
            val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val directory = File(pictures, "LumiBooks").apply { mkdirs() }
            val output = File(directory, displayName)
            output.writeBytes(resource.bytes)
            MediaScannerConnection.scanFile(
                context,
                arrayOf(output.absolutePath),
                arrayOf(resource.mediaType),
                null
            )
        }
        true
    }.getOrElse { false }
}

private fun extensionForImage(mediaType: String, path: String): String = when (mediaType.lowercase()) {
    "image/jpeg", "image/jpg" -> "jpg"
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    "image/avif" -> "avif"
    "image/svg+xml" -> "svg"
    "image/bmp" -> "bmp"
    else -> path.substringAfterLast('.', "png")
        .substringBefore('?')
        .lowercase()
        .takeIf { it.matches(Regex("[a-z0-9]{2,5}")) }
        ?: "png"
}
