package dev.chieppa.squash.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How a pose holds a bone to its joint's limits. */
@Serializable
enum class JointMode {
    /** Turns past the template's limits. */
    @SerialName("free") FREE,

    /** Held within the template's limits. */
    @SerialName("limited") LIMITED,

    /** Held within its limits and left out of every solve, so only its own controls move it. */
    @SerialName("locked") LOCKED,
}

/** A point in body space, which a pose keeps where it names a place rather than a turn. */
@Serializable
data class BodyPoint(val x: Float, val y: Float, val z: Float) {
    companion object {
        val ZERO = BodyPoint(0f, 0f, 0f)
    }
}

/**
 * One bone of a pose, the bone named by part and joint name: its turn in [degrees] about the joint's hinge, its [turn] about its
 * own length and its [lean] across the hinge, how far the joint sits from the one before it on the part as a [stretch] factor,
 * whether that stretch [keepVolume] by narrowing, and how its limits hold it. A template's `stretch` marks the joints a stretch
 * handle offers; the body curve's length stretches every joint along the spine and tail.
 */
@Serializable
data class BonePose(
    val part: PartId,
    val joint: String,
    val degrees: Float = 0f,
    val stretch: Float = 1f,
    val turn: Float = 0f,
    val lean: Float = 0f,
    val mode: JointMode = JointMode.LIMITED,
    val keepVolume: Boolean = true,
) {
    /** Whether it leaves its bone as it rests. A mode or a volume rule moves nothing by itself. */
    val isRest: Boolean get() = degrees == 0f && stretch == 1f && turn == 0f && lean == 0f

    /** Whether it says nothing a bone left out of the pose would not: at rest, limited, keeping volume. */
    val isDefault: Boolean get() = isRest && mode == JointMode.LIMITED && keepVolume
}

/**
 * A pose: the [bones] it sets, each named for itself, the paws it has [planted], by part, which stay where they stand while the
 * rest of the body moves, the lifted paws that keep their place too rather than ride with the body, [still], how far the whole
 * body has moved from where it stands, [offset], the point the head looks at, [lookAt], or null while it looks ahead, and the body
 * [curve] its spine and tail were last bent to, if any. A bone it does not name rests; the curve moves nothing by itself, the bones
 * it was solved into do. See HLD: data-model, Entities.
 */
@Serializable
data class Pose(
    val bones: List<BonePose> = emptyList(),
    val planted: Set<PartId> = emptySet(),
    val curve: BodyCurve? = null,
    val offset: BodyPoint = BodyPoint.ZERO,
    val lookAt: BodyPoint? = null,
    val still: Set<PartId> = emptySet(),
) {
    init {
        val named = bones.map { it.part to it.joint }
        require(named.size == named.toSet().size) { "A pose sets a bone twice: $named" }
    }

    /** Whether every bone rests and the body stands where it was built. The planted set and the look-at point move nothing by themselves. */
    val isRest: Boolean get() = offset == BodyPoint.ZERO && bones.all { it.isRest }

    /** What this pose sets for the bone at [joint] on [part], or null when it leaves it at rest. */
    fun of(part: PartId, joint: String): BonePose? = bones.firstOrNull { it.part == part && it.joint == joint }

    /** This pose with [bone] set, in place of what it set for that bone before, if anything; a bone set back to its default drops out. */
    fun with(bone: BonePose): Pose {
        val at = bones.indexOfFirst { it.part == bone.part && it.joint == bone.joint }
        val kept = if (bone.isDefault) null else bone
        val next = bones.toMutableList()
        when {
            at < 0 && kept != null -> next += kept
            at >= 0 && kept != null -> next[at] = kept
            at >= 0 -> next.removeAt(at)
        }
        return copy(bones = next)
    }

    /** This pose leaving the bone at [joint] on [part] at rest, keeping how its limits hold it. */
    fun without(part: PartId, joint: String): Pose {
        val held = of(part, joint) ?: return this
        return with(BonePose(part, joint, mode = held.mode, keepVolume = held.keepVolume))
    }

    /** This pose with [part] planted, or free when [planted] is false. */
    fun withPlanted(part: PartId, planted: Boolean): Pose = copy(planted = if (planted) this.planted + part else this.planted - part)

    /** This pose with the lifted paw [part] riding with the body, or keeping its place when [rides] is false. */
    fun withRiding(part: PartId, rides: Boolean): Pose = copy(still = if (rides) still - part else still + part)
}
