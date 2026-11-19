package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.LandedShape
import dev.chieppa.squash.character.build.RANK_REGION
import dev.chieppa.squash.character.build.ResolvedMark
import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.character.build.ShapeSource
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.WidthStop
import dev.chieppa.squash.document.mirrorOf
import dev.chieppa.squash.geom.ColorShape
import dev.chieppa.squash.geom.SurfaceUv
import dev.chieppa.squash.geom.UvShapeDistance
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/** How many places a mark printed on cloth takes along each cell of its part's chart it crosses, so it can end where the cloth does. */
private const val PLACES_A_CELL = 2

/** What starts the id of the ink line along a printed region's border. */
private const val BORDER_PREFIX = "border:"

/** A line of one weight from end to end, as a region's border is inked. */
private val EVEN = MarkWidth(listOf(WidthStop(0f, 1f), WidthStop(1f, 1f)))

/**
 * What a garment prints on its cloth: its [regions] and [marks], each mirrored one followed by its other side, read in the (u, v) of the
 * body parts under the cloth, as the garment's vertices carry them.
 */
data class GarmentPrints(val regions: List<Region> = emptyList(), val marks: List<Mark> = emptyList()) {
    val isEmpty: Boolean get() = regions.isEmpty() && marks.isEmpty()

    /**
     * The color shapes these prints lay on [part]'s run [patch]: each region's border places on it, three or more, as a shape there; a
     * region whose border runs across parts is laid on each part from its own places.
     */
    fun shapesOn(part: PartId, patch: String): List<LandedShape> = regions.mapNotNull { region ->
        val on = region.border.filter { it.part == part && it.patch == patch }
        if (on.size < MIN_REGION_PLACES) return@mapNotNull null
        val corners = if (on.size == region.border.size) region.corners else emptySet()
        LandedShape(RANK_REGION, region.slot, part, patch, ColorShape.Border(on.map { SurfaceUv(it.u, it.v) }, corners = corners, curved = region.curved), ShapeSource.Border(part, region.id))
    }

    /**
     * The ink lines these prints draw, as marks whose places name only their anchors, to be resolved on the cloth: each inked mark,
     * with places every half cell between two of its anchors on one patch of one part, and each inked region's border along its edge,
     * of one weight; each by the part its first place names. [closedV] says whether a part's patch wraps round in v.
     */
    fun lines(closedV: (PartId, String) -> Boolean): Map<PartId, List<ResolvedMark>> {
        val out = LinkedHashMap<PartId, MutableList<ResolvedMark>>()
        for (mark in marks) {
            if (!mark.ink || mark.kind != MarkKind.LINE) continue
            val places = densified(mark.points, closedV)
            out.getOrPut(places.first().part) { ArrayList() } += ResolvedMark(mark.id, places.map(::unplaced), mark.width, true, false, emptyMap(), MarkKind.LINE, mark.id, mark.slot)
        }
        for (region in regions) {
            if (!region.ink) continue
            val places = borderLine(region, closedV)
            if (places.size < 2) continue
            val id = MarkId(BORDER_PREFIX + region.id.value)
            out.getOrPut(places.first().part) { ArrayList() } += ResolvedMark(id, places.map(::unplaced), EVEN, true, false, emptyMap(), MarkKind.LINE, id)
        }
        return out
    }

    companion object {
        private const val MIN_REGION_PLACES = 3

        /** What [garment] prints, its mirrored regions and marks followed by their other sides on [character]. */
        fun of(garment: Garment, character: Character): GarmentPrints {
            if (garment.regions.isEmpty() && garment.marks.isEmpty()) return GarmentPrints()
            return GarmentPrints(
                garment.regions.flatMap { if (it.mirrored) listOf(it, character.mirrorOf(it)) else listOf(it) },
                garment.marks.flatMap { if (it.mirrored) listOf(it, character.mirrorOf(it)) else listOf(it) },
            )
        }
    }
}

/**
 * [marks] cut to where [onCloth] holds of their places, each run of two or more places kept as a mark of its own: a line printed on cloth
 * shows only on the cloth.
 */
fun clothCut(marks: Map<PartId, List<ResolvedMark>>, onCloth: (Anchor) -> Boolean): Map<PartId, List<ResolvedMark>> = marks.mapValues { (_, held) ->
    held.flatMap { mark ->
        val runs = ArrayList<List<ResolvedPoint>>()
        var run = ArrayList<ResolvedPoint>()
        for (point in mark.points) {
            if (onCloth(point.anchor)) {
                run += point
            } else {
                if (run.size >= 2) runs += run
                run = ArrayList()
            }
        }
        if (run.size >= 2) runs += run
        if (runs.size == 1 && runs.single().size == mark.points.size) listOf(mark) else runs.map { mark.copy(points = it) }
    }
}

private fun unplaced(anchor: Anchor) = ResolvedPoint(anchor, Vec3.ZERO, Vec3.ZERO)

/** [anchors] with places every half cell of the chart between each two on one patch of one part, straight in u and v. */
private fun densified(anchors: List<Anchor>, closedV: (PartId, String) -> Boolean): List<Anchor> {
    val out = ArrayList<Anchor>()
    anchors.forEachIndexed { i, anchor ->
        val previous = anchors.getOrNull(i - 1)
        if (previous != null && previous.part == anchor.part && previous.patch == anchor.patch) {
            val dv = if (closedV(anchor.part, anchor.patch)) wrapped(anchor.v - previous.v) else anchor.v - previous.v
            val du = anchor.u - previous.u
            val steps = ceil(maxOf(abs(du) * CHART_ROWS, abs(dv) * CHART_COLUMNS) * PLACES_A_CELL).toInt().coerceAtLeast(1)
            for (step in 1 until steps) {
                val t = step.toFloat() / steps
                val v = previous.v + dv * t
                out += anchor.copy(u = previous.u + du * t, v = v - floor(v), offset = previous.offset + (anchor.offset - previous.offset) * t)
            }
        }
        out += anchor
    }
    return out
}

/**
 * The places along [region]'s border edge, closed: on one patch of one part, the curve its fill follows (see [UvShapeDistance.edge]),
 * with places every half cell; across parts, its anchors with places between those on one patch, back to its first.
 */
private fun borderLine(region: Region, closedV: (PartId, String) -> Boolean): List<Anchor> {
    val first = region.border.first()
    if (region.border.all { it.part == first.part && it.patch == first.patch }) {
        val closed = closedV(first.part, first.patch)
        val shape = ColorShape.Border(region.border.map { SurfaceUv(it.u, it.v) }, corners = region.corners, curved = region.curved)
        val edge = UvShapeDistance(shape, closed).edge().map { first.copy(u = it.u, v = it.v - floor(it.v)) }
        return densified(edge, closedV)
    }
    return densified(region.border + first, closedV)
}

private fun wrapped(dv: Float): Float = dv - round(dv)
