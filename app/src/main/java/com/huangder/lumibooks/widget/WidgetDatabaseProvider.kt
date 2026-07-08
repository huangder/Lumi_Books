package com.huangder.lumibooks.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.huangder.lumibooks.data.local.dao.RandomNoteWithBook
import com.huangder.lumibooks.data.local.database.AppDatabase
import kotlinx.coroutines.flow.first

/**
 * Widget 进程专用的 DataStore 句柄（共享同名文件 "settings"）
 */
private val Context.widgetDataStore by preferencesDataStore(name = "settings")

/**
 * Widget 专用数据访问（非 Hilt，手动管理 Room 实例）
 */
object WidgetDatabaseProvider {

    private const val DB_NAME = "ebook_reader_database"

    @Volatile
    private var database: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return database ?: synchronized(this) {
            database ?: buildDatabase(context).also { database = it }
        }
    }

    private fun buildDatabase(context: Context): AppDatabase {
        val appContext = context.applicationContext
        return Room.databaseBuilder(appContext, AppDatabase::class.java, DB_NAME)
            .fallbackToDestructiveMigration()
            .build()
    }

    /** 获取一条随机摘录 */
    suspend fun getRandomQuote(context: Context): RandomNoteWithBook? {
        return getDatabase(context).noteDao().getRandomNoteWithBook()
    }

    /** 每日目标（分钟），用于 Widget 判断空状态等 */
    suspend fun getDailyGoalMinutes(context: Context): Int {
        val key = intPreferencesKey("daily_goal")
        val prefs = context.applicationContext.widgetDataStore.data.first()
        return prefs[key] ?: 30
    }
}
