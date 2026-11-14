package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.ClippedSolid
import dev.chieppa.squash.geom.field.Solid

/** How far past the rings it keeps, in cells, a run a garment covers part of is kept. */
private const val CLIP_CELLS = 6f

/**
 * Runs a garment covers only part of, kept to a box round the rings it covers, one ring more each way and [CLIP_CELLS] cells past, so a
 * garment is meshed only near what it covers; each clip kept while its solid and its rings are the same, so its member reads the same.
 */
class RunClips {
    private var kept: Map<List<Any>, Solid> = emptyMap()
    private var used = HashMap<List<Any>, Solid>()

    /** [solid], swept from [patch], kept to its rings between [from] and [to] in u; itself where those are every ring. */
    fun of(solid: Solid, patch: Patch, from: Float, to: Float, cell: Float): Solid {
        val us = patch.ringUs
        val first = (us.indexOfLast { it <= from }).coerceAtLeast(0).let { (it - 1).coerceAtLeast(0) }
        val last = us.indexOfFirst { it >= to }.let { if (it < 0) patch.nu - 1 else (it + 1).coerceAtMost(patch.nu - 1) }
        if (first == 0 && last == patch.nu - 1) return solid
        val key = listOf(Same(solid), first, last, cell)
        return used.getOrPut(key) { kept[key] ?: clipped(solid, patch, first, last, cell * CLIP_CELLS) }
    }

    /** Keeps only what the last build used. */
    fun settle() {
        kept = used
        used = HashMap()
    }

    private fun clipped(solid: Solid, patch: Patch, first: Int, last: Int, margin: Float): Solid {
        var lx = Float.MAX_VALUE
        var ly = Float.MAX_VALUE
        var lz = Float.MAX_VALUE
        var hx = -Float.MAX_VALUE
        var hy = -Float.MAX_VALUE
        var hz = -Float.MAX_VALUE
        for (ring in first..last) for (column in 0 until patch.nv) {
            val p = patch.point(patch.index(ring, column))
            lx = minOf(lx, p.x)
            ly = minOf(ly, p.y)
            lz = minOf(lz, p.z)
            hx = maxOf(hx, p.x)
            hy = maxOf(hy, p.y)
            hz = maxOf(hz, p.z)
        }
        val low = Vec3(maxOf(lx - margin, solid.low.x), maxOf(ly - margin, solid.low.y), maxOf(lz - margin, solid.low.z))
        val high = Vec3(minOf(hx + margin, solid.high.x), minOf(hy + margin, solid.high.y), minOf(hz + margin, solid.high.z))
        return ClippedSolid(solid, low, high)
    }
}
