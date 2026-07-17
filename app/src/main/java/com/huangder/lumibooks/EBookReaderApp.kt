package com.huangder.lumibooks

import android.app.Application
import android.content.Context
import com.huangder.lumibooks.util.LocaleHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EBookReaderApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(base))
    }
}
