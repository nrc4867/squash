package dev.chieppa.squash.geom.surface

import dev.chieppa.squash.geom.field.JoinedMember

/**
 * The member channels a mesh's blocks hold, lent to the meshing of another field on the same grid: a member grown by an ease reads its
 * solid's field less that ease, so where a block of this mesh read the same solid placed the same way exactly, a grown member's
 * channel there is those values less its ease, with no read, exactly as a read of its own would give them. A garment, grown from
 * the body and meshed on the body's grid, takes its channels from the body's blocks this way while both read through the same
 * [generation] of reads.
 */
class ChannelSource internal constructor(internal val cell: Float, private val blocks: Map<Long, SurfaceBlock>, internal val generation: Int) {
    /** [member]'s channel and middles in block [key] as this mesh read them, less [member]'s ease, where it read them; null elsewhere. */
    internal fun lend(key: Long, member: JoinedMember): Lent? {
        val block = blocks[key] ?: return null
        val ease = member.ease
        for (s in block.keys.indices) {
            val k = block.keys[s]
            if (k.ease != 0f || k.solid !== member.solid || k.origin != member.origin || k.mirrored != member.mirrored) continue
            val shift = ease
            if (block.exact[s] == 0L && block.middlesRead[s] == 0L) return null
            val channel = FloatArray(BLOCK_CORNERS) { block.channels[s][it] - shift }
            val middles = FloatArray(SUBS) { block.middles[s * SUBS + it] - shift }
            return Lent(channel, block.exact[s], middles, block.middlesRead[s])
        }
        return null
    }

    /** A member's values lent from another mesh's block: its [channel], exact in the sub-blocks [exact] holds, and its [middles], read where [middlesRead] says. */
    internal class Lent(val channel: FloatArray, val exact: Long, val middles: FloatArray, val middlesRead: Long)
}
