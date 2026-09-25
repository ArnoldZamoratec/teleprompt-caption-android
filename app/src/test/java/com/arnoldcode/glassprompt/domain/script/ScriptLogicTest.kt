package com.arnoldcode.glassprompt.domain.script

import com.arnoldcode.glassprompt.domain.project.ProjectNames
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScriptTextTest {

    @Test
    fun `word count ignores punctuation-only tokens and extra whitespace`() {
        assertThat(ScriptText.countWords("")).isEqualTo(0)
        assertThat(ScriptText.countWords("   \n\t ")).isEqualTo(0)
        assertThat(ScriptText.countWords("Hola, ¿qué tal?  Bien — gracias...")).isEqualTo(5)
        assertThat(ScriptText.countWords("año 2026\nniño")).isEqualTo(3)
    }

    @Test
    fun `normalize unifies line endings, drops BOM and trailing whitespace`() {
        assertThat(ScriptText.normalize("﻿uno\r\ndos\rtres  \n\n")).isEqualTo("uno\ndos\ntres")
    }

    @Test
    fun `append separates with a blank line and handles empty sides`() {
        assertThat(ScriptText.append("A\n", "B")).isEqualTo("A\n\nB")
        assertThat(ScriptText.append("  ", "B")).isEqualTo("B")
        assertThat(ScriptText.append("A", "")).isEqualTo("A")
    }

    @Test
    fun `search key is case and accent insensitive`() {
        assertThat(ScriptText.searchKey("Canción ÑANDÚ")).isEqualTo("cancion nandu")
    }
}

class ScriptStatsTest {

    @Test
    fun `empty text has zero stats`() {
        assertThat(ScriptStats.of("")).isEqualTo(ScriptStats.Empty)
    }

    @Test
    fun `150 words read in one minute at speed 1 and faster at higher speed`() {
        val text = List(150) { "palabra" }.joinToString(" ")
        assertThat(ScriptStats.of(text).readingTimeSeconds).isEqualTo(60)
        assertThat(ScriptStats.of(text, speed = 2f).readingTimeSeconds).isEqualTo(30)
        assertThat(ScriptStats.of(text, speed = 0.5f).readingTimeSeconds).isEqualTo(120)
    }

    @Test
    fun `counts characters and paragraphs`() {
        val stats = ScriptStats.of("Hola mundo.\n\nSegundo párrafo.\n  \nTercero")
        assertThat(stats.words).isEqualTo(5)
        assertThat(stats.paragraphs).isEqualTo(3)
        assertThat(stats.characters).isEqualTo(40)
        assertThat(stats.charactersNoSpaces).isEqualTo(32)
    }

    @Test
    fun `absurdly low speed is clamped instead of dividing by zero`() {
        assertThat(ScriptStats.of("uno dos", speed = 0f).readingTimeSeconds).isEqualTo(8)
    }
}

class ScriptSearchTest {

    @Test
    fun `finds non-overlapping case-insensitive matches`() {
        assertThat(ScriptSearch.findAll("Hola hola HOLA", "hola")).containsExactly(0..3, 5..8, 10..13).inOrder()
        assertThat(ScriptSearch.findAll("aaaa", "aa")).containsExactly(0..1, 2..3).inOrder()
    }

    @Test
    fun `empty query or text yields no matches`() {
        assertThat(ScriptSearch.findAll("texto", "")).isEmpty()
        assertThat(ScriptSearch.findAll("", "x")).isEmpty()
    }

    @Test
    fun `query is literal, not a regex`() {
        assertThat(ScriptSearch.findAll("a.b axb", "a.b")).containsExactly(0..2)
    }

    @Test
    fun `replace at a range and replace all`() {
        assertThat(ScriptSearch.replaceAt("uno dos tres", 4..6, "2")).isEqualTo("uno 2 tres")
        assertThat(ScriptSearch.replaceAll("gato Gato perro", "gato", "león")).isEqualTo("león león perro" to 2)
        assertThat(ScriptSearch.replaceAll("sin cambios", "x", "y")).isEqualTo("sin cambios" to 0)
    }
}

class ProjectNamesTest {

    @Test
    fun `copy names count up and skip taken numbers`() {
        assertThat(ProjectNames.copyName("Demo", listOf("Demo"))).isEqualTo("Demo (2)")
        assertThat(ProjectNames.copyName("Demo", listOf("Demo", "Demo (2)", "Demo (3)"))).isEqualTo("Demo (4)")
    }

    @Test
    fun `duplicating a copy reuses the base name`() {
        assertThat(ProjectNames.copyName("Demo (2)", listOf("Demo", "Demo (2)"))).isEqualTo("Demo (3)")
    }
}
