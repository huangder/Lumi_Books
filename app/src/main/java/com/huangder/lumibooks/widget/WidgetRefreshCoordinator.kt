package com.huangder.lumibooks.widget

import android.content.Context
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.dao.BookDao
import com.huangder.lumibooks.data.local.dao.NoteDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@Singleton
class WidgetRefreshCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val noteDao: NoteDao,
    private val dataStoreManager: DataStoreManager
) {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return

        scope.launch {
            bookDao.observeContinueReadingWidgetData()
                .distinctUntilChanged()
                .debounce(REFRESH_DEBOUNCE_MILLIS)
                .collect { ContinueReadingWidgetProvider.requestUpdate(context) }
        }
        scope.launch {
            noteDao.observeWidgetQuotes()
                .distinctUntilChanged()
                .debounce(REFRESH_DEBOUNCE_MILLIS)
                .collect { QuoteWidgetProvider.requestUpdate(context) }
        }
        scope.launch {
            dataStoreManager.appLanguage
                .distinctUntilChanged()
                .drop(1)
                .debounce(REFRESH_DEBOUNCE_MILLIS)
                .collect {
                    ContinueReadingWidgetProvider.requestUpdate(context)
                    QuoteWidgetProvider.requestUpdate(context)
                }
        }
    }

    private companion object {
        const val REFRESH_DEBOUNCE_MILLIS = 300L
    }
}
