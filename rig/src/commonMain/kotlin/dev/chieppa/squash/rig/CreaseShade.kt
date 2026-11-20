package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.document.Role

/** The shade at which a crease's or a fold's band shows, and what a speck is lowered to so it shows none. */
internal const val BAND_FROM = 0.5f
private const val UNDER_BAND = 0.49f

/**
 * Which vertices of [surface]'s drawn mesh skin creases may shade: all but those of hair, which lies over the body and never creases with
 * it; a quad's middle where all four of its corners may.
 */
fun skinCreasable(surface: CharacterSurface): BooleanArray {
    val mesh = surface.mesh
    val corners = mesh.vertexCount
    val runs = surface.field.runs
    val hair = BooleanArray(runs.size) { runs[it].part.role == Role.HAIR }
    val count = surface.render.vertexCount
    val triangles = mesh.triangles
    val kept = BooleanArray(count) { v -> v >= corners || mesh.winners[v].let { it < 0 || !hair[it] } }
    for (v in corners until count) {
        val q = v - corners
        if (q * QUAD_INDICES + QUAD_CORNERS.last() >= triangles.size) continue
        kept[v] = QUAD_CORNERS.all { k -> kept[triangles[q * QUAD_INDICES + k]] }
    }
    return kept
}

/**
 * [shade] with every island of band, the vertices at or over [BAND_FROM] joined along [triangles]' edges, of fewer than [least] vertices
 * lowered under it, so no speck of band shows; [shade] itself where none is that small.
 */
fun withoutSpecks(shade: FloatArray, triangles: IntArray, least: Int): FloatArray {
    val count = shade.size
    val parent = IntArray(count) { it }
    fun root(v: Int): Int {
        var at = v
        while (parent[at] != at) {
            parent[at] = parent[parent[at]]
            at = parent[at]
        }
        return at
    }
    for (t in 0 until triangles.size / 3) {
        for (k in 0 until 3) {
            val a = triangles[t * 3 + k]
            val b = triangles[t * 3 + (k + 1) % 3]
            if (a >= count || b >= count || shade[a] < BAND_FROM || shade[b] < BAND_FROM) continue
            val ra = root(a)
            val rb = root(b)
            if (ra != rb) parent[ra] = rb
        }
    }
    val sizes = IntArray(count)
    for (v in 0 until count) if (shade[v] >= BAND_FROM) sizes[root(v)]++
    var lowered: FloatArray? = null
    for (v in 0 until count) {
        if (shade[v] < BAND_FROM || sizes[root(v)] >= least) continue
        val out = lowered ?: shade.copyOf().also { lowered = it }
        out[v] = UNDER_BAND
    }
    return lowered ?: shade
}
