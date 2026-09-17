package com.huangder.lumibooks.domain.model

/** Persisted values: never reinterpret the three existing layouts as column counts for Cover Flow. */
internal object BookshelfLayout {
    const val List = 1
    const val Grid = 2
    const val CompactGrid = 3
    const val CoverFlow = 4

    fun normalize(value: Int): Int = value.coerceIn(List, CoverFlow)
    fun conventional(value: Int): Int = if (value == CoverFlow) Grid else value.coerceIn(List, CompactGrid)
}
