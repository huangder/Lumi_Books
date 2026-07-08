package com.huangder.lumibooks

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.view.ActionMode
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.ui.navigation.MainNavGraph
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.util.FileUtils
import com.huangder.lumibooks.util.parser.BookParserFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var bookRepository: BookRepository

    /**
     * 当 ReaderScreen 处于前台时置为 true，
     * 确保 ActionMode 拦截只在阅读页生效，不影响其他页面。
     */
    var isInReaderScreen = false

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleImportIntent(intent)
    }

    /**
     * 处理外部 App 通过 ACTION_VIEW 传入的文件（PDF/EPUB/TXT）
     * 将文件复制到应用内部存储后写入数据库，首页自动刷新
     */
    private fun handleImportIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            importBookFromUri(uri)
        }
    }

    private suspend fun importBookFromUri(uri: Uri) {
        val fileName = FileUtils.getFileNameFromUri(this, uri) ?: return
        val extension = FileUtils.getFileExtension(fileName)
        if (extension !in listOf("epub", "pdf", "txt")) return

        val file = FileUtils.copyFileToInternal(this, uri, fileName) ?: return
        val format = when (extension) {
            "epub" -> BookFormat.EPUB
            "pdf" -> BookFormat.PDF
            else -> BookFormat.TXT
        }
        val coverPath = try {
            val parser = BookParserFactory.createParser(format, this)
            parser.parse(file.absolutePath).coverPath
        } catch (_: Exception) { null }

        val book = Book(
            id = FileUtils.generateBookId(),
            title = fileName.substringBeforeLast('.'),
            author = "未知作者",
            filePath = file.absolutePath,
            coverPath = coverPath,
            format = format,
            lastReadTime = System.currentTimeMillis(),
            readingProgress = 0f,
            createdAt = System.currentTimeMillis()
        )
        bookRepository.insertBook(book)
        withContext(Dispatchers.Main) {
            Toast.makeText(this@MainActivity, "导入成功了喵~(=^‥^=)", Toast.LENGTH_SHORT).show()
        }
        Log.d("MainActivity", "Imported book from intent: ${book.title}")
    }

    /**
     * 不拦截 ActionMode：选区检测由 ReadView 的 SpanWatcher 处理，
     * 系统浮动工具栏通过 menu.clear() 清空菜单项（显示为空气泡）。
     * 不调用 mode.finish()，避免破坏选区手柄状态。
     */

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 处理外部文件打开（冷启动）
        handleImportIntent(intent)

        setContent {
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = "system")
            val isDark = when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            EBookReaderTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    MainNavGraph(navController = navController)
                }
            }
        }
    }
}
