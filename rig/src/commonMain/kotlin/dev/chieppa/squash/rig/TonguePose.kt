package dev.chieppa.squash.rig

import dev.chieppa.squash.character.mouth.shaped.TONGUE_JOINTS
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose

/** Where along the tongue control the tongue is lifted highest, before it starts out past the lip. */
private const val LIFTED = 0.5f

/** Degrees each tongue joint turns up, from the root, with the tongue lifted to the upper teeth. */
private val LIFT_DEGREES = listOf(-14f, -28f, -34f)

/** Degrees each tongue joint turns with the tongue out past the lower lip: the root up over the lower teeth, the tip down over the lip. */
private val OUT_DEGREES = listOf(-10f, 12f, 30f)

/** How long each span of the tongue grows with it all the way out, a factor of its rest length; the root joint's span is the jaw's own. */
private val OUT_STRETCH = listOf(1f, 1.45f, 1.45f)

/**
 * This pose with the shaped tongue of each part in [tongues] bent as its control asks: lifted toward the upper teeth up to [LIFTED], then
 * reaching out past the lower lip and drooping over it by 1. A joint the pose sets itself keeps what it sets. See HLD: look, Tongue.
 */
fun Pose.withTongues(tongues: Map<PartId, Float>): Pose = tongues.entries.fold(this) { pose, (part, control) ->
    val c = control.coerceIn(0f, 1f)
    val lift = if (c <= LIFTED) c / LIFTED else 1f - (c - LIFTED) / (1f - LIFTED)
    val out = ((c - LIFTED) / (1f - LIFTED)).coerceIn(0f, 1f)
    TONGUE_JOINTS.foldIndexed(pose) { k, held, joint ->
        if (held.of(part, joint) != null) return@foldIndexed held
        val degrees = LIFT_DEGREES[k] * lift + OUT_DEGREES[k] * out
        val stretch = 1f + (OUT_STRETCH[k] - 1f) * out
        if (degrees == 0f && stretch == 1f) held else held.with(BonePose(part, joint, degrees = degrees, stretch = stretch, keepVolume = false))
    }
}
