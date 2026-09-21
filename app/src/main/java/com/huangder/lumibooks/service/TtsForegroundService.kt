package com.huangder.lumibooks.service

import android.annotation.SuppressLint
import android.app.Service
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.IntentCompat
import android.view.KeyEvent
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.tts.TtsController
import com.huangder.lumibooks.tts.TtsPlaybackState
import com.huangder.lumibooks.widget.decodeWidgetCover
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        private const val TAG = "TtsForegroundService"

        fun startIntent(context: Context, bookTitle: String): Intent {
            return Intent(context, TtsForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_BOOK_TITLE, bookTitle)
        }
    }

    @Inject lateinit var ttsController: TtsController
    @Inject lateinit var notificationManager: TtsNotificationManager
    @Inject lateinit var bookRepository: BookRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var mediaSession: MediaSession
    private lateinit var playbackWakeLock: PowerManager.WakeLock
    private lateinit var audioFocusController: TtsAudioFocusController
    private var bookTitle = ""
    private var foregroundStarted = false
    private var awaitingSessionStart = false
    private var sessionBookId: String? = null
    private var coverBitmap: Bitmap? = null
    private var placeholderBitmap: Bitmap? = null

    override fun onCreate() {
        super.onCreate()
        logTtsServiceEvent("foreground_service_created", ttsController)
        notificationManager.createChannel()
        audioFocusController = TtsAudioFocusController(this, ttsController)
        playbackWakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:TtsPlayback")
            .apply { setReferenceCounted(false) }
        mediaSession = MediaSession(this, "LumiTtsPlayback").apply {
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            // 系统媒体界面按音频属性展示/排序，之前是 CONTENT_TYPE_UNKNOWN。
            setPlaybackToLocal(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setSessionActivity(openAppIntent())
            // Android 12+：声明本应用的媒体按键广播接收器，系统在本应用不是当前
            // “媒体按键会话”时（例如按键仍归属其它播放器）也会把耳机按键投递过来。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setMediaButtonBroadcastReceiver(
                    ComponentName(this@TtsForegroundService, TtsMediaButtonReceiver::class.java)
                )
                logTtsServiceEvent(
                    "media_button_receiver_registered",
                    ttsController,
                    mapOf("api" to Build.VERSION.SDK_INT, "method" to "broadcast_receiver")
                )
            }
            // 会话级媒体按键接收器：MediaSessionCompat/Media3 也这么做。系统会把它记成
            // “Last MediaButtonReceiver”，部分机型（实测 ColorOS）正是按这条记录投递耳机按键。
            // 该 API 在 Android 12+ 标记为 deprecated，但仍会被系统记录，因此继续使用。
            @Suppress("DEPRECATION")
            setMediaButtonReceiver(mediaButtonReceiverIntent())
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    logTransportCommand("media_session_transport", "PLAY")
                    ttsController.resume()
                }
                override fun onPause() {
                    logTransportCommand("media_session_transport", "PAUSE")
                    ttsController.pause()
                }
                override fun onStop() {
                    logTransportCommand("media_session_transport", "STOP")
                    ttsController.stop()
                    stopSelf()
                }
                override fun onSkipToNext() {
                    logTransportCommand("media_session_transport", "NEXT")
                    ttsController.skip(forward = true)
                }
                override fun onSkipToPrevious() {
                    logTransportCommand("media_session_transport", "PREVIOUS")
                    ttsController.skip(forward = false)
                }

                override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                    val event = IntentCompat.getParcelableExtra(
                        mediaButtonEvent,
                        Intent.EXTRA_KEY_EVENT,
                        KeyEvent::class.java
                    )
                        ?: return super.onMediaButtonEvent(mediaButtonEvent).also {
                            logTtsServiceEvent(
                                event = "media_button_missing_key_event",
                                controller = ttsController,
                                result = "ignored"
                            )
                        }
                    val result = TtsMediaButtons.handleEvent(ttsController, event)
                    logMediaButtonDelivery("media_session", event, result, ttsController)
                    if (!result.consumed) return super.onMediaButtonEvent(mediaButtonEvent)
                    if (event.keyCode == KeyEvent.KEYCODE_MEDIA_STOP &&
                        ttsController.playbackState.value == TtsPlaybackState.IDLE
                    ) {
                        stopSelf()
                    }
                    return true
                }
            })
            isActive = true
            logTtsServiceEvent(
                "media_session_activated",
                ttsController,
                mapOf("sessionTag" to "LumiTtsPlayback")
            )
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
                        postPlaybackNotification(
                            state = state,
                            chapterIndex = page?.location?.chapterIndex ?: 0
                        )
                    }
                }
        }
        serviceScope.launch {
            combine(
                ttsController.playbackState,
                ttsController.usesAndroidTts
            ) { state, usesAndroidTts -> state to usesAndroidTts }
                .collect { (state, usesAndroidTts) ->
                    audioFocusController.update(
                        playbackState = state,
                        usesSystemEngine = usesAndroidTts
                    )
                }
        }
        serviceScope.launch {
            ttsController.activeBookId.collectLatest { bookId ->
                applyBookMetadata(bookId)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logTtsServiceEvent(
            "foreground_service_command",
            ttsController,
            mapOf(
                "action" to (intent?.action ?: "null"),
                "startId" to startId,
                "stateBefore" to ttsController.playbackState.value.name
            )
        )
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                togglePlayback()
            }
            ACTION_PREVIOUS -> {
                logTransportCommand("notification", "PREVIOUS")
                ttsController.skip(forward = false)
            }
            ACTION_NEXT -> {
                logTransportCommand("notification", "NEXT")
                ttsController.skip(forward = true)
            }
            ACTION_STOP -> {
                logTransportCommand("notification", "STOP")
                ttsController.stop()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                bookTitle = intent?.getStringExtra(EXTRA_BOOK_TITLE).orEmpty()
                awaitingSessionStart = true
                val state = ttsController.playbackState.value
                startForeground(
                    TtsNotificationManager.NOTIFICATION_ID,
                    buildPlaybackNotification(
                        state = state,
                        chapterIndex = ttsController.currentPage.value?.location?.chapterIndex ?: 0
                    )
                )
                foregroundStarted = true
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun togglePlayback() {
        logTransportCommand("notification", "PLAY_PAUSE")
        TtsMediaButtons.toggle(ttsController)
    }

    private fun logTransportCommand(source: String, command: String) {
        logTtsServiceEvent(
            event = "transport_command_received",
            controller = ttsController,
            attributes = mapOf(
                "source" to source,
                "command" to command,
                "stateBefore" to ttsController.playbackState.value.name
            )
        )
    }

    private fun buildPlaybackNotification(state: TtsPlaybackState, chapterIndex: Int) =
        notificationManager.buildNotification(
            bookTitle = bookTitle,
            chapterIndex = chapterIndex,
            isPlaying = state == TtsPlaybackState.PLAYING,
            mediaSessionToken = mediaSession.sessionToken,
            largeIcon = coverBitmap
        )

    private fun postPlaybackNotification(state: TtsPlaybackState, chapterIndex: Int) {
        getSystemService(android.app.NotificationManager::class.java)
            .notify(
                TtsNotificationManager.NOTIFICATION_ID,
                buildPlaybackNotification(state, chapterIndex)
            )
    }

    /**
     * 把当前书籍的标题/作者/封面写进媒体会话，供系统媒体播放器（锁屏、下拉媒体控件）展示。
     * 没有封面或解码失败时用应用图标占位。
     */
    private suspend fun applyBookMetadata(bookId: String?) {
        if (sessionBookId == bookId && bookId != null) return
        sessionBookId = bookId
        val book = bookId?.let { id ->
            withContext(Dispatchers.IO) { runCatching { bookRepository.getBookById(id) }.getOrNull() }
        }
        val artwork = withContext(Dispatchers.IO) { loadCoverArtwork(book) }
        coverBitmap = artwork
        mediaSession.setMetadata(buildMediaMetadata(book, artwork))
        if (foregroundStarted && ttsController.playbackState.value != TtsPlaybackState.IDLE) {
            postPlaybackNotification(
                state = ttsController.playbackState.value,
                chapterIndex = ttsController.currentPage.value?.location?.chapterIndex ?: 0
            )
        }
    }

    private fun buildMediaMetadata(book: Book?, artwork: Bitmap?): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .putString(
                MediaMetadata.METADATA_KEY_TITLE,
                book?.title?.takeIf { it.isNotBlank() } ?: bookTitle.ifBlank { getString(R.string.app_name) }
            )
            .putString(
                MediaMetadata.METADATA_KEY_ARTIST,
                book?.author?.takeIf { it.isNotBlank() } ?: getString(R.string.app_name)
            )
            .putString(MediaMetadata.METADATA_KEY_ALBUM, getString(R.string.app_name))
        artwork?.let { bitmap ->
            // 同时提供 ART 与 ALBUM_ART：不同系统媒体界面读取的键不同。
            builder.putBitmap(MediaMetadata.METADATA_KEY_ART, bitmap)
            builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
        }
        return builder.build()
    }

    private fun loadCoverArtwork(book: Book?): Bitmap? {
        val target = coverArtworkTargetPx()
        val decoded = decodeWidgetCover(book?.coverPath, target)
        decoded?.let { return scaleToFit(it, target) }
        return placeholderBitmap ?: runCatching {
            BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher_lumi2)
        }.getOrNull()?.also { placeholderBitmap = it }
    }

    /** Android 13+ 对媒体通知封面有尺寸上限，取 240dp 并设上限兜底。 */
    private fun coverArtworkTargetPx(): Int =
        (240f * resources.displayMetrics.density).toInt().coerceIn(192, 1024)

    private fun scaleToFit(bitmap: Bitmap, maxSizePx: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxSizePx || maxSide <= 0) return bitmap
        val scale = maxSizePx.toFloat() / maxSide
        val scaled = runCatching {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        }.getOrNull() ?: return bitmap
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
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
        logTtsServiceEvent(
            "foreground_service_destroyed",
            ttsController,
            mapOf(
                "stateBefore" to ttsController.playbackState.value.name,
                "foregroundStarted" to foregroundStarted
            )
        )
        releasePlaybackWakeLock()
        audioFocusController.release()
        if (ttsController.playbackState.value != TtsPlaybackState.IDLE) {
            ttsController.stop()
        }
        mediaSession.isActive = false
        mediaSession.release()
        coverBitmap = null
        placeholderBitmap = null
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
        logTtsServiceEvent(
            "media_session_state_synced",
            ttsController,
            mapOf(
                "appState" to state.name,
                "platformState" to playbackState,
                "actions" to actions,
                "active" to mediaSession.isActive
            )
        )
    }

}
