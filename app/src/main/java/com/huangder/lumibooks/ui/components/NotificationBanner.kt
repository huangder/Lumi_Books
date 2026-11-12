package com.huangder.lumibooks.ui.components

import android.content.Context
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.LiquidGlassSamplingQuality
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppSurfaceRole
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassTransparency
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.LocalUseMaterial3Theme
import com.huangder.lumibooks.ui.theme.cardOutline
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.LocalBackdropSamplingScale
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.math.min

/** One iOS-style banner. [onClick] makes the banner tappable; it is dismissed afterwards. */
data class BannerMessage(
    val title: String,
    val body: String? = null,
    val onClick: (() -> Unit)? = null,
    val durationMillis: Long = DEFAULT_BANNER_DURATION_MILLIS
)

const val DEFAULT_BANNER_DURATION_MILLIS = 2_000L

/**
 * Queue for the top notification banner. Mirrors `SnackbarHostState`: [show] suspends until
 * the banner leaves, so callers can clear their message source afterwards.
 */
@Stable
class NotificationBannerState {
    internal class Entry(
        val message: BannerMessage,
        private val continuation: CancellableContinuation<Unit>
    ) {
        /** Bumped when the same text arrives again, restarting the visible timer. */
        var restartToken by mutableIntStateOf(0)

        fun complete() {
            if (continuation.isActive) continuation.resume(Unit)
        }
    }

    private val mutex = Mutex()

    internal var current by mutableStateOf<Entry?>(null)
        private set

    suspend fun show(message: BannerMessage) {
        current?.takeIf { it.message.sameTextAs(message) }?.let {
            it.restartToken++
            return
        }
        mutex.withLock {
            try {
                suspendCancellableCoroutine<Unit> { continuation ->
                    current = Entry(message, continuation)
                }
            } finally {
                current = null
            }
        }
    }

    suspend fun show(title: String) = show(BannerMessage(title))

    /** Called by the host after the exit animation has finished. */
    internal fun onDismissed(entry: Entry) {
        if (current === entry) entry.complete()
    }
}

private fun BannerMessage.sameTextAs(other: BannerMessage): Boolean =
    title == other.title && body == other.body

/**
 * Swipe-up decision: dismiss after 30 % of the banner height, or on an upward fling faster
 * than 600 dp/s. [dragOffsetPx] and [velocityPxPerSecond] are negative when moving upwards.
 */
internal fun shouldDismissBanner(
    dragOffsetPx: Float,
    bannerHeightPx: Float,
    velocityPxPerSecond: Float,
    density: Float
): Boolean {
    val upwardVelocityDp = -velocityPxPerSecond / density.coerceAtLeast(0.01f)
    if (upwardVelocityDp >= BANNER_DISMISS_VELOCITY_DP) return true
    if (bannerHeightPx <= 0f) return dragOffsetPx < 0f
    return -dragOffsetPx / bannerHeightPx >= BANNER_DISMISS_FRACTION
}

/** Downward pulls stretch at a quarter of the finger distance, capped at [maxPx]. */
internal fun rubberBandBannerOffset(rawOffsetPx: Float, maxPx: Float): Float =
    if (rawOffsetPx <= 0f) rawOffsetPx else min(rawOffsetPx * 0.25f, maxPx)

private const val BANNER_DISMISS_FRACTION = 0.3f
private const val BANNER_DISMISS_VELOCITY_DP = 600f
private val BannerMaxWidth = 420.dp
private val BannerTopGap = 8.dp
private val BannerMaxStretch = 12.dp

@Composable
fun NotificationBannerHost(
    state: NotificationBannerState,
    modifier: Modifier = Modifier,
    liquidGlassBackdrop: Backdrop? = null
) {
    val context = LocalContext.current
    val launcherIcon = remember(context) { loadLauncherIcon(context) }
    val entry = state.current ?: return
    key(entry) {
        NotificationBannerItem(
            entry = entry,
            state = state,
            icon = launcherIcon,
            liquidGlassBackdrop = liquidGlassBackdrop,
            modifier = modifier
        )
    }
}

