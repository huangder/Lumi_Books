package com.huangder.lumibooks.util.parser

import java.nio.charset.Charset

/** TXT 阅读器支持的编码选项。AUTO 会优先识别 BOM 和 UTF 编码，再回退到 GB18030。 */
enum class TxtEncoding(
    val storageValue: String,
    val charsetName: String?
) {
    AUTO("auto", null),
    UTF_8("utf-8", "UTF-8"),
    GB18030("gb18030", "GB18030"),
    BIG5("big5", "Big5"),
    UTF_16LE("utf-16le", "UTF-16LE"),
    UTF_16BE("utf-16be", "UTF-16BE"),
    SHIFT_JIS("shift-jis", "Shift_JIS"),
    EUC_KR("euc-kr", "EUC-KR"),
    WINDOWS_1252("windows-1252", "windows-1252");

    fun charsetOrNull(): Charset? = charsetName?.let(Charset::forName)

    companion object {
        fun fromStorage(value: String?): TxtEncoding = entries.firstOrNull {
            it.storageValue.equals(value, ignoreCase = true) ||
                it.charsetName?.equals(value, ignoreCase = true) == true
        } ?: AUTO
    }
}
