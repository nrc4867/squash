package dev.chieppa.squash.rig

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private val PAW_L = PartId("front-paw-l")

/** How many toes the dragon's paws have. */
private const val TOES = 3

/** How far a whole curl carries each claw's point at least, in body units, so the claw is seen to ride its toe. */
private const val CARRIED = 0.01f

/** The dragon's claws, grown from its paws' toes: each claw's point rides its toe's last joint as the paw curls. */
@Tag("integration")
class ClawRigTest {
    private val dragon get() = dragonRig

    private fun point(positions: FloatArray, vertex: Int) = Vec3(positions[vertex * 3], positions[vertex * 3 + 1], positions[vertex * 3 + 2])

    /** The vertex of toe [i] farthest along it: its claw's point. */
    private fun clawPoint(paw: RigPart, i: Int) = paw.vertices.indices.filter { paw.vertices[it].patch == "toes:$i" }.maxBy { paw.vertices[it].u }

    @Test
    fun aClawFollowsItsToesLastJoint() {
        val paw = assertNotNull(dragon.part(PAW_L))
        for (i in 0 until TOES) {
            val (bone, share) = paw.weights.of(clawPoint(paw, i)).maxBy { it.second }
            assertEquals(BoneName(PAW_L, "toes:$i.2"), dragon.bones[bone].name, "claw $i")
            assertTrue(share > 0.5f, "claw $i rides its last joint: $share")
        }
    }

    @Test
    fun aCurledToeCarriesItsClaw() {
        val paw = assertNotNull(dragon.part(PAW_L))
        val curled = dragon.posed(dragon.curled(Pose(), listOf(PAW_L), 1f)).parts.getValue(PAW_L)
        for (i in 0 until TOES) {
            val tip = clawPoint(paw, i)
            val moved = (point(curled, tip) - point(paw.rest, tip)).length
            assertTrue(moved > CARRIED, "claw $i curls with its toe: $moved")
        }
    }
}
