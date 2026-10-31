package com.huangder.lumibooks.util

import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Cold flow: each visible owner observes date changes only for its own lifetime. */
fun localDateChanges() = flow {
    while (true) {
        emit(LocalDate.now())
        delay(1_000)
    }
}.distinctUntilChanged()
