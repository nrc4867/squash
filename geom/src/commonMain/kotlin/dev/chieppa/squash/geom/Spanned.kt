package dev.chieppa.squash.geom

/**
 * Where each vertex of a patch spanned between two others takes its bones from: the point at [firstUs] on [first] and the one at
 * [secondUs] on [second], [shares] of the way from the first to the second. Vertices are in the patch's grid order.
 */
class Spanned(
    val first: String,
    val second: String,
    val firstUs: FloatArray,
    val secondUs: FloatArray,
    val shares: FloatArray,
) {
    init {
        require(firstUs.size == shares.size && secondUs.size == shares.size) { "Spanned between $first and $second holds uneven arrays" }
    }

    val vertexCount: Int get() = shares.size

    /** The u along the part of the vertex at [local]: the two rails' u blended by its share. */
    fun u(local: Int): Float = firstUs[local] + (secondUs[local] - firstUs[local]) * shares[local]

    /** The rail the vertex at [local] follows the more. */
    fun nearer(local: Int): String = if (shares[local] < HALF) first else second

    /** The u on [nearer] of the vertex at [local]. */
    fun nearerU(local: Int): Float = if (shares[local] < HALF) firstUs[local] else secondUs[local]

    /** This, for the patch of [nu] rings by [nv] columns cut open at v = 0 with a column more, the last on the first. */
    fun reopened(nu: Int, nv: Int): Spanned {
        val columns = nv + 1
        fun reopened(values: FloatArray) = FloatArray(nu * columns) { values[(it / columns) * nv + (it % columns) % nv] }
        return Spanned(first, second, reopened(firstUs), reopened(secondUs), reopened(shares))
    }

    override fun equals(other: Any?): Boolean =
        other is Spanned && first == other.first && second == other.second && firstUs.contentEquals(other.firstUs) &&
            secondUs.contentEquals(other.secondUs) && shares.contentEquals(other.shares)

    override fun hashCode(): Int = 31 * (31 * first.hashCode() + second.hashCode()) + shares.contentHashCode()

    private companion object {
        const val HALF = 0.5f
    }
}
