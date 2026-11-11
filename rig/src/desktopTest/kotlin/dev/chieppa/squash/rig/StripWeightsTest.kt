package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.nearestVertex
import dev.chieppa.squash.character.build.patchWithFirst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Closer than this is the same weight. */
private const val SAME = 1e-4f

/** A host's bone weighing less than this at the vertex nearest the foot may be left out between its neighbours. */
private const val NEGLIGIBLE = 0.05f

/** A strip's foot skins as its host does under it and its far ring as its tube's ring, so the strip bends as one with both. */
class StripWeightsTest {
    @Test
    fun aStripsEndsFollowWhatTheyMeet() {
        val strung = fennec.assembled.parts.filter { it.covering == null && it.strips.isNotEmpty() }
        assertTrue(strung.isNotEmpty(), "the fennec's joins are strips")
        for (tube in strung) for (strip in tube.strips) {
            val tubeWeights = checkNotNull(fennec.rig.part(checkNotNull(tube.id))).weights
            val hostWeights = checkNotNull(fennec.rig.part(strip.host)).weights
            val host = checkNotNull(fennec.assembled.part(strip.host))
            val (own, ownFirst) = checkNotNull(tube.patchWithFirst(strip.tube))
            val (patch, first) = checkNotNull(tube.patchWithFirst(strip.patch))
            val (onHost, hostFirst) = checkNotNull(host.patchWithFirst(strip.hostPatch))
            val footRow = if (strip.atStart) 0 else patch.nu - 1
            val ringRow = patch.nu - 1 - footRow
            for (j in 0 until patch.nv) {
                val under = nearestVertex(onHost, hostFirst, strip.foot[j].u, strip.foot[j].v)
                val footBones = tubeWeights.of(first + patch.index(footRow, j)).map { it.first }.toSet()
                assertTrue(hostWeights.of(under).all { (bone, w) -> w < NEGLIGIBLE || bone in footBones }, "the foot follows the host's bones")
                assertSameWeights(tubeWeights, ownFirst + own.index(strip.ring, j), tubeWeights, first + patch.index(ringRow, j))
            }
        }
    }

    private fun assertSameWeights(from: PartWeights, vertex: Int, to: PartWeights, onStrip: Int) {
        val expected = from.of(vertex).toMap()
        for ((bone, weight) in to.of(onStrip)) assertEquals(expected[bone] ?: 0f, weight, SAME, "bone $bone")
    }
}
