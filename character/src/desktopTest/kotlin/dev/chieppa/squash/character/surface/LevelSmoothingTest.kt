package dev.chieppa.squash.character.surface

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LevelSmoothingTest {
    /** A strip of quads, two vertices across and [length] along, as triangles. */
    private fun strip(length: Int): IntArray = (0 until length - 1).flatMap { i ->
        val a = i * 2
        listOf(a, a + 1, a + 2, a + 1, a + 3, a + 2)
    }.toIntArray()

    @Test
    fun aStepNearZeroIsEasedAndValuesFarFromItStay() {
        val length = 12
        val values = FloatArray(length * 2) { v -> if (v / 2 < length / 2) -0.01f else 0.01f }
        values[0] = -1f
        val eased = smoothedNearZero(values, strip(length), values.size, band = 0.05f)
        assertEquals(-1f, eased[0], "a value further from zero than the band stays")
        val across = (length / 2 - 1) * 2
        assertTrue(eased[across] > values[across] && eased[across + 2] < values[across + 2], "the step either side of zero eases toward its neighbours")
        for (v in values.indices) assertTrue(eased[v] in -1f..0.01f)
    }
}
