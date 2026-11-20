package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Makes [garment] the wardrobe's garment of its id whole: the undo of every change to one garment. */
@Serializable
@SerialName("setGarment")
data class SetGarment(val garment: Garment) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val before = requireNotNull(document.character.wardrobe.garment(garment.id)) { "No garment ${garment.id.value}" }
        val next = document.character.wardrobe.withGarment(garment.id) { garment }
        return Applied(document.copy(character = document.character.copy(wardrobe = next)), SetGarment(before))
    }
}

/** Names [garment] [name]. */
@Serializable
@SerialName("renameGarment")
data class RenameGarment(val garment: GarmentId, val name: String) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { it.copy(name = name) }
}

/**
 * Has [garment] cover [branch]'s part with it, or replaces the branch it has there; where no area of the garment paints the part, the
 * branch's box is painted as its cells (see [Garment.withBoxesPainted]).
 */
@Serializable
@SerialName("coverPart")
data class CoverPart(val garment: GarmentId, val branch: GarmentBranch) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { held ->
        document.requirePart(branch.part)
        val kept = held.branches.map { if (it.part == branch.part) branch else it }
        held.copy(branches = if (held.branch(branch.part) == null) held.branches + branch else kept).withBoxesPainted()
    }
}

/** Takes [garment]'s branch off [part], with its inked join and the loose ends it carries. */
@Serializable
@SerialName("uncoverPart")
data class UncoverPart(val garment: GarmentId, val part: PartId) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { held ->
        require(held.branch(part) != null) { "Garment ${garment.value} does not cover ${part.value}" }
        held.copy(branches = held.branches.filter { it.part != part }, joins = held.joins - part, looseEnds = held.looseEnds.filter { it.branch != part })
    }
}

/**
 * Sets how far along [part] [garment]'s branch runs, [from] and [to] in u, the bounds of its painted cells; past 1 it continues past the
 * part's end where the cells reach it. Its cells, which say what it covers, stay as painted.
 */
@Serializable
@SerialName("setBranchRange")
data class SetBranchRange(val garment: GarmentId, val part: PartId, val from: Float, val to: Float) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        document.garmentChanged(garment) { it.withBranch(part) { branch -> branch.copy(from = from, to = to) } }
}

/** Sets how far round [part] [garment]'s branch runs, [fromV] to [toV], the bounds of its painted cells; a span of 1 or more goes the whole way round. */
@Serializable
@SerialName("setBranchAround")
data class SetBranchAround(val garment: GarmentId, val part: PartId, val fromV: Float, val toV: Float) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        document.garmentChanged(garment) { it.withBranch(part) { branch -> branch.copy(fromV = fromV, toV = toV) } }
}

/** Sets how far [garment]'s branch on [part] stands off the body where nothing drawn takes it further. */
@Serializable
@SerialName("setBranchEase")
data class SetBranchEase(val garment: GarmentId, val part: PartId, val ease: Float) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        document.garmentChanged(garment) { it.withBranch(part) { branch -> branch.copy(ease = ease) } }
}

/** Sets the outline [garment]'s branch on [part] takes in [view]; a null [curve] clears it and the ease alone shapes it there. */
@Serializable
@SerialName("setBranchOutline")
data class SetBranchOutline(val garment: GarmentId, val part: PartId, val view: View, val curve: OutlineCurve?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) {
        it.withBranch(part) { branch -> branch.copy(outlines = if (curve == null) branch.outlines - view else branch.outlines + (view to curve)) }
    }
}

/** Sets the surface work on [garment]'s branch on [part], its colour, regions, paint and marks. */
@Serializable
@SerialName("setBranchSurface")
data class SetBranchSurface(val garment: GarmentId, val part: PartId, val surface: SurfaceWork) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        document.garmentChanged(garment) { it.withBranch(part) { branch -> branch.copy(surface = surface) } }
}

/** Inks the join where [garment]'s branch on [part] meets the branch it comes out of, or leaves it bare. */
@Serializable
@SerialName("setJoinInk")
data class SetJoinInk(val garment: GarmentId, val part: PartId, val inked: Boolean) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) {
        require(it.branch(part) != null) { "Garment ${garment.value} does not cover ${part.value}" }
        it.copy(joins = if (inked) it.joins + part else it.joins - part)
    }
}

