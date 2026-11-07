package dev.chieppa.squash.rig

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Spike 4, weights and stretch: the fennec's back leg and paw bent and stretched, with nothing smearing and volume held. */
class SpikeFourTest {
    private val leg = PartId("back-leg-l")
    private val paw = PartId("back-paw-l")
    private val torso = PartId("torso")
    private val bent = listOf(BonePose(leg, "hip", 30f), BonePose(leg, "knee", 60f), BonePose(leg, "ankle", 30f))
    private val stretched = Pose(bent.dropLast(1) + BonePose(leg, "ankle", 30f, stretch = STRETCH))

    private fun measure(pose: Pose): Map<PartId, Triple<Int, Float, Float>> {
        val rig = fennec.rig
        val transforms = boneTransforms(rig, pose)
        val posed = rig.posed(pose)
        return listOf(torso, leg, paw).associateWith { id ->
            val part = checkNotNull(rig.part(id))
            val moved = checkNotNull(posed.parts[id])
            val single = edges(part.indices).filter { (a, b) -> part.weights.of(a).size == 1 && part.weights.of(b).size == 1 }
            val band = edges(part.indices) - single.toSet()
            Triple(
                invertedTriangles(part, moved, transforms),
                single.maxOfOrNull { edgeChange(part, moved, it) } ?: 0f,
                band.maxOfOrNull { edgeChange(part, moved, it) } ?: 0f,
            )
        }
    }

    @Test
    fun aBentLegSmearsNothing() {
        val measured = measure(Pose(bent))
        println("bent: $measured")
        for ((part, m) in measured) {
            assertEquals(0, m.first, "$part has inverted triangles")
            assertTrue(m.second <= RIGID_EDGE, "$part: an edge outside every band changed by ${m.second}")
            assertTrue(m.third <= BAND_EDGE, "$part: an edge in a band changed by ${m.third}")
        }
    }

    @Test
    fun aStretchedAnkleHoldsTheLegsVolume() {
        val rig = fennec.rig
        val part = checkNotNull(rig.part(leg))
        val rest = enclosedVolume(part.rest, part.indices)
        fun change(pose: Pose) = enclosedVolume(checkNotNull(rig.posed(pose).parts[leg]), part.indices) / rest - 1f
        val measured = measure(stretched)
        println("stretched: $measured")
        for ((p, m) in measured) assertEquals(0, m.first, "$p has inverted triangles")
        val changes = mapOf(
            "bent" to change(Pose(bent)),
            "bent and stretched" to change(stretched),
            "stretched" to change(Pose(listOf(BonePose(leg, "ankle", stretch = STRETCH)))),
            "squashed" to change(Pose(listOf(BonePose(leg, "ankle", stretch = SQUASH)))),
        )
        println("volume $rest, changes $changes")
        for ((name, c) in changes) assertTrue(kotlin.math.abs(c) <= VOLUME, "$name: the leg's volume changed by $c")
    }

    private companion object {
        const val STRETCH = 1.3f
        const val SQUASH = 0.7f
        const val RIGID_EDGE = 1e-4f
        const val BAND_EDGE = 0.8f
        const val VOLUME = 0.03f
    }
}
