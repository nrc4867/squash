package dev.chieppa.squash.geom.surface

/**
 * What each of [count] vertices holds of the members where it lies, each member as a slot among those its block holds: the member whose
 * term of the union is least there, its [winners], the run it grows from across a smooth union, its [parents] (-1 where it joins nothing
 * smoothly there), the winner's mixing weight against that parent, [shares], and both one's own fields there; and the [HELD] members
 * with the largest shares of the surface there ([held], -1 for none), each with its share, where on its run the vertex lies, (u, v), and
 * its own field.
 */
internal class VertexCoordinates(private val count: Int) {
    val winners = IntArray(count) { -1 }
    val parents = IntArray(count) { -1 }
    val shares = FloatArray(count)
    val winnerOwn = FloatArray(count)
    val parentOwn = FloatArray(count)
    val held = IntArray(count * HELD) { -1 }
    val heldShares = FloatArray(count * HELD)
    val heldU = FloatArray(count * HELD)
    val heldV = FloatArray(count * HELD)
    val heldOwn = FloatArray(count * HELD)

    fun copy(): VertexCoordinates = VertexCoordinates(count).also { c ->
        winners.copyInto(c.winners)
        parents.copyInto(c.parents)
        shares.copyInto(c.shares)
        winnerOwn.copyInto(c.winnerOwn)
        parentOwn.copyInto(c.parentOwn)
        held.copyInto(c.held)
        heldShares.copyInto(c.heldShares)
        heldU.copyInto(c.heldU)
        heldV.copyInto(c.heldV)
        heldOwn.copyInto(c.heldOwn)
    }

    /** These coordinates with every member's slot [s] renumbered [slot] at s. */
    fun withSlots(slot: IntArray): VertexCoordinates = copy().also { c ->
        for (i in winners.indices) c.winners[i] = winners[i].let { if (it >= 0) slot[it] else -1 }
        for (i in parents.indices) c.parents[i] = parents[i].let { if (it >= 0) slot[it] else -1 }
        for (i in held.indices) c.held[i] = held[i].let { if (it >= 0) slot[it] else -1 }
    }

    /** Vertex [v] as [from] holds vertex [old], with every member's slot s renumbered [slot] at s. */
    fun takeVertex(v: Int, from: VertexCoordinates, old: Int, slot: IntArray) {
        winners[v] = from.winners[old].let { if (it >= 0) slot[it] else -1 }
        parents[v] = from.parents[old].let { if (it >= 0) slot[it] else -1 }
        shares[v] = from.shares[old]
        winnerOwn[v] = from.winnerOwn[old]
        parentOwn[v] = from.parentOwn[old]
        for (k in 0 until HELD) {
            held[v * HELD + k] = from.held[old * HELD + k].let { if (it >= 0) slot[it] else -1 }
            heldShares[v * HELD + k] = from.heldShares[old * HELD + k]
            heldU[v * HELD + k] = from.heldU[old * HELD + k]
            heldV[v * HELD + k] = from.heldV[old * HELD + k]
            heldOwn[v * HELD + k] = from.heldOwn[old * HELD + k]
        }
    }
}
