package dev.chieppa.squash.rig

import dev.chieppa.squash.character.model.Limits
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A walk's frames on two legs: each thigh swings forward and back by turns, the right half a stride behind, and each knee bends back. */
class WalkPoseTest {
    private val left = PartId("leg-l")
    private val right = PartId("leg-r")

    private fun leg(part: PartId, z: Float) = listOf(
        Bone(BoneName(part, "base"), null, Vec3(0f, 0.4f, z), Vec3(0f, -1f, 0f), 0.2f, Vec3(0f, 0f, 1f), null, false),
        Bone(BoneName(part, "joint.1"), BoneName(part, "base"), Vec3(0f, 0.2f, z), Vec3(0f, -1f, 0f), 0.2f, Vec3(0f, 0f, 1f), Limits(0f, 140f), false),
    )

    private val rig = Rig(leg(left, 0.05f) + leg(right, -0.05f), emptyList())
    private val legs = listOf(left to Side.LEFT, right to Side.RIGHT)

    /** Where [part]'s thigh points in the frame at [phase]: its tip's x, forward positive. */
    private fun thighForward(part: PartId, phase: Float): Float {
        val bone = rig.bones.first { it.name == BoneName(part, "base") }
        val pose = walkPoseOf(rig, legs, emptyList(), phase)
        return (localTurn(bone, pose.of(part, "base").angles()) * bone.direction).x
    }

    @Test
    fun eachThighSwingsForwardAndBackAndTheRightRunsHalfAStrideBehind() {
        assertTrue(thighForward(left, 0.25f) > 0.1f, "a quarter stride in, the left thigh is forward")
        assertTrue(thighForward(left, 0.75f) < -0.1f, "three quarters in, it is back")
        assertEquals(thighForward(left, 0.25f), thighForward(right, 0.75f), 1e-5f)
    }

    @Test
    fun eachKneeBendsTheWayItsLimitsLetIt() {
        val bends = (0 until 6).map { walkPoseOf(rig, legs, emptyList(), it / 6f).of(left, "joint.1")?.degrees ?: 0f }
        assertTrue(bends.all { it > 0f }, "every frame bends the knee within its limits: $bends")
        assertTrue(bends.max() > 30f, "it bends most as the leg swings through: $bends")
    }
}
