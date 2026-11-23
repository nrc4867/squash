package dev.chieppa.squash.character.keys

/**
 * A pose key's correction as a shape on the one rest mesh: each vertex in [moved] (a render vertex), the [offsets] it moves by at rest
 * and the [turns] its normal takes there, three floats each, in the order of [moved]. A corrective shape: skinned with the vertex, it
 * gives the keyed surface in any pose.
 */
class KeyShape(val moved: IntArray, val offsets: FloatArray, val turns: FloatArray) {
    /** Whether it moves nothing. */
    val isEmpty: Boolean get() = moved.isEmpty()

    companion object {
        val EMPTY = KeyShape(IntArray(0), FloatArray(0), FloatArray(0))
    }
}
