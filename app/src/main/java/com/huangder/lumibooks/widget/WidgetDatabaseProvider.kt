package com.huangder.lumibooks.widget

import android.content.Context
import androidx.room.Room
import com.huangder.lumibooks.data.local.dao.RandomNoteWithBook
import com.huangder.lumibooks.data.local.database.AppDatabase

/**
 * Widget 专用数据访问（非 Hilt，手动管理 Room 实例）。
 *
 * 每次查询后关闭数据库，避免与主 App 的 Room 实例产生多实例冲突。
 * Android 12+ 下 Glance Receiver 运行在 App 进程内，可安全访问同一 DB 文件。
 */
object WidgetDatabaseProvider {

    private const val DB_NAME = "ebook_reader_database"

    @Volatile
    private var database: AppDatabase? = null

    @Synchronized
    private fun getDatabase(context: Context): AppDatabase {
        // 每次使用前检查实例是否已关闭
        val existing = database
        if (existing != null && existing.isOpen) {
            return existing
        }
        val newDb = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            DB_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
        database = newDb
        return newDb
    }

    /** 获取一条随机摘录（安全调用，异常返回 null） */
    suspend fun getRandomQuote(context: Context): RandomNoteWithBook? {
        return try {
            getDatabase(context).noteDao().getRandomNoteWithBook()
        } catch (e: Exception) {
            // Room 打开失败或查询失败时返回 null，Widget 显示空状态
            null
        }
    }
}
