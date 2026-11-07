package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/**
 * One bone of a pose, the bone named by part and joint name: its turn in [degrees] about the joint's hinge, and how far the
 * joint sits from the one before it on the part, as a [stretch] factor. A template's `stretch` marks the joints a stretch
 * handle offers; the body curve's length stretches every joint along the spine and tail.
 */
@Serializable
data class BonePose(val part: PartId, val joint: String, val degrees: Float = 0f, val stretch: Float = 1f) {
    /** Whether it leaves its bone as it rests. */
    val isRest: Boolean get() = degrees == 0f && stretch == 1f
}

/**
 * A pose: the [bones] it sets, each named for itself, the paws it has [planted], by part, which stay where they stand
 * while the rest of the body moves, and the body [curve] its spine and tail were last bent to, if any. A bone it does not name
 * rests; the curve moves nothing by itself, the bones it was solved into do. See HLD: data-model, Entities.
 */
@Serializable
data class Pose(val bones: List<BonePose> = emptyList(), val planted: Set<PartId> = emptySet(), val curve: BodyCurve? = null) {
    init {
        val named = bones.map { it.part to it.joint }
        require(named.size == named.toSet().size) { "A pose sets a bone twice: $named" }
    }

    /** Whether every bone rests, so the body stands as built. The planted set moves nothing by itself. */
    val isRest: Boolean get() = bones.all { it.isRest }

    /** What this pose sets for the bone at [joint] on [part], or null when it leaves it at rest. */
    fun of(part: PartId, joint: String): BonePose? = bones.firstOrNull { it.part == part && it.joint == joint }

    /** This pose with [bone] set, in place of what it set for that bone before, if anything. */
    fun with(bone: BonePose): Pose {
        val at = bones.indexOfFirst { it.part == bone.part && it.joint == bone.joint }
        return copy(bones = if (at < 0) bones + bone else bones.toMutableList().also { it[at] = bone })
    }

    /** This pose leaving the bone at [joint] on [part] at rest. */
    fun without(part: PartId, joint: String): Pose = copy(bones = bones.filterNot { it.part == part && it.joint == joint })

    /** This pose with [part] planted, or free when [planted] is false. */
    fun withPlanted(part: PartId, planted: Boolean): Pose = copy(planted = if (planted) this.planted + part else this.planted - part)
}
