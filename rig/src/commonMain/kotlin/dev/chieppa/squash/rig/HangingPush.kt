package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.SurfaceGarment
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.turnedNormals
import dev.chieppa.squash.geom.surface.HELD

/** How far past a part hanging inside it, in the garment's ease, a hanging garment is pushed. */
private const val CLEAR_EASES = 1.5f

/** How far from a garment's vertex, in cells, the part hanging inside it is looked for. */
private const val PUSH_REACH_CELLS = 6f

/** How many times the push is eased toward each vertex's neighbours' along the garment's quads, so the cloth moves as one sheet. */
private const val PUSH_STEPS = 8
private const val PUSH_FACTOR = 0.5f

/**
 * [garment], posed as its surface [moved], pushed out of the parts of the posed [body] that hang inside it, as a skirt is pushed out of
 * the legs in each pose: belled out first as cloth hanging free does (see [flaredHem]), then each vertex of the garment that lies less than
 * [CLEAR_EASES] of its ease outside the body's surface, where
 * that surface is a part the garment does not cover, is moved out along the body's normal there until it clears it, the push eased over
 * the garment's quads so the cloth moves as one sheet. Its lines read normals turned as the push turns the cloth, so its contour follows
 * the pushed silhouette; its shading keeps the posed normals. A garment with no branch hanging free, or nothing inside it, is left as it is.
 */
fun hangingPushed(garment: SurfaceGarment, moved: CharacterSurface, body: CharacterSurface): CharacterSurface {
    if (!garment.cover.hanging) return moved
    val ease = garment.cover.branches.values.maxOf { it.ease }
    val clear = ease * CLEAR_EASES
    val cell = moved.mesh.cell
    val inside = hangingInside(garment, body)
    if (inside.isEmpty()) return moved
    val near = NearestVertex(FloatArray(inside.size * 3) { body.positions[inside[it / 3] * 3 + it % 3] }, cell)
    val corners = moved.mesh.vertexCount
    val flared = flaredHem(garment, moved.positions, body, inside, ease)
    val positions = flared ?: moved.positions
    val normals = body.lineNormals
    val push = FloatArray(corners * 3)
    var any = false
    for (v in 0 until corners) {
        val k = near.within(positions, v, cell * PUSH_REACH_CELLS) ?: continue
        val q = inside[k]
        val nx = normals[q * 3]
        val ny = normals[q * 3 + 1]
        val nz = normals[q * 3 + 2]
        val gap = (positions[v * 3] - body.positions[q * 3]) * nx + (positions[v * 3 + 1] - body.positions[q * 3 + 1]) * ny + (positions[v * 3 + 2] - body.positions[q * 3 + 2]) * nz
        if (gap >= clear) continue
        val by = clear - gap
        push[v * 3] = nx * by
        push[v * 3 + 1] = ny * by
        push[v * 3 + 2] = nz * by
        any = true
    }
    if (!any && flared == null) return moved
    if (any) eased(push, moved.mesh.triangles, corners)
    val placed = positions.copyOf()
    for (i in placed.indices) placed[i] += push[i]
    val vertices = moved.render.vertices.copyOf()
    val width = RenderMesh.FLOATS_PER_VERTEX
    for (v in 0 until corners) for (a in 0 until 3) vertices[v * width + a] = placed[v * 3 + a]
    val triangles = moved.mesh.triangles
    for (quad in 0 until triangles.size / 6) {
        val middle = corners + quad
        if (middle * width >= vertices.size) break
        for (a in 0 until 3) {
            vertices[middle * width + a] = (placed[triangles[quad * 6] * 3 + a] + placed[triangles[quad * 6 + 1] * 3 + a] + placed[triangles[quad * 6 + 4] * 3 + a] + placed[triangles[quad * 6 + 5] * 3 + a]) / 4f
        }
    }
    val lines = moved.lines.triangles
    return moved.movedTo(RenderMesh(vertices, moved.render.indices), placed, turnedNormals(moved.lineNormals, moved.positions, placed, lines), lines)
}

/**
 * The body's vertices held most by a part [garment] does not cover that hangs inside it: a part on a socket within the range of a branch
 * hanging free, or a part on such a part.
 */
private fun hangingInside(garment: SurfaceGarment, body: CharacterSurface): IntArray {
    val runs = body.field.runs
    val covered = garment.cover.branches.keys
    val parts = runs.map { it.part }.distinctBy { it.id }
    val under = HashSet<PartId>()
    for (part in parts) {
        val spec = part.socketSpec ?: continue
        val branch = part.socket?.parent?.let { garment.cover.branchOf(it) } ?: continue
        if (branch.allRound && (branch.from < 0f || branch.to > 1f) && spec.u in branch.from..branch.to) part.id?.let { under += it }
    }
    var grew = true
    while (grew) {
        grew = false
        for (part in parts) if (part.socket?.parent in under && part.id?.let { under.add(it) } == true) grew = true
    }
    val mesh = body.mesh
    return (0 until mesh.vertexCount).filter { v ->
        val member = mesh.held[v * HELD]
        member >= 0 && runs[member].partId !in covered && runs[member].partId in under
    }.toIntArray()
}

/** [push], three values a vertex, eased toward each vertex's neighbours along [triangles], never below what it held. */
private fun eased(push: FloatArray, triangles: IntArray, corners: Int) {
    val sum = FloatArray(corners * 3)
    val count = IntArray(corners)
    repeat(PUSH_STEPS) {
        sum.fill(0f)
        count.fill(0)
        for (tri in 0 until triangles.size / 3) for (c in 0 until 3) {
            val a = triangles[tri * 3 + c]
            val b = triangles[tri * 3 + (c + 1) % 3]
            for (k in 0 until 3) {
                sum[a * 3 + k] += push[b * 3 + k]
                sum[b * 3 + k] += push[a * 3 + k]
            }
            count[a]++
            count[b]++
        }
        for (v in 0 until corners) {
            if (count[v] == 0) continue
            for (k in 0 until 3) {
                val mean = sum[v * 3 + k] / count[v]
                val next = push[v * 3 + k] + PUSH_FACTOR * (mean - push[v * 3 + k])
                push[v * 3 + k] = if (kotlin.math.abs(next) > kotlin.math.abs(push[v * 3 + k])) next else push[v * 3 + k]
            }
        }
    }
}
