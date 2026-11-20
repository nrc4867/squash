package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.IN_TURN
import dev.chieppa.squash.character.surface.RunAll
import dev.chieppa.squash.character.surface.chunked
import dev.chieppa.squash.character.surface.SurfaceDress
import dev.chieppa.squash.character.surface.SurfaceGarment
import dev.chieppa.squash.character.surface.SurfacePaint
import dev.chieppa.squash.document.GARMENT_PART_PREFIX
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.isBranchPart
import dev.chieppa.squash.geom.LevelCut

/** How far inside a garment's posed surface, in cells, skin under its coverage still counts as having come out through it. */
private const val POKE_SLACK_CELLS = 0.5f

/** How far from a vertex, in cells, the garment over it is looked for. */
private const val POKE_REACH_CELLS = 4f

/** How many vertices a piece of the search for skin pushed through takes. */
private const val POKE_CHUNK = 2048

/**
 * The body and each garment as [dress] wears them on a posed [body] and posed [garments], with what the pose carries out through a
 * garment over it hidden too: a vertex inside the coverage of a garment over it at rest that the pose leaves outside that garment's
 * surface, or less than [POKE_SLACK_CELLS] cells inside it, is cut away along the curve where it comes out, and the body's paint with
 * it. The garment's surface is read where it is cloth and drawn, over the same body part the vertex lies on, so an arm posed against a torso is read against its
 * sleeve, never against the cloth over the torso beside it. [rest] is the body at rest, whose drawn mesh the paint's layers were cut on; posed, a pose may leave out quads where parts touch.
 * What coverage hides follows the pose by itself, since coverage reads coordinates; this finds what a pose pushes through the cloth.
 */
class PosedHiding(private val body: CharacterSurface, private val rest: CharacterSurface, private val garments: List<SurfaceGarment>, private val dress: SurfaceDress) {
    private val restIndices = rest.render.indices

    /** The body's paint kept to the skin that shows posed, and the dress as posed, the search for what came through run in pieces by [runAll]. */
    suspend fun of(paint: SurfacePaint?, runAll: RunAll = IN_TURN): Pair<SurfacePaint?, SurfaceDress> {
        val restShown = dress.shown
        if (restShown == null) return paint to SurfaceDress(garments, dress.skin, null, dress.under, body)
        if (garments.isEmpty() || dress.under.size != garments.size + 1) return paint to SurfaceDress(garments, recut(body, restShown), restShown, dress.under, body)
        val surfaces = listOf(body) + garments.map { it.surface }
        val near = garments.map { garment -> NearestVertex(garment.surface.positions, garment.surface.mesh.cell) }
        val parts = (listOf(body) + garments.map { it.surface }).map(::bodyPartsOf)
        val shown = surfaces.mapIndexed { s, surface ->
            val base = if (s == 0) restShown else garments[s - 1].shownValues
            pushedThrough(surface, base, dress.under[s], (s until garments.size).toList(), near, parts, s, runAll)
        }
        val skin = recut(body, shown[0] ?: restShown)
        val worn = garments.mapIndexed { i, garment -> shown[i + 1]?.let { garment.within(LevelCut.of(it, garment.surface.render.indices), it) } ?: garment }
        val shownPaint = shown[0]?.let { values -> paint?.within(values, rest, body.render) } ?: paint
        return shownPaint to SurfaceDress(worn, skin, shown[0] ?: restShown, dress.under, body)
    }

    /**
     * The body's paint and the dress as posed with only what coverage hides hidden, nothing searched for that the pose pushes through a
     * garment: the dress's skin kept as it is at rest.
     */
    fun kept(paint: SurfacePaint?): Pair<SurfacePaint?, SurfaceDress> =
        paint to SurfaceDress(garments, dress.shown?.let { recut(body, it) } ?: dress.skin, dress.shown, dress.under, body)

    /** [surface]'s skin cut where [values] are at least zero, along its drawn mesh as posed: the rest cut where the pose keeps every quad and the values are the rest's. */
    private fun recut(surface: CharacterSurface, values: FloatArray): LevelCut? =
        if (values === dress.shown && surface.render.indices.contentEquals(restIndices)) dress.skin else LevelCut.of(values, surface.render.indices)

    /**
     * [base] lowered where [surface]'s vertices that lie inside the coverage of the garments [outer] over it, as [under] says, have come
     * out through one of them; null where none has.
     */
    private suspend fun pushedThrough(
        surface: CharacterSurface, base: FloatArray, under: FloatArray, outer: List<Int>, near: List<NearestVertex>, parts: List<Array<String?>>, s: Int, runAll: RunAll,
    ): FloatArray? {
        if (outer.isEmpty()) return null
        val cell = surface.mesh.cell
        val corners = surface.mesh.vertexCount
        val positions = surface.positions
        val lowered = base.copyOf()
        val lowers = BooleanArray((corners + POKE_CHUNK - 1) / POKE_CHUNK)
        runAll(chunked(corners, POKE_CHUNK) { from, until ->
            for (v in from until until) {
                if (under[v] <= 0f || base[v] < 0f) continue
                var least = Float.MAX_VALUE
                val part = parts[s][v]
                for (g in outer) {
                    val garment = garments[g]
                    val over = parts[g + 1]
                    val q = near[g].within(positions, v, cell * POKE_REACH_CELLS) { garment.coverage[it] >= 0f && garment.shownValues[it] >= 0f && (part == null || over[it] == part) } ?: continue
                    val gp = garment.surface.positions
                    val gn = garment.surface.lineNormals
                    val inside = (gp[q * 3] - positions[v * 3]) * gn[q * 3] + (gp[q * 3 + 1] - positions[v * 3 + 1]) * gn[q * 3 + 1] + (gp[q * 3 + 2] - positions[v * 3 + 2]) * gn[q * 3 + 2]
                    least = minOf(least, inside - cell * POKE_SLACK_CELLS)
                }
                if (least >= base[v]) continue
                lowered[v] = least
                lowers[from / POKE_CHUNK] = true
            }
        })
        val made = lowered.takeIf { lowers.any { it } } ?: return null
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

/** The body part each vertex of [surface] lies on, read from the run it is held by most: a garment's branch or cuff by the part under it. */
private fun bodyPartsOf(surface: CharacterSurface): Array<String?> {
    val runs = surface.field.runs.map { run -> bodyPartOf(run.partId) }
    val winners = surface.mesh.winners
    return Array(surface.mesh.vertexCount) { v -> winners[v].takeIf { it >= 0 }?.let { runs[it] } }
}

/** The body part [part] lies over: itself, or for a garment's branch or cuff, the part it covers. */
private fun bodyPartOf(part: PartId): String {
    if (!isBranchPart(part)) return part.value
    return part.value.removePrefix(GARMENT_PART_PREFIX).split('/').getOrElse(1) { part.value }
}
