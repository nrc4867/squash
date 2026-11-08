package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.character.model.JointSpec
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.ExpressionSet
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.isMouth
import dev.chieppa.squash.geom.JointBend
import dev.chieppa.squash.geom.PartAxes

/** The joint a mouth's open drives on the part that holds it. */
const val JAW_JOINT = "jaw"

/** The expression [Character.shownExpression] names, or null when it names none the character holds. */
val Character.shownSet: ExpressionSet? get() = shownExpression?.let { id -> expressions.firstOrNull { it.id == id } }

/** A hinge letter as a direction of [axes]: x across the part, so a jaw or an ear pitches, y about its up, z about its own length. */
private fun Hinge.direction(axes: PartAxes) = when (this) {
    Hinge.X -> axes.side
    Hinge.Y -> axes.up
    Hinge.Z -> axes.along
}

/**
 * The degrees [character] holds each of [part]'s joints at: those its shown expression sets, and for a part holding a mouth its jaw, which the mouth's open runs up to
 * the joint's limit, on top. A part of role jaw holding no mouth, a beak's lower half, shares the jaw of the nearest part it hangs from that holds one: its own jaw
 * takes the degrees the shown expression sets that part's jaw and that mouth's open. Named by joint.
 */
fun shownJoints(character: Character, part: PartId, template: PartTemplate): Map<String, Float> {
    val instance = character.part(part) ?: return emptyMap()
    val joints = character.shownSet?.pose?.joints.orEmpty()
    val held = joints.filter { it.part == part }.associateTo(LinkedHashMap()) { it.joint to it.degrees }
    val holder = mouthHolder(character, instance, template) ?: return held
    if (holder.id != part) joints.firstOrNull { it.part == holder.id && it.joint == JAW_JOINT }?.let { held.getOrPut(JAW_JOINT) { it.degrees } }
    val open = character.resolved(holder).surface.groups.firstOrNull { it.isMouth }?.controls?.open ?: 0f
    if (open > 0f) {
        val jaw = template.blocks.flatMap { it.joints() }.firstOrNull { it.name == JAW_JOINT }
        if (jaw != null) held[JAW_JOINT] = (held[JAW_JOINT] ?: 0f) + open * (jaw.limits?.max ?: 0f)
    }
    return held
}

/** The part whose mouth opens [instance]'s jaw: itself when it holds a mouth, else for a part of role jaw the nearest part it hangs from that holds one. */
private fun mouthHolder(character: Character, instance: PartInstance, template: PartTemplate): PartInstance? {
    fun holds(part: PartInstance) = character.resolved(part).surface.groups.any { it.isMouth }
    if (holds(instance)) return instance
    if ((instance.role ?: template.role) != Role.JAW) return null
    return generateSequence(instance.socket?.parent?.let(character::part)) { it.socket?.parent?.let(character::part) }.firstOrNull(::holds)
}

/** What [joints] do to a mesh of [template] in [axes]: each named joint it has, held to its limits. */
fun bendsOf(joints: Map<String, Float>, template: PartTemplate, axes: PartAxes): List<JointBend> =
    template.blocks.flatMap { it.joints() }.mapNotNull { joint -> joints[joint.name]?.let { joint.bend(it, axes) } }

private fun JointSpec.bend(degrees: Float, axes: PartAxes): JointBend =
    JointBend(u, v, hinge.direction(axes), limits?.let { degrees.coerceIn(it.min, it.max) } ?: degrees)
