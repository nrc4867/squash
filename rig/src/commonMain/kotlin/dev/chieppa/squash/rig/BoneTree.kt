package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.Vec3

/** Below this a bone has no length, and its direction is the axis's. */
private const val MIN_BONE_LENGTH = 1e-5f

/** A part of the document as the rig reads it: where it stands, its joints and its rings. */
internal class RiggedPart(val placed: PlacedPart) {
    val id: PartId = requireNotNull(placed.id)
    val joints = PartJoints(placed.template)
    val rings = PartRings(placed)

    fun bone(index: Int) = BoneName(id, joints.specs[index].name)
}

/**
 * The bones of [character]'s parts, parents before children. Every template joint is a bone; a part's first bone hangs from
 * the bone of its parent that carries its socket, and each later one from the one before it. Planned parts, which a plan's
 * empty socket shows but the document does not hold, have none. See HLD: data-model, Rig.
 */
internal fun boneTree(parts: List<RiggedPart>): List<Bone> {
    val byId = parts.associateBy { it.id }
    return parts.flatMap { part -> bonesOf(part, carrierOf(part, byId)) }
}

/** The bone [part] hangs from: its parent's bone carrying its socket, through any parent with no joints of its own. */
internal fun carrierOf(part: RiggedPart, byId: Map<PartId, RiggedPart>): BoneName? {
    val parent = part.placed.socket?.parent?.let(byId::get) ?: return null
    val socketU = part.placed.socketSpec?.u ?: return null
    if (parent.joints.isEmpty) return carrierOf(parent, byId)
    return parent.bone(parent.joints.carrierAt(socketU))
}

private fun bonesOf(part: RiggedPart, carrier: BoneName?): List<Bone> {
    val specs = part.joints.specs
    val axes = PartBuilder.axes(part.placed.template)
    return specs.mapIndexed { index, joint ->
        val pivot = part.rings.middle(joint.u)
        val owns = index < part.joints.owning
        val end = if (owns) part.rings.middle(part.joints.spanEnd(index)) else pivot
        val reach = end - pivot
        val length = reach.length
        Bone(
            name = part.bone(index),
            parent = if (index == 0) carrier else part.bone(index - 1),
            pivot = pivot,
            direction = if (length > MIN_BONE_LENGTH) reach.normalized() else part.rings.tangent(joint.u),
            length = if (length > MIN_BONE_LENGTH) length else 0f,
            hinge = hingeOf(joint.hinge, axes, part.placed.mirrored),
            limits = joint.limits,
            stretch = joint.stretch,
        )
    }
}

/**
 * A hinge letter as a body-space direction: x across the part, y about its up, z about its own length. A mirrored part turns
 * the other way about the mirrored direction, so one angle poses a pair as mirror images.
 */
private fun hingeOf(hinge: Hinge, axes: PartAxes, mirrored: Boolean): Vec3 {
    val own = when (hinge) {
        Hinge.X -> axes.side
        Hinge.Y -> axes.up
        Hinge.Z -> axes.along
    }
    return if (mirrored) own.mirrored * -1f else own
}
