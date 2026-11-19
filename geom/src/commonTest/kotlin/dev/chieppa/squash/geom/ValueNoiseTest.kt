package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Value noise stays within -1 to 1, gives the same value for the same point, varies smoothly and differs by seed. */
class ValueNoiseTest {
    @Test
    fun noiseIsBoundedRepeatableSmoothAndSeeded() {
        var differs = 0
        for (i in 0 until 200) {
            val x = i * 0.137f
            val y = i * 0.071f - 3f
            val z = i * 0.019f + 1f
            val value = valueNoise(x, y, z)
            assertTrue(value in -1f..1f, "noise $value at $i")
            assertEquals(value, valueNoise(x, y, z))
            assertTrue(abs(valueNoise(x + 0.001f, y, z) - value) < 0.01f, "noise jumps at $i")
            if (abs(valueNoise(x, y, z, seed = 7) - value) > 1e-3f) differs++
        }
        assertTrue(differs > 150, "seeds give their own noise: $differs of 200 differ")
    }
}
