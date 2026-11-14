package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.CurvePoint
import dev.chieppa.squash.geom.LevelCut
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.TriangleLines
import dev.chieppa.squash.geom.collections.IntList
import dev.chieppa.squash.geom.surface.HELD

/**
 * One part's own section of a [CharacterSurface]: where that part's own field is lower than every other part's the vertex holds.
 * [values] holds, one a vertex of the surface's drawn mesh, half how much lower, held within the surface chart's reach, so the section is
 * where it is at least zero and its border the curve where the part shares the surface equally with a neighbour; a quad's middle takes
 * the mean of its corners.
 */
class SurfaceSection(val surface: CharacterSurface, val part: PartId) {
    val values: FloatArray = valuesOf(surface, part)

    /** The section cut from the surface's drawn mesh along its border. */
    val cut: LevelCut by lazy { LevelCut.of(values, surface.render.indices) }

    /** [layer] kept to the section, laid on [render]; null where none of it lies there. */
    fun layer(layer: PaintLayer, render: RenderMesh = surface.render): PaintLayer? = layer.within(values, surface, render)

    /** The curves where the section meets its neighbours, on [lines], the surface's lines as it shows. */
    fun border(lines: TriangleLines = surface.lines): List<List<CurvePoint>> = lines.zeroCurves(values) { true }.map(::eased)

    /** The surface's triangles, two a quad, with a corner in the section, as [TriangleLines] reads them. */
    val triangles: IntArray by lazy {
        val t = surface.mesh.triangles
        val out = IntList()
        for (tri in 0 until t.size / 3) {
            if (values[t[tri * 3]] >= 0f || values[t[tri * 3 + 1]] >= 0f || values[t[tri * 3 + 2]] >= 0f) {
                out.add(t[tri * 3])
                out.add(t[tri * 3 + 1])
                out.add(t[tri * 3 + 2])
            }
        }
        out.toArray()
    }

    private companion object {
        fun valuesOf(surface: CharacterSurface, part: PartId): FloatArray {
            val mesh = surface.mesh
            val runs = surface.field.runs
            val corners = mesh.vertexCount
            val reach = surface.chart.reach
            val indices = surface.render.indices
            val out = FloatArray(surface.render.vertexCount)
            for (v in 0 until corners) {
                var own = Float.MAX_VALUE
                var other = Float.MAX_VALUE
                for (k in 0 until HELD) {
                    val member = mesh.held[v * HELD + k]
                    if (member < 0) continue
                    val value = mesh.heldOwn[v * HELD + k]
                    if (runs[member].partId == part) own = minOf(own, value) else other = minOf(other, value)
                }
                out[v] = when {
                    own == Float.MAX_VALUE -> -reach
                    other == Float.MAX_VALUE -> reach
                    else -> ((other - own) * 0.5f).coerceIn(-reach, reach)
                }
            }
            for (tri in 0 until indices.size / 3) {
                val middle = indices[tri * 3 + 2]
                if (middle < corners) continue
                val quad = tri / 4
                var sum = 0f
                for (k in 0 until 4) sum += out[indices[(quad * 4 + k) * 3]]
                out[middle] = sum / 4f
            }
            return out
        }
    }
}
