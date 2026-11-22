package dev.chieppa.squash.character.mouth

import dev.chieppa.squash.character.block.Landmark
import dev.chieppa.squash.character.block.Tube
import dev.chieppa.squash.character.build.JAW_JOINT
import dev.chieppa.squash.character.build.RigParams
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.model.JointSpec
import dev.chieppa.squash.character.model.MouthLine
import dev.chieppa.squash.character.model.MouthStyle
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.GroupRole
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.SetMarkGroup
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.blendedSlot
import dev.chieppa.squash.document.isMouth

/** How far toward the ink the inside of a mouth whose group names no fill for it is taken from the head's own color. */
private const val INSIDE_TOWARD_INK = 0.72f

/** The slot a palette's ink is in, which an inside with no fill of its own darkens toward. */
private val INK = SlotName("ink")

/** The group and marks a jaw adds where its head draws no mouth line of its own. */
private val JAW_GROUP = MarkGroupId("jaw-mouth")
private const val JAW_LINE = "jaw-line"

/**
 * A muzzle's line as its template gives it, in the muzzle's u from its tip back toward the jaw joint and in v down from its top either
 * side of its underside: the line runs from under the nose at the tip back along each side, dropping toward the underside as it goes.
 */
private const val MUZZLE_TIP = 0.985f
private const val MUZZLE_BACK_PAST_JAW = 0.08f
private val MUZZLE_FROM_UNDER = floatArrayOf(0.1f, 0.16f, 0.19f, 0.22f)

/** A front mouth's line as its template gives it: across the mouth landmark, this far in v either side, its corners this far up in u. */
private const val FRONT_HALF_WIDTH = 0.05f
private const val FRONT_CORNER_LIFT = 0.006f
private const val FRONT_POINTS = 5

private const val UNDERSIDE = 0.5f

/** [template]'s jaw: its joint named jaw that turns only one side of the part, or null where it has none. */
fun jawJointOf(template: PartTemplate): JointSpec? = template.blocks.flatMap { it.joints() }.firstOrNull { it.name == JAW_JOINT && it.v != null }

/**
 * How a head's jaw splits it: along the lip line of [group] where the head draws one, else along the line [lines] its template and mouth
 * [style] give, as marks to add. See HLD: look, Mouth.
 */
sealed interface JawSplit {
    val style: MouthStyle

    /** The head's own mouth [group], whose lip line the jaw splits along. */
    class Drawn(val group: MarkGroup, override val style: MouthStyle) : JawSplit

    /** The line the head's template and style give, [lines] by its marks' anchors, with the mouth [group], if any, that names no lip line. */
    class Inferred(val lines: List<List<Anchor>>, val group: MarkGroup?, override val style: MouthStyle) : JawSplit
}

/**
 * How [part] of [character], built from [template], splits when its jaw opens, or null where it cannot: no jaw joint, a mouth on a beak's
 * seam, which opens on its own part, or no line drawn on it nor given by its template and style.
 */
fun jawSplitOf(character: Character, part: PartInstance, template: PartTemplate, templates: TemplateLibrary): JawSplit? {
    jawJointOf(template) ?: return null
    val groups = character.resolved(part).surface.groups.filter { it.isMouth }
    val marks = character.resolved(part).surface.marks.associateBy { it.id }
    val drawn = groups.firstOrNull { group -> lipMarks(group).any { id -> marks[id]?.points?.any { it.part == part.id } == true } }
    if (drawn != null) {
        val style = templates.mouthStyleOf(part, drawn)
        return if (style.line == MouthLine.SEAM) null else JawSplit.Drawn(drawn, style)
    }
    val group = groups.firstOrNull()
    val style = group?.let { templates.mouthStyleOf(part, it) } ?: templates.mouthStyle(template.mouth)
    if (template.mouth == null && group == null) return null
    val lines = templateMouthLine(part.id, template, style) ?: return null
    return JawSplit.Inferred(lines, group, style)
}

private fun lipMarks(group: MarkGroup): List<MarkId> = group.roles.firstOrNull { it.role == MarkRole.LIP_LINE }?.marks.orEmpty()

/**
 * The mouth line [template] and [style] give a head [part] with none drawn: across the front of the face through its mouth landmark for a
 * front style, back along both sides of its muzzle from under the nose for a muzzle; null for a beak's seam or a front style with no mouth
 * landmark. Each line is one mark's anchors.
 */
fun templateMouthLine(part: PartId, template: PartTemplate, style: MouthStyle): List<List<Anchor>>? {
    val jaw = jawJointOf(template) ?: return null
    return when (style.line) {
        MouthLine.SEAM -> null
        MouthLine.FRONT -> {
            val mouth = template.blocks.filterIsInstance<Landmark>().firstOrNull { it.name == MOUTH_LANDMARK } ?: return null
            listOf(
                (0 until FRONT_POINTS).map { i ->
                    val across = (i.toFloat() / (FRONT_POINTS - 1)) * 2f - 1f
                    val v = mouth.v + across * FRONT_HALF_WIDTH
                    Anchor(part, BODY_PATCH, mouth.u + FRONT_CORNER_LIFT * across * across, v - kotlin.math.floor(v))
                },
            )
        }
        MouthLine.MUZZLE -> {
            val patch = muzzlePatchOf(template, jaw)
            val back = (jaw.u + MUZZLE_BACK_PAST_JAW).coerceAtMost(MUZZLE_TIP)
            val us = MUZZLE_FROM_UNDER.indices.map { i -> MUZZLE_TIP + (back - MUZZLE_TIP) * i / (MUZZLE_FROM_UNDER.size - 1) }
            listOf(-1f, 1f).map { side -> us.mapIndexed { i, u -> Anchor(part, patch, u, UNDERSIDE + side * MUZZLE_FROM_UNDER[i]) } }
        }
    }
}

