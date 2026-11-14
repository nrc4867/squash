package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.SurfaceDress
import dev.chieppa.squash.character.surface.SurfaceGarment
import dev.chieppa.squash.character.surface.SurfacePaint
import dev.chieppa.squash.geom.LevelCut

/** How far inside a garment's posed surface, in cells, skin under its coverage still counts as having come out through it. */
private const val POKE_SLACK_CELLS = 0.5f

/** How far from a vertex, in cells, the garment over it is looked for. */
private const val POKE_REACH_CELLS = 4f

/**
 * The body and each garment as [dress] wears them on a posed [body] and posed [garments], with what the pose carries out through a
 * garment over it hidden too: a vertex inside the coverage of a garment over it at rest that the pose leaves outside that garment's
 * surface, or less than [POKE_SLACK_CELLS] cells inside it, is cut away along the curve where it comes out, and the body's paint with
 * it. [restIndices] are the body's drawn mesh's at rest. What coverage hides follows the pose by itself, since coverage reads coordinates; this finds what a pose pushes through the cloth.
 */
class PosedHiding(private val body: CharacterSurface, private val restIndices: IntArray, private val garments: List<SurfaceGarment>, private val dress: SurfaceDress) {
    /** The body's paint kept to the skin that shows posed, and the dress as posed. */
    fun of(paint: SurfacePaint?): Pair<SurfacePaint?, SurfaceDress> {
        val restShown = dress.shown
        if (restShown == null) return paint to SurfaceDress(garments, dress.skin, null, dress.under)
        if (garments.isEmpty() || dress.under.size != garments.size + 1) return paint to SurfaceDress(garments, recut(body, restShown), restShown, dress.under)
        val surfaces = listOf(body) + garments.map { it.surface }
        val near = garments.map { garment -> NearestVertex(garment.surface.positions, garment.surface.mesh.cell) }
        val shown = surfaces.mapIndexed { s, surface ->
            val base = if (s == 0) restShown else garments[s - 1].shownValues
            pushedThrough(surface, base, dress.under[s], (s until garments.size).toList(), near)
        }
        val skin = recut(body, shown[0] ?: restShown)
        val worn = garments.mapIndexed { i, garment -> shown[i + 1]?.let { garment.within(LevelCut.of(it, garment.surface.render.indices), it) } ?: garment }
        val shownPaint = shown[0]?.let { values -> paint?.within(values, body) } ?: paint
        return shownPaint to SurfaceDress(worn, skin, shown[0] ?: restShown, dress.under)
    }

    /** [surface]'s skin cut where [values] are at least zero, along its drawn mesh as posed: the rest cut where the pose keeps every quad and the values are the rest's. */
    private fun recut(surface: CharacterSurface, values: FloatArray): LevelCut? =
        if (values === dress.shown && surface.render.indices.contentEquals(restIndices)) dress.skin else LevelCut.of(values, surface.render.indices)

    /**
     * [base] lowered where [surface]'s vertices that lie inside the coverage of the garments [outer] over it, as [under] says, have come
     * out through one of them; null where none has.
     */
    private fun pushedThrough(surface: CharacterSurface, base: FloatArray, under: FloatArray, outer: List<Int>, near: List<NearestVertex>): FloatArray? {
        if (outer.isEmpty()) return null
        val cell = surface.mesh.cell
        val corners = surface.mesh.vertexCount
        val positions = surface.positions
        var out: FloatArray? = null
        for (v in 0 until corners) {
            if (under[v] <= 0f || base[v] < 0f) continue
            var least = Float.MAX_VALUE
            for (g in outer) {
                val garment = garments[g]
                val q = near[g].within(positions, v, cell * POKE_REACH_CELLS) ?: continue
                if (garment.coverage[q] < 0f) continue
                val gp = garment.surface.positions
                val gn = garment.surface.lineNormals
                val inside = (gp[q * 3] - positions[v * 3]) * gn[q * 3] + (gp[q * 3 + 1] - positions[v * 3 + 1]) * gn[q * 3 + 1] + (gp[q * 3 + 2] - positions[v * 3 + 2]) * gn[q * 3 + 2]
                least = minOf(least, inside - cell * POKE_SLACK_CELLS)
            }
            if (least >= base[v]) continue
            val lowered = out ?: base.copyOf().also { out = it }
            lowered[v] = least
        }
        val made = out ?: return null
        val indices = surface.render.indices
        for (tri in 0 until indices.size / 3) {
            val middle = indices[tri * 3 + 2]
            if (middle < corners || tri % 4 != 0) continue
            var sum = 0f
            for (k in 0 until 4) sum += made[indices[(tri + k) * 3]]
            made[middle] = minOf(base[middle], sum / 4f)
        }
        return made
    }
}
