package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.RunAll
import dev.chieppa.squash.character.surface.chunked
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.surface.HELD
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.sqrt

/** The most projection steps a vertex takes before it is relaxed. */
private const val MOST_STEPS = 8

/** The projection steps a vertex takes after each round of relaxing. */
private const val SETTLING_STEPS = 2

/** How many rounds of relaxing along the surface the moved vertices take. */
private const val RELAX_ROUNDS = 3

/** How many rounds of relaxing a pose that starts from the last refit takes, whose corners that last refit relaxed already. */
private const val TRACKED_RELAX_ROUNDS = 1

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

/** How far apart, entry by entry, two poses' bone maps may lie for the later to start from where the earlier left the surface. */
private const val TRACKED_TURN = 0.1f

/** How far apart, in the field's cells, two poses' bone maps may move a point for the later to start from where the earlier left it. */
private const val TRACKED_SHIFT_CELLS = 2f

/** Vertices a piece of the work takes. */
private const val CHUNK = 256

/** How deep, as a share of the field's cell, linear blend must bury a corner in a member that holds none of it for the corner to stay there. */
private const val BURIED_SHARE = 0.5f

/**
 * Implicit skinning on a one surface (Vaillant, Barthe, Guennebaud, Cani, Rohmer, Wyvill, Gourmel, Paulin, "Implicit Skinning", 2013):
 * after linear blend, each vertex near a bend, one whose bones or the member pieces round it move apart ([ChosenVertices]), is moved
 * along the posed field's gradient ([PosedField]) back to the level it lay on at rest, so an inner bend creases where the members meet and
 * a joint keeps the volume its join gives. A vertex stops where the gradient turns sharply between steps, a contact, and one linear blend
 * buries in a member holding none of it stays where linear blend leaves it ([buried]). The moved vertices
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

    /**
     * The corners linear blend alone carries: those of a head built with its jaw open that follow only its jaw and the bone the jaw hangs
     * from. Its halves are cut to fit, so a pose shuts them onto each other exactly, and moving them onto the posed field would crumple
     * the lips and chin where the halves meet.
     */
    private val rigid = BooleanArray(corners) { v ->
        val jaw = surface.jaws.getOrElse(v) { -1 }
        jaw >= 0 && (0 until MAX_INFLUENCES).all { slot ->
            val bone = skin.bones[v * MAX_INFLUENCES + slot]
            bone < 0 || skin.weights[v * MAX_INFLUENCES + slot] <= 0f || bone == jaw || bone == rig.parents[jaw]
        }
    }

    /** Each corner's field at rest, NaN until first read. */
    private val levels = FloatArray(corners) { Float.NaN }

    /** Each corner's joins at rest, read with its level. */
    private val joins = RestJoins(corners)

    /** The last refit and the bone maps it was made for, which a pose near them starts from. */
    @Volatile
    private var tracked: Tracked? = null

    /**
     * Moves the corners at [positions], posed by linear blend through [frames], onto the field [frames] poses, each piece of the work run by
     * [runAll] and timed into [breakdown]: how far each moved, or null when the pose moves none.
     */
    suspend fun refit(frames: BoneFrames, positions: FloatArray, runAll: RunAll, breakdown: PoseBreakdown? = null): Refit? {
        val posed = field.posed(frames)
        val chosen = breakdown.time("choose") { ChosenVertices.of(surface, field, members, rigid, frames, positions, posed, runAll) } ?: return null
        val moved = chosen.moved
        val sets = Array(moved.size) { chosen.setOf(moved[it], positions.vec(moved[it] * 3)) }
        val blended = FloatArray(moved.size * 3) { positions[moved[it / 3] * 3 + it % 3] }
        val last = tracked?.takeIf { it.near(frames.transforms, cell) }
        val starts = if (last == null) blended else FloatArray(moved.size * 3) { blended[it] + last.offsets[moved[it / 3] * 3 + it % 3] }
        val normals = arrayOfNulls<Vec3>(moved.size)
        val reads = LongArray((moved.size + CHUNK - 1) / CHUNK)
        breakdown.time("project") {
            runAll(chunked(moved.size, CHUNK) { from, until ->
                val reader = PosedFieldReader(field, h)
                for (i in from until until) {
                    val v = moved[i]
                    if (levels[v].isNaN()) levels[v] = reader.readRest(field.rest, surface.positions.vec(v * 3), members.sets, v * MOST_MEMBERS, members.counts[v], joins)
                    if (buried(v, starts.vec(i * 3), posed, reader.reads)) {
                        positions.put(v * 3, starts.vec(i * 3))
                        continue
                    }
                    reader.restAt(members.sets, v * MOST_MEMBERS, members.counts[v], joins)
                    val (p, normal) = settle(reader, posed, levels[v], sets[i], starts.vec(i * 3), MOST_STEPS, stopAtContact = true)
                    positions.put(v * 3, p)
                    normals[i] = normal
                }
                reads[from / CHUNK] += reader.reads[0]
            })
        }
        val relaxed = breakdown.time("relax") {
            val relaxing = relaxing(moved, starts, positions)
            repeat(if (last == null) RELAX_ROUNDS else TRACKED_RELAX_ROUNDS) {
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
                        reader.restAt(members.sets, v * MOST_MEMBERS, members.counts[v], joins)
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
        breakdown?.count("reads", reads.sum() + chosen.reads)
        val offsets = FloatArray(moved.size * 3) { positions[moved[it / 3] * 3 + it % 3] - blended[it] }
        return Refit(moved, offsets, normals).also { tracked = Tracked(frames.transforms.toList(), it.offsetsOf(corners)) }
    }

    /**
     * Whether corner [v], started at [p], lies buried by linear blend more than [BURIED_SHARE] of a cell inside a member [posed] that
     * holds no share of it, as a sleeve's underside lowered into the cloth over the side: it stays where linear blend leaves it, since
     * moving it onto that member's surface lays it over the other's skin; [reads] counts the solid reads.
     */
    private fun buried(v: Int, p: Vec3, posed: PosedField.Posed, reads: LongArray): Boolean {
        for (j in 0 until members.counts[v]) {
            val m = members.sets[v * MOST_MEMBERS + j]
            if (holds(v, m)) continue
            if (posed.own(m, p, reads) < -cell * BURIED_SHARE) return true
        }
        return false
    }

    private fun holds(v: Int, m: Int): Boolean {
        for (k in 0 until HELD) if (surface.held[v * HELD + k] == m && surface.heldShares[v * HELD + k] > 0f) return true
        return false
    }

    /**
     * Which of the [moved] corners to relax: those projecting moved from where it started them, [starts], further than [RELAX_FROM_SHARE]
     * of a cell to [positions], and their neighbours.
     */
    private fun relaxing(moved: IntArray, starts: FloatArray, positions: FloatArray): BooleanArray {
        val shifted = BooleanArray(corners)
        val limit = cell * RELAX_FROM_SHARE
        for ((i, v) in moved.withIndex()) if ((positions.vec(v * 3) - starts.vec(i * 3)).length > limit) shifted[v] = true
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

/**
 * A refit's [offsets] from linear blend, three floats a corner, and the bone maps, [transforms], it was made for: a pose whose maps lie
 * near them starts each corner from its last offset, tracking the surface from one step of a drag to the next (Vaillant, Guennebaud,
 * Barthe, Wyvill, Cani, "Robust Iso-Surface Tracking for Interactive Character Skinning", 2014), so most corners start on their level and
 * only what the step changed is projected and relaxed again. A pose far from it starts again from linear blend.
 */
private class Tracked(val transforms: List<Affine>, val offsets: FloatArray) {
    /** Whether [other] lies near enough to these maps to start from them, for a field of [cell]. */
    fun near(other: List<Affine>, cell: Float): Boolean {
        if (other.size != transforms.size) return false
        val shift = cell * TRACKED_SHIFT_CELLS
        for (b in other.indices) {
            val a = transforms[b]
            val o = other[b]
            if (!close(a.linear.x, o.linear.x, TRACKED_TURN) || !close(a.linear.y, o.linear.y, TRACKED_TURN) || !close(a.linear.z, o.linear.z, TRACKED_TURN)) return false
            if (!close(a.offset, o.offset, shift)) return false
        }
        return true
    }

    private fun close(a: Vec3, b: Vec3, most: Float): Boolean = abs(a.x - b.x) <= most && abs(a.y - b.y) <= most && abs(a.z - b.z) <= most
}
