package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.NeckJoin
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.smoothstep
import dev.chieppa.squash.geom.surface.HELD
import dev.chieppa.squash.geom.surface.SurfaceMesh

/** The way, in body space, the shade under a chin is cast from: up and a little forward, as the view's light falls from above. */
private val TOWARD_LIGHT = Vec3(0.35f, 1f, 0f).normalized()

/** How many places along the way to the light, up to a head's reach, a vertex reads the head at. */
private const val SHADE_STEPS = 10

/** How wide the head's edge is eased over, as a share of the reach, so the shade's border is a smooth curve. */
private const val EDGE_SHARE = 0.2f

/** How many rounds each vertex's shade is taken half way to the mean of its neighbours', so its border runs smooth across the cells. */
private const val SMOOTHING_ROUNDS = 4

/** The fewest of the body's cells a shade reaches below the head, so a small head's shade still spans vertices the smoothing keeps. */
private const val LEAST_REACH_CELLS = 4f

/** How much of the reach below the head the shade holds whole before it fades. */
private const val CORE_SHARE = 0.45f

/**
 * The shade a head casts under its chin, for each head in [field] whose neck style casts one (see [NeckJoin.shade]): at a point, the
 * most, over places up toward the light from it within the head's reach, of how far inside the head's own solid that place lies,
 * eased over the head's edge, whole over the nearest [CORE_SHARE] of the reach and fading to nothing at the reach, so it is darkest
 * just under the jaw. Only the parts the head sits on take it, by their share of the surface there: the neck and what it sits on, never
 * the head or anything on it. A smooth function of the point, drawn where it is at least a half, so the throat's shade is one clean shape.
 */
internal class ChinShade(private val field: CharacterField, private val mesh: SurfaceMesh) {
    private class Head(val member: JoinedMember, val under: Set<Int>, val reach: Float, val low: Vec3, val high: Vec3)

    private val heads: List<Head> = field.runs.indices.mapNotNull { i ->
        val run = field.runs[i]
        val member = field.field.members[i]
        val share = NeckJoin.of(run.part.placement).shade
        if (!run.first || member.parent < 0 || share <= 0f) return@mapNotNull null
        val size = member.high - member.low
        val reach = maxOf(minOf(size.x, size.y, size.z) * share, field.cell * LEAST_REACH_CELLS)
        val grow = Vec3(reach, reach, reach)
        Head(member, ancestors(i), reach, member.low - grow, member.high + grow)
    }

    /** Whether any head casts a shade. */
    val on: Boolean get() = heads.isNotEmpty()

    /** Each of vertices [from] until [to]'s shade, 0 to 1, into [out]. */
    fun fill(from: Int, to: Int, out: FloatArray) {
        if (heads.isEmpty()) return
        val positions = mesh.positions
        for (v in from until to) {
            val at = Vec3(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2])
            var most = 0f
            for (head in heads) {
                if (!inside(at, head.low, head.high)) continue
                val own = shareOf(v, head.under)
                if (own <= 0f) continue
                most = maxOf(most, cover(at, head) * own)
            }
            out[v] = most
        }
    }

    /** [values], one a vertex of the mesh, each taken half way to the mean of its neighbours' [SMOOTHING_ROUNDS] times. */
    fun smooth(values: FloatArray) {
        if (heads.isEmpty()) return
        val count = mesh.vertexCount
        val triangles = mesh.triangles
        val sum = FloatArray(count)
        val seen = IntArray(count)
        repeat(SMOOTHING_ROUNDS) {
            sum.fill(0f)
            seen.fill(0)
            for (t in 0 until triangles.size / 3) for (c in 0 until 3) {
                val v = triangles[t * 3 + c]
                for (o in 1..2) {
                    val w = triangles[t * 3 + (c + o) % 3]
                    sum[v] += values[w]
                    seen[v]++
                }
            }
            for (v in 0 until count) if (seen[v] > 0) values[v] = (values[v] + sum[v] / seen[v]) * 0.5f
        }
    }

    private fun cover(at: Vec3, head: Head): Float {
        val edge = head.reach * EDGE_SHARE
        var most = 0f
        for (step in 1..SHADE_STEPS) {
            val t = head.reach * step / SHADE_STEPS
            val near = smoothstep(1f, CORE_SHARE, t / head.reach)
            if (near <= most) break
            val inside = smoothstep(edge, -edge, head.member.own(at + TOWARD_LIGHT * t))
            most = maxOf(most, inside * near)
        }
        return most
    }

    /** The parts, by index, of the members [member] joins through, its parent's and on to the root's. */
    private fun ancestors(member: Int): Set<Int> {
        val parts = HashSet<Int>()
        var at = field.field.members[member].parent
        while (at >= 0) {
            parts += field.field.members[at].part
            at = field.field.members[at].parent
        }
        return parts
    }

    private fun shareOf(v: Int, parts: Set<Int>): Float {
        var share = 0f
        for (k in 0 until HELD) {
            val member = mesh.held[v * HELD + k]
            if (member >= 0 && field.field.members[member].part in parts) share += mesh.heldShares[v * HELD + k]
        }
        return share.coerceIn(0f, 1f)
    }

    private fun inside(p: Vec3, low: Vec3, high: Vec3) = p.x in low.x..high.x && p.y in low.y..high.y && p.z in low.z..high.z
}
