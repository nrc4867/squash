package dev.chieppa.squash.character.mouth.shaped

import dev.chieppa.squash.character.build.JointTree
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.lipLineOf
import dev.chieppa.squash.character.model.MouthLine
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SurfaceMethod
import dev.chieppa.squash.character.mouth.MouthDentition
import dev.chieppa.squash.character.mouth.dentitionOf
import dev.chieppa.squash.character.mouth.mouthStyleOf
import dev.chieppa.squash.character.surface.JAW_JOINT
import dev.chieppa.squash.character.surface.lipNormals
import dev.chieppa.squash.character.surface.lipPlaces
import dev.chieppa.squash.character.surface.openJawCut
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.TeethLevel
import dev.chieppa.squash.document.TongueLevel
import dev.chieppa.squash.document.TongueTip
import dev.chieppa.squash.document.isMouth
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.PartMesh

/** The run a head's jaw is cut from, which a head built open needs. */
private const val MUZZLE = "muzzle"

/** The fewest places of a drawn lip line that make a mouth line. */
private const val LEAST_LIP_PLACES = 2

/**
 * What a part's shaped mouth parts are built from: for a head ([role] head) its lip line [lipLine] and the teeth [dentition] its mouth
 * shows, and for it or a beak's lower half ([role] jaw) how its shaped tongue ends, [tongue], null for none.
 */
data class MouthPartsInput(val role: Role, val lipLine: List<Anchor>, val dentition: MouthDentition?, val tongue: TongueTip?)

/**
 * Whether [instance] of [character], on [template], is a head built with its jaw open, as the document says: a head, not on the right
 * side, of a template in the field with a muzzle run and a jaw joint off its axis that opens, holding a drawn lip line. See HLD: surface,
 * Jaws built open.
 */
fun buildsJawOpen(character: Character, instance: PartInstance, template: PartTemplate?): Boolean {
    template ?: return false
    if ((instance.role ?: template.role) != Role.HEAD || instance.side == Side.RIGHT || template.surface != SurfaceMethod.FIELD) return false
    if (template.blocks.none { it.name == MUZZLE && it.surfaceMethod() == SurfaceMethod.FIELD }) return false
    if (template.blocks.flatMap { it.joints() }.none { it.name == JAW_JOINT && it.v != null && (it.limits?.max ?: 0f) > 0f }) return false
    return lipLineOf(character.resolved(instance).surface).size >= LEAST_LIP_PLACES
}

/**
 * What [part] of [character] builds as shaped mouth parts, through [templates]: a head built open whose mouth has a jaw at shaped or a
 * shaped tongue, or a beak's lower half under a mouth on a seam with a shaped tongue, each only where its role has a fill to show in; null
 * for every other part.
 */
fun mouthPartsInputOf(character: Character, part: PartId, templates: TemplateLibrary): MouthPartsInput? {
    val instance = character.part(part) ?: return null
    val template = templates.find(instance.template) ?: return null
    return when (instance.role ?: template.role) {
        Role.HEAD -> {
            if (!buildsJawOpen(character, instance, template)) return null
            val group = character.resolved(instance).surface.groups.firstOrNull { it.isMouth } ?: return null
            val dentition = templates.dentitionOf(group, templates.mouthStyleOf(instance, group), builtOpen = true)
            val shaped = dentition.takeIf { d -> group.fills(MarkRole.TEETH) && (d.upper.level == TeethLevel.SHAPED || d.lower.level == TeethLevel.SHAPED) }
            val tongue = group.tongueTip.takeIf { group.tongueLevel == TongueLevel.SHAPED && group.fills(MarkRole.TONGUE) }
            if (shaped == null && tongue == null) null else MouthPartsInput(Role.HEAD, lipLineOf(character.resolved(instance).surface), shaped, tongue)
        }
        Role.JAW -> {
            if (template.blocks.flatMap { it.joints() }.none { it.name == JAW_JOINT }) return null
            val group = beakMouthOf(character, instance, templates) ?: return null
            if (group.tongueLevel != TongueLevel.SHAPED || !group.fills(MarkRole.TONGUE)) return null
            MouthPartsInput(Role.JAW, emptyList(), null, group.tongueTip)
        }
        else -> null
    }
}

/** Whether this mouth's [role] has a fill. */
private fun MarkGroup.fills(role: MarkRole): Boolean = roles.firstOrNull { it.role == role }?.fill != null

