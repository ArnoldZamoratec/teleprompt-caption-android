package com.arnoldcode.glassprompt.domain.captions

import com.arnoldcode.glassprompt.domain.model.Caption
import com.arnoldcode.glassprompt.domain.model.CaptionAnimation
import com.arnoldcode.glassprompt.domain.model.CaptionWord
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CaptionMotionTest {

    private val caption = Caption(
        "a", "uno dos tres", 1_000, 2_000,
        listOf(CaptionWord("uno", 1_000, 1_300), CaptionWord("dos", 1_300, 1_600), CaptionWord("tres", 1_600, 2_000)),
    )
    private val track = listOf(Caption("0", "x", 0, 500), caption, Caption("b", "y", 2_500, 3_000))

    @Test
    fun `finds the caption on screen`() {
        assertThat(CaptionMotion.captionAt(track, 1_000)?.id).isEqualTo("a")
        assertThat(CaptionMotion.captionAt(track, 1_999)?.id).isEqualTo("a")
        assertThat(CaptionMotion.captionAt(track, 2_000)).isNull()
        assertThat(CaptionMotion.captionAt(track, 2_700)?.id).isEqualTo("b")
        assertThat(CaptionMotion.captionAt(emptyList(), 0)).isNull()
    }

    @Test
    fun `active word is the last one that started`() {
        assertThat(CaptionMotion.activeWord(caption, 999)).isEqualTo(-1)
        assertThat(CaptionMotion.activeWord(caption, 1_000)).isEqualTo(0)
        assertThat(CaptionMotion.activeWord(caption, 1_450)).isEqualTo(1)
        assertThat(CaptionMotion.activeWord(caption, 1_999)).isEqualTo(2)
    }

    @Test
    fun `fade goes in, holds and goes out`() {
        val at = { t: Long -> CaptionMotion.frame(caption, CaptionAnimation.FADE, t).alpha }
        assertThat(at(1_000)).isEqualTo(0f)
        assertThat(at(1_100)).isIn(com.google.common.collect.Range.open(0f, 1f))
        assertThat(at(1_500)).isEqualTo(1f)
        assertThat(at(1_930)).isLessThan(1f)
        assertThat(at(2_000)).isEqualTo(0f)
    }

    @Test
    fun `pop overshoots then settles`() {
        val scales = (1_000L..1_300L step 10).map { CaptionMotion.frame(caption, CaptionAnimation.POP, it).scale }
        assertThat(scales.first()).isWithin(0.001f).of(0.6f)
        assertThat(scales.max()).isGreaterThan(1f)
        assertThat(scales.last()).isWithin(0.001f).of(1f)
    }

    @Test
    fun `slide rises into place`() {
        assertThat(CaptionMotion.frame(caption, CaptionAnimation.SLIDE, 1_000).offsetY).isWithin(0.001f).of(0.6f)
        assertThat(CaptionMotion.frame(caption, CaptionAnimation.SLIDE, 1_500).offsetY).isEqualTo(0f)
    }

    @Test
    fun `word highlight and karaoke follow the spoken word`() {
        val highlight = CaptionMotion.frame(caption, CaptionAnimation.WORD_HIGHLIGHT, 1_450)
        assertThat(highlight.highlightWord).isEqualTo(1)
        assertThat(highlight.filledThrough).isEqualTo(-1)
        val karaoke = CaptionMotion.frame(caption, CaptionAnimation.KARAOKE, 1_700)
        assertThat(karaoke.filledThrough).isEqualTo(2)
        assertThat(karaoke.highlightWord).isEqualTo(-1)
    }

    @Test
    fun `no animation is static`() {
        assertThat(CaptionMotion.frame(caption, CaptionAnimation.NONE, 1_000)).isEqualTo(CaptionFrame(1f, 1f, 0f, -1, -1))
    }
}
