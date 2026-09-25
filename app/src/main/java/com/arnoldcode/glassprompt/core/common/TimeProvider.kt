package com.arnoldcode.glassprompt.core.common

import java.time.LocalTime

/** Injectable clock so time-dependent logic (greetings, timestamps) is testable. */
fun interface TimeProvider {
    fun now(): LocalTime

    companion object {
        val System = TimeProvider { LocalTime.now() }
    }
}
