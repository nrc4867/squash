package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3

/** What a worn branch covers: a place on the body, by its part, u, v and point in body space. */
fun interface GarmentCover {
    fun covers(part: PartId, u: Float, v: Float, point: Vec3): Boolean
}

/**
 * What [branch] covers. A branch the whole way round covers whatever lies inside it; one over part of the way round covers its own
 * part's surface under it, from its u range and v range.
 */
internal fun coverOf(branch: PlacedPart): GarmentCover = when (val hider = hiderOf(branch)) {
    is Hider.Inside -> GarmentCover { _, _, _, point -> hider.hides(point) }
    is Hider.Over -> GarmentCover { part, u, v, _ -> part == hider.part && hider.covers(u, v) }
    null -> GarmentCover { _, _, _, _ -> false }
}

/** What every garment branch and cuff this character wears covers of the body. */
fun AssembledCharacter.garmentCovers(): List<GarmentCover> = parts.filter { part -> part.covering != null }.map(::coverOf)
