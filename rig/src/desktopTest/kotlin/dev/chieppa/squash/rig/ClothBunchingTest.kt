package dev.chieppa.squash.rig

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A strip of two quads along x: cloth that keeps its length does not bunch, cloth squeezed along it does, and a quad's middle follows. */
class ClothBunchingTest {
    /** Six corners in two rows of three, x 0 to 2 and y 0 to 1, as two quads, each two triangles with corners 0, 1 and 4, 5 in order. */
    private val rest = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 2f, 0f, 0f, 0f, 1f, 0f, 1f, 1f, 0f, 2f, 1f, 0f)
    private val triangles = intArrayOf(0, 1, 4, 0, 4, 3, 1, 2, 5, 1, 5, 4)

    private fun squeezed(by: Float) = FloatArray(rest.size) { if (it % 3 == 0) rest[it] * by else rest[it] }

    @Test
    fun clothThatKeepsItsLengthDoesNotBunch() {
        val bunched = clothBunching(triangles, 6, rest, rest.copyOf(), 8)
        assertTrue(bunched.all { it == 0f })
    }

    @Test
    fun clothSqueezedAlongItBunchesMoreTheMoreItIsSqueezed() {
        val some = clothBunching(triangles, 6, rest, squeezed(0.85f), 8)
        val most = clothBunching(triangles, 6, rest, squeezed(0.4f), 8)
        assertTrue(some.take(6).all { it > 0f && it < 1f }, some.toList().toString())
        assertTrue(most.take(6).all { it == 1f }, most.toList().toString())
        assertEquals((some[0] + some[1] + some[4] + some[3]) / 4f, some[6], 1e-5f)
    }
}
