package dev.chieppa.squash.rig

import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TPoseTest {
    private val left = PartId("arm-l")
    private val right = PartId("arm-r")

    /**
     * An arm pair at rest leaning out [outward] degrees from straight down in body space (x forward, z to the right, the left arm on -z,
     * a hinge across the body as a hinge-x joint of a part resting down has),
     * the root bone named [root] and its next [elbow].
     */
    private fun arms(outward: Float, root: String, elbow: String, forward: Float = 0f): Rig {
        val radians = outward * PI.toFloat() / 180f
        val ahead = forward * PI.toFloat() / 180f
        fun bone(part: PartId, joint: String, parent: String?, sign: Float, length: Float) = Bone(
            BoneName(part, joint), parent?.let { BoneName(part, it) }, Vec3(0.004f, 1.4f, sign * 0.2f),
            Vec3(cos(radians) * sin(ahead), -cos(radians) * cos(ahead), sign * sin(radians)).normalized(), length, Vec3.Z, null, false, mirrored = sign > 0f,
        )
        val bones = listOf(left to -1f, right to 1f).flatMap { (part, sign) ->
            listOf(bone(part, root, null, sign, 0.3f), bone(part, elbow, root, sign, 0.3f), bone(part, "tip", elbow, sign, 0f))
        }
        return Rig(bones, emptyList())
    }

    private fun upperArmAfter(rig: Rig, part: PartId, root: String): Vec3 {
        val bone = assertNotNull(rig.bone(BoneName(part, root)))
        val pose = tPoseOf(rig, listOf(left, right))
        return localTurn(bone, pose.of(part, root).angles()) * bone.direction
    }

    private fun assertLevelAndOut(rig: Rig, root: String) {
        val l = upperArmAfter(rig, left, root)
        val r = upperArmAfter(rig, right, root)
        assertEquals(0f, l.y, 1e-3f, "left $l")
        assertEquals(0f, r.y, 1e-3f, "right $r")
        assertTrue(l.z < -0.9f && r.z > 0.9f, "straight out to each side: $l $r")
    }

    @Test
    fun aTubeArmLevelsAtTwoRestOutwards() {
        listOf(45f, 20f).forEach { assertLevelAndOut(arms(it, "base", "joint.1"), "base") }
    }

    @Test
    fun theArmTemplateLevelsAtTwoRestOutwards() {
        listOf(45f, 70f).forEach { assertLevelAndOut(arms(it, "shoulder", "elbow"), "shoulder") }
    }

    @Test
    fun anArmRestingAheadOfTheBodyStillLevelsOutToTheSide() {
        val rig = arms(30f, "shoulder", "elbow", forward = 20f)
        val l = upperArmAfter(rig, left, "shoulder")
        assertEquals(0f, l.y, 1e-3f)
        assertTrue(l.z < 0f)
    }

    @Test
    fun theRootLeansFreeAndTheElbowIsStraight() {
        val pose = tPoseOf(arms(45f, "base", "joint.1"), listOf(left))
        assertEquals(JointMode.FREE, pose.of(left, "base")?.mode)
        assertTrue(abs(assertNotNull(pose.of(left, "base")).lean) > 0f)
        assertEquals(0f, pose.of(left, "joint.1")?.degrees ?: 0f)
        assertEquals(null, pose.of(left, "tip"), "an end joint owns none of the arm")
        assertEquals(null, pose.of(right, "base"), "an arm not asked for is left out")
    }

    @Test
    fun aRigWithNoArmsGivesAPoseWithNoBones() {
        assertTrue(tPoseOf(arms(45f, "base", "joint.1"), emptyList()).bones.isEmpty())
    }
}
