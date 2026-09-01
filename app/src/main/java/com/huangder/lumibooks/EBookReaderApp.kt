package com.huangder.lumibooks

import android.app.Application
import android.app.Activity
import android.content.Context
import android.os.Bundle
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.service.FloatingSubtitleOverlayController
import com.huangder.lumibooks.util.LaunchThemeController
import com.huangder.lumibooks.util.LocaleHelper
import com.huangder.lumibooks.widget.WidgetRefreshCoordinator
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import com.huangder.lumibooks.data.sync.WebdavAutoSyncScheduler
import com.huangder.lumibooks.util.diagnostics.DiagnosticLevel
import com.huangder.lumibooks.util.diagnostics.DiagnosticLogger
import com.huangder.lumibooks.util.diagnostics.DiagnosticLoggerRegistry
import com.huangder.lumibooks.util.ErrorHandler

@HiltAndroidApp
class EBookReaderApp : Application(), Application.ActivityLifecycleCallbacks, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var widgetRefreshCoordinator: WidgetRefreshCoordinator

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var floatingSubtitleOverlayController: FloatingSubtitleOverlayController

    @Inject
    lateinit var webdavAutoSyncScheduler: WebdavAutoSyncScheduler

    @Inject
    lateinit var diagnosticLogger: DiagnosticLogger

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private var startedActivityCount = 0
    private val postFirstFrameStarted = AtomicBoolean(false)
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(base))
    }

    override fun onCreate() {
        super.onCreate()
        installDiagnosticExceptionHandler()
        ErrorHandler.installDiagnosticLogger(diagnosticLogger)
        DiagnosticLoggerRegistry.logger = diagnosticLogger
        diagnosticLogger.log("app", "process_started", DiagnosticLevel.INFO)
        LaunchThemeController.synchronizeLauncherComponents(this)
        registerActivityLifecycleCallbacks(this)
        floatingSubtitleOverlayController.start()
        webdavAutoSyncScheduler.start()
        applicationScope.launch(Dispatchers.IO) {
            dataStoreManager.launchThemeSnapshot.collectLatest { snapshot ->
                LaunchThemeController.updateThemeSnapshot(this@EBookReaderApp, snapshot)
            }
        }
        applicationScope.launch(Dispatchers.IO) {
            combine(
                dataStoreManager.completedWelcomeInstallTime,
                dataStoreManager.splashEnabled,
                dataStoreManager.hasCompletedWelcomeLanguageSetup
            ) { completedInstallTime, splashEnabled, hasCompletedLanguageSetup ->
                Triple(completedInstallTime, splashEnabled, hasCompletedLanguageSetup)
            }.collectLatest { (completedInstallTime, splashEnabled, hasCompletedLanguageSetup) ->
                LaunchThemeController.updateWelcomeSnapshot(
                    context = this@EBookReaderApp,
                    completedInstallTime = completedInstallTime,
                    splashEnabled = splashEnabled,
                    hasCompletedLanguageSetup = hasCompletedLanguageSetup
                )
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        val wasInBackground = startedActivityCount == 0
        startedActivityCount++
        if (wasInBackground) floatingSubtitleOverlayController.setAppInForeground(true)
        diagnosticLogger.log("app", "activity_started", screen = activity.javaClass.simpleName)
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        if (startedActivityCount == 0 && !activity.isChangingConfigurations) {
            floatingSubtitleOverlayController.setAppInForeground(false)
            LaunchThemeController.applyPendingSplashSetting(this)
            webdavAutoSyncScheduler.onAppBackgrounded()
            diagnosticLogger.log("app", "app_backgrounded", screen = activity.javaClass.simpleName)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) {
        if (!postFirstFrameStarted.compareAndSet(false, true)) return
        activity.window.decorView.post {
            activity.window.decorView.post {
                applicationScope.launch {
                    PDFBoxResourceLoader.init(this@EBookReaderApp)
                    widgetRefreshCoordinator.start()
                }
            }
        }
    }
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        floatingSubtitleOverlayController.onDisplayConfigurationChanged()
    }

    private fun installDiagnosticExceptionHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            diagnosticLogger.recordCrashSynchronously(throwable, thread.name)
            previous?.uncaughtException(thread, throwable)
        }
    }
}
