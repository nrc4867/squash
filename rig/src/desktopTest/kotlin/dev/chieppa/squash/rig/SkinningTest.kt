package dev.chieppa.squash.rig

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkinningTest {
    private val rig get() = fennec.rig

    @Test
    fun theRestPoseLeavesEveryVertexWhereItRests() {
        val posed = rig.posed(Pose())
        for (part in rig.parts) {
            val moved = checkNotNull(posed.parts[part.id])
            for (i in moved.indices) assertEquals(part.rest[i], moved[i], 1e-5f, "${part.id}")
        }
    }

    @Test
    fun oneAngleOnBothSidesPosesThemAsMirrorImages() {
        val legs = listOf("back-leg-l", "back-leg-r").map(::PartId)
        val pose = Pose(legs.flatMap { listOf(BonePose(it, "hip", 25f), BonePose(it, "knee", 50f), BonePose(it, "ankle", -20f, stretch = 1.2f)) })
        val posed = rig.posed(pose)
        for ((left, right) in listOf("back-leg-l" to "back-leg-r", "back-paw-l" to "back-paw-r").map { (l, r) -> PartId(l) to PartId(r) }) {
            val l = checkNotNull(posed.parts[left])
            val r = checkNotNull(posed.parts[right])
            for (v in 0 until l.size / 3) {
                assertEquals(l[v * 3], r[v * 3], 1e-4f)
                assertEquals(l[v * 3 + 1], r[v * 3 + 1], 1e-4f)
                assertEquals(l[v * 3 + 2], -r[v * 3 + 2], 1e-4f)
            }
        }
    }

    @Test
    fun aTurnIsHeldToItsJointsLimits() {
        val leg = PartId("back-leg-l")
        val past = rig.posed(Pose(listOf(BonePose(leg, "knee", 200f)))).parts.getValue(leg)
        val atLimit = rig.posed(Pose(listOf(BonePose(leg, "knee", 140f)))).parts.getValue(leg)
        assertContentEquals(atLimit, past)
    }

    @Test
    fun aStretchCarriesWhatSitsPastTheJoint() {
        val leg = PartId("back-leg-l")
        val transforms = boneTransforms(rig, Pose(listOf(BonePose(leg, "ankle", stretch = 1.5f))))
        val knee = checkNotNull(rig.bone(BoneName(leg, "knee")))
        val ankle = checkNotNull(rig.indexOf(BoneName(leg, "ankle")))
        val wrist = checkNotNull(rig.indexOf(BoneName(PartId("back-paw-l"), "wrist")))
        val reach = transforms[ankle](rig.bones[ankle].pivot) - knee.pivot
        assertEquals(1.5f * knee.length, reach.length, 1e-4f)
        assertTrue((transforms[wrist](rig.bones[wrist].pivot) - rig.bones[wrist].pivot).length > 0.4f * knee.length)
    }
}
