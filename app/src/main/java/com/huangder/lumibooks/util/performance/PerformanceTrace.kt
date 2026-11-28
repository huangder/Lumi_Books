package com.huangder.lumibooks.util.performance

import android.os.Build
import android.os.Trace

internal inline fun <T> tracePerformance(name: String, block: () -> T): T {
    if (Build.VERSION.SDK_INT >= 29 && !Trace.isEnabled()) return block()
    Trace.beginSection(name)
    try {
        return block()
    } finally {
        Trace.endSection()
    }
}
