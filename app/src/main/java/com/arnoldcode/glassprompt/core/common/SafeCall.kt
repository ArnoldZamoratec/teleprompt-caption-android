package com.arnoldcode.glassprompt.core.common

import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block] and converts thrown exceptions into [AppResult.Failure] using [mapError].
 * Cancellation is always rethrown so structured concurrency keeps working.
 */
inline fun <T> safeCall(
    logger: Logger,
    tag: String,
    mapError: (Throwable) -> AppError = { AppError.Unexpected(it) },
    block: () -> T,
): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    logger.e(tag, "Operation failed", e)
    AppResult.Failure(mapError(e))
}
