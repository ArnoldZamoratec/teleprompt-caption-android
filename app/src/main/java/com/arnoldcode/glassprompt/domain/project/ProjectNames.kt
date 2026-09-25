package com.arnoldcode.glassprompt.domain.project

/** Language-neutral naming for duplicated projects: "Demo" → "Demo (2)" → "Demo (3)". */
object ProjectNames {

    private val NumberSuffix = Regex("^(.*) \\((\\d+)\\)$")

    fun copyName(original: String, existing: Collection<String>): String {
        val base = NumberSuffix.matchEntire(original)?.groupValues?.get(1) ?: original
        val taken = existing.toHashSet()
        var n = 2
        while ("$base ($n)" in taken) n++
        return "$base ($n)"
    }
}
