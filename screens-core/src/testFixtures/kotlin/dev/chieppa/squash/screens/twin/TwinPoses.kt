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
 * The reference's rest: each upper arm 9 degrees out from straight down, from the 45 she is built at; the elbow bent forward 5.7 degrees,
 * and the forearm turned at the elbow, past its limits, so the palms face her thighs.
 */
private const val ARMS_DOWN = 9f - ARM_OUTWARD
private const val REST_ELBOW = -5.73f
private const val PALMS_IN = 70f

/** The elbow of [arm] bent [degrees] with the forearm turned so the palm faces in, as every pose that names an elbow keeps it. */
private fun elbow(arm: PartId, degrees: Float) = BonePose(arm, ARM_ELBOW, degrees, turn = PALMS_IN, mode = JointMode.FREE)

private val ARMS_AT_REST = listOf(BonePose(ARM_L, TUBE_BASE, lean = ARMS_DOWN), BonePose(ARM_R, TUBE_BASE, lean = ARMS_DOWN), elbow(ARM_L, REST_ELBOW), elbow(ARM_R, REST_ELBOW))

/** The pose she is shown in: the reference's arms-down rest. */
val TWIN_DISPLAY = Pose(ARMS_AT_REST)

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
))

/** How far the waving arm rises out to the side from where she is built, 114.6 degrees from hanging, and how far its forearm folds up. */
private const val WAVE_RAISE = 114.59f - ARM_OUTWARD + 25f
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
))

/** The shades tipped up onto the forehead: each half turned about its own length, which runs out from the bridge. */
private const val SHADES_TIP = 57.3f
val TWIN_SHADES_UP = Pose(listOf(BonePose(SHADES_L, TUBE_BASE, turn = SHADES_TIP, mode = JointMode.FREE), BonePose(SHADES_R, TUBE_BASE, turn = SHADES_TIP, mode = JointMode.FREE)))

/** The poses she is looked at in, by name, as the reference names them; the shades tipped up are a pose of the shades alone. */
val TWIN_POSES: Map<String, Pose> = mapOf("walk" to TWIN_WALK, "wave" to TWIN_WAVE, "shades-up" to TWIN_SHADES_UP)
