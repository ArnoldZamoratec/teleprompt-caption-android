package com.arnoldcode.glassprompt.core.common

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Test

class AppResultTest {

    private val logger = object : Logger {
        val errors = mutableListOf<String>()
        override fun d(tag: String, message: String) = Unit
        override fun w(tag: String, message: String, throwable: Throwable?) = Unit
        override fun e(tag: String, message: String, throwable: Throwable?) {
            errors += tag
        }
    }

    @Test
    fun `map transforms success and keeps failure`() {
        val success: AppResult<Int> = AppResult.Success(2)
        val failure: AppResult<Int> = AppResult.Failure(AppError.StorageFull)

        assertThat(success.map { it * 3 }).isEqualTo(AppResult.Success(6))
        assertThat(failure.map { it * 3 }).isEqualTo(failure)
    }

    @Test
    fun `safeCall wraps exceptions using the provided mapper and logs them`() {
        val result = safeCall(logger, "test", mapError = { AppError.FileNotReadable(it) }) {
            error("boom")
        }

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error).isInstanceOf(AppError.FileNotReadable::class.java)
        assertThat(logger.errors).containsExactly("test")
    }

    @Test
    fun `safeCall rethrows cancellation`() {
        assertThrows(CancellationException::class.java) {
            safeCall(logger, "test") { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun `getOrNull returns data only on success`() {
        assertThat(AppResult.Success("x").getOrNull()).isEqualTo("x")
        val failure: AppResult<String> = AppResult.Failure(AppError.StorageFull)
        assertThat(failure.getOrNull()).isNull()
    }
}
