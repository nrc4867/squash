package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.SurfaceDress
import dev.chieppa.squash.document.GARMENT_PART_PREFIX
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.isBranchPart
import dev.chieppa.squash.geom.surface.HELD

/** How far from a drawn vertex, in cells of the surface, the outer garment's surface is looked for. */
private const val REACH_CELLS = 4f

/**
 * How far along the outer garment's surface, in cells, a vertex may lie from the cloth nearest it and still be under it, rather than past
 * its hem or beside a narrow band.
 */
private const val BESIDE_CELLS = 1f

/** How far outside an outer garment's surface, in cells, a drawn vertex may lie before it counts as showing through. */
private const val SLACK_CELLS = 0.1f

/**
 * Surface of one layer drawn outside a garment over it, where that garment is drawn: [inner] (`body` or a garment's id) showing through
 * [outer] at [vertices] drawn vertices, [deepest] the furthest one lies outside the outer surface in body units, [parts] how many of those
 * vertices lie on each part and [along] from where to where along it, in its u.
 */
data class PokeThrough(val inner: String, val outer: String, val vertices: Int, val deepest: Float, val parts: Map<String, Int>, val along: Map<String, ClosedFloatingPointRange<Float>> = emptyMap())

/**
 * Where [dress], worn as posed on [body], shows a lower layer through a garment over it: each drawn vertex of the body's skin and of each
 * garment, on a part that garment covers, that lies outside its surface over that same part near a place where it is drawn and covers. A part the garment does
 * not cover, such as a hand hanging beside a skirt, is not counted. A garment worn in pieces counts as one. Only pairs that show through
 * anywhere are listed.
 */
fun pokeThrough(dress: SurfaceDress, body: CharacterSurface): List<PokeThrough> {
    val garments = dress.garments
    if (garments.isEmpty()) return emptyList()
    val skin = dress.on ?: body
    val layers = listOf(Layer("body", skin, dress.shown)) + garments.map { Layer(it.cover.garment.id.value, it.surface, it.shownValues) }
    val grids = garments.map { PointGrid(it.surface.positions, it.surface.mesh.cell) }
    val found = mutableListOf<PokeThrough>()
    for ((s, layer) in layers.withIndex()) {
        val cell = layer.surface.mesh.cell
        for (g in s until garments.size) {
            val outer = garments[g]
            val gp = outer.surface.positions
            val gn = outer.surface.lineNormals
            val shownOuter = outer.shownValues
            val outerLayer = layers[g + 1]
            var count = 0
            var deepest = 0f
            val parts = HashMap<String, Int>()
            val along = HashMap<String, ClosedFloatingPointRange<Float>>()
            val p = layer.surface.positions
            val covered = HashMap<String, Boolean>()
            for (v in 0 until layer.surface.mesh.vertexCount) {
                if (layer.shown != null && layer.shown[v] < 0f) continue
                val part = layer.partOf(v) ?: continue
                if (!covered.getOrPut(part) { outer.cover.clothOf(PartId(part)) != null || outer.cover.branchOf(PartId(part)) != null }) continue
                val q = grids[g].nearest(p[v * 3], p[v * 3 + 1], p[v * 3 + 2], cell * REACH_CELLS) {
                    outer.coverage[it] >= 0f && shownOuter[it] >= 0f && outerLayer.partOf(it) == part
                } ?: continue
                val inside = (gp[q * 3] - p[v * 3]) * gn[q * 3] + (gp[q * 3 + 1] - p[v * 3 + 1]) * gn[q * 3 + 1] + (gp[q * 3 + 2] - p[v * 3 + 2]) * gn[q * 3 + 2]
                if (inside >= -cell * SLACK_CELLS) continue
                val dx = gp[q * 3] - p[v * 3]
                val dy = gp[q * 3 + 1] - p[v * 3 + 1]
                val dz = gp[q * 3 + 2] - p[v * 3 + 2]
                if (dx * dx + dy * dy + dz * dz - inside * inside > cell * cell * BESIDE_CELLS * BESIDE_CELLS) continue
                count++
                deepest = maxOf(deepest, -inside)
                parts.merge(part, 1, Int::plus)
                val u = layer.uOf(v)
                along.merge(part, u..u) { a, b -> minOf(a.start, b.start)..maxOf(a.endInclusive, b.endInclusive) }
            }
            if (count > 0) found += PokeThrough(layer.name, outer.cover.garment.id.value, count, deepest, parts, along)
        }
    }
    return found.groupBy { it.inner to it.outer }.map { (key, pieces) ->
        val parts = HashMap<String, Int>()
        val along = HashMap<String, ClosedFloatingPointRange<Float>>()
        pieces.forEach { piece -> piece.parts.forEach { (part, count) -> parts.merge(part, count, Int::plus) } }
        pieces.forEach { piece -> piece.along.forEach { (part, span) -> along.merge(part, span) { a, b -> minOf(a.start, b.start)..maxOf(a.endInclusive, b.endInclusive) } } }
        PokeThrough(key.first, key.second, pieces.sumOf { it.vertices }, pieces.maxOf { it.deepest }, parts, along)
    }
}

/** One layer: its [surface], and which of its vertices are drawn, at least zero in [shown], or all of them when null. */
private class Layer(val name: String, val surface: CharacterSurface, val shown: FloatArray?) {
    private val runs = surface.field.runs

    /** The part vertex [v] lies on, a garment's branch or cuff named by the body part it lies over. */
    fun partOf(v: Int): String? {
        val winner = surface.mesh.winners[v]
        if (winner < 0) return null
        val id = runs[winner].partId
        return if (isBranchPart(id)) id.value.removePrefix(GARMENT_PART_PREFIX).split('/').getOrElse(1) { id.value } else id.value
    }

    /** Where vertex [v] lies along the part it lies on, in its u. */
    fun uOf(v: Int): Float {
        val mesh = surface.mesh
        val winner = mesh.winners[v]
        for (k in 0 until HELD) if (mesh.held[v * HELD + k] == winner) return mesh.heldU[v * HELD + k]
        return Float.NaN
    }
}
