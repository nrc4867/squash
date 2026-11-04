package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Each command here writes the pair for a paired part, or with thisSide that side's override alone; an unpaired part has one
 * place. A null value clears, and clearing an override shows the pair's value again. Anchors are given as the part's side
 * sees them unless stored says they are as the pair holds them, as an undo's are. A list item keeps its place when replaced;
 * index places a new one, last if null.
 */

/** Sets the palette slot a part starts from. */
@Serializable
@SerialName("setBaseSlot")
data class SetBaseSlot(val part: PartId, val slot: SlotName?, val thisSide: Boolean = false) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val before = document.writtenData(instance, thisSide).surface
        return Applied(
            document.withWrittenSurface(instance, thisSide, before.copy(baseSlot = slot)),
            SetBaseSlot(part, before.baseSlot, thisSide),
        )
    }
}

/** Fills the area its template names [area] with a palette slot. */
@Serializable
@SerialName("setAreaFill")
data class SetAreaFill(val part: PartId, val area: String, val slot: SlotName?, val thisSide: Boolean = false) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val before = document.writtenData(instance, thisSide).surface
        val fills = if (slot == null) before.areaFills - area else before.areaFills + (area to slot)
        return Applied(
            document.withWrittenSurface(instance, thisSide, before.copy(areaFills = fills)),
            SetAreaFill(part, area, before.areaFills[area], thisSide),
        )
    }
}

/** Sets a part's combed grain. */
@Serializable
@SerialName("setGrain")
data class SetGrain(val part: PartId, val grain: Grain?, val thisSide: Boolean = false) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val before = document.writtenData(instance, thisSide).surface
        return Applied(
            document.withWrittenSurface(instance, thisSide, before.copy(grain = grain)),
            SetGrain(part, before.grain, thisSide),
        )
    }
}

/** Sets how a part's fur strokes show. */
@Serializable
@SerialName("setFurRule")
data class SetFurRule(val part: PartId, val rule: FurRule?, val thisSide: Boolean = false) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val before = document.writtenData(instance, thisSide).surface
        return Applied(
            document.withWrittenSurface(instance, thisSide, before.copy(fur = rule)),
            SetFurRule(part, before.fur, thisSide),
        )
    }
}

/**
 * Adds [marks] as one step, as one drag of the Fur stroke tool lays them: after those the part holds, or at [indices] when undoing a
 * removal, each the place it goes in the list as it stands by then.
 */
@Serializable
@SerialName("addMarks")
data class AddMarks(
    val part: PartId,
    val marks: List<Mark>,
    val thisSide: Boolean = false,
    val stored: Boolean = false,
    val indices: List<Int>? = null,
) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val before = document.writtenData(instance, thisSide).surface
        val written = marks.map { it.mapAnchors(document.character.writeTransform(instance, thisSide, stored)) }
        val next = before.marks.toMutableList()
        written.forEachIndexed { i, mark -> next.add(indices?.get(i) ?: next.size, mark) }
        return Applied(
            document.withWrittenSurface(instance, thisSide, before.copy(marks = next)),
            RemoveMarks(part, marks.map { it.id }, thisSide),
        )
    }
}

/** Removes the marks [ids] as one step. */
@Serializable
@SerialName("removeMarks")
data class RemoveMarks(val part: PartId, val ids: List<MarkId>, val thisSide: Boolean = false) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val before = document.writtenData(instance, thisSide).surface
        val going = ids.toSet()
        val places = before.marks.indices.filter { before.marks[it].id in going }
        return Applied(
            document.withWrittenSurface(instance, thisSide, before.copy(marks = before.marks.filter { it.id !in going })),
            AddMarks(part, places.map { before.marks[it] }, thisSide, stored = true, indices = places),
        )
    }
}

