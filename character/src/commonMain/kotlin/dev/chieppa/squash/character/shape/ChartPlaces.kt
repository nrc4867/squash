package dev.chieppa.squash.character.shape

import dev.chieppa.squash.document.CHART_CELLS
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.pointAt
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3

/**
 * Where the middle of each cell of a body's [chart] lies at rest, on each side of a pair: what a brush on the body or a shape drawn on
 * screen reaches. General: knows nothing of what is painted.
 */
class ChartPlaces private constructor(val chart: BodyChart, private val sides: List<PlacedSide>) {
    /** The cells of one side of a part, read as its leader's [part], at [xyz], three floats a cell, NaN where a cell lies nowhere. */
    private class PlacedSide(val part: dev.chieppa.squash.document.PartId, val xyz: FloatArray)

    /** The cells whose middle lies within [radius] of [center] on either side of a pair. */
    fun within(center: Vec3, radius: Float): SurfaceMask = reached { x, y, z ->
        val dx = x - center.x
        val dy = y - center.y
        val dz = z - center.z
        dx * dx + dy * dy + dz * dz <= radius * radius
    }

    /** The cells within [radius] of any of [centers]. */
    fun within(centers: List<Vec3>, radius: Float): SurfaceMask = reached { x, y, z ->
        centers.any { c ->
            val dx = x - c.x
            val dy = y - c.y
            val dz = z - c.z
            dx * dx + dy * dy + dz * dz <= radius * radius
        }
    }

    /**
     * Each side's ring of [part]'s cells at [row], a pair's on both sides: the middle of the ring's cells, and how far they lie from it on
     * average.
     */
    fun rings(part: dev.chieppa.squash.document.PartId, row: Int): List<Pair<Vec3, Float>> = ringPoints(part, row).map { points ->
        val middle = points.fold(Vec3.ZERO) { sum, p -> sum + p } * (1f / points.size)
        middle to points.sumOf { (it - middle).length.toDouble() }.toFloat() / points.size
    }

    /** Each side's ring of [part]'s cells at [row], a pair's on both sides: the middles of the ring's cells that lie somewhere, round it in order. */
    fun ringPoints(part: dev.chieppa.squash.document.PartId, row: Int): List<List<Vec3>> = sides.filter { it.part == chart.leaderOf(part) }.mapNotNull { side ->
        (0 until CHART_COLUMNS).mapNotNull { column ->
            val cell = row * CHART_COLUMNS + column
            val x = side.xyz[cell * 3]
            if (x.isNaN()) null else Vec3(x, side.xyz[cell * 3 + 1], side.xyz[cell * 3 + 2])
        }.ifEmpty { null }
    }

    /** The cells whose middle [project] puts on screen where [inside] holds, through the whole body, front and back alike. */
    fun inside(project: (Vec3) -> Vec2?, inside: (Vec2) -> Boolean): SurfaceMask = reached { x, y, z -> project(Vec3(x, y, z))?.let(inside) == true }

    /** The cells of the part (a pair's leader) whose middle lies where [test] holds, on either side of a pair. */
    fun where(test: (dev.chieppa.squash.document.PartId, Vec3) -> Boolean): SurfaceMask {
        val held = HashMap<dev.chieppa.squash.document.PartId, BooleanArray>()
        for (side in sides) {
            val cells = held.getOrPut(side.part) { BooleanArray(CHART_CELLS) }
            for (cell in 0 until CHART_CELLS) {
                if (cells[cell]) continue
                val x = side.xyz[cell * 3]
                if (x.isNaN()) continue
                if (test(side.part, Vec3(x, side.xyz[cell * 3 + 1], side.xyz[cell * 3 + 2]))) cells[cell] = true
            }
        }
        return SurfaceMask.build(chart) { part, cell -> held[part]?.get(cell) == true }
    }

    private fun reached(test: (Float, Float, Float) -> Boolean): SurfaceMask = where { _, p -> test(p.x, p.y, p.z) }

    companion object {
        /** The places of [chart]'s cells on [body], each side of a pair placed apart. */
        fun of(chart: BodyChart, body: AssembledCharacter): ChartPlaces {
            val sides = body.parts.mapNotNull { placed ->
                val id = placed.id ?: return@mapNotNull null
                if (placed.covering != null || placed.hanging != null || placed.spanning != null || placed.locks != null) return@mapNotNull null
                val part = chart.part(id) ?: return@mapNotNull null
                val xyz = FloatArray(CHART_CELLS * 3) { Float.NaN }
                var any = false
                for (cell in 0 until CHART_CELLS) {
                    val point = placed.surface.pointAt(cellMiddle(id, part.patch, cell))?.position ?: continue
                    val at = placed.origin + point
                    xyz[cell * 3] = at.x
                    xyz[cell * 3 + 1] = at.y
                    xyz[cell * 3 + 2] = at.z
                    any = true
                }
                if (any) PlacedSide(part.id, xyz) else null
            }
            return ChartPlaces(chart, sides)
        }
    }
}
