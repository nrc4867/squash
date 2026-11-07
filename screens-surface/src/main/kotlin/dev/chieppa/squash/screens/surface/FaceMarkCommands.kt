package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.character.build.shownSet
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.MarkVersion
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetExpressionMarks
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.SetMarkGroup
import dev.chieppa.squash.document.Together
import dev.chieppa.squash.document.expressionMarks
import dev.chieppa.squash.document.eyeGroup
import dev.chieppa.squash.document.GroupRole
import dev.chieppa.squash.document.SlotName
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import dev.chieppa.squash.document.isEye
import dev.chieppa.squash.document.mouthGroup
import dev.chieppa.squash.screens.lines.PickedGroup

/** The roles whose marks close on themselves. */
val CLOSED_ROLES = setOf(MarkRole.LID, MarkRole.WHITE, MarkRole.IRIS, MarkRole.PUPIL, MarkRole.HIGHLIGHT, MarkRole.INNER, MarkRole.TEETH, MarkRole.TONGUE)

private const val EYE_LEFT = "eye-l"
private const val EYE_RIGHT = "eye-r"
private const val MOUTH_ID = "mouth"

/** The group [picked] names on [character], as its part shows it. */
fun Character.groupOf(picked: PickedGroup): MarkGroup? =
    part(picked.part)?.let { resolved(it) }?.surface?.groups?.firstOrNull { it.id == picked.id }

/** The other eye of [group] on its part, if it has one. */
fun Character.twinOf(group: PickedGroup): MarkGroup? {
    val own = groupOf(group)?.takeIf { it.isEye } ?: return null
    return part(group.part)?.let { resolved(it) }?.surface?.groups?.firstOrNull { it.isEye && it.id != own.id }
}

/** The first mark [role] of [group] draws, in the shown expression where it names the role, else the group's own. */
fun Character.markIn(group: MarkGroup, role: MarkRole, part: PartId): Mark? {
    val instance = part(part) ?: return null
    shownSet?.let { set -> expressionMarks(set, instance, group.id)[role]?.firstOrNull()?.let { return it } }
    val id = group.roles.firstOrNull { it.role == role }?.marks?.firstOrNull() ?: return null
    return resolved(instance).surface.marks.firstOrNull { it.id == id }
}

/** The commands that make [points] what [role] of [group] draws in [bin]: the shown expression's mark if one shows, else the group's own, which a new mark joins. */
private fun Character.writing(part: PartId, group: MarkGroup, role: MarkRole, points: List<Anchor>, bin: CameraBin): List<DocumentCommand> {
    val shown = shownSet
    val held = markIn(group, role, part)
    val written = held?.let { it.copy(versions = it.versions + (bin to MarkVersion(points, it.versions[bin]?.width ?: it.width))) }
        ?: Mark(freeMarkId(this, "${group.id.value}-${role.name.lowercase()}"), points, ink = group.roles.firstOrNull { it.role == role }?.ink ?: true, versions = mapOf(bin to MarkVersion(points)))
    if (shown != null) {
        val own = shown.marks[group.id] != null && part(part)?.let { expressionMarks(shown, it, group.id)[role] } != null
        return listOf(SetExpressionMarks(shown.id, part, group.id, role, listOf(if (own) written else written.copy(id = freeMarkId(this, "${shown.id.value}-${group.id.value}-${role.name.lowercase()}")))))
    }
    val joined = if (group.roles.any { it.role == role && written.id in it.marks }) null else {
        group.copy(roles = group.roles.map { if (it.role == role) it.copy(marks = it.marks + written.id) else it })
    }
    return listOfNotNull(SetMark(part, written.id, written), joined?.let { SetMarkGroup(part, group.id, it) })
}

/** The command storing a stroke through [anchors] as what [role] of [picked] draws in [bin], closed where the role's marks close, and across the middle on the other eye with [mirror]. */
fun drawnMarkCommand(character: Character, picked: PickedGroup, role: MarkRole, anchors: List<Anchor>, bin: CameraBin, mirror: Boolean): DocumentCommand? {
    val group = character.groupOf(picked) ?: return null
    if (anchors.size < 2) return null
    val points = if (role in CLOSED_ROLES && anchors.first() != anchors.last()) anchors + anchors.first() else anchors
    val twin = character.twinOf(picked).takeIf { mirror }
    val mirrored = twin?.let { character.writing(picked.part, it, role, points.map { p -> p.copy(v = 1f - p.v) }, bin) }.orEmpty()
    return Together(character.writing(picked.part, group, role, points, bin) + mirrored)
}

/** The command moving the place at [index] of what [role] of [picked] draws in [bin] to [to], on the other eye too with [mirror]; a closed mark's two ends move as one. */
fun movedPlaceCommand(character: Character, picked: PickedGroup, role: MarkRole, bin: CameraBin, index: Int, to: Anchor, mirror: Boolean): DocumentCommand? {
    val group = character.groupOf(picked) ?: return null
    fun moved(of: MarkGroup, place: Anchor): List<DocumentCommand> {
        val mark = character.markIn(of, role, picked.part) ?: return emptyList()
        val held = mark.versions[bin]?.points ?: mark.points
        if (index !in held.indices) return emptyList()
        val closes = held.first() == held.last()
        val points = held.mapIndexed { i, old -> if (i == index || (closes && (i == 0 || i == held.lastIndex) && (index == 0 || index == held.lastIndex))) place else old }
        return character.writing(picked.part, of, role, points, bin)
    }
    val twin = character.twinOf(picked).takeIf { mirror }
    return Together(moved(group, to) + twin?.let { moved(it, to.copy(v = 1f - to.v)) }.orEmpty())
}

