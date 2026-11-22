package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.RunAll
import dev.chieppa.squash.character.surface.chunked
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

/** The most projection steps a vertex takes before it is relaxed. */
private const val MOST_STEPS = 8

/** The projection steps a vertex takes after each round of relaxing. */
private const val SETTLING_STEPS = 2

/** How many rounds of relaxing along the surface the moved vertices take. */
private const val RELAX_ROUNDS = 3

/** How far toward where its neighbours place it, along the surface, a vertex moves each round. */
private const val RELAX_SHARE = 0.5f

/** The cosine of the turn of the gradient between two steps past which a vertex stops: it has met a contact. */
private const val CONTACT_COS = 0.574f

/** How close to its own level, as a share of the field's cell, a vertex counts as on it. */
private const val ON_LEVEL_SHARE = 0.03f

/** How far, as a share of the field's cell, projecting must move a vertex or one of its neighbours for the vertex to be relaxed. */
private const val RELAX_FROM_SHARE = 0.03f

/** The longest step, in the field's cells. */
private const val LONGEST_STEP_CELLS = 4f

/** The differences' step, as a share of the field's cell. */
private const val GRADIENT_SHARE = 0.25f

/** Vertices a piece of the work takes. */
private const val CHUNK = 256

/**
 * Implicit skinning on a one surface (Vaillant, Barthe, Guennebaud, Cani, Rohmer, Wyvill, Gourmel, Paulin, "Implicit Skinning", 2013):
 * after linear blend, each vertex near a bend, one whose bones or the member pieces round it move apart, and each that a pose brings
 * against a member of another part ([ChosenVertices]), is moved along the posed field's gradient ([PosedField]) back to the level it lay
 * on at rest, so an inner bend creases where the members meet, a joint keeps the volume its join gives, and a limb pressed against the
 * body or another limb flattens against it. A vertex stops where the gradient turns sharply between steps, a contact. The moved vertices
 * are then relaxed along the surface toward where their neighbours place them by their rest mean value weights ([RestRings]) and moved
 * back onto the level, a few rounds, so they keep the rest surface's spacing over what the pose bent. Each vertex ends at an offset from
 * where linear blend puts it on the same rest mesh, in the same order ([Refit]).
 */
class ImplicitSkin(rig: Rig, val surface: SkinSurface, skin: SurfaceSkin) {
    private val field = PosedField(surface, skin, rig)
    private val members = VertexMembers(surface, skin, field)
    private val corners = surface.vertexCount
    private val cell = surface.cell
    private val h = cell * GRADIENT_SHARE
    private val rings by lazy { RestRings(corners, surface.triangles, surface.positions, surface.normals) }

    /** Each corner's field at rest, NaN until first read. */
    private val levels = FloatArray(corners) { Float.NaN }

