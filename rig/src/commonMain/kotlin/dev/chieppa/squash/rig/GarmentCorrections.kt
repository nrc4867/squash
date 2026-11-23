package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3

/** How far past the body's moved vertices, in body units, a garment vertex is looked at. */
private const val GARMENT_REACH = 0.05f

/**
 * Where each garment vertex takes a body correction from: the body's nearest corner to it at rest, for the garment vertices near any
 * corner a key moves, kept per garment surface by [prepare] while the body's keys are the same.
 */
internal class GarmentCorrections(private val body: CharacterSurface, private val moved: IntArray) {
    private val near by lazy { NearestVertex(body.positions, body.mesh.cell) }
    private val box: FloatArray by lazy {
        val corners = body.mesh.vertexCount
        val b = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (v in moved) if (v < corners) for (c in 0 until 3) {
            b[c] = minOf(b[c], body.positions[v * 3 + c] - GARMENT_REACH)
            b[c + 3] = maxOf(b[c + 3], body.positions[v * 3 + c] + GARMENT_REACH)
        }
        b
    }
    private var kept: Map<CharacterSurface, Pair<IntArray, IntArray>> = emptyMap()

    /** Finds, for each of [garments] it has not seen, the vertices a body correction reaches; called before the garments are posed. */
    fun prepare(garments: List<CharacterSurface>) {
        kept = garments.associateWith { kept[it] ?: reach(it) }
    }

    /** For [garment], the garment render vertices a body correction reaches and the slot in the body's correction each takes it from. */
    private fun reach(garment: CharacterSurface): Pair<IntArray, IntArray> {
        val slots = moved.withIndex().associate { (i, v) -> v to i }
        val from = ArrayList<Int>()
        val to = ArrayList<Int>()
        val vertices = garment.render.vertices
        for (g in 0 until garment.render.vertexCount) {
            val at = g * RenderMesh.FLOATS_PER_VERTEX
            val p = Vec3(vertices[at], vertices[at + 1], vertices[at + 2])
            if (p.x < box[0] || p.y < box[1] || p.z < box[2] || p.x > box[3] || p.y > box[4] || p.z > box[5]) continue
            val v = near.of(p) ?: continue
            val slot = slots[v] ?: continue
            from += g
            to += slot
        }
        return from.toIntArray() to to.toIntArray()
    }

    /** [correction] of the body carried onto [garment]: each garment vertex near a moved body vertex moved as that vertex is. */
    fun of(garment: CharacterSurface, correction: RestCorrection): RestCorrection {
        val (vertices, slots) = kept[garment] ?: reach(garment)
        val offsets = FloatArray(vertices.size * 3)
        val turns = FloatArray(vertices.size * 3)
        for (i in vertices.indices) for (c in 0 until 3) {
            offsets[i * 3 + c] = correction.offsets[slots[i] * 3 + c]
            turns[i * 3 + c] = correction.turns[slots[i] * 3 + c]
        }
        return RestCorrection(vertices, offsets, turns)
    }
}
