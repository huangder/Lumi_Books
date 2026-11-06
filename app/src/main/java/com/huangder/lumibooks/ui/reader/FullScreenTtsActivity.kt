package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.huangder.lumibooks.R
import com.huangder.lumibooks.service.TtsMediaButtons
import com.huangder.lumibooks.ui.theme.*
import com.huangder.lumibooks.ui.components.LiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.LocalPredictiveBackEnabled
import com.huangder.lumibooks.domain.model.MenuAnimationStyle
import com.huangder.lumibooks.util.LaunchThemeController
import dagger.hilt.android.AndroidEntryPoint

/** The translucent window keeps the reader visible beneath our own layered transition. */
@AndroidEntryPoint
class FullScreenTtsActivity : ComponentActivity() {
    private val model: FullScreenTtsViewModel by viewModels()
    private var finished = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        val snapshot = LaunchThemeController.themeSnapshot(this)
        if (!model.isCurrentSession()) { finishOnce(); return }
        setContent {
            val motion by model.settings.motionPreference.collectAsState(snapshot.motionPreference)
            val eInk by model.settings.eInkModeEnabled.collectAsState(snapshot.eInkModeEnabled)
            val reduce by model.settings.reduceTransparencyEnabled.collectAsState(snapshot.reduceTransparencyEnabled)
            val predictive by model.settings.predictiveBackEnabled.collectAsState(snapshot.predictiveBackEnabled)
            val menus by model.settings.menuAnimationStyle.collectAsState(snapshot.menuAnimationStyle)
            val appTheme by model.settings.appTheme.collectAsState(snapshot.appTheme)
            val glassSamplingQuality by model.settings.liquidGlassSamplingQuality.collectAsState(snapshot.liquidGlassSamplingQuality)
            val context = LocalContext.current
            LaunchedEffect(model) {
                model.engineError.collect { Toast.makeText(context, R.string.tts_full_engine_failed, Toast.LENGTH_LONG).show() }
            }
            EBookReaderTheme(
                liquidGlassSamplingQuality = glassSamplingQuality,
                darkTheme = true, appTheme = appTheme, eInkMode = eInk,
                reduceTransparencyEnabled = reduce, motionPreference = MotionPreference.fromStoredValue(motion),
                menuAnimationStyle = MenuAnimationStyle.fromStoredValue(menus)
            ) {
                CompositionLocalProvider(LocalPredictiveBackEnabled provides predictive) {
                    LiquidGlassMenuHost { FullScreenTtsRoute(model, ::finishOnce) }
                }
            }
        }
    }
    private fun finishOnce() {
        if (finished) return
        finished = true
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (TtsMediaButtons.isSupportedEvent(event) && TtsMediaButtons.handleEvent(model.controller, event).consumed) return true
        return super.dispatchKeyEvent(event)
    }
    companion object {
        const val BOOK_ID = "tts_book_id"
        const val SESSION_ID = "tts_session_id"
        fun launch(context: Context, bookId: String, sessionId: Long) {
            val intent = Intent(context, FullScreenTtsActivity::class.java)
                .putExtra(BOOK_ID, bookId).putExtra(SESSION_ID, sessionId)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent, ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle())
        }
    }
}
