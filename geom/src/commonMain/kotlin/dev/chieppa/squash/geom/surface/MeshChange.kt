package dev.chieppa.squash.geom.surface

import dev.chieppa.squash.geom.field.JoinedField

/** Blocks one piece of matching work takes. */
private const val MATCH_CHUNK = 64

/**
 * Which vertices of a mesh are vertices of [previous], the mesh meshed before it: [previousOf] holds each vertex's index there, -1 for a
 * vertex that is new or holds anything differently, so work laid on [previous] can be kept where the vertex is the same.
 */
class MeshChange(val previous: SurfaceMesh, val previousOf: IntArray) {
    /** How many vertices are the same as [previous]'s. */
    val kept: Int = previousOf.count { it >= 0 }
}

/**
 * Pieces of work matching the vertices gathered into [arrays], block [keys] from [starts], against [last], meshed from [lastField], into
 * [previousOf]: a vertex matches when its block holds as many vertices there, every value it holds is the same, and each member it
 * holds is, by its place in [field], the same member placed the same way and joined the same way as at that place in [lastField].
 */
internal fun matchTasks(
    keys: LongArray,
    starts: IntArray,
    arrays: SurfaceMesh,
    field: JoinedField,
    last: SurfaceMesh,
    lastField: JoinedField,
    previousOf: IntArray,
): List<() -> Unit> {
    val same = BooleanArray(field.members.size) { m ->
        m < lastField.members.size && lastField.members[m].key == field.members[m].key && lastField.members[m].blend == field.members[m].blend &&
            lastField.members[m].parent == field.members[m].parent && lastField.members[m].angles === field.members[m].angles
    }
    return (keys.indices step MATCH_CHUNK).map { from ->
        {
            for (b in from until minOf(keys.size, from + MATCH_CHUNK)) {
                val at = last.blockKeys.binarySearch(keys[b])
                if (at < 0) continue
                val count = starts[b + 1] - starts[b]
                if (last.blockStarts[at + 1] - last.blockStarts[at] != count) continue
                for (l in 0 until count) {
                    val v = starts[b] + l
                    val o = last.blockStarts[at] + l
                    if (alike(arrays, v, last, o, same)) previousOf[v] = o
                }
            }
        }
    }
}

private fun alike(a: SurfaceMesh, v: Int, b: SurfaceMesh, o: Int, same: BooleanArray): Boolean {
    for (c in 0 until 3) if (a.positions[v * 3 + c] != b.positions[o * 3 + c] || a.normals[v * 3 + c] != b.normals[o * 3 + c]) return false
    if (!member(a.winners[v], b.winners[o], same) || !member(a.parents[v], b.parents[o], same)) return false
    if (a.shares[v] != b.shares[o] || a.winnerOwn[v] != b.winnerOwn[o] || a.parentOwn[v] != b.parentOwn[o]) return false
    for (k in 0 until HELD) {
        val i = v * HELD + k
        val j = o * HELD + k
        if (!member(a.held[i], b.held[j], same)) return false
        if (a.heldShares[i] != b.heldShares[j] || a.heldU[i] != b.heldU[j] || a.heldV[i] != b.heldV[j] || a.heldOwn[i] != b.heldOwn[j]) return false
    }
    return true
}

private fun member(now: Int, before: Int, same: BooleanArray): Boolean = now == before && (now < 0 || same[now])
