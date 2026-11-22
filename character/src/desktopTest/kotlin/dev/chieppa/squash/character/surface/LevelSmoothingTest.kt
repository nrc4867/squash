package dev.chieppa.squash.character.surface

import kotlin.math.abs
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

    @Test
    fun withPositionsAValueChangingEvenlyStaysOnAnUnevenMesh() {
        val length = 12
        val positions = FloatArray(length * 2 * 3)
        for (i in 0 until length) for (side in 0 until 2) {
            val v = i * 2 + side
            positions[v * 3] = i * 0.01f + if (i % 3 == 1) 0.004f else 0f
            positions[v * 3 + 1] = side * 0.01f + if (i % 2 == 1) 0.003f else 0f
        }
        val values = FloatArray(length * 2) { v -> positions[v * 3] - 0.055f + 0.5f * positions[v * 3 + 1] }
        val eased = smoothedNearZero(values, strip(length), values.size, band = 0.05f, positions = positions)
        val plain = smoothedNearZero(values, strip(length), values.size, band = 0.05f)
        for (v in values.indices) assertTrue(abs(eased[v] - values[v]) < 5e-4f, "vertex $v moved from ${values[v]} to ${eased[v]}")
        assertTrue(values.indices.any { abs(plain[it] - values[it]) > 1e-3f }, "a plain mean bends the same values")
    }

    @Test
    fun aBandNarrowerThanTheSmoothingKeepsItsSignWithinItsShift() {
        val length = 12
        val values = FloatArray(length * 2) { v -> if (v / 2 == length / 2) 0.004f else -0.004f }
        val free = smoothedNearZero(values, strip(length), values.size, band = 0.05f)
        val held = smoothedNearZero(values, strip(length), values.size, band = 0.05f, shift = 0.003f)
        val middle = length / 2 * 2
        assertTrue(free[middle] < 0f, "unheld, the narrow band is eased away: ${free[middle]}")
        assertTrue(held[middle] > 0f, "held, the narrow band stays: ${held[middle]}")
        for (v in values.indices) assertTrue(abs(held[v] - values[v]) <= 0.003f + 1e-6f)
    }

    @Test
    fun eachVertexMovesNoFurtherThanItsOwnShift() {
        val length = 12
        val values = FloatArray(length * 2) { v -> if (v / 2 == length / 2) 0.004f else -0.004f }
        val middle = length / 2 * 2
        val shifts = FloatArray(values.size) { v -> if (v == middle) 0.001f else 0.01f }
        val eased = NearZeroSmoothing(values, strip(length), values.size, band = 0.05f, shift = 0.01f, shifts = shifts).run()
        assertTrue(eased[middle] >= values[middle] - 0.001f - 1e-6f, "a vertex of a small shift is held to it: ${eased[middle]}")
        assertTrue(eased[middle + 1] < 0f, "beside it a vertex of the larger shift is eased across zero: ${eased[middle + 1]}")
    }
}
