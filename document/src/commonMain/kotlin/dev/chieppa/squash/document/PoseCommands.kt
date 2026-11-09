package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Turns the bone at [joint] on [part] to [degrees] about its hinge, keeping its stretch. With [bothSides] the twin of a paired
 * part takes the same angle, which its mirrored hinge turns the mirrored way. See HLD: data-model, The rig.
 */
@Serializable
@SerialName("setBoneAngle")
data class SetBoneAngle(val part: PartId, val joint: String, val degrees: Float, val bothSides: Boolean = false) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        document.posed(part, bothSides) { pose, id -> pose.with((pose.of(id, joint) ?: BonePose(id, joint)).copy(degrees = degrees)) }
}

/** Sets how far the joint [joint] on [part] sits from the one before it, as a [stretch] factor; [bothSides] as [SetBoneAngle]. */
@Serializable
@SerialName("setBoneStretch")
data class SetBoneStretch(val part: PartId, val joint: String, val stretch: Float, val bothSides: Boolean = false) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        document.posed(part, bothSides) { pose, id -> pose.with((pose.of(id, joint) ?: BonePose(id, joint)).copy(stretch = stretch)) }
}

/** Returns the bone at [joint] on [part] to rest, its angles and its stretch, keeping how its limits hold it; [bothSides] as [SetBoneAngle]. */
@Serializable
@SerialName("resetBone")
data class ResetBone(val part: PartId, val joint: String, val bothSides: Boolean = false) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        document.posed(part, bothSides) { pose, id -> pose.without(id, joint) }
}

/** Plants the paw [part] where it stands, or frees it when [planted] is false. */
@Serializable
@SerialName("setPlanted")
data class SetPlanted(val part: PartId, val planted: Boolean) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        document.posed(part, bothSides = false) { pose, id -> pose.withPlanted(id, planted) }
}

/** Returns every bone to rest, the body to where it stands, the head to looking ahead, and frees every paw. */
@Serializable
@SerialName("resetPose")
data object ResetPose : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = SetPose(Pose()).apply(document)
}

/** Makes [pose] the character's pose whole: the undo of every pose command. */
@Serializable
@SerialName("setPose")
data class SetPose(val pose: Pose) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        Applied(document.copy(character = document.character.copy(pose = pose)), SetPose(document.character.pose))
}

/** This document with [change] made to its pose for [part], and with [bothSides] for its twin too; a garment's loose end has no twin. */
internal fun SquashDocument.posed(part: PartId, bothSides: Boolean, change: (Pose, PartId) -> Pose): Applied<SquashDocument> {
    if (character.isLooseEnd(part)) return SetPose(change(character.pose, part)).apply(this)
    val instance = requirePart(part)
    val twin = if (bothSides) character.twinOf(instance) else null
    val ids = listOfNotNull(instance.id, twin?.id)
    return SetPose(ids.fold(character.pose, change)).apply(this)
}
