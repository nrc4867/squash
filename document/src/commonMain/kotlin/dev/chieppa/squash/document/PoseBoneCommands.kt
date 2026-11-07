package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Turns the bone at [joint] on [part] to [degrees] about its hinge, [turn] about its own length and [lean] across the hinge, keeping
 * its stretch; [bothSides] as [SetBoneAngle], the twin turning the mirrored way about each. See HLD: data-model, The rig.
 */
@Serializable
@SerialName("setBoneTurn")
data class SetBoneTurn(val part: PartId, val joint: String, val degrees: Float, val turn: Float, val lean: Float, val bothSides: Boolean = false) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.posed(part, bothSides) { pose, id ->
        pose.with((pose.of(id, joint) ?: BonePose(id, joint)).copy(degrees = degrees, turn = turn, lean = lean))
    }
}

/** Sets how the pose holds the bone at [joint] on [part] to its limits: free of them, within them, or locked out of every solve; [bothSides] as [SetBoneAngle]. */
@Serializable
@SerialName("setBoneMode")
data class SetBoneMode(val part: PartId, val joint: String, val mode: JointMode, val bothSides: Boolean = false) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        document.posed(part, bothSides) { pose, id -> pose.with((pose.of(id, joint) ?: BonePose(id, joint)).copy(mode = mode)) }
}

/** Sets whether the stretch of the joint [joint] on [part] narrows the span to keep its volume; [bothSides] as [SetBoneAngle]. */
@Serializable
@SerialName("setKeepVolume")
data class SetKeepVolume(val part: PartId, val joint: String, val keep: Boolean, val bothSides: Boolean = false) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        document.posed(part, bothSides) { pose, id -> pose.with((pose.of(id, joint) ?: BonePose(id, joint)).copy(keepVolume = keep)) }
}

/** Sets whether the lifted paw [part] rides with the body when it moves, or keeps its place. */
@Serializable
@SerialName("setRiding")
data class SetRiding(val part: PartId, val rides: Boolean) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        document.posed(part, bothSides = false) { pose, id -> pose.withRiding(id, rides) }
}
