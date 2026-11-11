package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.look.shots.groundHeight

/** Where one part landed: its socket origin and the box round its mesh, in body units, heights from the ground under the soles. */
data class PartBox(val part: String, val origin: Vec3, val low: Vec3, val high: Vec3)

/** Every shown part's origin and box as posed, heights measured up from the body's lowest vertex. */
fun partBoxes(body: AssembledCharacter): List<PartBox> {
    val ground = body.groundHeight()
    fun lifted(p: Vec3) = Vec3(p.x, p.y - ground, p.z)
    return body.parts.filter { it.id != null }.map { part ->
        val mesh = part.surface.mesh
        val points = List(mesh.vertexCount) { part.origin + mesh.position(it) }
        PartBox(
            requireNotNull(part.id).value,
            lifted(part.origin),
            lifted(Vec3(points.minOf { it.x }, points.minOf { it.y }, points.minOf { it.z })),
            lifted(Vec3(points.maxOf { it.x }, points.maxOf { it.y }, points.maxOf { it.z })),
        )
    }
}
