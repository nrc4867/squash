package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.LevelCut
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.surface.HELD
import dev.chieppa.squash.geom.surface.SurfaceMesh

/**
 * Coverage read at the vertices of the meshes it was last read on, by what read it, so the next read on a mesh takes the value at every
 * vertex that mesh shares with the one before it (see [SurfaceMesh.change]), or at every vertex of the same mesh, and reads only the rest.
 * A read of the same mesh by the same reader is [Pending.unchanged] and needs no work, and what was made from it last time, its values
 * smoothed ([smoothedOf]) and the level cut of what they show ([cutOf]), is taken as it was. Everything kept since [begin] becomes the
 * one kept for its slot once [settle] is called; a build calls [begin] first. One build at a time, from one thread; only the pieces of
 * work [read] gives run on any.
 */
class CoverageMemo {
    private val reads = SlotMemo<Read>()
    private val smoothed = SlotMemo<FloatArray>()
    private val cuts = SlotMemo<KeptCut>()

    /**
     * Pieces of work filling a fresh array of [reader]'s coverage at each vertex of [mesh], [chunk] vertices a piece, which [values] gives
     * once they have run; [slot] names which surface and cover it is, [key] what the reader reads but the parts coming out through its
     * holes and ends, and [parts] those parts. Where the parts differ from the last read's, a vertex takes its value only where neither
     * reader reads them there. Where nothing differs, it gives the last read's values and no work.
     */
    fun read(slot: Any, key: Any, parts: Any, reader: Coverage, field: CharacterField, mesh: SurfaceMesh, chunk: Int): Pending {
        val before = reads.last(slot)?.takeIf { it.key == key }
        val sameRun = BooleanArray(field.runs.size) { m ->
            val old = before?.runs?.getOrNull(m)
            val now = field.runs[m]
            old != null && old.patch === now.patch && old.partId == now.partId && old.first == now.first
        }
        val partsMoved = before != null && before.parts != parts
        if (before != null && !partsMoved && before.mesh === mesh && before.runs.size == field.runs.size && sameRun.all { it }) {
            reads.keep(slot, Read(key, parts, reader, mesh, before.values, field.runs))
            return Pending(before.values, emptyList(), unchanged = true)
        }
        val out = FloatArray(mesh.vertexCount)
        reads.keep(slot, Read(key, parts, reader, mesh, out, field.runs))
        val previousOf = when {
            before == null -> null
            before.mesh === mesh -> IntArray(mesh.vertexCount) { it }
            mesh.change?.previous === before.mesh -> mesh.change?.previousOf
            else -> null
        }
        val tasks = chunked(mesh.vertexCount, chunk) { from, to ->
            if (previousOf == null || before == null) {
                reader.fill(mesh, out, from, to)
            } else {
                for (v in from until to) {
                    val o = previousOf[v]
                    val same = o >= 0 && heldSame(mesh, v, sameRun) && (!partsMoved || !reader.readsParts(mesh, v) && !before.reader.readsParts(before.mesh, o))
                    out[v] = if (same) before.values[o] else reader.at(mesh, v)
                }
            }
        }
        return Pending(out, tasks)
    }

    /**
     * The values [read], begun for [slot], were smoothed to last time, its first [count]; null unless [read] is unchanged and they were
     * kept. What it gives is kept again.
     */
    fun smoothedOf(slot: Any, read: Pending, count: Int): FloatArray? {
        if (!read.unchanged) return null
        val last = smoothed.last(slot)?.takeIf { it.size == count } ?: return null
        smoothed.keep(slot, last)
        return last
    }

    /** Keeps [values] as what [slot]'s read was smoothed to; not to be changed after. */
    fun keepSmoothed(slot: Any, values: FloatArray) {
        smoothed.keep(slot, values)
    }

    /** The level cut kept for [slot] where it was cut from the same [indices] at the same [values]; null otherwise. What it gives is kept again. */
    fun cutOf(slot: Any, indices: IntArray, values: FloatArray): LevelCut? {
        val last = cuts.last(slot)?.takeIf { it.indices === indices && it.values.contentEquals(values) } ?: return null
        cuts.keep(slot, last)
        return last.cut
    }

    /** Keeps [cut], cut from [indices] at [values], for [slot]; [values] is not to be changed after. */
    fun keepCut(slot: Any, indices: IntArray, values: FloatArray, cut: LevelCut) {
        cuts.keep(slot, KeptCut(indices, values, cut))
    }

    /** Forgets what was kept since the last [settle], which a build stopped part way leaves unfinished. */
    fun begin() {
        reads.begin()
        smoothed.begin()
        cuts.begin()
    }

    /** Keeps what was kept since the last call in place of what was there before in the same slots. */
    fun settle() {
        reads.settle()
        smoothed.settle()
        cuts.settle()
    }

    /** A read begun: its [tasks], and the [values] they fill; [unchanged] where they are the last read's, with no tasks. */
    class Pending(val values: FloatArray, val tasks: List<() -> Unit>, val unchanged: Boolean = false)

    private class Read(val key: Any, val parts: Any, val reader: Coverage, val mesh: SurfaceMesh, val values: FloatArray, val runs: List<FieldRun>)

    private class KeptCut(val indices: IntArray, val values: FloatArray, val cut: LevelCut)

    /** Whether every run vertex [v] of [mesh] holds is the run it was. */
    private fun heldSame(mesh: SurfaceMesh, v: Int, sameRun: BooleanArray): Boolean {
        for (k in 0 until HELD) {
            val member = mesh.held[v * HELD + k]
            if (member >= 0 && !sameRun[member]) return false
        }
        return true
    }

    companion object {
        /** What a [Coverage] reads besides a vertex's own values and the parts coming out through it: see [partsOf]. */
        fun keyOf(worn: WornCover, reach: Float, clear: Float): Any = listOf(worn.key, reach, clear)

        /** The parts coming out through a [Coverage]'s [holes] and [ends], as they read. */
        fun partsOf(holes: List<JoinedMember>, ends: List<JoinedMember>): Any = listOf(holes.map { it.key }, ends.map { it.key })
    }
}
