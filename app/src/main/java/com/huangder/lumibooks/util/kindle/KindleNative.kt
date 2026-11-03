package com.huangder.lumibooks.util.kindle

@androidx.annotation.Keep
internal object KindleNative {
    init { System.loadLibrary("lumi_kindle") }
    external fun reconstruct(sourcePath: String, outputDirectory: String): Int
}

internal enum class KindleFailure { IO, INVALID, ENCRYPTED, UNSUPPORTED, LIMIT, CONVERSION, NO_SPACE }

internal class KindleException(val failure: KindleFailure, cause: Throwable? = null) :
    java.io.IOException(failure.name, cause)
