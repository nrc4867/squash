package dev.chieppa.squash.rig

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.UpdateFromLibrary
import dev.chieppa.squash.document.readDocument
import dev.chieppa.squash.geom.Vec3
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private val PAW_L = PartId("front-paw-l")

/** How many toes the dragon's paws have. */
private const val TOES = 3

/** The paw version that offers hard claws. */
private const val CLAWED_PAW = 13

/** How much of a claw point's weight its root bone holds at least: all of it, but for rounding. */
private const val HARD = 0.999f

/** How near, in body units, two places count as one. */
private const val SAME_PLACE = 1e-4f

/** The dragon with hard claws on its front paws, rigged once. */
private val clawedRig: Rig by lazy {
    val dragon = readDocument(Path.of(System.getProperty("squash.examples"), "dragon.json").readText())
    val updated = UpdateFromLibrary(PAW_L, CLAWED_PAW).apply(dragon).document
    settledRig(SetParameter(PAW_L, "clawParts", 1f).apply(updated).document, "dragon with hard claws")
}

/** Hard claws: each its own two bones hanging from its toe's last joint, carried whole, and slid back into the toe by Retract. */
@Tag("integration")
class ClawRetractTest {
    private val rig get() = clawedRig

    private fun point(positions: FloatArray, vertex: Int) = Vec3(positions[vertex * 3], positions[vertex * 3 + 1], positions[vertex * 3 + 2])

    /** The vertex of claw [i] farthest along it: its point. */
    private fun clawPoint(paw: RigPart, i: Int) = paw.vertices.indices.filter { paw.vertices[it].patch == "claws:$i" }.maxBy { paw.vertices[it].u }

    @Test
    fun eachClawHangsFromItsToesLastJointAndRidesItsOwnRootWhole() {
        val paw = assertNotNull(rig.part(PAW_L))
        assertEquals(TOES, rig.clawBones(PAW_L).size)
        for (i in 0 until TOES) {
            val anchor = assertNotNull(rig.bone(BoneName(PAW_L, "claws:$i.1")))
            assertEquals(BoneName(PAW_L, "toes:$i.2"), anchor.parent, "claw $i's anchor hangs from its toe's last joint")
            val (bone, share) = paw.weights.of(clawPoint(paw, i)).maxBy { it.second }
            assertEquals(BoneName(PAW_L, "claws:$i.2"), rig.bones[bone].name, "claw $i")
            assertTrue(share > HARD, "claw $i is hard: its point rides its root alone: $share")
        }
    }

    @Test
    fun aCurlCarriesTheClawsAndLeavesTheirBonesAlone() {
        val paw = assertNotNull(rig.part(PAW_L))
        val curl = rig.curled(Pose(), listOf(PAW_L), 1f)
        assertTrue(curl.bones.none { it.joint.startsWith("claws:") }, "a curl sets no claw bone")
        val curled = rig.posed(curl).parts.getValue(PAW_L)
        for (i in 0 until TOES) assertTrue((point(curled, clawPoint(paw, i)) - point(paw.rest, clawPoint(paw, i))).length > SAME_PLACE, "claw $i curls with its toe")
    }

    @Test
    fun retractSlidesEveryClawBackTowardItsAnchorAndReadsBack() {
        val paw = assertNotNull(rig.part(PAW_L))
        assertEquals(0f, rig.retractOf(Pose(), PAW_L))
        for (amount in listOf(0.5f, 1f)) assertEquals(amount, assertNotNull(rig.retractOf(rig.retracted(Pose(), listOf(PAW_L), amount), PAW_L)), 1e-4f)
        val half = rig.posed(rig.retracted(Pose(), listOf(PAW_L), 0.5f)).parts.getValue(PAW_L)
        val full = rig.posed(rig.retracted(Pose(), listOf(PAW_L), 1f)).parts.getValue(PAW_L)
        for (i in 0 until TOES) {
            val tip = clawPoint(paw, i)
            val anchor = assertNotNull(rig.bone(BoneName(PAW_L, "claws:$i.1"))).pivot
            val out = (point(paw.rest, tip) - anchor).length
            val halfway = (point(half, tip) - anchor).length
            val inside = (point(full, tip) - anchor).length
            assertTrue(halfway < out && inside < halfway, "claw $i slides toward its anchor: $out, $halfway, $inside")
        }
    }
}
