package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose

/** Her bones by name: a tube's own bone at its root, the arm's shoulder and elbow, the leg's knee, and the round head's neck. */
private const val TUBE_BASE = "base"
private const val ARM_SHOULDER = "joint.1"
private const val ARM_ELBOW = "joint.2"
private const val KNEE = "joint.1"
private const val NECK_TURN = "neck"

/** Her torso's hand-made joints at the ribs and the chest. */
private const val RIBS = "joint.2"
private const val CHEST = "joint.3"

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

/** How far the head turns to look over a shoulder, tilts toward one, and the chest bends forward, in degrees. */
private const val HEAD_TURN = 60f
private const val HEAD_TILT = 25f
private const val CHEST_BEND = 20f

/** The left arm raised overhead from the shoulder. */
private const val ARM_UP = -150f

/** The head turned to look over her left shoulder, and over her right. */
val WOMAN_LOOK_LEFT = Pose(WOMAN_DISPLAY.bones + BonePose(HEAD, NECK_TURN, turn = HEAD_TURN, mode = JointMode.FREE))
val WOMAN_LOOK_RIGHT = Pose(WOMAN_DISPLAY.bones + BonePose(HEAD, NECK_TURN, turn = -HEAD_TURN, mode = JointMode.FREE))

/** The head tilted toward her left shoulder. */
val WOMAN_TILT = Pose(WOMAN_DISPLAY.bones + BonePose(HEAD, NECK_TURN, lean = HEAD_TILT, mode = JointMode.FREE))

/** Bent forward at the chest and ribs, and the left arm raised overhead. */
val WOMAN_BEND = Pose(WOMAN_DISPLAY.bones + BonePose(TORSO, RIBS, CHEST_BEND, mode = JointMode.FREE) + BonePose(TORSO, CHEST, CHEST_BEND, mode = JointMode.FREE))
val WOMAN_ARM_UP = Pose(listOf(BonePose(ARM_L, ARM_SHOULDER, ARM_UP, mode = JointMode.FREE)))

/** The poses she is looked at in, by name. */
val WOMAN_POSES: Map<String, Pose> = mapOf(
    "walk" to WOMAN_WALK, "salute" to WOMAN_SALUTE, "look-left" to WOMAN_LOOK_LEFT, "look-right" to WOMAN_LOOK_RIGHT, "tilt" to WOMAN_TILT,
    "bend" to WOMAN_BEND, "arm-up" to WOMAN_ARM_UP,
)
