package com.arnoldcode.glassprompt.core.common

/** Logging abstraction so data/domain code stays testable and a crash reporter can be plugged in later. */
interface Logger {
    fun d(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
