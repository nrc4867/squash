package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.IN_TURN
import dev.chieppa.squash.character.surface.RunAll
import dev.chieppa.squash.character.surface.chunked
import dev.chieppa.squash.character.surface.PosedSurface
import dev.chieppa.squash.character.surface.SurfacePaint
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import kotlin.math.floor

/**
 * [surface] and its [paint] posed by [frames] through [skin], linear blend skinning as the parts' own meshes are posed: every vertex of
 * the drawn mesh with its normal and shading normal, the normals its lines read, the paint laid again on the posed mesh, and each ink
 * mark's places carried by the vertex nearest each. The skin's contact quads are left out. A pose that moves no bone leaves them as
 * they rest. Each batch of vertices is posed by [runAll]. A pose that moves a bone hands the posed drawn vertices and the corners' places to
 * [shape] before they are made a surface, for it to move them further; with [fit], they are first refitted to the posed field
 * ([ImplicitSkin]), and a mark carried by a vertex the refit moved moves and turns with it. Each stage's time goes to [breakdown] when
 * there is one.
 */
suspend fun posedSurface(
    surface: CharacterSurface,
    skin: SurfaceSkin,
    paint: SurfacePaint?,
    frames: BoneFrames,
    runAll: RunAll = IN_TURN,
    shape: ((vertices: FloatArray, positions: FloatArray) -> Unit)? = null,
    breakdown: PoseBreakdown? = null,
    fit: ImplicitSkin? = null,
): PosedSurface {
    val transforms = frames.transforms
    val twists = frames.twists
    if (skin.bones.all { it < 0 || transforms[it].isIdentity }) return PosedSurface(surface, paint)
    val width = RenderMesh.FLOATS_PER_VERTEX
    val rest = surface.render.vertices
    val vertices = rest.copyOf()
    val corners = surface.mesh.vertexCount
    val positions = surface.positions.copyOf()
    val lineNormals = surface.lineNormals.copyOf()
    breakdown.time("blend") { runAll(chunked(skin.vertexCount, POSE_CHUNK) { from, until ->
        for (v in from until until) {
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
    }) }
    val refit = fit?.refit(frames, positions, runAll, breakdown)
    val shifts = refit?.let { made ->
        breakdown.time("write") {
            val turns = FloatArray(made.moved.size * 3)
            for ((i, v) in made.moved.withIndex()) made.normals[i]?.let { turns.put(i * 3, it - lineNormals.vec(v * 3)) }
            writeRefit(made, corners, surface.render.indices, vertices, positions, lineNormals)
            VertexShifts(made, turns)
        }
    }
    breakdown.time("shape") { shape?.invoke(vertices, positions) }
    val parted = skin.contacts.any { it }
    val fan = surface.render.indices
    val drawn = if (!parted) fan else fan.keptQuads(skin.contacts, if (surface.render.vertexCount > corners) QUAD_FAN_INDICES else QUAD_INDICES)
    val render = RenderMesh(vertices, drawn)
    val triangles = if (!parted) surface.mesh.triangles else surface.mesh.triangles.keptQuads(skin.contacts, QUAD_INDICES)
    val posed = breakdown.time("lines") { surface.movedTo(render, positions, lineNormals, triangles) }
    val near by lazy { NearestVertex(surface.positions, surface.mesh.cell) }
    fun carried(point: ResolvedPoint): ResolvedPoint {
        val v = near.of(point.position) ?: return point
        val blended = blendVertex(point.position, point.normal, point.normal, skin.bones, skin.weights, v * MAX_INFLUENCES, transforms, twists) ?: return point
        val normal = blended.normal.unitOr(point.normal)
        val shift = shifts?.of(v) ?: return point.copy(position = blended.position, normal = normal)
        return point.copy(position = blended.position + shift.first, normal = (normal + shift.second).unitOr(normal))
    }
    val posedPaint = breakdown.time("paint") { paint?.posed(render, ::carried) }
    return PosedSurface(posed, posedPaint, carry = ::carried)
}

/** How many vertices a piece of the posing takes. */
private const val POSE_CHUNK = 4096

/** How many indices a quad holds as the four triangles of its fan. */
private const val QUAD_FAN_INDICES = 12

/** These indices, [width] a quad, without the quads [left] marks; the triangles standing alone after the quads kept as they are. */
private fun IntArray.keptQuads(left: BooleanArray, width: Int): IntArray {
    val tail = size - left.size * width
    val kept = IntArray(left.count { !it } * width + tail)
    var at = 0
    for (quad in left.indices) if (!left[quad]) {
        copyInto(kept, at, quad * width, quad * width + width)
        at += width
    }
    copyInto(kept, at, left.size * width, size)
    return kept
}
