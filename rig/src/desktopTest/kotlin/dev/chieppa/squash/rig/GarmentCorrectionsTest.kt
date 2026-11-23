package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.RenderMesh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cloth taking the body's key correction at a crease: a flat skin, the half past x = 0.05 moved out along its normal by a key, and cloth
 * a little over it. Cloth over the edge of the moved half moves out at least as far as the skin beside it, so none passes through.
 */
class GarmentCorrectionsTest {
    private val cell = 0.01f
    private val out = 0.01f

    /** A flat skin of corners every [cell] over x and y from 0 to 0.1, at z = 0, facing +z. */
    private val skin: FloatArray = FloatArray(11 * 11 * 3).also { p ->
        for (i in 0..10) for (j in 0..10) {
            val v = i * 11 + j
            p[v * 3] = i * cell
            p[v * 3 + 1] = j * cell
        }
    }

    /** The corners a key moves: those past x = 0.05, each moved [out] along +z. */
    private val moved = (0 until 121).filter { skin[it * 3] > 0.055f }.toIntArray()
    private val correction = RestCorrection(moved, FloatArray(moved.size * 3) { if (it % 3 == 2) out else 0f }, FloatArray(moved.size * 3))

    /** Cloth vertices at [places], 0.003 over the skin, facing +z. */
    private fun cloth(vararg places: Pair<Float, Float>): FloatArray = FloatArray(places.size * RenderMesh.FLOATS_PER_VERTEX).also { v ->
        places.forEachIndexed { i, (x, y) ->
            val at = i * RenderMesh.FLOATS_PER_VERTEX
            v[at] = x
            v[at + 1] = y
            v[at + 2] = 0.003f
            v[at + RenderMesh.NORMAL_OFFSET_FLOATS + 2] = 1f
        }
    }

    private fun carried(vararg places: Pair<Float, Float>): Map<Int, Float> {
        val made = GarmentCorrections(skin, 121, cell, moved).of(cloth(*places), places.size, correction)
        return made.moved.withIndex().associate { (i, g) -> g to made.offsets[i * 3 + 2] }
    }

    @Test
    fun clothOverTheCreaseMovesOutAsFarAsTheSkinBesideIt() {
        val atEdge = carried(0.052f to 0.05f)[0] ?: 0f
        assertTrue(atEdge >= out - 1e-6f, "the cloth over the crease moves out with the skin beside it: $atEdge")
    }

    @Test
    fun clothOverMovedSkinTakesItsMoveAndClothFarFromItNone() {
        val made = carried(0.09f to 0.05f, 0.01f to 0.05f)
        assertEquals(out, made[0] ?: 0f, 1e-5f, "over the moved half the cloth moves as the skin does")
        assertEquals(0f, made[1] ?: 0f, 1e-6f, "far from it nothing moves")
    }
}