/** The command taking what [role] of [picked] draws in [bin] away: that version if the mark has others, else the mark. */
fun erasedMarkCommand(character: Character, picked: PickedGroup, role: MarkRole, bin: CameraBin): DocumentCommand? {
    val group = character.groupOf(picked) ?: return null
    val mark = character.markIn(group, role, picked.part) ?: return null
    val left = mark.versions - bin
    val shown = character.shownSet
    if (shown != null && character.part(picked.part)?.let { character.expressionMarks(shown, it, group.id)[role] } != null) {
        return SetExpressionMarks(shown.id, picked.part, group.id, role, if (left.isEmpty()) null else listOf(mark.copy(versions = left)))
    }
    if (left.isNotEmpty()) return SetMark(picked.part, mark.id, mark.copy(versions = left))
    val without = group.copy(roles = group.roles.map { if (it.role == role) it.copy(marks = it.marks - mark.id) else it })
    return Together(listOf(SetMarkGroup(picked.part, group.id, without), SetMark(picked.part, mark.id, null)))
}

/** The part the Face stage works on: the part whose role, its own or its template's in [templates], is the head, else the first part holding a face, whatever is selected. */
fun Character.faceHead(templates: TemplateLibrary): PartId? =
    parts.firstOrNull { (it.role ?: templates.find(it.template)?.role) == Role.HEAD }?.id ?: parts.firstOrNull { resolved(it).surface.groups.isNotEmpty() }?.id

/** Where a starter eye sits on the head and how wide its lid is, in u and v; the other eye mirrors it across the middle. */
private const val STARTER_U = 0.6f
private const val STARTER_V = 0.185f
private const val STARTER_HALF_U = 0.075f
private const val STARTER_HALF_V = 0.052f
private const val STARTER_WHITE = 1.15f
private const val STARTER_PUPIL = 0.45f
private const val RING_POINTS = 12
private const val LID_UNDER = 0.72f

/** The slots a starter eye fills its white and its pupil with, where the palette holds them. */
private val STARTER_WHITE_SLOT = SlotName("cream")
private val STARTER_PUPIL_SLOT = SlotName("ink")

/** A closed ring of places about [u], [v] on [part], [left] as drawn or mirrored across the middle. */
private fun ring(part: PartId, left: Boolean, u: Float, v: Float, halfU: Float, halfV: Float, under: Float = 1f): List<Anchor> = List(RING_POINTS + 1) { i ->
    val angle = 2.0 * PI * (i % RING_POINTS) / RING_POINTS
    val over = sin(angle).toFloat()
    val at = v - halfV * over * (if (over > 0f) 1f else under)
    Anchor(part, u = u - halfU * cos(angle).toFloat(), v = if (left) at else 1f - at)
}

/** One starter eye: an inked lid, a filled white inside it and a filled pupil, enough for the eye's controls to move something at once. */
private fun starterEye(character: Character, part: PartId, id: String, left: Boolean): List<DocumentCommand> {
    val held = { slot: SlotName -> slot.takeIf { character.palette.color(it) != null } }
    val lid = Mark(MarkId("$id-lid"), ring(part, left, STARTER_U, STARTER_V, STARTER_HALF_U, STARTER_HALF_V, LID_UNDER))
    val white = Mark(MarkId("$id-white"), ring(part, left, STARTER_U, STARTER_V, STARTER_HALF_U * STARTER_WHITE, STARTER_HALF_V * STARTER_WHITE), ink = false)
    val pupil = Mark(MarkId("$id-pupil"), ring(part, left, STARTER_U, STARTER_V, STARTER_HALF_U * STARTER_PUPIL, STARTER_HALF_V * STARTER_PUPIL), ink = false)
    val drawn = mapOf(MarkRole.LID to GroupRole(MarkRole.LID, marks = listOf(lid.id)),
        MarkRole.WHITE to GroupRole(MarkRole.WHITE, ink = false, fill = held(STARTER_WHITE_SLOT), marks = listOf(white.id)),
        MarkRole.PUPIL to GroupRole(MarkRole.PUPIL, ink = false, fill = held(STARTER_PUPIL_SLOT), marks = listOf(pupil.id)))
    val empty = eyeGroup(MarkGroupId(id), id)
    val group = empty.copy(roles = empty.roles.map { drawn[it.role] ?: it })
    return listOf(lid, white, pupil).map { SetMark(part, it.id, it) } + SetMarkGroup(part, group.id, group)
}

/** The command adding a pair of starter eyes to [part], or null when it has eyes. */
fun newEyesCommand(character: Character, part: PartId): DocumentCommand? {
    val groups = character.part(part)?.let { character.resolved(it) }?.surface?.groups ?: return null
    if (groups.any { it.isEye }) return null
    return Together(starterEye(character, part, EYE_LEFT, left = true) + starterEye(character, part, EYE_RIGHT, left = false))
}

/** The command adding an empty mouth to [part]. */
fun newMouthCommand(part: PartId): DocumentCommand = SetMarkGroup(part, MarkGroupId(MOUTH_ID), mouthGroup(MarkGroupId(MOUTH_ID), MOUTH_ID))

private fun freeMarkId(character: Character, stem: String): MarkId {
    val held = character.parts.flatMapTo(hashSetOf()) { part -> character.resolved(part).surface.marks.map { it.id.value } } +
        character.expressions.flatMap { set -> set.marks.keys.map { it.value } }
    return generateSequence(1) { it + 1 }.map { "$stem-$it" }.first { it !in held }.let(::MarkId)
}
