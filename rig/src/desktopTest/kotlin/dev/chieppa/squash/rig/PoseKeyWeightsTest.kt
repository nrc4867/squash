package dev.chieppa.squash.rig

import dev.chieppa.squash.character.keys.KeyShape
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.PoseKey
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How much of each pose key a pose takes: none at the rest, all at the key, smoothly between, by side, and shapes blended by it. */
class PoseKeyWeightsTest {
    private val left = PartId("arm-l")
    private val right = PartId("arm-r")
    private val sides = mapOf(left to Side.LEFT, right to Side.RIGHT)
    private val twins = mapOf(left to right, right to left)

    private fun raised(lean: Float, arms: List<PartId> = listOf(left, right)) = Pose(arms.map { BonePose(it, "base", lean = lean, mode = JointMode.FREE) })

    private val tKey = PoseKey("t", raised(45f).bones)

    private fun weights(pose: Pose, keys: List<PoseKey> = listOf(tKey)) = keyWeights(keys, pose, sides::get, twins::get)

    @Test
    fun theRestTakesNothingAndTheKeyTakesAll() {
        assertEquals(KeyWeight(0f, 0f), weights(Pose()).single())
        val atKey = weights(raised(45f)).single()
        assertEquals(1f, atKey.left, 1e-5f)
        assertEquals(1f, atKey.right, 1e-5f)
    }

    @Test
    fun aSweepFromTheRestToTheKeyRisesSmoothlyWithNoJump() {
        val sweep = (0..45).map { weights(raised(it.toFloat())).single().left }
        assertTrue(sweep.zipWithNext().all { (a, b) -> b >= a }, "the weight only rises toward the key: $sweep")
        val steps = sweep.zipWithNext { a, b -> b - a }
        assertTrue(steps.all { it < 0.05f }, "no degree of the sweep jumps: ${steps.max()}")
        val halfway = sweep[22]
        assertTrue(halfway in 0.3f..0.7f, "halfway takes about half: $halfway")
    }

    @Test
    fun eachSideFollowsItsOwnArm() {
        val one = weights(raised(45f, listOf(left))).single()
        assertEquals(1f, one.left, 1e-5f)
        assertEquals(0f, one.right, 1e-5f)
        val oneSided = PoseKey("left only", raised(45f, listOf(left)).bones)
        val mirrored = weights(raised(45f, listOf(right)), listOf(oneSided)).single()
        assertEquals(1f, mirrored.right, 1e-5f, "a key naming one side drives the other through its twin")
    }

    @Test
    fun aPoseLoweredPastTheRestTakesNothing() {
        assertEquals(KeyWeight(0f, 0f), weights(raised(-20f)).single())
    }

    @Test
    fun twoKeysOnOneJointEachTakeAllAtTheirOwnAngles() {
        val half = PoseKey("half", raised(20f).bones)
        val both = listOf(half, tKey)
        val atHalf = weights(raised(20f), both)
        assertEquals(1f, atHalf[0].left, 1e-4f)
        assertEquals(0f, atHalf[1].left, 1e-4f)
        val atT = weights(raised(45f), both)
        assertEquals(0f, atT[0].left, 1e-4f)
        assertEquals(1f, atT[1].left, 1e-4f)
    }

    @Test
    fun aShapeIsTakenInFullAtTheKeyAndNotAtAllAtTheRest() {
        val character = Character(
            CharacterId("pup"), "Pup", BodyPlanRef(BodyPlanId("blank"), 1),
            listOf(left, right).map { PartInstance(it, TemplateRef(TemplateId("arm"), 1), side = sides.getValue(it)) },
            poseKeys = listOf(tKey),
        )
        val shape = KeyShape(intArrayOf(3, 7), floatArrayOf(0.01f, 0f, 0f, 0f, 0.02f, 0f), floatArrayOf(0f, 0.1f, 0f, 0f, 0f, 0f))
        val held = HeldKeys(null, character, character.poseKeys, listOf(shape))
        assertNull(held.correction(Pose()), "the rest takes no correction")
        val full = requireNotNull(held.correction(raised(45f)))
        assertEquals(listOf(3, 7), full.moved.toList(), "the vertices keep their order")
        assertTrue(full.offsets.indices.all { abs(full.offsets[it] - shape.offsets[it]) < 1e-5f }, "the key's own shape at the key")
        val partWay = requireNotNull(held.correction(raised(22f)))
        assertTrue(partWay.offsets[0] > 0f && partWay.offsets[0] < shape.offsets[0], "part way there, part of the shape")
    }
}
