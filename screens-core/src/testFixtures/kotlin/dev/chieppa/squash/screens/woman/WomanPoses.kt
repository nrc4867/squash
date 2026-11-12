package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.JointMode
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
 * just clear of her hips; and how far it rolls the forearm about its own length from the elbow, past the elbow's limits, so the palms
 * face her thighs. A pose that sets an elbow sets its twist too.
 */
private const val ARMS_DOWN = -28f
private const val PALMS_IN = 70f

/** The forearm of [arm] rolled from the elbow so its palm faces in. */
private fun palmIn(arm: PartId) = BonePose(arm, ARM_ELBOW, twist = PALMS_IN, mode = JointMode.FREE)

/** The pose she is shown in: her arms brought down to her sides, which the body keeps on its surface, palms in. */
val WOMAN_DISPLAY = Pose(listOf(BonePose(ARM_L, TUBE_BASE, lean = ARMS_DOWN), BonePose(ARM_R, TUBE_BASE, lean = ARMS_DOWN), palmIn(ARM_L), palmIn(ARM_R)))

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
