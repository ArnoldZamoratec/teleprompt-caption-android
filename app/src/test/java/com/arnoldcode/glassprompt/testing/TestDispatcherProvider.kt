package com.arnoldcode.glassprompt.testing

import com.arnoldcode.glassprompt.core.common.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher

/** Routes every dispatcher to one test dispatcher so virtual time controls all work. */
class TestDispatcherProvider(dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}
