package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.surface.HELD
import dev.chieppa.squash.geom.surface.SurfaceMesh

/**
 * Coverage read at the vertices of the meshes it was last read on, by what read it, so the next read on a mesh takes the value at every
 * vertex that mesh shares with the one before it (see [SurfaceMesh.change]), or at every vertex of the same mesh, and reads only the rest.
 * Each read is begun by [read] and becomes the one kept for its slot once [settle] is called; a build calls [begin] first. One build at a time; [read] is called from
 * one thread, its pieces from any.
 */
class CoverageMemo {
    private val kept = HashMap<Any, Read>()
    private val made = HashMap<Any, Read>()

    /**
     * Pieces of work filling a fresh array of [reader]'s coverage at each vertex of [mesh], [chunk] vertices a piece, which [values] gives
     * once they have run; [slot] names which surface and cover it is, [key] what the reader reads but the parts coming out through its
     * holes and ends, and [parts] those parts. Where the parts differ from the last read's, a vertex takes its value only where neither
     * reader reads them there.
     */
    fun read(slot: Any, key: Any, parts: Any, reader: Coverage, field: CharacterField, mesh: SurfaceMesh, chunk: Int): Pending {
        val out = FloatArray(mesh.vertexCount)
        val before = kept[slot]?.takeIf { it.key == key }
        made[slot] = Read(key, parts, reader, mesh, out, field.runs)
        val partsMoved = before != null && before.parts != parts
        val sameRun = BooleanArray(field.runs.size) { m ->
            val old = before?.runs?.getOrNull(m)
            val now = field.runs[m]
            old != null && old.patch === now.patch && old.partId == now.partId && old.first == now.first
        }
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

    /** Forgets the reads begun since the last [settle], which a build stopped part way leaves unfinished. */
    fun begin() {
        made.clear()
    }

    /** Keeps the reads begun since the last call in place of those before them in the same slots. */
    fun settle() {
        kept.putAll(made)
        made.clear()
    }

    /** A read begun: its [tasks], and the [values] they fill. */
    class Pending(val values: FloatArray, val tasks: List<() -> Unit>)

    private class Read(val key: Any, val parts: Any, val reader: Coverage, val mesh: SurfaceMesh, val values: FloatArray, val runs: List<FieldRun>)

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
