package com.arnoldcode.glassprompt.core.common

import android.util.Log
import com.arnoldcode.glassprompt.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidLogger @Inject constructor() : Logger {
    override fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(prefixed(tag), message)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        Log.w(prefixed(tag), message, throwable)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e(prefixed(tag), message, throwable)
    }

    private fun prefixed(tag: String) = "GP/$tag"
}
