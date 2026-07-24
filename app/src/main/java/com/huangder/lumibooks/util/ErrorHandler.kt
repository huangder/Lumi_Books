package com.huangder.lumibooks.util

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineExceptionHandler

object ErrorHandler {
    private const val TAG = "ErrorHandler"

    fun handleException(context: Context, exception: Exception) {
        val message = when (exception) {
            is java.io.FileNotFoundException -> "文件未找到"
            is java.io.IOException -> "文件读取错误"
            is SecurityException -> "权限不足"
            else -> "发生未知错误: ${exception.message}"
        }

        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun createExceptionHandler(context: Context): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, exception ->
            handleException(context, exception as Exception)
        }
    }

    fun showError(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun showSuccess(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
