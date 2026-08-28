package com.huangder.lumibooks.benchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.dao.BookDao
import com.huangder.lumibooks.util.cache.ReaderCacheStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ReaderBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CLEAR_READER_CACHE) {
            ReaderCacheStore.get(context).clear()
        }
        if (intent.action == ACTION_PREPARE_APP) {
            val installMarker = context.packageManager
                .getPackageInfo(context.packageName, 0)
                .lastUpdateTime
            runBlocking {
                DataStoreManager(context).apply {
                    completeWelcomeFlow(installMarker)
                    saveWelcomeLanguageSetupCompleted()
                    saveSplashEnabled(false)
                }
            }
        }
        if (intent.action == ACTION_RESET_READING_POSITION) {
            val title = intent.getStringExtra(EXTRA_BOOK_TITLE) ?: return
            val bookDao = EntryPointAccessors.fromApplication(
                context.applicationContext,
                BenchmarkDatabaseEntryPoint::class.java
            ).bookDao()
            runBlocking {
                bookDao.getAllBooks().first()
                    .filter { it.title == title || it.title.startsWith("$title (") }
                    .forEach { book ->
                        bookDao.updateBook(
                            book.copy(readingProgress = 0f, locatorJson = null)
                        )
                    }
            }
        }
    }

    companion object {
        const val ACTION_CLEAR_READER_CACHE =
            "com.huangder.lumibooks.benchmark.CLEAR_READER_CACHE"
        const val ACTION_PREPARE_APP =
            "com.huangder.lumibooks.benchmark.PREPARE_APP"
        const val ACTION_RESET_READING_POSITION =
            "com.huangder.lumibooks.benchmark.RESET_READING_POSITION"
        const val EXTRA_BOOK_TITLE = "book_title"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface BenchmarkDatabaseEntryPoint {
    fun bookDao(): BookDao
}
