package dev.chieppa.squash.screens.twin

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose

/** Her bones by name: a tube's own bone at its root, the arm's shoulder and elbow, the leg's knee, the foot's ankle and the head's neck. */
private const val TUBE_BASE = "base"
private const val ARM_SHOULDER = "joint.1"
private const val ARM_ELBOW = "joint.2"
private const val KNEE = "joint.1"
private const val ANKLE = "ankle"
private const val NECK_TURN = "neck"

/**
 * The reference's rest: each upper arm 9 degrees out from straight down, from the 45 she is built at, and the elbow bent forward 5.7
 * degrees.
 */
private const val ARMS_DOWN = 9f - ARM_OUTWARD
private const val REST_ELBOW = -5.73f

/**
 * How far every pose leans the forearm in toward her side across the elbow's hinge, past its limits: the reference's forearm hangs 4.3
 * degrees nearer straight down than its upper arm. A lean bends the forearm without twisting it, so the hand faces her thigh as built.
 */
private const val FOREARM_IN = -4.3f

/** The elbow of [arm] bent [degrees] forward and leaned in, as every pose that names an elbow keeps it. */
private fun elbow(arm: PartId, degrees: Float) = BonePose(arm, ARM_ELBOW, degrees, lean = FOREARM_IN, mode = JointMode.FREE)

/** The sign that turns a finger's joints toward the palm, and the thumb's, on the hand as built. */
private const val CURL = -1f
private const val THUMB_CURL = 1f
private const val THUMB = 4

/** The reference's finger presets in degrees, joints 1 to 3, index to pinky then the thumb: relaxed and open. */
private val RELAXED = listOf(
    listOf(10.3f, 14.3f, 9.2f), listOf(13.8f, 18.3f, 11.5f), listOf(17.2f, 20.6f, 12.6f), listOf(21.8f, 22.9f, 14.3f), listOf(12.6f, 14.3f, 11.5f),
)
private val SPREAD = listOf(
    listOf(2.3f, 3.4f, 2.3f), listOf(1.1f, 2.9f, 2.3f), listOf(2.9f, 4.6f, 2.9f), listOf(5.7f, 5.7f, 3.4f), listOf(-8.6f, 2.9f, 2.9f),
)

/** [hand]'s fingers and thumb curled by [preset]. */
private fun fingers(hand: PartId, preset: List<List<Float>>) = preset.flatMapIndexed { finger, joints ->
    joints.mapIndexed { k, degrees -> BonePose(hand, "fingers:$finger.${k + 1}", degrees * if (finger == THUMB) THUMB_CURL else CURL) }
}

private val ARMS_AT_REST = listOf(BonePose(ARM_L, TUBE_BASE, lean = ARMS_DOWN), BonePose(ARM_R, TUBE_BASE, lean = ARMS_DOWN), elbow(ARM_L, REST_ELBOW), elbow(ARM_R, REST_ELBOW))
private val HANDS_RELAXED = fingers(HAND_L, RELAXED) + fingers(HAND_R, RELAXED)

/** The pose she is shown in: the reference's arms-down rest, the fingers relaxed. */
val TWIN_DISPLAY = Pose(ARMS_AT_REST + HANDS_RELAXED)

/**
 * The reference's walk, mid-stride on the left leg: the left thigh forward 25.8 and its knee 8, the right thigh back 17.2 with its knee bent
 * 28.7 and the foot pointing; the arms swing against the legs, the left back 18.3 with the elbow 12.6, the right forward 21.8 with the elbow
 * 31.5; the head nods 2.3. The hips' and chest's turns about the vertical have no bone here.
 */
val TWIN_WALK = Pose(listOf(
    BonePose(LEG_L, TUBE_BASE, -25.78f), BonePose(LEG_L, KNEE, 8.02f), BonePose(FOOT_L, ANKLE, 6.88f),
    BonePose(LEG_R, TUBE_BASE, 17.19f), BonePose(LEG_R, KNEE, 28.65f), BonePose(FOOT_R, ANKLE, -18.33f),
    BonePose(ARM_L, ARM_SHOULDER, 18.33f), elbow(ARM_L, -12.61f),
    BonePose(ARM_R, ARM_SHOULDER, -21.77f), elbow(ARM_R, -31.51f),
    BonePose(HEAD, NECK_TURN, 2.29f),
) + HANDS_RELAXED)

/** How far the waving arm rises out to the side from where she is built, 114.6 degrees from hanging, and how far its forearm folds up. */
private const val WAVE_RAISE = 114.59f - ARM_OUTWARD + 22f
private const val WAVE_ELBOW = -60.16f
private const val WAVE_TWIST = -90f

/**
 * The reference's wave: the right arm raised out to the side above the shoulder, its forearm folded up toward vertical, turned about its
 * length so the elbow folds upward rather than forward; the left arm a little further out than at rest with the elbow at 8; the head
 * nodding 1.7.
 */
val TWIN_WAVE = Pose(listOf(
    BonePose(ARM_L, TUBE_BASE, lean = ARMS_DOWN + 3.44f), elbow(ARM_L, -8.02f),
    BonePose(ARM_R, TUBE_BASE, lean = WAVE_RAISE, mode = JointMode.FREE), BonePose(ARM_R, ARM_ELBOW, WAVE_ELBOW, turn = WAVE_TWIST, mode = JointMode.FREE),
    BonePose(HEAD, NECK_TURN, 1.72f),
) + fingers(HAND_L, RELAXED) + fingers(HAND_R, SPREAD))

/** The shades tipped up onto the forehead: each half turned about its own length, which runs out from the bridge. */
private const val SHADES_TIP = 57.3f
val TWIN_SHADES_UP = Pose(listOf(BonePose(SHADES_L, TUBE_BASE, turn = SHADES_TIP, mode = JointMode.FREE), BonePose(SHADES_R, TUBE_BASE, turn = SHADES_TIP, mode = JointMode.FREE)))

/** The poses she is looked at in, by name, as the reference names them; the shades tipped up are a pose of the shades alone. */
val TWIN_POSES: Map<String, Pose> = mapOf("walk" to TWIN_WALK, "wave" to TWIN_WAVE, "shades-up" to TWIN_SHADES_UP)
