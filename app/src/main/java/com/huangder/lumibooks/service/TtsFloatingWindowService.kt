package com.huangder.lumibooks.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.R
import com.huangder.lumibooks.tts.TtsController
import com.huangder.lumibooks.tts.TtsPlaybackState
import com.huangder.lumibooks.ui.components.G2ContinuousCornerShape
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject

@AndroidEntryPoint
class TtsFloatingWindowService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val CHANNEL_ID = "tts_floating_window"
        private const val NOTIFICATION_ID = 2208
        private const val ACTION_STOP = "com.huangder.lumibooks.tts.floating.STOP"

        @Volatile
        private var keepVisibleOnNextForeground = false

        fun start(context: Context) {
            context.startService(Intent(context, TtsFloatingWindowService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TtsFloatingWindowService::class.java))
        }

        fun consumeKeepVisibleOnForeground(): Boolean {
            val keepVisible = keepVisibleOnNextForeground
            keepVisibleOnNextForeground = false
            return keepVisible
        }
    }

    @Inject lateinit var ttsController: TtsController

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry


    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopFloatingWindow()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        showFloatingWindow()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopFloatingWindow()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.tts_floating_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.tts_floating_channel_desc)
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TtsFloatingWindowService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.tts_floating_notification_title))
            .setContentText(getString(R.string.tts_floating_notification_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(
                        this,
                        android.R.drawable.ic_menu_close_clear_cancel
                    ),
                    getString(R.string.tts_stop),
                    stopIntent
                ).build()
            )
            .build()
    }

    private fun showFloatingWindow() {
        if (floatingView != null) return
        // 未授予「显示在其他应用上层」权限时 addView 必然失败，直接收工避免静默崩溃路径
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val windowWidth = (screenWidth - (32 * density).toInt()).coerceAtLeast(240)

        val params = WindowManager.LayoutParams(
            windowWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            // 默认位置：竖直方向偏上、左右居中
            gravity = Gravity.TOP or Gravity.START
            x = ((screenWidth - windowWidth) / 2f).toInt()
            y = (screenHeight * 0.2f).toInt().coerceAtLeast(0)
        }
        layoutParams = params

        val cv = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@TtsFloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@TtsFloatingWindowService)
            setContent {
                Box(Modifier.padding(12.dp)) {
                    TtsFloatingWindowContent(
                        ttsController = ttsController,
                        onDrag = { dx, dy -> moveFloatingWindow(dx, dy) },
                        onClose = {
                            ttsController.stop()
                            stopFloatingWindow()
                            stopSelf()
                        },
                        onNavigateToReader = {
                            keepVisibleOnNextForeground = true
                            val intent = Intent(this@TtsFloatingWindowService, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            startActivity(intent)
                        }
                    )
                }
            }
        }
        composeView = cv
        floatingView = cv

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun stopFloatingWindow() {
        floatingView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {}
        }
        floatingView = null
        composeView = null
        windowManager = null
        layoutParams = null
    }

    private fun moveFloatingWindow(dx: Float, dy: Float) {
        val lp = layoutParams ?: return
        val wm = windowManager ?: return
        val view = floatingView ?: return
        val maxX = (resources.displayMetrics.widthPixels - view.width).coerceAtLeast(0)
        val maxY = (resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0)
        lp.x = (lp.x + dx.toInt()).coerceIn(0, maxX)
        lp.y = (lp.y + dy.toInt()).coerceIn(0, maxY)
        wm.updateViewLayout(view, lp)
    }

}

// ── Floating Window UI ──

@Composable
private fun TtsFloatingWindowContent(
    ttsController: TtsController,
    onDrag: (dx: Float, dy: Float) -> Unit = { _, _ -> },
    onClose: () -> Unit,
    onNavigateToReader: () -> Unit
) {
    val currentSentence by ttsController.currentSentence.collectAsState()
    val playbackState by ttsController.playbackState.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val density = LocalDensity.current
    val outerShape = remember(density) {
        G2ContinuousCornerShape(with(density) { 48.dp.toPx() })
    }
    val subtitleShape = remember(density) {
        G2ContinuousCornerShape(with(density) { 34.dp.toPx() })
    }

    val surfaceColor = Color(0xFFF9F9F7).copy(alpha = 0.9f)
    val subtitleColor = Color(0xFFE8E8E5).copy(alpha = 0.82f)
    val textColor = Color(0xFF171719)
    val iconColor = Color(0xFF6E6E73)
    val isPlaying = playbackState == TtsPlaybackState.PLAYING

    Box {
        Surface(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                },
            shape = outerShape,
            color = surfaceColor,
            border = BorderStroke(0.8.dp, Color.Black.copy(alpha = 0.24f)),
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 88.dp, max = 112.dp)
                        .background(subtitleColor, subtitleShape)
                        .clickable { onNavigateToReader() }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Text(
                        text = currentSentence?.text ?: context.getString(R.string.tts_floating_loading),
                        color = textColor,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (isPlaying) ttsController.pause() else ttsController.resume()
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = context.getString(
                                if (isPlaying) R.string.tts_pause else R.string.tts_play
                            ),
                            tint = iconColor,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    FloatingControlButton(
                        icon = Icons.Rounded.SkipPrevious,
                        contentDescription = context.getString(R.string.tts_previous_sentence),
                        tint = iconColor,
                        onClick = { ttsController.skip(false) }
                    )
                    FloatingControlButton(
                        icon = Icons.Rounded.SkipNext,
                        contentDescription = context.getString(R.string.tts_next_sentence),
                        tint = iconColor,
                        onClick = { ttsController.skip(true) }
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = context.getString(R.string.close),
                            tint = iconColor.copy(alpha = 0.72f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
