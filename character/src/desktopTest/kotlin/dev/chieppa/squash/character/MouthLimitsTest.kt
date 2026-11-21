package dev.chieppa.squash.character

import dev.chieppa.squash.character.model.MouthLimits
import dev.chieppa.squash.character.model.MouthScales
import dev.chieppa.squash.character.model.MouthStyle
import dev.chieppa.squash.document.MarkControls
import kotlin.test.Test
import kotlin.test.assertEquals

/** A style's limits hold each control after its scale, so a shape asking for more gives way. */
class MouthLimitsTest {
    private val asked = MarkControls(open = 0.9f, wide = 0.8f, round = 0.7f, smile = -0.6f, upperLip = 0.5f, lowerLip = -0.9f, tongue = 0.8f)

    @Test
    fun freeLimitsLeaveTheControlsAsScaled() {
        assertEquals(asked, MouthLimits().of(asked))
    }

    @Test
    fun eachControlGoesNoFurtherThanItsLimit() {
        val limits = MouthLimits(open = 0.5f, wideOut = 0f, round = 0.4f, smile = 0.3f, upperLip = 0.2f, lowerLip = 0.5f, tongue = 0.1f)
        val held = limits.of(asked)
        assertEquals(MarkControls(open = 0.5f, wide = 0f, round = 0.4f, smile = -0.3f, upperLip = 0.2f, lowerLip = -0.5f, tongue = 0.1f), held)
        assertEquals(-0.4f, limits.of(asked.copy(wide = -0.4f)).wide, "drawing the corners in has its own limit")
    }

    @Test
    fun aStyleScalesBeforeItLimits() {
        val style = MouthStyle(id = "held", name = "Held", scale = MouthScales(open = 0.5f), limits = MouthLimits(open = 0.4f))
        assertEquals(0.4f, style.controlsOf(MarkControls(open = 1f)).open)
        assertEquals(0.3f, style.controlsOf(MarkControls(open = 0.6f)).open)
    }
}