/** The mouth on a seam a beak's lower half [instance] shows: that of the nearest part it hangs from holding a mouth whose line is a seam. */
private fun beakMouthOf(character: Character, instance: PartInstance, templates: TemplateLibrary): MarkGroup? {
    if (character.resolved(instance).surface.groups.any { it.isMouth }) return null
    val holder = holderOf(character, instance) ?: return null
    val group = character.resolved(holder).surface.groups.first { it.isMouth }
    return group.takeIf { templates.mouthStyleOf(holder, it).line == MouthLine.SEAM }
}

/**
 * The palette slots [part]'s shaped mouth parts show in, by patch row: its teeth in the mouth's teeth role's fill and its tongue in the
 * tongue role's, each left out where the role has none.
 */
fun mouthPartSlotsOf(character: Character, part: PartId, templates: TemplateLibrary): Map<String, SlotName> {
    val input = mouthPartsInputOf(character, part, templates) ?: return emptyMap()
    val instance = character.part(part) ?: return emptyMap()
    val group = if (input.role == Role.HEAD) character.resolved(instance).surface.groups.firstOrNull { it.isMouth } else beakMouthOf(character, instance, templates)
    group ?: return emptyMap()
    fun fill(role: MarkRole) = group.roles.firstOrNull { it.role == role }?.fill
    return buildMap {
        if (input.dentition != null) fill(MarkRole.TEETH)?.let { put(TEETH_PATCH, it) }
        if (input.tongue != null) fill(MarkRole.TONGUE)?.let { put(TONGUE_PATCH, it) }
    }
}

/**
 * This mesh of a part of [template] with the shaped mouth parts [input] asks for: on a head built open, each shaped tooth and its tongue
 * laid along its lip line; on a beak's lower half, its tongue. Each is a patch of its own, past every run of the field, tagged with the
 * bones it rides. Unchanged where nothing is built.
 */
fun PartMesh.withMouthParts(template: PartTemplate, input: MouthPartsInput?, mirrored: Boolean): PartMesh {
    input ?: return this
    val own = patches.map { it.patch }
    val extra = when (input.role) {
        Role.HEAD -> {
            val cut = openJawCut(template, input.role, mirrored, own, input.lipLine) ?: return this
            val path = LipPath.of(cut, lipPlaces(own, input.lipLine), lipNormals(own, input.lipLine)) ?: return this
            val room = HeadRoom.of(own.filter { it.slices != null })
            input.dentition?.let { shapedTeeth(it, path, room) }.orEmpty() + listOfNotNull(input.tongue?.let { shapedTongue(path, it) })
        }
        Role.JAW -> {
            val run = own.firstOrNull { it.slices != null } ?: return this
            val axes = axes ?: PartBuilder.axes(template)
            listOfNotNull(input.tongue?.let { beakTongue(run, axes.up, axes.side, it) })
        }
        else -> emptyList()
    }
    if (extra.isEmpty()) return this
    val tree = JointTree(template, own.map { it.name } + extra.map { it.name })
    return copy(patches = patches + extra.map { patch -> MeshBuilder.build(patch) { u -> tree.boneAt(patch.name, u) } })
}

/**
 * How far each part of [character] carrying a shaped tongue has it out, by part: the tongue control of the mouth it belongs to, as its
 * style scales and holds it, 0 at rest, 0.5 lifted to the upper teeth, 1 out past the lower lip.
 */
fun tongueControlsOf(character: Character, templates: TemplateLibrary): Map<PartId, Float> = character.parts.mapNotNull { instance ->
    val input = mouthPartsInputOf(character, instance.id, templates) ?: return@mapNotNull null
    if (input.tongue == null) return@mapNotNull null
    val holder = if (input.role == Role.HEAD) instance else holderOf(character, instance) ?: return@mapNotNull null
    val group = character.resolved(holder).surface.groups.firstOrNull { it.isMouth } ?: return@mapNotNull null
    val style = templates.mouthStyleOf(holder, group)
    instance.id to style.controlsOf(group.controls).tongue
}.toMap()

/** The nearest part [instance] hangs from that holds a mouth. */
private fun holderOf(character: Character, instance: PartInstance): PartInstance? =
    generateSequence(instance.socket?.parent?.let(character::part)) { it.socket?.parent?.let(character::part) }
        .firstOrNull { part -> character.resolved(part).surface.groups.any { it.isMouth } }
