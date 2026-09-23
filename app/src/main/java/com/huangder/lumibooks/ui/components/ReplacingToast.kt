package com.huangder.lumibooks.ui.components

import android.content.Context
import android.widget.Toast

class ReplacingToast(private val context: Context) {
    private var current: Toast? = null

    fun show(message: String) {
        cancel()
        current = Toast.makeText(context, message, Toast.LENGTH_SHORT).also { it.show() }
    }

    fun cancel() {
        current?.cancel()
        current = null
    }
}
