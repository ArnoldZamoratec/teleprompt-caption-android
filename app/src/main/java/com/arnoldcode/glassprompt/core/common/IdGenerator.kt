package com.arnoldcode.glassprompt.core.common

import java.util.UUID

/** Injectable ID source so persisted IDs are deterministic in tests. */
fun interface IdGenerator {
    fun newId(): String

    companion object {
        val Uuid = IdGenerator { UUID.randomUUID().toString() }
    }
}
