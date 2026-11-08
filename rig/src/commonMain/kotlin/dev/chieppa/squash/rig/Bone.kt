package dev.chieppa.squash.rig

import dev.chieppa.squash.character.model.Limits
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3

/** A bone, named by its part and the template joint it turns at. Never by index; see HLD: data-model, References are by name. */
data class BoneName(val part: PartId, val joint: String) {
    override fun toString(): String = "${part.value}/$joint"
}

/**
 * A bone of the rig in rest pose, in body space. It turns about [hinge] through [pivot], the middle of its part's ring at the
 * joint, and reaches [length] along [direction] to the next joint of its part, or to the part's end for the last bone that
 * owns any of it. A part's end joint owns none of its part and has no length; it carries what sits there. Its turn is held to
 * [limits] in degrees, and [stretch] marks it as one a stretch handle moves away from the joint before it on its part. A [mirrored]
 * bone, on the mirrored side of a pair, turns and leans the mirrored way, so one set of angles poses a pair as mirror images.
 */
data class Bone(
    val name: BoneName,
    val parent: BoneName?,
    val pivot: Vec3,
    val direction: Vec3,
    val length: Float,
    val hinge: Vec3,
    val limits: Limits?,
    val stretch: Boolean,
    val mirrored: Boolean = false,
    /** Where on a digit's chain this bone is, or null for a bone of its part's own chain. */
    val digit: DigitPlace? = null,
)

/**
 * A digit bone's place: the digit's [patch], its [index] in its toe row, the bone's [joint] number on the digit from 1 at its root,
 * and whether the digit is turned [back], as a bird's hind toe.
 */
data class DigitPlace(val patch: String, val index: Int, val joint: Int, val back: Boolean)