/** The enabled launcher alias carries the user's chosen app icon style. */
private fun loadLauncherIcon(context: Context): ImageBitmap? = runCatching {
    val pm = context.packageManager
    val intent = pm.getLaunchIntentForPackage(context.packageName) ?: return null
    val sizePx = (44 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    pm.getActivityIcon(intent).toBitmap(sizePx, sizePx).asImageBitmap()
}.getOrNull()

@Composable
private fun NotificationBannerItem(
    entry: NotificationBannerState.Entry,
    state: NotificationBannerState,
    icon: ImageBitmap?,
    liquidGlassBackdrop: Backdrop?,
    modifier: Modifier
) {
    val message = entry.message
    val eInk = LocalEInkMode.current
    val motionEnabled = LocalMotionEnabled.current && !eInk
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var heightPx by remember { mutableIntStateOf(0) }
    val offset = remember { Animatable(0f) }
    val alpha = remember { Animatable(if (eInk) 1f else 0f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    val maxStretchPx = with(density) { BannerMaxStretch.toPx() }

    // The banner sits below the status bar; leave enough room to hide it completely above.
    fun hiddenOffset(): Float = -(heightPx + with(density) { (BannerTopGap + 64.dp).toPx() })

    fun dismiss(velocity: Float = 0f) {
        if (dismissing) return
        dismissing = true
        scope.launch {
            if (offset.isRunning) offset.stop()
            val start = offset.value + dragOffset
            dragOffset = 0f
            offset.snapTo(start)
            when {
                eInk -> Unit
                motionEnabled -> {
                    launch { alpha.animateTo(0.6f, tween(260, easing = AppEasing.Accelerate)) }
                    offset.animateTo(
                        hiddenOffset(),
                        tween(durationMillis = 260, easing = AppEasing.Accelerate),
                        initialVelocity = velocity.coerceAtMost(0f)
                    )
                }
                else -> alpha.animateTo(0f, tween(120))
            }
            state.onDismissed(entry)
        }
    }

    // Enter: wait for the first measurement so the slide starts fully off-screen.
    LaunchedEffect(Unit) {
        snapshotFlow { heightPx }.first { it > 0 }
        when {
            eInk -> alpha.snapTo(1f)
            motionEnabled -> {
                offset.snapTo(hiddenOffset())
                launch { alpha.animateTo(1f, tween(150)) }
                offset.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 500f))
            }
            else -> alpha.animateTo(1f, tween(120))
        }
    }

    // Auto-dismiss; paused while the finger is down, restarted by a repeated message.
    LaunchedEffect(dragging, dismissing, entry.restartToken) {
        if (dragging || dismissing) return@LaunchedEffect
        delay(message.durationMillis)
        dismiss()
    }

    val dragState = rememberDraggableState { delta ->
        if (!dismissing) dragOffset += delta
    }

    Box(
        modifier = modifier
            .padding(top = BannerTopGap)
            .widthIn(max = BannerMaxWidth)
            .fillMaxWidth()
            .onSizeChanged { heightPx = it.height }
            .graphicsLayer {
                translationY = offset.value + rubberBandBannerOffset(dragOffset, maxStretchPx)
                this.alpha = alpha.value
                val scale = if (motionEnabled) 0.96f + 0.04f * alpha.value.coerceIn(0f, 1f) else 1f
                scaleX = scale
                scaleY = scale
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                enabled = !dismissing,
                onDragStarted = {
                    dragging = true
                    if (offset.isRunning) {
                        val current = offset.value
                        offset.stop()
                        offset.snapTo(current)
                    }
                },
                onDragStopped = { velocity ->
                    dragging = false
                    val visibleDrag = rubberBandBannerOffset(dragOffset, maxStretchPx)
                    if (shouldDismissBanner(visibleDrag, heightPx.toFloat(), velocity, density.density)) {
                        dragOffset = visibleDrag
                        dismiss(velocity)
                    } else {
                        offset.snapTo(offset.value + visibleDrag)
                        dragOffset = 0f
                        if (motionEnabled) {
                            offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
                        } else {
                            offset.snapTo(0f)
                        }
                    }
                }
            )
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        BannerSurface(liquidGlassBackdrop = liquidGlassBackdrop) { contentColor, secondaryColor ->
            val clickModifier = message.onClick?.let { action ->
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    action()
                    dismiss()
                }
            } ?: Modifier
            BannerContent(message, icon, contentColor, secondaryColor, clickModifier)
        }
    }
}

