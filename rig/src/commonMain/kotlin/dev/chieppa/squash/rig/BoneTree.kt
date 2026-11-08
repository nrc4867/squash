package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.JointTree
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.character.model.JointSpec
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.Vec3

/** Below this a bone has no length, and its direction is the axis's. */
private const val MIN_BONE_LENGTH = 1e-5f

/** A part of the document as the rig reads it: where it stands, its joints and its rings. */
internal class RiggedPart(val placed: PlacedPart) {
    val id: PartId = requireNotNull(placed.id)
    val joints = JointTree(placed.template, placed.surface.patches.map { it.name })
    val rings = PartRings(placed)

    fun bone(index: Int) = BoneName(id, joints.specs[index].name)
}

/**
 * Which way a digit joint's hinge turns from the part's own, so that a positive tip turns the digit toward the row's underside.
 * A digit turned back takes it the other way, so the same tip closes it toward the underside too.
 */
private const val DIGIT_CURL = 1f

/**
 * The bones of [character]'s parts, parents before children. Every template joint is a bone; a part's first bone hangs from
 * the bone of its parent that carries its socket, and each later one from the one before it; a digit's first bone hangs from the
 * part's bone owning the digit's root. Planned parts, which a plan's
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
    val joints = part.joints
    val axes = PartBuilder.axes(part.placed.template)
    val mirrored = part.placed.mirrored
    val chain = joints.chain.mapIndexed { index, joint ->
        val pivot = part.rings.middle(joint.u)
        val owns = index < joints.owning
        val end = if (owns) part.rings.middle(joints.spanEnd(index)) else pivot
        boneOf(part.bone(index), if (index == 0) carrier else part.bone(index - 1), pivot, end, { part.rings.tangent(joint.u) }, hingeOf(joint.hinge, axes, mirrored), joint, mirrored, null)
    }
    val digits = joints.digits.flatMapIndexed { d, digit ->
        val curl = if (digit.back) -DIGIT_CURL else DIGIT_CURL
        digit.joints.mapIndexed { k, joint ->
            val pivot = part.rings.middle(joint.u, digit.patch)
            val end = part.rings.middle(joints.digitSpanEnd(d, k), digit.patch)
            val parent = part.bone(if (k == 0) joints.rootOf(d) else joints.indexOf(d, k - 1))
            boneOf(part.bone(joints.indexOf(d, k)), parent, pivot, end, { part.rings.tangent(joint.u, digit.patch) }, hingeOf(joint.hinge, axes, mirrored) * curl, joint, mirrored, DigitPlace(digit.patch, digit.index, k + 1, digit.back))
        }
    }
    return chain + digits
}

private fun boneOf(
    name: BoneName,
    parent: BoneName?,
    pivot: Vec3,
    end: Vec3,
    tangent: () -> Vec3,
    hinge: Vec3,
    joint: JointSpec,
    mirrored: Boolean,
    digit: DigitPlace?,
): Bone {
    val reach = end - pivot
    val length = reach.length
    return Bone(
        name = name,
        parent = parent,
        pivot = pivot,
        direction = if (length > MIN_BONE_LENGTH) reach.normalized() else tangent(),
        length = if (length > MIN_BONE_LENGTH) length else 0f,
        hinge = hinge,
        limits = joint.limits,
        stretch = joint.stretch,
        mirrored = mirrored,
        digit = digit,
    )
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
