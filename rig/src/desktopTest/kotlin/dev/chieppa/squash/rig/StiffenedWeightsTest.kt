package dev.chieppa.squash.rig

import dev.chieppa.squash.document.PartId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A stiffness sharpens a bound part's weights toward each vertex's heaviest bone and keeps them summing to one. */
class StiffenedWeightsTest {
    private val part = PartId("shaft")

    /** One vertex held 0.7 by bone 0 and 0.3 by bone 1. */
    private val weights = PartWeights(part, intArrayOf(0, 1, -1, -1), floatArrayOf(0.7f, 0.3f, 0f, 0f))

    @Test
    fun noStiffnessKeepsTheWeights() {
        assertSame(weights, stiffened(weights, 0f))
    }

    @Test
    fun stiffnessGathersTheWeightOntoTheHeaviestBone() {
        val half = stiffened(weights, 0.5f)
        assertEquals(0.49f / 0.58f, half.weightOf(0, 0), TOLERANCE)
        assertEquals(1f, half.weightOf(0, 0) + half.weightOf(0, 1), TOLERANCE)
        assertTrue(half.weightOf(0, 0) > weights.weightOf(0, 0))
    }

    @Test
    fun fullStiffnessKeepsTheHeaviestBoneAlone() {
        val rigid = stiffened(weights, 1f)
        assertEquals(listOf(0 to 1f), rigid.of(0))
    }

    private companion object {
        const val TOLERANCE = 1e-5f
    }
}
