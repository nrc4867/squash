package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.LandedShape
import dev.chieppa.squash.geom.surface.SurfaceMesh

/**
 * What a color shape was last laid at: its [distances] from the shape, and its [values], those eased off its runs' own surfaces and kept
 * to their sections, a vertex each.
 */
class LaidShape(val distances: FloatArray, val values: FloatArray)

/**
 * What each color shape and each run's section was last laid at on a one surface's mesh, kept between paintings so that a painting of the
 * next mesh takes them at every vertex that mesh keeps from this one (see [SurfaceMesh.change]) and reads only near the vertices an edit
 * meshed again.
 */
class PaintMemo {
    private var last: Last? = null

    private class Last(val mesh: SurfaceMesh, val shapes: Map<LandedShape, LaidShape>, val sections: Map<List<Int>, FloatArray>)

    /** What a painting of the next mesh can take from the last one: each shape's values and each run's section there, and the [carry]. */
    class Earlier(val shapes: Map<LandedShape, LaidShape>, val sections: Map<List<Int>, FloatArray>, val carry: PaintCarry)

    /** What the last painting leaves [chart]'s mesh, null where that mesh does not follow the one it painted. */
    fun earlier(chart: SurfaceChart): Earlier? {
        val held = last ?: return null
        val next = chart.mesh
        val previousOf = if (next === held.mesh) IntArray(next.vertexCount) { it } else next.change?.takeIf { it.previous === held.mesh }?.previousOf
        return previousOf?.let { Earlier(held.shapes, held.sections, PaintCarry.of(chart, it)) }
    }

    /** Keeps [shapes] and [sections], as laid on [painted]. */
    fun keep(painted: SurfaceMesh, shapes: Map<LandedShape, LaidShape>, sections: Map<List<Int>, FloatArray>) {
        last = Last(painted, shapes, sections)
    }
}
