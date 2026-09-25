package com.arnoldcode.glassprompt.core.common

import javax.inject.Qualifier

/** Qualifies the app-wide `CoroutineScope` that is never cancelled by a screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
