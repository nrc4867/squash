package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.PosedSurface
import dev.chieppa.squash.character.surface.SurfacePaint
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import kotlin.math.floor

/**
 * [surface] and its [paint] posed by [frames] through [skin], linear blend skinning as the parts' own meshes are posed: every vertex of
 * the drawn mesh with its normal and shading normal, the normals its lines read, the paint laid again on the posed mesh, and each ink
 * mark's places carried by the vertex nearest each. The skin's contact quads are left out. A pose that moves no bone leaves them as
 * they rest.
 */
fun posedSurface(surface: CharacterSurface, skin: SurfaceSkin, paint: SurfacePaint?, frames: BoneFrames): PosedSurface {
    val transforms = frames.transforms
    val twists = frames.twists
    if (skin.bones.all { it < 0 || transforms[it].isIdentity }) return PosedSurface(surface, paint)
    val width = RenderMesh.FLOATS_PER_VERTEX
    val rest = surface.render.vertices
    val vertices = rest.copyOf()
    val corners = surface.mesh.vertexCount
    val positions = surface.positions.copyOf()
    val lineNormals = surface.lineNormals.copyOf()
    for (v in 0 until skin.vertexCount) {
        val at = v * width
        val point = rest.vec(at)
        val normal = rest.vec(at + RenderMesh.NORMAL_OFFSET_FLOATS)
        val proxy = rest.vec(at + RenderMesh.PROXY_OFFSET_FLOATS)
        val blended = blendVertex(point, normal, proxy, skin.bones, skin.weights, v * MAX_INFLUENCES, transforms, twists) ?: continue
        vertices.put(at, blended.position)
        vertices.put(at + RenderMesh.NORMAL_OFFSET_FLOATS, blended.normal.unitOr(normal))
        vertices.put(at + RenderMesh.PROXY_OFFSET_FLOATS, blended.proxy.unitOr(proxy))
        if (v < corners) {
            positions.put(v * 3, blended.position)
            val line = lineNormals.vec(v * 3)
            val turned = blendVertex(point, line, line, skin.bones, skin.weights, v * MAX_INFLUENCES, transforms, twists)
            if (turned != null) lineNormals.put(v * 3, turned.normal.unitOr(line))
        }
    }
    val parted = skin.contacts.any { it }
    val fan = surface.render.indices
    val drawn = if (!parted) fan else fan.keptQuads(skin.contacts, QUAD_FAN_INDICES)
    val render = RenderMesh(vertices, drawn)
    val triangles = if (!parted) surface.mesh.triangles else surface.mesh.triangles.keptQuads(skin.contacts, QUAD_INDICES)
    val posed = surface.movedTo(render, positions, lineNormals, triangles)
    val near = NearestVertex(surface.positions, surface.mesh.cell)
    fun carried(point: ResolvedPoint): ResolvedPoint {
        val v = near.of(point.position) ?: return point
        val blended = blendVertex(point.position, point.normal, point.normal, skin.bones, skin.weights, v * MAX_INFLUENCES, transforms, twists) ?: return point
        return point.copy(position = blended.position, normal = blended.normal.unitOr(point.normal))
    }
    val posedPaint = paint?.posed(render, ::carried)
    return PosedSurface(posed, posedPaint, carry = ::carried)
}

/** How many indices a quad holds as two triangles, and as the four of its fan. */
private const val QUAD_INDICES = 6
private const val QUAD_FAN_INDICES = 12

/** These indices, [width] a quad, without the quads [left] marks. */
private fun IntArray.keptQuads(left: BooleanArray, width: Int): IntArray {
    val kept = IntArray(left.count { !it } * width)
    var at = 0
    for (quad in left.indices) if (!left[quad]) {
        copyInto(kept, at, quad * width, quad * width + width)
        at += width
    }
    return kept
}

/** The nearest of a mesh's [positions], three values each, to any point, found through a grid of [cell]-sized buckets. */
private class NearestVertex(private val positions: FloatArray, private val cell: Float) {
    private val buckets = HashMap<Long, MutableList<Int>>()

    init {
        for (v in 0 until positions.size / 3) buckets.getOrPut(key(positions.vec(v * 3))) { ArrayList() } += v
    }

    fun of(p: Vec3): Int? {
        for (ring in 1..MOST_RINGS) {
            var best = -1
            var least = Float.MAX_VALUE
            val (x, y, z) = Triple(index(p.x), index(p.y), index(p.z))
            for (dx in -ring..ring) for (dy in -ring..ring) for (dz in -ring..ring) {
                buckets[pack(x + dx, y + dy, z + dz)]?.forEach { v ->
                    val d = (positions.vec(v * 3) - p).length
                    if (d < least) {
                        least = d
                        best = v
                    }
                }
            }
            if (best >= 0) return best
        }
        return null
    }

    private fun index(x: Float): Int = floor(x / cell).toInt()

    private fun key(p: Vec3): Long = pack(index(p.x), index(p.y), index(p.z))

    private fun pack(x: Int, y: Int, z: Int): Long = ((x + OFFSET).toLong() shl 42) or ((y + OFFSET).toLong() shl 21) or (z + OFFSET).toLong()

    private companion object {
        const val MOST_RINGS = 4
        const val OFFSET = 1 shl 20
    }
}