/** Sets whether [garment]'s edges are inked: its hems, its holes' edges and its cuffs. */
@Serializable
@SerialName("setHemInk")
data class SetHemInk(val garment: GarmentId, val inked: Boolean) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { it.copy(inkHem = inked) }
}

/** Sets the weight of [garment]'s silhouette and hems, [weight], as a share of the skin's line weight. */
@Serializable
@SerialName("setGarmentInkWeight")
data class SetGarmentInkWeight(val garment: GarmentId, val weight: Float) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        document.garmentChanged(garment) { it.copy(inkWeight = weight.coerceIn(0f, MOST_CLOTH_INK_WEIGHT)) }
}

/** Sets how far [garment]'s hems, neck openings and cuffs roll out past its cloth, [roll] in body units. */
@Serializable
@SerialName("setGarmentHemRoll")
data class SetGarmentHemRoll(val garment: GarmentId, val roll: Float) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        document.garmentChanged(garment) { it.copy(hemRoll = roll.coerceIn(0f, MOST_HEM_ROLL)) }
}

/** Sets how far [garment] stands off the body. */
@Serializable
@SerialName("setGarmentFit")
data class SetGarmentFit(val garment: GarmentId, val fit: Float) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        require(fit >= 0f) { "A garment's fit is negative" }
        return document.garmentChanged(garment) { it.copy(fit = fit) }
    }
}

/** Sets how far [garment] stands off [part], or of the pair it leads, in place of the garment's fit; null takes the override away. */
@Serializable
@SerialName("setPartFit")
data class SetPartFit(val garment: GarmentId, val part: PartId, val fit: Float?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { held ->
        document.requirePart(part)
        require(fit == null || fit >= 0f) { "A part's fit is negative" }
        held.copy(partFits = if (fit == null) held.partFits - part else held.partFits + (part to fit))
    }
}

/** Sets how far [garment] stands off [part], or the pair it leads, along and round it, over its fit; null takes the profile away. */
@Serializable
@SerialName("setPartEase")
data class SetPartEase(val garment: GarmentId, val part: PartId, val profile: EaseProfile?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { held ->
        document.requirePart(part)
        held.copy(eases = if (profile == null) held.eases - part else held.eases + (part to profile))
    }
}

/** Sets how [garment]'s cloth hangs below the widest place of each upright part it covers; null lets it follow the body. */
@Serializable
@SerialName("setGarmentHang")
data class SetGarmentHang(val garment: GarmentId, val hang: ClothHang?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { it.copy(hang = hang) }
}

/** Drops the seam where [garment]'s cloth over [part], or the pair it leads, meets the cloth it grows from to [drop] along it; null or 0 keeps it at the join. */
@Serializable
@SerialName("setGarmentDrop")
data class SetGarmentDrop(val garment: GarmentId, val part: PartId, val drop: Float?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { held ->
        document.requirePart(part)
        val at = drop?.coerceIn(0f, MOST_DROP)
        held.copy(drops = if (at == null || at == 0f) held.drops - part else held.drops + (part to at))
    }
}

/** Sets the areas painted for [garment] and its holes. */
@Serializable
@SerialName("setGarmentPaint")
data class SetGarmentPaint(val garment: GarmentId, val areas: List<GarmentArea>, val holes: List<GarmentHole>) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        document.garmentChanged(garment) { it.copy(areas = areas, holes = holes) }
}

/** Names [garment]'s area [area] [name]. */
@Serializable
@SerialName("renameGarmentArea")
data class RenameGarmentArea(val garment: GarmentId, val area: GarmentAreaId, val name: String) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { held ->
        require(held.area(area) != null) { "Garment ${garment.value} has no area ${area.value}" }
        held.copy(areas = held.areas.map { if (it.id == area) it.copy(name = name) else it })
    }
}

/** Colours [garment]'s area [area] from palette slot [color], or from the garment's own while null. */
@Serializable
@SerialName("setGarmentAreaColor")
data class SetGarmentAreaColor(val garment: GarmentId, val area: GarmentAreaId, val color: SlotName?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { held ->
        require(held.area(area) != null) { "Garment ${garment.value} has no area ${area.value}" }
        held.copy(areas = held.areas.map { if (it.id == area) it.copy(color = color) else it })
    }
}

