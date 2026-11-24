package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Sets how the group [id] that [part] shows says [shape], in place of its style's; null values, or none set, take the style's again. */
@Serializable
@SerialName("setMouthShape")
data class SetMouthShape(
    val part: PartId,
    val id: MarkGroupId,
    val shape: MouthShapeName,
    val values: MouthShapeValues?,
    val thisSide: Boolean = false,
) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val shown = document.character.editBase(instance, thisSide).surface.groups.firstOrNull { it.id == id }
        val group = requireNotNull(shown) { "No mark group ${id.value} on ${part.value}" }
        val shapes = if (values == null || values.isEmpty) group.shapes - shape else group.shapes + (shape to values)
        return SetMarkGroup(part, id, group.copy(shapes = shapes), thisSide).apply(document)
    }
}

/** Sets the teeth of the group [id] that [part] shows: its preset and what it changes of each jaw. */
@Serializable
@SerialName("setMouthTeeth")
data class SetMouthTeeth(
    val part: PartId,
    val id: MarkGroupId,
    val teeth: MouthTeeth,
    val thisSide: Boolean = false,
) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val group = document.shownGroup(part, id, thisSide)
        return SetMarkGroup(part, id, group.copy(teeth = teeth), thisSide).apply(document)
    }
}

/** Sets how much tongue the group [id] that [part] shows draws. */
@Serializable
@SerialName("setTongueLevel")
data class SetTongueLevel(
    val part: PartId,
    val id: MarkGroupId,
    val level: TongueLevel,
    val thisSide: Boolean = false,
) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val group = document.shownGroup(part, id, thisSide)
        return SetMarkGroup(part, id, group.copy(tongueLevel = level), thisSide).apply(document)
    }
}

/** Sets how the shaped tongue of the group [id] that [part] shows ends. */
@Serializable
@SerialName("setTongueTip")
data class SetTongueTip(
    val part: PartId,
    val id: MarkGroupId,
    val tip: TongueTip,
    val thisSide: Boolean = false,
) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val group = document.shownGroup(part, id, thisSide)
        return SetMarkGroup(part, id, group.copy(tongueTip = tip), thisSide).apply(document)
    }
}

private fun SquashDocument.shownGroup(part: PartId, id: MarkGroupId, thisSide: Boolean): MarkGroup {
    val shown = character.editBase(requirePart(part), thisSide).surface.groups.firstOrNull { it.id == id }
    return requireNotNull(shown) { "No mark group ${id.value} on ${part.value}" }
}

