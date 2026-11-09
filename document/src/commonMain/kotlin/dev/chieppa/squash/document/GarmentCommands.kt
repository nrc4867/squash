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

/** Has [garment] cover [branch]'s part with it, or replaces the branch it has there. */
@Serializable
@SerialName("coverPart")
data class CoverPart(val garment: GarmentId, val branch: GarmentBranch) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { held ->
        document.requirePart(branch.part)
        val kept = held.branches.map { if (it.part == branch.part) branch else it }
        held.copy(branches = if (held.branch(branch.part) == null) held.branches + branch else kept)
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

/** Sets how far along [part] [garment]'s branch runs, [from] and [to] in u; past 1 it continues past the part's end. */
@Serializable
@SerialName("setBranchRange")
data class SetBranchRange(val garment: GarmentId, val part: PartId, val from: Float, val to: Float) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        document.garmentChanged(garment) { it.withBranch(part) { branch -> branch.copy(from = from, to = to) } }
}

/** Sets how far round [part] [garment]'s branch runs, [fromV] to [toV]; a span of 1 or more goes the whole way round. */
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

/** Sets whether [garment]'s hem and its cuffs are inked. */
@Serializable
@SerialName("setEdgeInk")
data class SetEdgeInk(val garment: GarmentId, val hem: Boolean, val cuffs: Boolean) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        document.garmentChanged(garment) { it.copy(inkHem = hem, inkCuffs = cuffs) }
}

/** Sets how wide the fillets at [garment]'s joins are. */
@Serializable
@SerialName("setGarmentFillet")
data class SetGarmentFillet(val garment: GarmentId, val fillet: Float) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { it.copy(fillet = fillet) }
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
private fun SquashDocument.garmentChanged(garment: GarmentId, change: (Garment) -> Garment): Applied<SquashDocument> {
    val held = requireNotNull(character.wardrobe.garment(garment)) { "No garment ${garment.value}" }
    return SetGarment(change(held)).apply(this)
}
