package dev.chieppa.squash.screens.dress

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.character.dress.freshNumbered
import dev.chieppa.squash.character.surface.CLOTH_HEM_LEVEL
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.MIN_BORDER_POINTS
import dev.chieppa.squash.document.MIN_MARK_POINTS
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SetGarmentMark
import dev.chieppa.squash.document.SetGarmentRegion
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.WidthStop
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.squash.screens.sheet.dressThrough
import dev.chieppa.squash.screens.sheet.surfaceThrough
import dev.chieppa.squash.screens.surface.SurfaceHit
import dev.chieppa.squash.screens.surface.SurfaceMeshPick

/** What starts the ids of the regions and the lines a garment's cloth prints. */
const val PRINT_REGION_PREFIX = "print-"
const val PRINT_MARK_PREFIX = "line-"

/**
 * The place on [garment]'s cloth showing at [pixel] through [sight], as the body's part, u and v under it, which a print is anchored by;
 * null where the garment is not worn, its cloth is not under the pixel, or the body stands in front of it there.
 */
fun AppState.garmentHit(garment: GarmentId, pixel: Offset, sight: SurfaceView): SurfaceHit? {
    val worn = dressThrough(sight)?.garments?.firstOrNull { it.cover.garment.id == garment } ?: return null
    val (hit, depth) = SurfaceMeshPick(worn.surface, sight).hit(pixel) ?: return null
    if ((worn.cover.clothOf(hit.part)?.at(hit.u, hit.v) ?: 0f) < CLOTH_HEM_LEVEL) return null
    val body = surfaceThrough(sight)?.let { SurfaceMeshPick(it, sight).hit(pixel, limit = depth) }
    return if (body == null) hit else null
}

/** The command printing a region with [border] on [garment] in [slot], inked when [ink], on both sides of the body when [mirrored]; null with too few places. */
fun printRegionCommand(garment: Garment, border: List<Anchor>, slot: SlotName, ink: Boolean, mirrored: Boolean): SetGarmentRegion? {
    if (border.size < MIN_BORDER_POINTS) return null
    val id = RegionId(freshNumbered(PRINT_REGION_PREFIX, garment.regions.map { it.id.value }.toSet()).first)
    return SetGarmentRegion(garment.id, id, Region(id, border, slot, ink = ink, mirrored = mirrored))
}

/**
 * The command printing a line along [points] on [garment] in [slot], the ink's when null, at [weight] of the line rule's weight or
 * by [width] where a pen's pressure gave one, on both sides of the body when [mirrored]; null with too few places.
 */
fun printMarkCommand(garment: Garment, points: List<Anchor>, slot: SlotName?, weight: Float, width: MarkWidth, mirrored: Boolean): SetGarmentMark? {
    if (points.size < MIN_MARK_POINTS) return null
    val id = MarkId(freshNumbered(PRINT_MARK_PREFIX, garment.marks.map { it.id.value }.toSet()).first)
    val weighed = if (width.stops.isEmpty()) MarkWidth(listOf(WidthStop(0f, weight), WidthStop(1f, weight))) else MarkWidth(width.stops.map { it.copy(scale = it.scale * weight) })
    return SetGarmentMark(garment.id, id, Mark(id, points, weighed, mirrored = mirrored, slot = slot))
}
