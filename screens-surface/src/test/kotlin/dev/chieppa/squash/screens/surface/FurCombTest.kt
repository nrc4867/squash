package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.document.DEFAULT_COMB_REACH
import dev.chieppa.squash.document.GrainComb
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private const val PATCH = "body"
private val PART = PartId("tail")

class FurCombTest {
    private fun hit(u: Float, v: Float) = SurfaceHit(PART, PATCH, u, v, Vec3.ZERO)

    private fun comb(u: Float, v: Float) = GrainComb(PATCH, u, v, 0.1f, 0f, DEFAULT_COMB_REACH)

    @Test
    fun aCombRunsAlongTheDrag() {
        val comb = assertNotNull(combBetween(hit(0.2f, 0.5f), hit(0.3f, 0.5f)))
        assertEquals(0.3f, comb.u)
        assertEquals(0.1f, comb.du, 1e-5f)
        assertEquals(0f, comb.dv)
    }

    @Test
    fun aCombCrossesTheSeamTheShortWayRound() {
        val comb = assertNotNull(combBetween(hit(0.5f, 0.95f), hit(0.5f, 0.05f)))
        assertEquals(0.1f, comb.dv, 1e-5f)
    }

    @Test
    fun aDragThatBarelyMovesSaysNothing() {
        assertNull(combBetween(hit(0.5f, 0.5f), hit(0.5f, 0.5f)))
    }

    @Test
    fun combingAPlaceAgainReplacesWhatWasThere() {
        val held = listOf(comb(0.2f, 0.5f), comb(0.8f, 0.5f))
        val added = listOf(comb(0.21f, 0.5f))
        assertEquals(listOf(held[1], added[0]), mergedCombs(held, added))
    }

    @Test
    fun aPlaceAcrossTheSeamIsCloseToo() {
        assertEquals(1, mergedCombs(listOf(comb(0.5f, 0.99f)), listOf(comb(0.5f, 0.01f))).size)
    }
}