@Composable
private fun BannerSurface(
    liquidGlassBackdrop: Backdrop?,
    content: @Composable (contentColor: Color, secondaryColor: Color) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val eInk = LocalEInkMode.current
    val useMaterial3 = LocalUseMaterial3Theme.current
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !eInk
    val cornerRadius = if (useMaterial3 && !isLiquidGlass) 28.dp else 22.dp
    val shape: Shape = if (useMaterial3 && !isLiquidGlass) RoundedCornerShape(cornerRadius) else AppRoundedCornerShape(cornerRadius)
    val shadow = Modifier.softBannerShadow(cornerRadius, isDark)

    when {
        isLiquidGlass -> {
            // Heavily frosted on purpose: readable over busy covers. Its blur hides detail anyway,
            // so sample the backdrop at the lowest resolution to keep the overlay cheap.
            val baseSamplingScale = LocalBackdropSamplingScale.current
            CompositionLocalProvider(
                LocalBackdropSamplingScale provides min(baseSamplingScale, LiquidGlassSamplingQuality.PERFORMANCE.scale)
            ) {
                LiquidGlassSurface(
                    shape = shape,
                    fallbackColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
                    backdrop = liquidGlassBackdrop,
                    contentScrimColor = if (isDark) Color(0xFF1C1C1E).copy(alpha = 0.82f) else Color.White.copy(alpha = 0.82f),
                    transparencyOverride = min(LocalLiquidGlassTransparency.current, 0.25f),
                    interactive = false,
                    pressFeedbackEnabled = false,
                    role = AppSurfaceRole.Overlay,
                    contentAlignment = Alignment.CenterStart,
                    modifier = shadow.fillMaxWidth()
                ) {
                    content(AppColors.TextPrimary, AppColors.TextSecondary)
                }
            }
        }
        eInk -> Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color.White)
                .border(1.dp, Color.Black, shape)
        ) {
            content(Color.Black, Color(0xFF444444))
        }
        useMaterial3 -> {
            val scheme = MaterialTheme.colorScheme
            Box(
                shadow
                    .fillMaxWidth()
                    .clip(shape)
                    .background(scheme.surfaceContainerHigh)
            ) {
                content(scheme.onSurface, scheme.onSurfaceVariant)
            }
        }
        else -> {
            val background = if (isDark) Color(0xFF2C2C2E) else Color.White
            Box(
                shadow
                    .fillMaxWidth()
                    .clip(shape)
                    .background(background)
                    .cardOutline(shape)
            ) {
                content(AppColors.TextPrimary, AppColors.TextSecondary)
            }
        }
    }
}

/**
 * Soft, diffuse iOS-like shadow: a wide low-opacity ambient blur plus a faint tight contact
 * shadow, so a white banner still separates from a white page. Elevation shadows are too
 * hard-edged for this; API 26–27 cannot blur non-text shadow layers and keep a light elevation.
 */
private fun Modifier.softBannerShadow(cornerRadius: Dp, isDark: Boolean): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawBehind {
            val radius = cornerRadius.toPx()
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.TRANSPARENT
            }
            fun layer(blur: Dp, dy: Dp, alpha: Float) {
                paint.setShadowLayer(blur.toPx(), 0f, dy.toPx(), Color.Black.copy(alpha = alpha).toArgb())
                drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, paint) }
            }
            layer(blur = 28.dp, dy = 10.dp, alpha = if (isDark) 0.45f else 0.10f)
            layer(blur = 4.dp, dy = 1.dp, alpha = if (isDark) 0.30f else 0.05f)
        }
    } else {
        shadow(8.dp, RoundedCornerShape(cornerRadius), clip = false,
            ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.10f))
    }

@Composable
private fun BannerContent(
    message: BannerMessage,
    icon: ImageBitmap?,
    contentColor: Color,
    secondaryColor: Color,
    modifier: Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconModifier = Modifier.size(BannerIconSize).clip(AppRoundedCornerShape(6.dp))
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = iconModifier)
        } else {
            Image(painter = painterResource(R.mipmap.ic_launcher), contentDescription = null, modifier = iconModifier)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = message.title,
                color = contentColor,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (message.body == null) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
            message.body?.let { body ->
                Text(
                    text = body,
                    color = secondaryColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val BannerIconSize: Dp = 26.dp
