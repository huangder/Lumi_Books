package com.huangder.lumibooks.util.epub

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager

class EpubHighlightTestActivity : Activity() {
    companion object {
        @Volatile
        var current: EpubHighlightTestActivity? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }
}
