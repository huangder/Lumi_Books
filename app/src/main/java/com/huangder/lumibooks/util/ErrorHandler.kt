package com.huangder.lumibooks.util

import android.content.Context
import android.widget.Toast
import com.huangder.lumibooks.R
import kotlinx.coroutines.CoroutineExceptionHandler

object ErrorHandler {
    private const val TAG = "ErrorHandler"

    fun handleException(context: Context, exception: Throwable) {
        val message = when (exception) {
            is java.io.FileNotFoundException -> context.getString(R.string.error_file_not_found)
            is java.io.IOException -> context.getString(R.string.error_file_read)
            is SecurityException -> context.getString(R.string.error_permission_denied)
            else -> context.getString(R.string.error_unknown_detail, exception.message.orEmpty())
        }

        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun createExceptionHandler(context: Context): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, exception ->
            handleException(context, exception)
        }
    }

    fun showError(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun showSuccess(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
