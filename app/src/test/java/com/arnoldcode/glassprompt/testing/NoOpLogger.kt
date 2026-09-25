package com.arnoldcode.glassprompt.testing

import com.arnoldcode.glassprompt.core.common.Logger

object NoOpLogger : Logger {
    override fun d(tag: String, message: String) = Unit
    override fun w(tag: String, message: String, throwable: Throwable?) = Unit
    override fun e(tag: String, message: String, throwable: Throwable?) = Unit
}
