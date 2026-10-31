package com.huangder.lumibooks.domain.model

/** Milliseconds of mutually exclusive reading and listening activity. */
data class ActivityDuration(
    val readingDuration: Long = 0,
    val listeningDuration: Long = 0
) {
    val totalDuration: Long get() = readingDuration + listeningDuration
}
