package dev.chieppa.squash.geom

/**
 * Where each vertex of a patch takes its bones from when they follow its place rather than its own u: for every vertex, a point at a u
 * on each of [patches], with the share that point gives. Patches that meet on one surface give a vertex the same rides wherever it
 * lies, so they move as one in a pose. Values are in the patch's grid order, [patches]'s count a vertex.
 */
class Rides(val patches: List<String>, val us: FloatArray, val shares: FloatArray) {
    init {
        require(patches.isNotEmpty()) { "Rides name no patch" }
        require(us.size == shares.size && shares.size % patches.size == 0) { "Rides hold uneven arrays" }
    }

    val vertexCount: Int get() = shares.size / patches.size

    /** The u on patch [k] of the vertex at [local]. */
    fun u(local: Int, k: Int): Float = us[local * patches.size + k]

    /** The share patch [k] gives the vertex at [local]. */
    fun share(local: Int, k: Int): Float = shares[local * patches.size + k]

    /** This, for the patch of [nu] rings by [nv] columns cut open at v = 0 with a column more, the last on the first. */
    fun reopened(nu: Int, nv: Int): Rides {
        val width = patches.size
        val columns = nv + 1
        fun reopened(values: FloatArray) = FloatArray(nu * columns * width) { i ->
            val vertex = i / width
            values[((vertex / columns) * nv + (vertex % columns) % nv) * width + i % width]
        }
        return Rides(patches, reopened(us), reopened(shares))
    }

    override fun equals(other: Any?): Boolean =
        other is Rides && patches == other.patches && us.contentEquals(other.us) && shares.contentEquals(other.shares)

    override fun hashCode(): Int = 31 * patches.hashCode() + shares.contentHashCode()
}
