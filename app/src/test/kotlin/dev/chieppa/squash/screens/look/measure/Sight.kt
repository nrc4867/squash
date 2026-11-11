package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.smoke.RayGrid
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** How many ways out a point is looked at along. */
private const val WAYS = 96

/** How many of [WAYS] must leave the character for a point to be seen, so a look down a long sleeve or through a slit is not counted. */
const val SEEN_WAYS = 10

/** The golden angle, in radians. */
private const val GOLDEN_TURN = 2.3999631f

/** Ways out spread evenly over the sphere. */
val WAYS_OUT: List<Vec3> = List(WAYS) { i ->
    val y = 1f - 2f * (i + 0.5f) / WAYS
    val r = sqrt(1f - y * y)
    val turn = i * GOLDEN_TURN
    Vec3(r * cos(turn), y, r * sin(turn))
}

/** Vertex [vertex] of [part]'s mesh in body space. */
fun PlacedPart.point(vertex: Int): Vec3 = origin + surface.mesh.position(vertex)

/** The corners of every face [body] shows, in body space, three points a face. */
fun shownCorners(body: AssembledCharacter): FloatArray {
    val all = body.parts.filter { it.id != null }
    val corners = FloatArray(all.sumOf { it.surface.mesh.indices.size } * 3)
    var at = 0
    for (part in all) for (index in part.surface.mesh.indices) {
        val p = part.point(index)
        corners[at++] = p.x
        corners[at++] = p.y
        corners[at++] = p.z
    }
    return corners
}

/** Whether [point] is seen from outside: rays from it along at least [SEEN_WAYS] of [WAYS_OUT] leave crossing no shown face. */
fun RayGrid.seen(point: Vec3): Boolean = WAYS_OUT.count { escapes(point, it) } >= SEEN_WAYS

/** Which vertices of [part]'s mesh a face it shows has as a corner. */
fun PlacedPart.usedVertices(): BooleanArray = BooleanArray(surface.mesh.vertexCount).also { flags -> surface.mesh.indices.forEach { flags[it] = true } }
