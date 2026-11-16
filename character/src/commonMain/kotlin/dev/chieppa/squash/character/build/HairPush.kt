package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.surface.GarmentCover
import dev.chieppa.squash.character.surface.firstRunIndex
import dev.chieppa.squash.character.surface.isFieldPart
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.worn
import dev.chieppa.squash.geom.PointGrid
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.StrandPush
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.posed

/** How far hair stands off the body it is pushed out of, in body units. */
const val HAIR_CLEARANCE = 0.012f

/** How many times a strand is pushed, each from where the last left it, so a ring pushed out of one part clear of it lands clear of the next. */
private const val PUSH_PASSES = 3

/** How far from a strand's vertex, in body units, the body is looked for. */
private const val HAIR_REACH = 0.2f

/**
 * The garment branches worn over each part where they are grown from the body rather than placed as parts, as on a one surface: hair
 * stands off a part by their eases as well where one covers the part at that u, so it lies over the garment.
 */
typealias WornOver = Map<PartId, List<GarmentBranch>>

/**
 * This character with each hair part built as its own mesh pushed out of the body it lies against as it stands, by [StrandPush]: out of
 * every part but the one it sits on, those sitting on that part with it, other hair and garment branches over hair, standing off a part
 * further by the eases of what [worn] covers it with. Itself when nothing moves.
 */
fun AssembledCharacter.withHairPushed(worn: WornOver = emptyMap()): AssembledCharacter {
    val replaced = HashMap<PlacedPart, PlacedPart>()
    for (part in parts) {
        if (part.role != Role.HAIR || part.id == null || isFieldPart(part)) continue
        val pushed = part.pushedOutOf(this, worn)
        if (pushed !== part) replaced[part] = pushed
    }
    if (replaced.isEmpty()) return this
    return AssembledCharacter(parts.map { replaced[it] ?: it }, sockets)
}

/** This hair part, its run's rings held at its root, pushed out of [body] as [withHairPushed] says; itself when nothing moves. */
internal fun PlacedPart.pushedOutOf(body: AssembledCharacter, worn: WornOver = emptyMap()): PlacedPart {
    val patches = surface.patches
    val run = firstRunIndex(patches)
    if (run < 0) return this
    val patch = patches[run]
    val first = patches.take(run).sumOf { it.nu * it.nv }
    val mesh = surface.mesh
    val grid = FloatArray(patch.nu * patch.nv * 3)
    val low = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
    val high = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
    for (i in 0 until patch.nu * patch.nv) {
        val p = origin + mesh.position(first + i)
        grid[i * 3] = p.x
        grid[i * 3 + 1] = p.y
        grid[i * 3 + 2] = p.z
        for (k in 0 until 3) {
            low[k] = minOf(low[k], grid[i * 3 + k] - HAIR_REACH)
            high[k] = maxOf(high[k], grid[i * 3 + k] + HAIR_REACH)
        }
    }
    val (points, normals) = bodyAround(body, low, high, worn)
    var moved: Array<Vec3>? = null
    repeat(PUSH_PASSES) {
        val step = StrandPush.push(PointGrid(grid, patch.nu, patch.nv, patch.closedV), points, normals, HAIR_CLEARANCE, HAIR_REACH) ?: return@repeat
        for (i in step.indices) {
            grid[i * 3] += step[i].x
            grid[i * 3 + 1] += step[i].y
            grid[i * 3 + 2] += step[i].z
        }
        moved = moved?.let { held -> Array(held.size) { held[it] + step[it] } } ?: step
    }
    val shift = moved ?: return this
    val last = shift[shift.size - 1]
    val count = mesh.vertexCount
    val all = Array(count) { v ->
        val local = v - first
        when {
            local in shift.indices -> shift[local]
            v >= first + shift.size -> last
            else -> Vec3.ZERO
        }
    }
    val pushed = if (first == 0) mesh.shiftedOnGrid(all, patch.nu, patch.nv) else mesh.shifted { all[it] }
    return showing(surface.posed(pushed), turn)
}

/**
 * The points of [body] inside the box from [low] to [high], with their outward normals, of every part a hair part pushes out of: not
 * hair, nor a branch over hair, nor a part a garment owns or a loose end, nor the part [this] sits on or another part on it. A point of a
 * part's first run that [worn] covers at its u stands out along its normal by the branches' eases, the garment's surface over it.
 */
private fun PlacedPart.bodyAround(body: AssembledCharacter, low: FloatArray, high: FloatArray, worn: WornOver): Pair<Array<Vec3>, Array<Vec3>> {
    val on = socket?.parent
    val points = ArrayList<Vec3>()
    val normals = ArrayList<Vec3>()
    for (other in body.parts) {
        val id = other.id ?: continue
        if (other === this || other.role == Role.HAIR || other.hanging != null || other.owner != null) continue
        if (other.covering?.let { body.part(it.part)?.role == Role.HAIR } == true) continue
        if (id == on || (on != null && other.socket?.parent == on)) continue
        val mesh = other.surface.mesh
        val values = mesh.vertices
        val branches = worn[id].orEmpty()
        val patches = other.surface.patches
        val run = firstRunIndex(patches)
        val runFirst = if (run < 0) 0 else patches.take(run).sumOf { it.nu * it.nv }
        val runCount = if (run < 0) 0 else patches[run].nu * patches[run].nv
        for (v in 0 until mesh.vertexCount) {
            val at = v * RenderMesh.FLOATS_PER_VERTEX
            val x = values[at] + other.origin.x
            val y = values[at + 1] + other.origin.y
            val z = values[at + 2] + other.origin.z
            if (x < low[0] || y < low[1] || z < low[2] || x > high[0] || y > high[1] || z > high[2]) continue
            val n = Vec3(values[at + RenderMesh.NORMAL_OFFSET_FLOATS], values[at + RenderMesh.NORMAL_OFFSET_FLOATS + 1], values[at + RenderMesh.NORMAL_OFFSET_FLOATS + 2])
            if (n.length == 0f) continue
            val normal = n.normalized()
            val local = v - runFirst
            val ease = if (branches.isEmpty() || local !in 0 until runCount) 0f else easeAt(branches, patches[run].ringUs[local / patches[run].nv])
            points += Vec3(x, y, z) + normal * ease
            normals += normal
        }
    }
    return points.toTypedArray() to normals.toTypedArray()
}

/** How far the garments of [branches] stand off their part at [u], their eases added where each covers it. */
private fun easeAt(branches: List<GarmentBranch>, u: Float): Float {
    var ease = 0f
    for (branch in branches) if (u >= branch.from && u <= branch.to) ease += branch.ease
    return ease
}

/** What [character] wears grown from its body, by part, on both sides of a pair. */
fun wornOver(character: Character): WornOver {
    val out = HashMap<PartId, MutableList<GarmentBranch>>()
    for (garment in character.worn) for ((part, branch) in GarmentCover.of(garment, character).branches) out.getOrPut(part) { ArrayList() } += branch
    return out
}
