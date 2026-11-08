package dev.chieppa.squash.rig

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val PAW_L = PartId("front-paw-l")

/** The claw on each toe of the dragon's left front paw. */
private fun clawOf(index: Int) = PartId("front-paw-l-claw-$index")

/** How far a claw's root may drift from its toe's tip under a whole curl, beyond where it sat at rest, in body units. */
private const val HELD = 1e-3f

/** How far a whole curl carries each toe's tip at least, in body units, so the claw is seen to ride it. */
private const val CARRIED = 0.01f

/** The dragon's claws: each hangs from its toe's last joint and rides it as the paw curls. */
class ClawRigTest {
    private val dragon get() = dragonRig

    private fun point(positions: FloatArray, vertex: Int) = Vec3(positions[vertex * 3], positions[vertex * 3 + 1], positions[vertex * 3 + 2])

    @Test
    fun aClawFollowsOnlyItsToesLastJoint() {
        for (i in 0 until 3) {
            val claw = assertNotNull(dragon.part(clawOf(i)))
            val followed = (0 until claw.rest.size / 3).flatMap { v -> claw.weights.of(v).map { (bone, _) -> dragon.bones[bone].name } }.toSet()
            assertEquals(setOf(BoneName(PAW_L, "toes:$i.2")), followed, "claw $i")
        }
    }

    @Test
    fun aCurledToeCarriesItsClaw() {
        val paw = assertNotNull(dragon.part(PAW_L))
        val posed = dragon.posed(dragon.curled(Pose(), listOf(PAW_L), 1f)).parts
        val curled = posed.getValue(PAW_L)
        for (i in 0 until 3) {
            val tip = paw.vertices.indices.filter { paw.vertices[it].patch == "toes:$i" }.maxBy { paw.vertices[it].u }
            val claw = assertNotNull(dragon.part(clawOf(i)))
            val bent = posed.getValue(clawOf(i))
            val root = (0 until claw.rest.size / 3).minBy { (point(claw.rest, it) - point(paw.rest, tip)).length }
            val atRest = (point(claw.rest, root) - point(paw.rest, tip)).length
            val moved = (point(curled, tip) - point(paw.rest, tip)).length
            assertTrue(moved > CARRIED, "toe $i curls: $moved")
            assertEquals(atRest, (point(bent, root) - point(curled, tip)).length, HELD, "claw $i keeps to its toe's tip")
        }
    }
}
