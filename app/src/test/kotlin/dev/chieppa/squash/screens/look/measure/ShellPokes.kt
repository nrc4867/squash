package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.rig.PosedCharacter
import dev.chieppa.squash.rig.Rig

/** How far inside the body's surface round it a vertex of a bound part may lie before it counts as poked through, in body units. */
const val POKE_TOLERANCE = 0.002f

/** How near a vertex the body's surfaces on both sides of a joint must lie for it to be inside the joint's closed crease, in body units. */
const val CREASE_REACH = 0.03f

/**
 * How far past square to the inside of a joint's bend, as the cosine's negative, a point still lies in the groove at the joint's sides
 * rather than on its front.
 */
private const val CREASE_SIDES = 0.5f

/**
 * Where the posed body comes through a part a garment owns bound to it: of its [held] vertices standing off the body at rest, the
 * [poking] ones that lie more than [POKE_TOLERANCE] inside the posed body's nearest triangle, and of those the ones [inCrease]: in the
 * closed crease behind a joint and the groove at its sides, where both sides of it, as thigh and calf, lie within [CREASE_REACH]. For the rest,
 * the stretch of the part's u they lie along, [outsideAlong], and how far inside the body the deepest lies, [deepestOutside].
 */
data class ShellPokes(val held: Int, val poking: Int, val inCrease: Int, val outsideAlong: ClosedFloatingPointRange<Float>? = null, val deepestOutside: Float = 0f) {
    val outsideCrease: Int get() = poking - inCrease

    override fun toString(): String =
        "$poking of $held, $inCrease in a closed crease" + (outsideAlong?.let { " (outside it at u %.2f to %.2f, deepest %.4f)".format(it.start, it.endInclusive, -deepestOutside) } ?: "")
}

/** How far from a vertex of a bound part the body's triangles are read for which side of them it lies, in body units. */
private const val SURFACE_REACH = 0.1f

/**
 * [ShellPokes] of [part] in [posed], a pose of [rig], each vertex's side of the posed body read on the body's nearest triangle.
 * Null for a part not bound to the body.
 */
fun shellPokes(rig: Rig, posed: PosedCharacter, part: PartId): ShellPokes? {
    val rigged = rig.part(part) ?: return null
    val hold = rigged.hold ?: return null
    val moved = posed.parts[part] ?: return null
    val points = ArrayList<Vec3>()
    val normals = ArrayList<Vec3>()
    val bones = ArrayList<Int>()
    val triangles = ArrayList<Int>()
    for (body in hold.parts) {
        val at = posed.parts[body] ?: continue
        val mesh = posed.meshes[body] ?: continue
        val weights = rig.part(body)?.weights ?: continue
        val first = points.size
        for (v in 0 until at.size / 3) {
            points += Vec3(at[v * 3], at[v * 3 + 1], at[v * 3 + 2])
            val n = v * RenderMesh.FLOATS_PER_VERTEX + RenderMesh.NORMAL_OFFSET_FLOATS
            normals += Vec3(mesh.vertices[n], mesh.vertices[n + 1], mesh.vertices[n + 2])
            bones += weights.of(v).maxByOrNull { it.second }?.first ?: -1
        }
        mesh.indices.forEach { triangles += first + it }
    }
    val surface = SurfaceSide(points, normals, triangles.toIntArray())
    val middles = bones.indices.groupBy { bones[it] }.mapValues { (_, all) -> all.fold(Vec3.ZERO) { sum, k -> sum + points[k] } * (1f / all.size) }
    var held = 0
    var poking = 0
    var crease = 0
    val outside = ArrayList<Float>()
    var deepest = 0f
    for (v in 0 until rigged.vertexCount) {
        if (hold.standoff[v] <= 0f) continue
        held++
        val p = Vec3(moved[v * 3], moved[v * 3 + 1], moved[v * 3 + 2])
        val gap = surface.standoff(p, SURFACE_REACH) ?: continue
        if (gap >= -POKE_TOLERANCE) continue
        poking++
        if (inCrease(p, points, bones, middles)) {
            crease++
        } else {
            outside += rigged.vertices.getOrNull(v)?.u ?: 0f
            deepest = minOf(deepest, gap)
        }
    }
    return ShellPokes(held, poking, crease, outside.minOrNull()?.let { it..outside.max() }, deepest)
}

/**
 * Whether [p] lies in a joint's closed crease: behind the joint and in the groove at its sides, where the body pinches into itself and
 * any surface lies inside one side or the other. That is, body [points] of two [bones] (each point's heaviest), as thigh and calf, both
 * within [CREASE_REACH] of it, and [p], seen from where they meet near it, no further toward the front of the joint than its sides:
 * within [CREASE_SIDES] of the inside of the bend, the way both bones' [middles] lie.
 */
private fun inCrease(p: Vec3, points: List<Vec3>, bones: List<Int>, middles: Map<Int, Vec3>): Boolean {
    val close = points.indices.filter { (points[it] - p).length <= CREASE_REACH }
    val sides = close.groupBy { bones[it] }.filterKeys { it >= 0 }
    if (sides.size < 2) return false
    val meet = close.fold(Vec3.ZERO) { sum, k -> sum + points[k] } * (1f / close.size)
    val inward = sides.keys.fold(Vec3.ZERO) { sum, bone -> sum + (middles.getValue(bone) - meet).let { if (it.length > 0f) it.normalized() else it } }
    val away = p - meet
    if (away.length <= 0f || inward.length <= 0f) return true
    return (away.normalized() dot inward.normalized()) >= -CREASE_SIDES
}
