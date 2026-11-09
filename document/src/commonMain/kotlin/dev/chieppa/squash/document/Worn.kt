package dev.chieppa.squash.document

/**
 * What a part is under the worn garments: the [template] it builds with, its [data], whether it is [hidden], and the [through] change
 * of the garment it comes out through, if any. [changedBy] is the garment whose change decided it, or null for the bare part.
 */
data class WornPart(
    val template: TemplateRef,
    val data: PartData,
    val hidden: Boolean = false,
    val through: BodyPartChange.Through? = null,
    val changedBy: GarmentId? = null,
)

/** The garments the shown outfit wears, innermost first. */
val Character.worn: List<Garment> get() = wardrobe.worn

/**
 * [part] as the worn garments leave it: its pair's values, its side's overrides, then each worn garment's change to it, innermost
 * first, each replacing what came before, so the outermost garment changing it wins. A change on either side of a pair holds for both.
 * See HLD: data-model, Garments.
 */
fun Character.wornPart(part: PartInstance): WornPart {
    val bare = WornPart(part.template, resolved(part))
    val twin = twinOf(part)?.id
    return worn.fold(bare) { held, garment ->
        val change = garment.changes[part.id] ?: twin?.let { garment.changes[it] } ?: return@fold held
        when (change) {
            BodyPartChange.Hide -> held.copy(hidden = true, through = null, changedBy = garment.id)
            is BodyPartChange.Through -> bare.copy(through = change, changedBy = garment.id)
            is BodyPartChange.Redraw -> bare.copy(data = bare.data.overlaidWith(readFor(change.data, part)), changedBy = garment.id)
            is BodyPartChange.Swap -> WornPart(change.template, readFor(change.data, part), changedBy = garment.id)
        }
    }
}

/** Whether [part] is the part id of a loose end of a garment in the wardrobe, which the pose may turn as it turns a part's bones. */
fun Character.isLooseEnd(part: PartId): Boolean =
    wardrobe.garments.any { garment -> garment.looseEnds.any { looseEndPartId(garment.id, it.id) == part } }

/** The part's worn state, or null if it is not in this character. */
fun Character.wornPart(id: PartId): WornPart? = part(id)?.let { wornPart(it) }

/** A change's [data], held as a pair's left side reads it, read by [part]. */
private fun Character.readFor(data: PartData, part: PartInstance): PartData = if (part.pair == null) data else sharedReadBy(data, part)
