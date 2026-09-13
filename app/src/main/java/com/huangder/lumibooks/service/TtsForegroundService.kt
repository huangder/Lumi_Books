package com.huangder.lumibooks.service

import android.annotation.SuppressLint
import android.app.Service
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.IntentCompat
import android.view.KeyEvent
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.tts.TtsController
import com.huangder.lumibooks.tts.TtsPlaybackState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@AndroidEntryPoint
class TtsForegroundService : Service() {
    companion object {
        private const val ACTION_START = "com.huangder.lumibooks.tts.START"
        const val ACTION_PLAY_PAUSE = "com.huangder.lumibooks.tts.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.huangder.lumibooks.tts.PREVIOUS"
        const val ACTION_NEXT = "com.huangder.lumibooks.tts.NEXT"
        const val ACTION_STOP = "com.huangder.lumibooks.tts.STOP"
        private const val EXTRA_BOOK_TITLE = "book_title"

        fun startIntent(context: Context, bookTitle: String): Intent {
            return Intent(context, TtsForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_BOOK_TITLE, bookTitle)
        }
    }

    @Inject lateinit var ttsController: TtsController
    @Inject lateinit var notificationManager: TtsNotificationManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var mediaSession: MediaSession
    private lateinit var playbackWakeLock: PowerManager.WakeLock
    private var bookTitle = ""
    private var foregroundStarted = false
    private var awaitingSessionStart = false

    override fun onCreate() {
        super.onCreate()
        notificationManager.createChannel()
        playbackWakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:TtsPlayback")
            .apply { setReferenceCounted(false) }
        mediaSession = MediaSession(this, "LumiTtsPlayback").apply {
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setSessionActivity(openAppIntent())
            // Android 12+：声明本应用的媒体按键广播接收器，系统在本应用不是当前
            // “媒体按键会话”时（例如按键仍归属其它播放器）也会把耳机按键投递过来。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setMediaButtonBroadcastReceiver(
                    ComponentName(this@TtsForegroundService, TtsMediaButtonReceiver::class.java)
                )
            } else {
                @Suppress("DEPRECATION")
                setMediaButtonReceiver(mediaButtonReceiverIntent())
            }
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = ttsController.resume()
                override fun onPause() = ttsController.pause()
                override fun onStop() {
                    ttsController.stop()
                    stopSelf()
                }
                override fun onSkipToNext() = ttsController.skip(forward = true)
                override fun onSkipToPrevious() = ttsController.skip(forward = false)

                override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                    val event = IntentCompat.getParcelableExtra(
                        mediaButtonEvent,
                        Intent.EXTRA_KEY_EVENT,
                        KeyEvent::class.java
                    )
                        ?: return super.onMediaButtonEvent(mediaButtonEvent)
                    if (!TtsMediaButtons.isSupportedEvent(event)) {
                        return super.onMediaButtonEvent(mediaButtonEvent)
                    }
                    TtsMediaButtons.handle(ttsController, event.keyCode)
                    if (event.keyCode == KeyEvent.KEYCODE_MEDIA_STOP &&
                        ttsController.playbackState.value == TtsPlaybackState.IDLE
                    ) {
                        stopSelf()
                    }
                    return true
                }
            })
            isActive = true
        }

        serviceScope.launch {
            combine(ttsController.playbackState, ttsController.currentPage) { state, page -> state to page }
                .collect { (state, page) ->
                    updateMediaSessionState(state)
                    updatePlaybackWakeLock(state)
                    if (!foregroundStarted) return@collect
                    if (state == TtsPlaybackState.IDLE) {
                        if (!awaitingSessionStart) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            foregroundStarted = false
                            stopSelf()
                        }
                    } else {
                        awaitingSessionStart = false
                        val notification = notificationManager.buildNotification(
                            bookTitle = bookTitle,
                            chapterIndex = page?.location?.chapterIndex ?: 0,
                            isPlaying = state == TtsPlaybackState.PLAYING,
                            mediaSessionToken = mediaSession.sessionToken
                        )
                        getSystemService(android.app.NotificationManager::class.java)
                            .notify(TtsNotificationManager.NOTIFICATION_ID, notification)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                togglePlayback()
            }
            ACTION_PREVIOUS -> ttsController.skip(forward = false)
            ACTION_NEXT -> ttsController.skip(forward = true)
            ACTION_STOP -> {
                ttsController.stop()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                bookTitle = intent?.getStringExtra(EXTRA_BOOK_TITLE).orEmpty()
                awaitingSessionStart = true
                val state = ttsController.playbackState.value
                val notification = notificationManager.buildNotification(
                    bookTitle = bookTitle,
                    chapterIndex = ttsController.currentPage.value?.location?.chapterIndex ?: 0,
                    isPlaying = state == TtsPlaybackState.PLAYING,
                    mediaSessionToken = mediaSession.sessionToken
                )
                startForeground(TtsNotificationManager.NOTIFICATION_ID, notification)
                foregroundStarted = true
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun togglePlayback() {
        TtsMediaButtons.toggle(ttsController)
    }

    /** 会话被系统（锁屏/蓝牙媒体界面）点击时打开应用。 */
    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Android 11 及以下的媒体按键接收器入口。 */
    private fun mediaButtonReceiverIntent(): PendingIntent = PendingIntent.getBroadcast(
        this,
        1,
        Intent(Intent.ACTION_MEDIA_BUTTON).setClass(this, TtsMediaButtonReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    override fun onDestroy() {
        releasePlaybackWakeLock()
        if (ttsController.playbackState.value != TtsPlaybackState.IDLE) {
            ttsController.stop()
        }
        mediaSession.isActive = false
        mediaSession.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    @SuppressLint("WakelockTimeout")
    private fun updatePlaybackWakeLock(state: TtsPlaybackState) {
        val shouldHold = state == TtsPlaybackState.INITIALIZING ||
            state == TtsPlaybackState.PLAYING
        if (shouldHold) {
            if (!playbackWakeLock.isHeld) runCatching { playbackWakeLock.acquire() }
        } else {
            releasePlaybackWakeLock()
        }
    }

    private fun releasePlaybackWakeLock() {
        if (::playbackWakeLock.isInitialized && playbackWakeLock.isHeld) {
            runCatching { playbackWakeLock.release() }
        }
    }

    private fun updateMediaSessionState(state: TtsPlaybackState) {
        val playbackState = when (state) {
            TtsPlaybackState.PLAYING -> PlaybackState.STATE_PLAYING
            TtsPlaybackState.PAUSED -> PlaybackState.STATE_PAUSED
            TtsPlaybackState.INITIALIZING -> PlaybackState.STATE_BUFFERING
            TtsPlaybackState.IDLE -> PlaybackState.STATE_NONE
        }
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(playbackState, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (state == TtsPlaybackState.PLAYING) 1f else 0f)
                .build()
        )
    }
}