/** The patch a muzzle's line lies on: the run the template sweeps from the jaw joint on to the part's end, else the part's body. */
private fun muzzlePatchOf(template: PartTemplate, jaw: JointSpec): String =
    template.blocks.filterIsInstance<Tube>().firstOrNull { tube -> tube.name != null && tube.u.end >= MUZZLE_TIP && tube.u.start >= jaw.u - MUZZLE_BACK_PAST_JAW }?.name
        ?: BODY_PATCH

private const val MOUTH_LANDMARK = "mouth"

/** The degrees [character] shows [part]'s jaw open at. */
fun Character.jawDegrees(part: PartId): Float = shownPose.of(part, JAW_JOINT)?.degrees ?: 0f

/** The jaw split of [part] while its jaw shows open and may open at all, else null, so a jaw held to 0 shows its head exactly shut. */
fun openJawOf(character: Character, part: PartInstance, templates: TemplateLibrary): JawSplit? {
    if (character.jawDegrees(part.id) <= 0f) return null
    val template = templates.find(part.template) ?: return null
    if ((jawLimitOf(character, part, templates) ?: 0f) <= 0f) return null
    return jawSplitOf(character, part, template, templates)
}

/**
 * The widest [part]'s jaw opens, in degrees: the part's own `rig.jaw`, else its mouth style's jaw, else its template's jaw joint's limit;
 * 0 where the jaw cannot split, so it holds shut. Null where the part has no jaw.
 */
fun jawLimitOf(character: Character, part: PartInstance, templates: TemplateLibrary): Float? {
    val template = templates.find(part.template) ?: return null
    val joint = jawJointOf(template) ?: return null
    val split = jawSplitOf(character, part, template, templates) ?: return 0f
    val own = character.resolved(part).params[RigParams.JAW]
    return own ?: split.style.jaw ?: joint.limits?.max ?: 0f
}

/** [openJawGroup] with how far open [part]'s jaw shows, a share of its limit; empty while it shows shut. */
fun openJawShares(character: Character, part: PartInstance, templates: TemplateLibrary): Map<MarkGroupId, Float> {
    val group = openJawGroup(character, part, templates) ?: return emptyMap()
    val limit = jawLimitOf(character, part, templates)?.takeIf { it > 0f } ?: return emptyMap()
    return mapOf(group to (character.jawDegrees(part.id) / limit).coerceIn(0f, 1f))
}

/** The mouth group the jaw of [part] splits along while it shows open: its own, or the one [withOpenJaws] adds. */
fun openJawGroup(character: Character, part: PartInstance, templates: TemplateLibrary): MarkGroupId? = when (val split = openJawOf(character, part, templates)) {
    is JawSplit.Drawn -> split.group.id
    is JawSplit.Inferred -> split.group?.id ?: JAW_GROUP
    null -> null
}

/**
 * This document as it shows with every jaw posed open that its head draws no mouth line for given the line its template and style give,
 * added as marks of a mouth group, its inside in the group's inner fill or the head's color taken toward the ink. A head that draws its
 * line, or a jaw at rest, changes nothing. See HLD: look, Mouth.
 */
fun SquashDocument.withOpenJaws(templates: TemplateLibrary): SquashDocument {
    var shown = this
    for (part in character.parts) {
        val split = openJawOf(character, part, templates) as? JawSplit.Inferred ?: continue
        shown = inferred(shown, part, split)
    }
    return shown
}

private fun inferred(document: SquashDocument, part: PartInstance, split: JawSplit.Inferred): SquashDocument {
    var shown = document
    val ids = split.lines.indices.map { MarkId("$JAW_LINE-$it") }
    split.lines.forEachIndexed { i, line -> shown = SetMark(part.id, ids[i], Mark(ids[i], line)).apply(shown).document }
    val base = split.group
    val inside = base?.roles?.firstOrNull { it.role == MarkRole.INNER }?.fill ?: insideSlot(document.character, part)
    val roles = (base?.roles ?: emptyList()).filter { it.role != MarkRole.LIP_LINE && it.role != MarkRole.INNER } +
        GroupRole(MarkRole.INNER, ink = false, fill = inside) + GroupRole(MarkRole.LIP_LINE, marks = ids)
    val group = (base ?: MarkGroup(JAW_GROUP, JAW_GROUP.value, roles)).copy(roles = roles)
    return SetMarkGroup(part.id, group.id, group).apply(shown).document
}

/** The inside of [part]'s mouth where its group names no fill: the head's own color taken toward the ink, or the darkest slot where there is no ink. */
private fun insideSlot(character: Character, part: PartInstance): SlotName? {
    val base = character.resolved(part).surface.baseSlot ?: return null
    val palette = character.palette
    val ink = INK.takeIf { palette.color(it) != null } ?: palette.slots.minByOrNull { it.color.red + it.color.green + it.color.blue }?.name ?: return base
    return blendedSlot(base, ink, INSIDE_TOWARD_INK)
}
