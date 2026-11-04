package dev.chieppa.squash.geom

import kotlin.math.floor

/**
 * A shape cut to a patch's mesh: [mesh]'s triangles lie exactly on the part's mesh triangles, wound as they are, and each of its
 * vertices keeps its place on [patch] in [us] and [vs]. Never changes, so a frame can hand it to the render thread.
 */
class PatchCut(val patch: String, val mesh: RenderMesh, val us: FloatArray, val vs: FloatArray) {
    val isEmpty: Boolean get() = mesh.indices.isEmpty()
}

/** What [shape] covers of [grid]'s surface, cut along the mesh's triangles. Independent of every other shape. */
fun cutShape(shape: ColorShape, grid: PatchGrid, tolerance: Float, checkCancelled: () -> Unit = {}): PatchCut {
    val pieces = GridPieces(grid.rings, grid.columns, grid.closed, grid::isPoleCell)
    shape.outlines(grid, tolerance).forEach(pieces::add)
    val out = CutOutput(grid)
    for (column in pieces.columnsWithPieces) {
        checkCancelled()
        addColumn(column, pieces, grid, out)
    }
    return out.built()
}

/**
 * Adds what is filled of [column]. Below the column's lowest piece nothing is, since the winding counts pieces at lower u; past its
 * highest piece the winding holds, so the walk stops there unless it is filled.
 */
private fun addColumn(column: Int, pieces: GridPieces, grid: PatchGrid, out: CutOutput) {
    val inColumn = pieces.piecesIn(column)
    val lowest = inColumn.minOf { minOf(it.fromU, it.toU) }
    val highest = inColumn.maxOf { maxOf(it.fromU, it.toU) }
    val middleV = column + 0.5
    val filledPast = pieces.windingAt(highest + 1.0, middleV) != 0
    val firstRing = floor(lowest).toInt().coerceAtLeast(0)
    val lastRing = if (filledPast) grid.rings - 2 else floor(highest).toInt().coerceAtMost(grid.rings - 2)
    for (ring in firstRing..lastRing) {
        if (grid.isPoleCell(ring)) {
            addPart(ring, column, CellPart.WHOLE, pieces, out)
        } else {
            addPart(ring, column, CellPart.LOWER, pieces, out)
            addPart(ring, column, CellPart.UPPER, pieces, out)
        }
    }
}

private fun addPart(ring: Int, column: Int, part: CellPart, pieces: GridPieces, out: CutOutput) {
    val fill = TriangleFill(pieces, ring, column, part)
    val triangles = fill.filledTriangles()
    var corner = 0
    while (corner < triangles.size) {
        val a = out.vertex(fill.xs[triangles[corner]], fill.ys[triangles[corner]])
        val b = out.vertex(fill.xs[triangles[corner + 1]], fill.ys[triangles[corner + 1]])
        val c = out.vertex(fill.xs[triangles[corner + 2]], fill.ys[triangles[corner + 2]])
        out.triangle(a, b, c)
        corner += 3
    }
}

/** The cut's growing buffers, with each place on the grid held once, so that neighbouring triangles share its vertex. */
private class CutOutput(private val grid: PatchGrid) {
    private var vertices = FloatArray(INITIAL_VERTICES * RenderMesh.FLOATS_PER_VERTEX)
    private var us = FloatArray(INITIAL_VERTICES)
    private var vs = FloatArray(INITIAL_VERTICES)
    private var count = 0
    private var indices = IntArray(INITIAL_VERTICES * 3)
    private var indexCount = 0
    private val held = HashMap<Place, Int>()

    private data class Place(val u: Double, val v: Double)

    fun vertex(gridU: Double, gridV: Double): Int {
        val around = if (grid.closed && gridV >= grid.columns) gridV - grid.columns else gridV
        return held.getOrPut(Place(gridU, around)) {
            if (count == us.size) grow()
            grid.writeVertex(gridU, around, vertices, count * RenderMesh.FLOATS_PER_VERTEX)
            us[count] = grid.uAt(gridU)
            vs[count] = grid.vAt(around)
            count++
        }
    }

    private fun grow() {
        vertices = vertices.copyOf(vertices.size * 2)
        us = us.copyOf(us.size * 2)
        vs = vs.copyOf(vs.size * 2)
    }

    fun triangle(a: Int, b: Int, c: Int) {
        if (a == b || b == c || c == a) return
        if (indexCount + 3 > indices.size) indices = indices.copyOf(indices.size * 2)
        indices[indexCount++] = a
        indices[indexCount++] = if (grid.mirrored) c else b
        indices[indexCount++] = if (grid.mirrored) b else c
    }

    fun built() = PatchCut(
        grid.patch.name,
        RenderMesh(vertices.copyOf(count * RenderMesh.FLOATS_PER_VERTEX), indices.copyOf(indexCount)),
        us.copyOf(count),
        vs.copyOf(count),
    )

    private companion object {
        const val INITIAL_VERTICES = 64
    }
}
