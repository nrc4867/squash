package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.Vec3

/** How a [Refit] moved each corner it moved from linear blend, and how far it turned its normal, [turns], three floats a moved corner. */
internal class VertexShifts(private val refit: Refit, private val turns: FloatArray) {
    private val at = HashMap<Int, Int>(refit.moved.size * 2).also { map -> refit.moved.forEachIndexed { i, v -> map[v] = i } }

    /** Corner [v]'s move and its normal's turn, as differences to add; null where the refit left it. */
    fun of(v: Int): Pair<Vec3, Vec3>? {
        val i = at[v] ?: return null
        return refit.offsets.vec(i * 3) to turns.vec(i * 3)
    }
}
