package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/**
 * One bone of a pose, the bone named by part and joint name: its turn in [degrees] about the joint's hinge, and how far the
 * joint sits from the one before it on the part, as a [stretch] factor, which only a joint whose template says `stretch` reads.
 */
@Serializable
data class BonePose(val part: PartId, val joint: String, val degrees: Float = 0f, val stretch: Float = 1f)

/** A pose: the bones it sets, each named for itself. A bone it does not name rests. See HLD: data-model, Entities. */
@Serializable
data class Pose(val bones: List<BonePose> = emptyList()) {
    init {
        val named = bones.map { it.part to it.joint }
        require(named.size == named.toSet().size) { "A pose sets a bone twice: $named" }
    }

    /** What this pose sets for the bone at [joint] on [part], or null when it leaves it at rest. */
    fun of(part: PartId, joint: String): BonePose? = bones.firstOrNull { it.part == part && it.joint == joint }
}
