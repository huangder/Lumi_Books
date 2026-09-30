package com.huangder.lumibooks.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import com.huangder.lumibooks.ui.reader.engine.readerSerifTypeface

@Composable
internal fun rememberReaderSerifFontFamily(weight: Int = 400): FontFamily {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(context, weight, locale) { FontFamily(readerSerifTypeface(context, weight)) }
}
