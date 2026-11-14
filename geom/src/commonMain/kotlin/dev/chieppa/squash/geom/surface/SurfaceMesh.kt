package dev.chieppa.squash.geom.surface

import dev.chieppa.squash.geom.Vec3
import kotlin.concurrent.Volatile

/**
 * One surface meshed from a joined field: vertices at [positions] with the field's [normals] there, three values each, and
 * [triangles] facing out. Each vertex holds the member whose term of the union is least there ([winners]), the member that one joins
 * smoothly there ([parents], -1 for none), the winner's mixing weight against it ([shares]) and each one's own field there ([winnerOwn],
 * [parentOwn]); and the [HELD] members with the largest shares of the surface there ([held], -1 for none), each one's share
 * ([heldShares]), where on its run the vertex lies ([heldU], [heldV]) and its own field ([heldOwn]), [HELD] values a vertex. Members are
 * indices into the field meshed. [cell] is the grid's cell size and [blocks] how many blocks it read; [channels] lends what its blocks
 * read to the meshing of a field grown from this one.
 */
class SurfaceMesh(
    val cell: Float,
    val positions: FloatArray,
    val normals: FloatArray,
    val triangles: IntArray,
    val winners: IntArray,
    val parents: IntArray,
    val shares: FloatArray,
    val winnerOwn: FloatArray,
    val parentOwn: FloatArray,
    val held: IntArray,
    val heldShares: FloatArray,
    val heldU: FloatArray,
    val heldV: FloatArray,
    val heldOwn: FloatArray,
    val blocks: Int,
    val channels: ChannelSource? = null,
    internal val blockKeys: LongArray = LongArray(0),
    internal val blockStarts: IntArray = IntArray(1),
) {
    /**
     * How this mesh differs from the one its mesher made before it, null where there was none: each vertex's place in that mesh where
     * it is the same vertex holding the same members, and every member it holds is the same as there. Only the newest mesh of a mesher
     * keeps it, so meshes do not keep one another.
     */
    @Volatile
    var change: MeshChange? = null
        internal set

    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = triangles.size / 3

    fun position(v: Int): Vec3 = Vec3(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2])
    fun normal(v: Int): Vec3 = Vec3(normals[v * 3], normals[v * 3 + 1], normals[v * 3 + 2])
}
