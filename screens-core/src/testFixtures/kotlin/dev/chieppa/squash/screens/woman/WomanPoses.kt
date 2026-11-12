package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose

/** Her hand-made bones by name: a tube's own bone at its root, the arm's shoulder and elbow, the leg's knee, and the round head's neck. */
private const val TUBE_BASE = "base"
private const val ARM_SHOULDER = "joint.1"
private const val ARM_ELBOW = "joint.2"
private const val KNEE = "joint.1"
private const val NECK_TURN = "neck"

/**
 * How far her display pose brings each arm down from the A-pose she is built in, leaning it in across its shoulder's hinge, so it hangs
 * just clear of her hips. The blended hand already rests with its palm toward her middle.
 */
private const val ARMS_DOWN = -28f

/** Her relaxed finger curl in degrees, joints 1 to 3 of each finger, index to little finger, toward the palm, and the thumb's. */
private val FINGERS_RELAXED = listOf(listOf(10f, 14f, 9f), listOf(13f, 17f, 11f), listOf(16f, 19f, 12f), listOf(20f, 21f, 13f))
private val THUMB_RELAXED = listOf(10f, 12f, 9f)

/** The sign that turns a finger's joints toward the palm on the blended hand, and the thumb's. */
private const val CURL = -1f
private const val THUMB_CURL = 1f
private const val THUMB = 4

/** [hand]'s fingers and thumb in their relaxed curl. */
private fun relaxed(hand: PartId) = FINGERS_RELAXED.flatMapIndexed { finger, joints ->
    joints.mapIndexed { k, degrees -> BonePose(hand, "fingers:$finger.${k + 1}", degrees * CURL) }
} + THUMB_RELAXED.mapIndexed { k, degrees -> BonePose(hand, "fingers:$THUMB.${k + 1}", degrees * THUMB_CURL) }

/** The pose she is shown in: her arms brought down to her sides, which the body keeps on its surface, her fingers relaxed. */
val WOMAN_DISPLAY = Pose(listOf(BonePose(ARM_L, TUBE_BASE, lean = ARMS_DOWN), BonePose(ARM_R, TUBE_BASE, lean = ARMS_DOWN)) + relaxed(HAND_L) + relaxed(HAND_R))

/** A step forward on the left leg, the arms swinging against it. */
val WOMAN_WALK = Pose(listOf(
    BonePose(ARM_L, ARM_SHOULDER, 35f), BonePose(ARM_R, ARM_SHOULDER, -30f), BonePose(ARM_R, ARM_ELBOW, -40f),
    BonePose(LEG_L, KNEE, 45f), BonePose(LEG_L, TUBE_BASE, 25f), BonePose(LEG_R, TUBE_BASE, -12f), BonePose(HEAD, NECK_TURN, 8f),
))

/** The left hand raised to the brow, the right arm bent across the waist. */
val WOMAN_SALUTE = Pose(listOf(
    BonePose(ARM_L, ARM_SHOULDER, -95f), BonePose(ARM_L, ARM_ELBOW, -85f), BonePose(ARM_R, ARM_ELBOW, -95f),
    BonePose(HEAD, NECK_TURN, -12f),
))

/** The poses she is looked at in, by name. */
val WOMAN_POSES: Map<String, Pose> = mapOf("walk" to WOMAN_WALK, "salute" to WOMAN_SALUTE)
