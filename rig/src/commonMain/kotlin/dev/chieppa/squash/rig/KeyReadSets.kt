package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.CharacterField
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import kotlin.math.sqrt

/** How far past a member's box, in body units past its blend, a moved vertex at rest reads it: far enough that a joint's bend keeps it. */
private const val READ_REACH = 0.05f

/**
 * The members each vertex of a key's [region] reads, from its first in [sets] at [starts]: those of the plain or keyed field whose box
 * comes within its blend and [READ_REACH] of the vertex at rest, or whose box posed in the key's pose ([boxes], six floats a member, see
 * [PosedField.posedBoxes]) comes as near the vertex [posed] (three floats a vertex), so a limb the pose lays near it counts; and their
 * parents, which their joins read. [read] holds every member any vertex reads.
 */
internal class ReadSets(region: IntArray, rest: FloatArray, posed: FloatArray, boxes: FloatArray, base: CharacterField, keyed: CharacterField) {
    val starts = IntArray(region.size + 1)
    val sets: IntArray
    val read: IntArray

    init {
        val plain = base.field.members
        val changed = keyed.field.members
        val all = ArrayList<Int>()
        val seen = BooleanArray(plain.size)
        val width = RenderMesh.FLOATS_PER_VERTEX
        val own = ArrayList<Int>()
        for ((i, v) in region.withIndex()) {
            starts[i] = all.size
            val p = Vec3(rest[v * width], rest[v * width + 1], rest[v * width + 2])
            val q = posed.vec(v * 3)
            own.clear()
            for (m in plain.indices) {
                val reach = maxOf(plain[m].blend, changed[m].blend) + READ_REACH
                if (plain[m].boxDistance(p) < reach || changed[m].boxDistance(p) < reach || boxDistance(boxes, m, q) < reach) own += m
            }
            val direct = own.size
            for (k in 0 until direct) {
                val parent = plain[own[k]].parent
                if (parent >= 0 && parent !in own) own += parent
            }
            for (m in own) {
                all += m
                seen[m] = true
            }
        }
        starts[region.size] = all.size
        sets = all.toIntArray()
        read = plain.indices.filter { seen[it] }.toIntArray()
    }
}

/** How far [p] lies outside member [m]'s box in [boxes], six floats a member; 0 inside it. */
private fun boxDistance(boxes: FloatArray, m: Int, p: Vec3): Float {
    val dx = maxOf(boxes[m * 6] - p.x, 0f, p.x - boxes[m * 6 + 3])
    val dy = maxOf(boxes[m * 6 + 1] - p.y, 0f, p.y - boxes[m * 6 + 4])
    val dz = maxOf(boxes[m * 6 + 2] - p.z, 0f, p.z - boxes[m * 6 + 5])
    return sqrt(dx * dx + dy * dy + dz * dz)
}

/**
 * A posed field read at one point at a time over the members a vertex reads, each join blended by the share its members' gradients give
 * there, as implicit skinning reads it, with its gradient. One serves one thread.
 */
internal class PosedRead(field: PosedField, private val posed: PosedField.Posed, h: Float) {
    private val reader = PosedFieldReader(field, h)
    private var sets: IntArray = IntArray(0)
    private var from = 0
    private var count = 0

    /** Reads, from now on, the members [read] gives vertex [i] of its region. */
    fun over(read: ReadSets, i: Int) {
        sets = read.sets
        from = read.starts[i]
        count = read.starts[i + 1] - from
    }

    fun value(p: Vec3): Float = reader.read(posed, p, sets, from, count, true)

    fun gradient(p: Vec3): Vec3 {
        reader.read(posed, p, sets, from, count, true)
        return reader.gradient
    }
}

