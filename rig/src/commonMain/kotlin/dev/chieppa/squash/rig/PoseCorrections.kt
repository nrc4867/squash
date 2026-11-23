package dev.chieppa.squash.rig

import dev.chieppa.squash.character.keys.KeyShape
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.PoseKey
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3

/** How wide the band across the body's middle is, in body units, over which a vertex goes from one side's weight to the other's. */
private const val MIDDLE_BAND = 0.02f

/**
 * A pose's correction of one surface at rest, before it is skinned: each vertex in [moved] (a render vertex) moved by [offsets] and its
 * normal turned by [turns], three floats each.
 */
class RestCorrection(val moved: IntArray, val offsets: FloatArray, val turns: FloatArray)

/**
 * The pose keys of a [character] as shapes on its one rest [surface], one a key ([KeyShape]), each vertex's share of the left side's
 * weight read across the body's middle. Null pieces with no key or no surface.
 */
class HeldKeys(val surface: CharacterSurface?, val character: Character?, val keys: List<PoseKey>, val shapes: List<KeyShape>) {
    /** Every vertex any key moves, in order. */
    private val moved: IntArray = shapes.flatMap { it.moved.asIterable() }.distinct().sorted().toIntArray()

    /** For each key, where each vertex it moves sits in [moved]. */
    private val slots: List<IntArray> = shapes.map { shape -> IntArray(shape.moved.size) { moved.binarySearch(shape.moved[it]) } }

    /** Each moved vertex's share of the left side's weight, 1 on the left past the middle band and 0 on the right; half each with no surface. */
    private val leftShare: FloatArray = surface?.let { leftShares(it, character) } ?: FloatArray(moved.size) { 0.5f }

    val isEmpty: Boolean get() = moved.isEmpty()

    /** The correction [pose] takes: each key's shape by its weight on the side each vertex lies on; null where no key is weighed in. */
    fun correction(pose: Pose): RestCorrection? {
        val held = character ?: return null
        if (isEmpty) return null
        val byId = held.parts.associateBy { it.id }
        val weights = keyWeights(keys, pose, { byId[it]?.side }, { id -> byId[id]?.let(held::twinOf)?.id })
        if (weights.all { it.left == 0f && it.right == 0f }) return null
        val offsets = FloatArray(moved.size * 3)
        val turns = FloatArray(moved.size * 3)
        shapes.forEachIndexed { k, shape ->
            val w = weights[k]
            if (w.left == 0f && w.right == 0f) return@forEachIndexed
            val slot = slots[k]
            for (i in slot.indices) {
                val s = slot[i]
                val share = leftShare[s]
                val weight = w.left * share + w.right * (1f - share)
                for (c in 0 until 3) {
                    offsets[s * 3 + c] += weight * shape.offsets[i * 3 + c]
                    turns[s * 3 + c] += weight * shape.turns[i * 3 + c]
                }
            }
        }
        return RestCorrection(moved, offsets, turns)
    }

    private fun leftShares(surface: CharacterSurface, character: Character?): FloatArray {
        val sides = character?.parts?.associate { it.id to it.side }.orEmpty()
        fun mean(side: Side): Vec3? {
            val origins = surface.field.runs.indices.filter { sides[surface.field.runs[it].partId] == side }.map { surface.field.field.members[it].origin }
            if (origins.isEmpty()) return null
            return origins.fold(Vec3.ZERO) { a, b -> a + b } * (1f / origins.size)
        }
        val left = mean(Side.LEFT)
        val right = mean(Side.RIGHT)
        if (left == null || right == null || (left - right).length < 1e-6f) return FloatArray(moved.size) { 0.5f }
        val across = (left - right).normalized()
        val middle = (left + right) * 0.5f
        val vertices = surface.render.vertices
        return FloatArray(moved.size) { i ->
            val at = moved[i] * RenderMesh.FLOATS_PER_VERTEX
            val t = ((Vec3(vertices[at], vertices[at + 1], vertices[at + 2]) - middle) dot across) / MIDDLE_BAND
            val u = (t * 0.5f + 0.5f).coerceIn(0f, 1f)
            u * u * (3f - 2f * u)
        }
    }

    companion object {
        val NONE = HeldKeys(null, null, emptyList(), emptyList())
    }
}

