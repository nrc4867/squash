package dev.chieppa.squash.character.mouth

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.model.MouthStyle
import dev.chieppa.squash.character.mouth.shaped.buildsJawOpen
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.MouthShapeName
import dev.chieppa.squash.document.MouthShapeValues
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetMarkControls
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TongueLevel
import dev.chieppa.squash.document.isMouth

/** The style [group] on [part] talks in: the one it names, else its part's template's, else lips. */
fun TemplateLibrary.mouthStyleOf(part: PartInstance, group: MarkGroup): MouthStyle = mouthStyle(group.style ?: find(part.template)?.mouth)

/** The style each mouth on [part] of [character] talks in, by group. */
fun mouthStylesOf(character: Character, part: PartId, templates: TemplateLibrary): Map<MarkGroupId, MouthStyle> {
    val instance = character.part(part) ?: return emptyMap()
    return character.resolved(instance).surface.groups.filter { it.isMouth }.associate { it.id to templates.mouthStyleOf(instance, it) }
}

/** The teeth each mouth on [part] of [character] draws, by group, from the presets [templates] holds. */
fun dentitionsOf(character: Character, part: PartId, templates: TemplateLibrary): Map<MarkGroupId, MouthDentition> {
    val instance = character.part(part) ?: return emptyMap()
    val open = buildsJawOpen(character, instance, templates.find(instance.template))
    return character.resolved(instance).surface.groups.filter { it.isMouth }.associate { it.id to templates.dentitionOf(it, templates.mouthStyleOf(instance, it), open) }
}

/** What [group], talking in [style], sets saying [shape]: the style's shape under the group's own override. */
fun MarkGroup.saying(shape: MouthShapeName, style: MouthStyle): MouthShapeValues = style.shape(shape, shapes[shape])

/**
 * This document with every mouth whose style speaks saying [shape], its controls the shape's from its style with its own smile added as a mood; the
 * document itself when [shape] is null. What a preview shows, never sent to the history.
 */
fun SquashDocument.speaking(shape: MouthShapeName?, library: TemplateLibrary): SquashDocument {
    if (shape == null) return this
    var shown = this
    for (part in character.parts) {
        for (group in character.resolved(part).surface.groups.filter { it.isMouth }) {
            val style = library.mouthStyleOf(part, group)
            if (!style.speaks) continue
            val controls = group.controls.speaking(group.saying(shape, style))
            shown = SetMarkControls(part.id, group.id, controls).apply(shown).document
        }
    }
    return shown
}

/**
 * [group]'s marks as the shaper reads them, each mark's places from [placesOf]: the mark's own and its mirror's where it is mirrored, none
 * for a mark not there, and [lipRegions], the borders of its lips' painted color.
 */
fun MarkGroup.mouthMarks(placesOf: (MarkId) -> List<List<Anchor>>, lipRegions: List<List<Anchor>> = emptyList(), builtOpen: Boolean = false): MouthMarks {
    fun role(role: MarkRole) = roles.firstOrNull { it.role == role }
    fun marks(role: MarkRole) = role(role)?.let { held -> held.marks.flatMap { id -> placesOf(id).map { AnchoredMark(id, it, id in held.over) } } }.orEmpty()
    return MouthMarks(
        lip = role(MarkRole.LIP_LINE)?.marks.orEmpty().flatMap { placesOf(it) },
        inner = role(MarkRole.INNER)?.marks?.firstNotNullOfOrNull { placesOf(it).firstOrNull() }?.takeIf { it.size >= MIN_INNER },
        teeth = marks(MarkRole.TEETH),
        tongue = marks(MarkRole.TONGUE),
        tongueFilled = role(MarkRole.TONGUE)?.fill != null,
        lipsFilled = role(MarkRole.LIP_LINE)?.fill != null,
        tongueShown = paintsTongue(builtOpen),
        lipRegions = lipRegions,
    )
}

private const val MIN_INNER = 3

/** Whether this mouth paints its tongue: at flat, or at shaped on a mouth whose jaw is not [builtOpen], where no tongue is built. */
fun MarkGroup.paintsTongue(builtOpen: Boolean): Boolean = tongueLevel == TongueLevel.FLAT || (tongueLevel == TongueLevel.SHAPED && !builtOpen)
