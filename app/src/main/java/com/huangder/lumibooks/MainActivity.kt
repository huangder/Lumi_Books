package com.huangder.lumibooks

import android.os.Bundle
import android.view.ActionMode
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.huangder.lumibooks.ui.navigation.MainNavGraph
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * 当 ReaderScreen 处于前台时置为 true，
     * 确保 ActionMode 拦截只在阅读页生效，不影响其他页面。
     */
    var isInReaderScreen = false

    /**
     * Compose 层注册的回调：ActionMode 被拦截后触发，
     * 通知 ReaderScreen 弹出自定义选择菜单。
     */
    var onSelectionActionModeStarted: (() -> Unit)? = null

    /**
     * 拦截 TextView 原生浮动工具栏（Copy / Select All / Web Search 等）。
     * 只在阅读页且 TYPE_PRIMARY 时生效：finish() 关闭 ActionMode UI，
     * 但不清除 Spannable 选区，选择手柄依然保留。
     */
    override fun onActionModeStarted(mode: ActionMode) {
        // Android API 23+ 文字选择使用 TYPE_FLOATING（浮动工具栏）
        if (isInReaderScreen && mode.type == ActionMode.TYPE_FLOATING) {
            // 先触发 Compose 层回调（设置 selectionState），再 finish()
            // 顺序很重要：finish() 会同步触发 onDestroyActionMode，
            // 若 onDestroyActionMode 有副作用必须在 selectionState 设置之后执行
            onSelectionActionModeStarted?.invoke()
            mode.finish()
            return
        }
        super.onActionModeStarted(mode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            EBookReaderTheme {
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
