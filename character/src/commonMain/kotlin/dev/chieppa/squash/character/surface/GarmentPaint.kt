package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.HeldShapes
import dev.chieppa.squash.character.build.LandedShape
import dev.chieppa.squash.character.build.RANK_AREA
import dev.chieppa.squash.character.build.RANK_REGION
import dev.chieppa.squash.character.build.RANK_STROKE
import dev.chieppa.squash.character.build.ShapeSource
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.ColorShape
import dev.chieppa.squash.geom.SurfaceUv

/** How many of a garment's painted shapes [GarmentPaints] keeps between builds. */
private const val KEPT_PAINTS = 16

/**
 * A garment's own color shapes, part by part: each painted area in a palette slot of its own over what it holds of the part's cloth
 * ([areaShapes]), then each branch's regions and brush strokes, anchored in the (u, v) of its part's first run, as the garment's
 * vertices carry them; a branch read on the other side of a pair takes the same shapes there. Its base slot is the garment's color,
 * painted under these, so none is held here.
 */
fun garmentShapes(garment: SurfaceGarment): Map<PartId, HeldShapes> {
    val out = LinkedHashMap<PartId, HeldShapes>()
    for (run in garment.surface.field.runs) {
        if (!run.first || run.partId in out) continue
        val branch = garment.cover.cover.branches[run.partId] ?: continue
        val shapes = areaShapes(garment.cover, run.partId, run.patch.name) + branchShapes(branch, run.partId, run.patch.name, run.part.surface.axisLength)
        if (shapes.isNotEmpty()) out[run.partId] = HeldShapes(null, shapes, emptyMap(), 0)
    }
    return out
}

/** Each area of [worn]'s garment in a slot apart from the garment's, over what it holds of [part]'s cloth on its run [patch]. */
private fun areaShapes(worn: WornCover, part: PartId, patch: String): List<LandedShape> {
    val cloth = worn.clothOf(part) ?: return emptyList()
    val garment = worn.garment
    return garment.areas.withIndex().mapNotNull { (i, area) ->
        val slot = area.color?.takeIf { it != garment.color } ?: return@mapNotNull null
        if (i >= cloth.areaCount || !cloth.paints(i)) return@mapNotNull null
        LandedShape(RANK_AREA, slot, part, patch, ColorShape.Level(AreaShare(cloth, i)))
    }
}

/** [branch]'s regions and strokes as shapes on [part]'s run [patch], read wherever their anchors name the branch's part. */
private fun branchShapes(branch: GarmentBranch, part: PartId, patch: String, axisLength: Float): List<LandedShape> {
    fun uv(anchors: List<Anchor>) = anchors.filter { it.part == branch.part && it.patch == patch }.map { SurfaceUv(it.u, it.v) }
    val regions = branch.surface.regions.mapNotNull { region ->
        val points = uv(region.border)
        val corners = if (points.size == region.border.size) region.corners else emptySet()
        if (points.size < 3) null else LandedShape(RANK_REGION, region.slot, part, patch, ColorShape.Border(points, corners = corners, curved = region.curved), ShapeSource.Border(part, region.id))
    }
    val strokes = branch.surface.paint.strokes.mapNotNull { stroke ->
        val points = uv(stroke.points)
        if (points.isEmpty()) null else LandedShape(RANK_STROKE, stroke.slot, part, patch, ColorShape.Brush(points, stroke.radius * axisLength, stroke.softness), ShapeSource.Stroke(part, stroke.id))
    }
    return regions + strokes
}

/**
 * The paint of garments' own shapes, laid on each garment's surface and kept to what of it is drawn, kept for a garment while it is the
 * same object, so a garment kept whole from the last build keeps its paint.
 */
class GarmentPaints {
    private var kept: List<Pair<SurfaceGarment, SurfacePaint?>> = emptyList()

    /** [garments], each with its own shapes painted on it (see [garmentShapes]), [runAll] running each one's pieces. */
    suspend fun painted(garments: List<SurfaceGarment>, runAll: RunAll): List<SurfaceGarment> {
        val made = garments.map { garment ->
            val paint = kept.firstOrNull { it.first === garment }?.second ?: run {
                val shapes = garmentShapes(garment)
                if (shapes.isEmpty()) null else SurfacePaint.of(garment.surface, shapes, runAll).within(garment.shownValues, garment.surface)
            }
            garment to paint
        }
        val keep = ArrayList<Pair<SurfaceGarment, SurfacePaint?>>()
        for (each in made + kept) if (keep.size < KEPT_PAINTS && keep.none { it.first === each.first }) keep += each
        kept = keep
        return made.map { (garment, paint) -> if (paint == null) garment else garment.painted(paint) }
    }
}
