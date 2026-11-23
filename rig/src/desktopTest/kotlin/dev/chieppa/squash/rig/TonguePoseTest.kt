package dev.chieppa.squash.rig

import dev.chieppa.squash.character.mouth.shaped.TONGUE_JOINTS
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A shaped tongue's joints as its control bends them: at rest untouched, lifted up toward 0.5, out and lengthened by 1. */
class TonguePoseTest {
    private val head = PartId("head")

    private fun Pose.tip(joint: String) = of(head, joint)

    @Test
    fun atRestTheTongueLiesAsBuilt() {
        assertEquals(Pose(), Pose().withTongues(mapOf(head to 0f)))
    }

    @Test
    fun halfWayItCurlsUpAndAllTheWayItReachesOutAndDroops() {
        val lifted = Pose().withTongues(mapOf(head to 0.5f))
        TONGUE_JOINTS.forEach { assertTrue(requireNotNull(lifted.tip(it)).degrees < 0f) }
        val out = Pose().withTongues(mapOf(head to 1f))
        val tip = requireNotNull(out.tip(TONGUE_JOINTS.last()))
        assertTrue(tip.degrees > 0f)
        assertTrue(tip.stretch > 1f)
    }

    @Test
    fun aJointThePoseSetsKeepsWhatItSets() {
        val own = BonePose(head, TONGUE_JOINTS.first(), degrees = 5f)
        assertEquals(own, Pose().with(own).withTongues(mapOf(head to 1f)).tip(TONGUE_JOINTS.first()))
    }
}