/** Prints [region] on [garment]'s cloth as its region [id], replacing the one of that id, or takes that region away when [region] is null. */
@Serializable
@SerialName("setGarmentRegion")
data class SetGarmentRegion(val garment: GarmentId, val id: RegionId, val region: Region?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { held ->
        require(region == null || region.id == id) { "Region ${region?.id?.value} is set as ${id.value}" }
        region?.border?.forEach { document.requirePart(it.part) }
        held.copy(regions = replacedOrAdded(held.regions, id, region) { it.id })
    }
}

/** Prints [mark] on [garment]'s cloth as its mark [id], replacing the one of that id, or takes that mark away when [mark] is null. */
@Serializable
@SerialName("setGarmentMark")
data class SetGarmentMark(val garment: GarmentId, val id: MarkId, val mark: Mark?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { held ->
        require(mark == null || mark.id == id) { "Mark ${mark?.id?.value} is set as ${id.value}" }
        mark?.points?.forEach { document.requirePart(it.part) }
        held.copy(marks = replacedOrAdded(held.marks, id, mark) { it.id })
    }
}

/** [items] with the one of [id] replaced by [item], [item] added after them where none is, or the one of [id] taken away where [item] is null. */
private fun <T, K> replacedOrAdded(items: List<T>, id: K, item: T?, idOf: (T) -> K): List<T> = when {
    item == null -> {
        require(items.any { idOf(it) == id }) { "Nothing to take away as $id" }
        items.filter { idOf(it) != id }
    }
    items.any { idOf(it) == id } -> items.map { if (idOf(it) == id) item else it }
    else -> items + item
}

/** Records the Tuck choice for [kind] on [part] in [garment], or takes it away when [tucked] is null. */
@Serializable
@SerialName("setTuck")
data class SetTuck(val garment: GarmentId, val part: PartId, val kind: TuckKind, val tucked: Boolean?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { held ->
        document.requirePart(part)
        val others = held.tucks.filterNot { it.part == part && it.kind == kind }
        held.copy(tucks = if (tucked == null) others else others + TuckChoice(part, kind, tucked))
    }
}

/** Colours [garment] from palette slot [color], or from the part's own while null. */
@Serializable
@SerialName("setGarmentColor")
data class SetGarmentColor(val garment: GarmentId, val color: SlotName?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { it.copy(color = color) }
}

/** Sets what [garment] does to [part] while worn, or nothing when [change] is null. */
@Serializable
@SerialName("setBodyPartChange")
data class SetBodyPartChange(val garment: GarmentId, val part: PartId, val change: BodyPartChange?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { held ->
        document.requirePart(part)
        held.copy(changes = if (change == null) held.changes - part else held.changes + (part to change))
    }
}

/** Adds [end] to [garment], or replaces the loose end of its id. */
@Serializable
@SerialName("setLooseEnd")
data class SetLooseEnd(val garment: GarmentId, val end: LooseEnd) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { held ->
        require(held.branch(end.branch) != null) { "Garment ${garment.value} does not cover ${end.branch.value}" }
        val replaced = held.looseEnds.map { if (it.id == end.id) end else it }
        held.copy(looseEnds = if (held.looseEnds.any { it.id == end.id }) replaced else held.looseEnds + end)
    }
}

/** Removes the loose end [end] from [garment]. */
@Serializable
@SerialName("removeLooseEnd")
data class RemoveLooseEnd(val garment: GarmentId, val end: LooseEndId) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { held ->
        require(held.looseEnds.any { it.id == end }) { "Garment ${garment.value} has no loose end ${end.value}" }
        held.copy(looseEnds = held.looseEnds.filter { it.id != end })
    }
}

/** This document with [garment] changed by [change], undone by setting the garment back whole. */
internal fun SquashDocument.garmentChanged(garment: GarmentId, change: (Garment) -> Garment): Applied<SquashDocument> {
    val held = requireNotNull(character.wardrobe.garment(garment)) { "No garment ${garment.value}" }
    return SetGarment(change(held)).apply(this)
}