    /**
     * Moves the corners at [positions], posed by linear blend through [frames], onto the field [frames] poses, each piece of the work run by
     * [runAll] and timed into [breakdown]: how far each moved, or null when the pose moves none.
     */
    suspend fun refit(frames: BoneFrames, positions: FloatArray, runAll: RunAll, breakdown: PoseBreakdown? = null): Refit? {
        val posed = field.posed(frames)
        val chosen = breakdown.time("choose") { ChosenVertices.of(surface, field, members, frames, positions, posed, runAll) } ?: return null
        val moved = chosen.moved
        val sets = Array(moved.size) { chosen.setOf(moved[it], positions.vec(moved[it] * 3)) }
        val blended = FloatArray(moved.size * 3) { positions[moved[it / 3] * 3 + it % 3] }
        val normals = arrayOfNulls<Vec3>(moved.size)
        val reads = LongArray((moved.size + CHUNK - 1) / CHUNK)
        breakdown.time("project") {
            runAll(chunked(moved.size, CHUNK) { from, until ->
                val reader = PosedFieldReader(field, h)
                for (i in from until until) {
                    val v = moved[i]
                    if (levels[v].isNaN()) levels[v] = reader.read(field.rest, surface.positions.vec(v * 3), members.sets, v * MOST_MEMBERS, members.counts[v], withGradient = true)
                    val (p, normal) = settle(reader, posed, levels[v], sets[i], positions.vec(v * 3), MOST_STEPS, stopAtContact = true)
                    positions.put(v * 3, p)
                    normals[i] = normal
                }
                reads[from / CHUNK] += reader.reads[0]
            })
        }
        val relaxed = breakdown.time("relax") {
            val relaxing = relaxing(moved, blended, positions)
            repeat(RELAX_ROUNDS) {
                val before = positions.copyOf()
                runAll(chunked(moved.size, CHUNK) { from, until ->
                    val reader = PosedFieldReader(field, h)
                    for (i in from until until) {
                        val v = moved[i]
                        val normal = normals[i] ?: continue
                        if (!relaxing[i]) continue
                        val p = before.vec(v * 3)
                        val target = if (rings.open[v]) {
                            val a = rings.alongBorder[v * 2]
                            if (a < 0) continue
                            val share = rings.borderShares[v]
                            before.vec(a * 3) * share + before.vec(rings.alongBorder[v * 2 + 1] * 3) * (1f - share)
                        } else {
                            var sum = Vec3.ZERO
                            for (k in rings.starts[v] until rings.starts[v + 1]) sum += before.vec(rings.around[k] * 3) * rings.weights[k]
                            sum
                        }
                        val toward = target - p
                        val along = toward - normal * (toward dot normal)
                        val (settled, turned) = settle(reader, posed, levels[v], sets[i], p + along * RELAX_SHARE, SETTLING_STEPS, stopAtContact = false)
                        positions.put(v * 3, settled)
                        if (turned != null) normals[i] = turned
                    }
                    reads[from / CHUNK] += reader.reads[0]
                })
            }
            relaxing.count { it }
        }
        breakdown?.count("moved", moved.size.toLong())
        breakdown?.count("relaxed", relaxed.toLong())
        breakdown?.count("in contact", chosen.inContact.toLong())
        breakdown?.count("reads", reads.sum() + chosen.reads)
        val offsets = FloatArray(moved.size * 3) { positions[moved[it / 3] * 3 + it % 3] - blended[it] }
        return Refit(moved, offsets, normals)
    }

    /**
     * Which of the [moved] corners to relax: those projecting moved from where linear blend put them, [blended], further than
     * [RELAX_FROM_SHARE] of a cell to [positions], and their neighbours.
     */
    private fun relaxing(moved: IntArray, blended: FloatArray, positions: FloatArray): BooleanArray {
        val shifted = BooleanArray(corners)
        val limit = cell * RELAX_FROM_SHARE
        for ((i, v) in moved.withIndex()) if ((positions.vec(v * 3) - blended.vec(i * 3)).length > limit) shifted[v] = true
        return BooleanArray(moved.size) { i ->
            val v = moved[i]
            shifted[v] || (rings.starts[v] until rings.starts[v + 1]).any { shifted[rings.around[it]] }
        }
    }

    /** [from] moved along the posed field's gradient over [set] toward [level], at most [steps] steps, with the last unit gradient. */
    private fun settle(reader: PosedFieldReader, posed: PosedField.Posed, level: Float, set: IntArray, from: Vec3, steps: Int, stopAtContact: Boolean): Pair<Vec3, Vec3?> {
        var p = from
        var normal: Vec3? = null
        for (step in 0 until steps) {
            val f = reader.read(posed, p, set, 0, set.size, withGradient = true)
            val g = reader.gradient
            val g2 = g dot g
            if (g2 <= 1e-12f) break
            val unit = g * (1f / sqrt(g2))
            if (stopAtContact && normal != null && (unit dot normal) < CONTACT_COS) break
            normal = unit
            val off = level - f
            if (abs(off) < cell * ON_LEVEL_SHARE) break
            var move = g * (off / g2)
            val length = move.length
            if (length > cell * LONGEST_STEP_CELLS) move = move * (cell * LONGEST_STEP_CELLS / length)
            p += move
        }
        return p to normal
    }
}

/**
 * The [moved] corners of a refit surface, each with how far it lies from where linear blend puts it, three floats a corner in [offsets],
 * and the posed field's unit gradient there in [normals], null where it read none: a per-vertex correction of linear blend on the same rest
 * mesh, as a corrective shape for the pose.
 */
class Refit(val moved: IntArray, val offsets: FloatArray, val normals: Array<Vec3?>) {
    /** Each corner of a surface of [corners]'s offset, three floats a corner, zero where it did not move. */
    fun offsetsOf(corners: Int): FloatArray = FloatArray(corners * 3).also { all ->
        for ((i, v) in moved.withIndex()) for (k in 0 until 3) all[v * 3 + k] = offsets[i * 3 + k]
    }
}