/** Adds, replaces or removes the mark [id]. */
@Serializable
@SerialName("setMark")
data class SetMark(
    val part: PartId,
    val id: MarkId,
    val mark: Mark?,
    val thisSide: Boolean = false,
    val index: Int? = null,
    val stored: Boolean = false,
) : DocumentCommand {
    init {
        require(mark == null || mark.id == id) { "Mark ${mark?.id?.value} given for ${id.value}" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val before = document.writtenData(instance, thisSide).surface
        val written = mark?.mapAnchors(document.character.writeTransform(instance, thisSide, stored))
        val old = before.marks.locate { it.id == id }
        return Applied(
            document.withWrittenSurface(instance, thisSide, before.copy(marks = before.marks.withItem(written, index) { it.id == id })),
            SetMark(part, id, old.value, thisSide, old.restoreIndex, stored = true),
        )
    }
}

/** Adds, replaces or removes the mark group [id]. */
@Serializable
@SerialName("setMarkGroup")
data class SetMarkGroup(
    val part: PartId,
    val id: MarkGroupId,
    val group: MarkGroup?,
    val thisSide: Boolean = false,
    val index: Int? = null,
) : DocumentCommand {
    init {
        require(group == null || group.id == id) { "Mark group ${group?.id?.value} given for ${id.value}" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val before = document.writtenData(instance, thisSide).surface
        val old = before.groups.locate { it.id == id }
        return Applied(
            document.withWrittenSurface(instance, thisSide, before.copy(groups = before.groups.withItem(group, index) { it.id == id })),
            SetMarkGroup(part, id, old.value, thisSide, old.restoreIndex),
        )
    }
}

/** Sets the controls of the group [id] that [part] shows; with [thisSide] the side takes its own copy of the group. */
@Serializable
@SerialName("setMarkControls")
data class SetMarkControls(
    val part: PartId,
    val id: MarkGroupId,
    val controls: MarkControls,
    val thisSide: Boolean = false,
) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val shown = document.character.editBase(instance, thisSide).surface.groups.firstOrNull { it.id == id }
        val group = requireNotNull(shown) { "No mark group ${id.value} on ${part.value}" }
        return SetMarkGroup(part, id, group.copy(controls = controls), thisSide).apply(document)
    }
}

/** Adds, replaces or removes the region [id]. */
@Serializable
@SerialName("setRegion")
data class SetRegion(
    val part: PartId,
    val id: RegionId,
    val region: Region?,
    val thisSide: Boolean = false,
    val index: Int? = null,
    val stored: Boolean = false,
) : DocumentCommand {
    init {
        require(region == null || region.id == id) { "Region ${region?.id?.value} given for ${id.value}" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val before = document.writtenData(instance, thisSide).surface
        val written = region?.mapAnchors(document.character.writeTransform(instance, thisSide, stored))
        val old = before.regions.locate { it.id == id }
        return Applied(
            document.withWrittenSurface(instance, thisSide, before.copy(regions = before.regions.withItem(written, index) { it.id == id })),
            SetRegion(part, id, old.value, thisSide, old.restoreIndex, stored = true),
        )
    }
}

/** Adds, replaces or removes the brush stroke [id]. */
@Serializable
@SerialName("setBrushStroke")
data class SetBrushStroke(
    val part: PartId,
    val id: BrushStrokeId,
    val stroke: BrushStroke?,
    val thisSide: Boolean = false,
    val index: Int? = null,
    val stored: Boolean = false,
) : DocumentCommand {
    init {
        require(stroke == null || stroke.id == id) { "Brush stroke ${stroke?.id?.value} given for ${id.value}" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val before = document.writtenData(instance, thisSide).surface
        val written = stroke?.mapAnchors(document.character.writeTransform(instance, thisSide, stored))
        val old = before.paint.strokes.locate { it.id == id }
        val strokes = before.paint.strokes.withItem(written, index) { it.id == id }
        return Applied(
            document.withWrittenSurface(instance, thisSide, before.copy(paint = Paint(strokes))),
            SetBrushStroke(part, id, old.value, thisSide, old.restoreIndex, stored = true),
        )
    }
}

private fun SquashDocument.withWrittenSurface(part: PartInstance, thisSide: Boolean, surface: SurfaceWork): SquashDocument =
    withWritten(part, thisSide, writtenData(part, thisSide).copy(surface = surface))
