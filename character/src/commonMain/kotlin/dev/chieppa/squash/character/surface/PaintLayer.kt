package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.ChartAlmond
import dev.chieppa.squash.geom.ChartOval
import dev.chieppa.squash.geom.ChartShape
import dev.chieppa.squash.geom.LevelCut
import dev.chieppa.squash.geom.RenderMesh

/**
 * A fill drawn to a shape read at each fragment: each vertex of the surface's drawn mesh at ([u], [v]) in the shape's chart, the fill kept
 * inside [opening], turned [turn] degrees, and, where given, [oval], turned [ovalTurn] degrees; its layer's mesh reaches [grow] past
 * where its values cut it, so the drawn shape's whole edge lies on it.
 */
class LayerFill(val u: FloatArray, val v: FloatArray, val opening: ChartAlmond, val oval: ChartOval?, val turn: Float, val ovalTurn: Float, val grow: Float)

/**
 * One color layer on the one surface: [slot] over [cut], laid as [mesh] on the surface it was cut from, with each of its vertices' share
 * of paired parts on either side of the body's plane, [positive] and [negative], null where it holds none. A [flat] layer shows its
 * slot's color unshaded, as an eye's fills do. It keeps the [values] it was cut at over its [candidates] triangles, which are triangles of
 * [indices], the drawn mesh's index array it was cut on: a posed surface may leave out quads, so its own indices number its triangles
 * differently. A layer with a
 * [fill] is drawn to its [shape], read at each fragment over a cut grown past its values.
 */
class PaintLayer(
    val slot: SlotName?,
    val cut: LevelCut,
    val mesh: RenderMesh,
    val positive: FloatArray?,
    val negative: FloatArray?,
    val flat: Boolean = false,
    val values: FloatArray = FloatArray(0),
    val candidates: IntArray = IntArray(0),
    val fill: LayerFill? = null,
    val shape: ChartShape? = null,
    val indices: IntArray = IntArray(0),
) {
    /** This layer laid on [render], the same surface moved. */
    fun on(render: RenderMesh): PaintLayer = PaintLayer(slot, cut, cut.mesh(render), positive, negative, flat, values, candidates, fill, shape, indices)

    /** This layer where [kept] is at least zero too, one value a vertex of [surface]'s drawn mesh, cut over the triangles of the mesh it was first cut on and laid on [render]; null where none is. */
    fun within(kept: FloatArray, surface: CharacterSurface, render: RenderMesh): PaintLayer? =
        of(slot, surface, indices, FloatArray(values.size) { minOf(values[it], kept[it]) }, candidates, flat, fill)?.on(render)

    companion object {
        /**
         * [slot] where [values], one a vertex of [surface]'s drawn mesh, are at least zero, over [candidates]; null where none is. With a
         * [fill], where they are at least the fill's grow below zero, drawn to the fill's shape.
         */
        fun of(slot: SlotName?, surface: CharacterSurface, values: FloatArray, candidates: IntArray, flat: Boolean = false, fill: LayerFill? = null): PaintLayer? =
            of(slot, surface, surface.render.indices, values, candidates, flat, fill)

        /** As [of], with [candidates] triangles of [indices], the index array of the drawn mesh they were found on, which [surface] may not share. */
        private fun of(slot: SlotName?, surface: CharacterSurface, indices: IntArray, values: FloatArray, candidates: IntArray, flat: Boolean, fill: LayerFill?): PaintLayer? {
            if (fill != null) {
                val cut = LevelCut.of(FloatArray(values.size) { values[it] + fill.grow }, indices, candidates)
                if (cut.isEmpty) return null
                val u = cut.values(fill.u)
                val v = cut.values(fill.v)
                val chart = FloatArray(u.size * 2) { if (it % 2 == 0) u[it / 2] else v[it / 2] }
                val shape = ChartShape(chart, fill.opening, fill.oval, fill.turn, fill.ovalTurn)
                return PaintLayer(slot, cut, cut.mesh(surface.render), null, null, flat, values, candidates, fill, shape, indices)
            }
            val cut = LevelCut.of(values, indices, candidates)
            if (cut.isEmpty) return null
            if (flat) return PaintLayer(slot, cut, cut.mesh(surface.render), null, null, flat = true, values = values, candidates = candidates, indices = indices)
            return PaintLayer(slot, cut, cut.mesh(surface.render), sides(cut, surface.positiveZ), sides(cut, surface.negativeZ), values = values, candidates = candidates, indices = indices)
        }

        private fun sides(cut: LevelCut, shares: FloatArray): FloatArray? = cut.values(shares).takeIf { values -> values.any { it > 0f } }
    }
}
