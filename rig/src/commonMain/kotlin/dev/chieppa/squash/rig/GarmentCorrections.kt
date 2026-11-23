package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3

/** How far past the body's moved vertices, in body units, a garment vertex is looked at. */
private const val GARMENT_REACH = 0.05f

/** How far past the body's nearest corner, in the body's cells, the corners a garment vertex reads its correction from reach. */
private const val SPREAD_CELLS = 2f

/** The least distance a corner's weight is read at, as a share of the body's cell, so a corner under the garment vertex does not take all. */
private const val NEAREST_SHARE = 0.5f

/**
 * How each garment vertex takes the body's correction: from the body's corners round where it lies at rest, those within
 * [SPREAD_CELLS] cells past the nearest, each weighed by its inverse squared distance, so across a crease the cloth takes the move of
 * the skin on both sides of it; and along its own normal at least as far out as the furthest any of them moves out, so no skin the key
 * moves out passes through it. Kept per garment surface by [prepare] while the body's keys are the same.
 */
internal class GarmentCorrections(private val positions: FloatArray, private val corners: Int, private val cell: Float, private val moved: IntArray) {
    constructor(body: CharacterSurface, moved: IntArray) : this(body.positions, body.mesh.vertexCount, body.mesh.cell, moved)

    private val near by lazy { NearestVertex(positions, cell) }
    private val box: FloatArray by lazy {
        val b = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (v in moved) if (v < corners) for (c in 0 until 3) {
            b[c] = minOf(b[c], positions[v * 3 + c] - GARMENT_REACH)
            b[c + 3] = maxOf(b[c + 3], positions[v * 3 + c] + GARMENT_REACH)
        }
        b
    }
    private var kept: Map<CharacterSurface, Reach> = emptyMap()

    /**
     * The garment render [vertices] a body correction reaches, each with its rest [normals], three floats each, and the [slots] in the
     * body's correction it reads from [starts], with the [weights] each takes, its share of all the corners round it.
     */
    private class Reach(val vertices: IntArray, val normals: FloatArray, val starts: IntArray, val slots: IntArray, val weights: FloatArray)

    /** Finds, for each of [garments] it has not seen, the vertices a body correction reaches; called before the garments are posed. */
    fun prepare(garments: List<CharacterSurface>) {
        kept = garments.associateWith { kept[it] ?: reach(it.render.vertices, it.render.vertexCount) }
    }

    /** The reach of the garment whose render [vertices] are given, [count] of them. */
    private fun reach(rest: FloatArray, count: Int): Reach {
        val slotOf = HashMap<Int, Int>(moved.size * 2)
        moved.forEachIndexed { i, v -> slotOf[v] = i }
        val least = cell * NEAREST_SHARE
        val vertices = ArrayList<Int>()
        val normals = ArrayList<Float>()
        val starts = arrayListOf(0)
        val slots = ArrayList<Int>()
        val weights = ArrayList<Float>()
        val width = RenderMesh.FLOATS_PER_VERTEX
        for (g in 0 until count) {
            val at = g * width
            val p = Vec3(rest[at], rest[at + 1], rest[at + 2])
            if (p.x < box[0] || p.y < box[1] || p.z < box[2] || p.x > box[3] || p.y > box[4] || p.z > box[5]) continue
            val nearest = near.of(p) ?: continue
            val reach = (positions.vec(nearest * 3) - p).length + SPREAD_CELLS * cell
            var total = 0f
            val from = slots.size
            near.forEachWithin(p, reach) { v, d ->
                val w = 1f / (maxOf(d, least) * maxOf(d, least))
                total += w
                val slot = slotOf[v] ?: return@forEachWithin
                slots += slot
                weights += w
            }
            if (slots.size == from || total <= 0f) {
                while (slots.size > from) {
                    slots.removeAt(slots.size - 1)
                    weights.removeAt(weights.size - 1)
                }
                continue
            }
            for (k in from until weights.size) weights[k] = weights[k] / total
            vertices += g
            normals += rest[at + RenderMesh.NORMAL_OFFSET_FLOATS]
            normals += rest[at + RenderMesh.NORMAL_OFFSET_FLOATS + 1]
            normals += rest[at + RenderMesh.NORMAL_OFFSET_FLOATS + 2]
            starts += slots.size
        }
        return Reach(vertices.toIntArray(), normals.toFloatArray(), starts.toIntArray(), slots.toIntArray(), weights.toFloatArray())
    }

    /** [correction] of the body carried onto [garment]: each garment vertex near a moved body vertex moved as the skin round it is. */
    fun of(garment: CharacterSurface, correction: RestCorrection): RestCorrection =
        carried(kept[garment] ?: reach(garment.render.vertices, garment.render.vertexCount), correction)

    /** [correction] carried onto the garment whose render [vertices] are given, [count] of them. */
    fun of(vertices: FloatArray, count: Int, correction: RestCorrection): RestCorrection = carried(reach(vertices, count), correction)

    private fun carried(reach: Reach, correction: RestCorrection): RestCorrection {
        val count = reach.vertices.size
        val offsets = FloatArray(count * 3)
        val turns = FloatArray(count * 3)
        for (i in 0 until count) {
            var offset = Vec3.ZERO
            var turn = Vec3.ZERO
            val normal = reach.normals.vec(i * 3)
            var outmost = 0f
            for (k in reach.starts[i] until reach.starts[i + 1]) {
                val slot = reach.slots[k]
                val o = correction.offsets.vec(slot * 3)
                offset += o * reach.weights[k]
                turn += correction.turns.vec(slot * 3) * reach.weights[k]
                outmost = maxOf(outmost, o dot normal)
            }
            val out = offset dot normal
            if (out < outmost) offset += normal * (outmost - out)
            offsets.put(i * 3, offset)
            turns.put(i * 3, turn)
        }
        return RestCorrection(reach.vertices, offsets, turns)
    }
}
