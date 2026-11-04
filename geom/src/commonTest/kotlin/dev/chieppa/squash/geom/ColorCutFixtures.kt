package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.assertTrue

const val FINE = 2e-3f
const val PATCH_NAME = "body"

/** A patch of [rings] by [segments] as a surface's grid, its points from u and v. */
fun gridOf(rings: Int, segments: Int, closed: Boolean, slices: List<Slice>? = null, mirrored: Boolean = false, point: (Float, Float) -> Vec3): PatchGrid {
    val points = FloatArray(rings * segments * Patch.COMPONENTS)
    var at = 0
    for (ring in 0 until rings) {
        for (segment in 0 until segments) {
            val p = point(ring.toFloat() / (rings - 1), segment.toFloat() / (if (closed) segments else segments - 1))
            points[at++] = p.x
            points[at++] = p.y
            points[at++] = p.z
        }
    }
    val patch = Patch(PATCH_NAME, rings, segments, closed, slices, points)
    val built = PartMesh("p", listOf(MeshBuilder.build(patch) { 0 }))
    return requireNotNull(PartSurface.of(built, PartAxes.of(Vec3.X), mirrored).grid(PATCH_NAME))
}

/** A round tube of radius [radius] and length [length] along +x, closed in v. */
fun tubeGrid(rings: Int = 9, segments: Int = 12, radius: Float = 0.5f, length: Float = 2f, mirrored: Boolean = false) = gridOf(rings, segments, true, mirrored = mirrored) { u, v ->
    val angle = 2.0 * PI * v
    Vec3(length * u, radius * cos(angle).toFloat(), -radius * sin(angle).toFloat())
}

/** A flat sheet 2 by 1, open in v, its grid even in u and v. */
fun sheetGrid(rings: Int = 5, segments: Int = 5) = gridOf(rings, segments, false) { u, v -> Vec3(2f * u, v, 0f) }

/** A hemisphere of radius 1 with a pole at its last ring. */
fun domeGrid(rings: Int = 13, segments: Int = 16) = gridOf(rings, segments, true) { u, v ->
    val polar = (PI / 2.0 * u).toFloat()
    val angle = (2.0 * PI * v).toFloat()
    Vec3(sin(polar), cos(polar) * cos(angle), -cos(polar) * sin(angle))
}

private fun PatchCut.corner(t: Int, i: Int) = mesh.position(mesh.indices[t * 3 + i])

/** The cut's area on the surface. */
fun PatchCut.surfaceArea(): Float = (0 until mesh.indices.size / 3).fold(0f) { sum, t ->
    val a = corner(t, 0)
    sum + ((corner(t, 1) - a) cross (corner(t, 2) - a)).length / 2f
}

/** The cut's area in u and v, reading a triangle across a seam the short way. */
fun PatchCut.parameterArea(): Float = (0 until mesh.indices.size / 3).fold(0f) { sum, t ->
    val i = mesh.indices[t * 3]
    val j = mesh.indices[t * 3 + 1]
    val k = mesh.indices[t * 3 + 2]
    fun near(v: Float, to: Float) = if (v - to > 0.5f) v - 1f else if (to - v > 0.5f) v + 1f else v
    val vj = near(vs[j], vs[i])
    val vk = near(vs[k], vs[i])
    sum + abs((us[j] - us[i]) * (vk - vs[i]) - (us[k] - us[i]) * (vj - vs[i])) / 2f
}

/** The shortest distance from [point] to any triangle of the grid's mesh, found across the whole surface. */
fun PatchGrid.distanceToMesh(point: Vec3): Float {
    var best = Float.MAX_VALUE
    for (ring in 0 until rings - 1) {
        for (column in 0 until columns) {
            val a = positionOn(ring.toDouble(), column.toDouble())
            val b = positionOn(ring.toDouble(), column + 1.0)
            val c = positionOn(ring + 1.0, column + 1.0)
            val d = positionOn(ring + 1.0, column.toDouble())
            best = minOf(best, distanceToTriangle(point, a, c, b), distanceToTriangle(point, a, d, c))
        }
    }
    return best
}

private fun distanceToTriangle(p: Vec3, a: Vec3, b: Vec3, c: Vec3): Float {
    val normal = (b - a) cross (c - a)
    val square = normal dot normal
    if (square > 1e-12f) {
        val offset = ((p - a) dot normal) / square
        val on = p - normal * offset
        val inside = (((b - a) cross (on - a)) dot normal) >= -1e-6f && (((c - b) cross (on - b)) dot normal) >= -1e-6f && (((a - c) cross (on - c)) dot normal) >= -1e-6f
        if (inside) return abs(offset) * sqrt(square)
    }
    return minOf(distanceToSegment(p, a, b), distanceToSegment(p, b, c), distanceToSegment(p, c, a))
}

private fun distanceToSegment(p: Vec3, a: Vec3, b: Vec3): Float {
    val along = b - a
    val square = along dot along
    val t = if (square > 0f) (((p - a) dot along) / square).coerceIn(0f, 1f) else 0f
    return (a + along * t - p).length
}

/** Fails unless every vertex of the cut lies on the mesh and is where its u and v put it. */
fun assertOnMesh(cut: PatchCut, grid: PatchGrid, tolerance: Float = 1e-5f) {
    for (v in 0 until cut.mesh.vertexCount) {
        val at = cut.mesh.position(v)
        assertTrue(grid.distanceToMesh(at) <= tolerance, "vertex $at is off the mesh by ${grid.distanceToMesh(at)}")
        val placed = grid.positionOn(grid.gridU(cut.us[v].toDouble()), grid.gridV(cut.vs[v].toDouble()))
        assertTrue((placed - at).length <= 1e-3f, "vertex $at holds a u and v that land at $placed")
    }
}

/** The cut's edges that only one triangle uses: its outline and the patch's edge, so none may lie inside the shape. */
fun PatchCut.boundaryEdges(): List<Pair<Int, Int>> {
    val uses = HashMap<Pair<Int, Int>, Int>()
    for (t in 0 until mesh.indices.size / 3) {
        for (i in 0 until 3) {
            val a = mesh.indices[t * 3 + i]
            val b = mesh.indices[t * 3 + (i + 1) % 3]
            val key = if (a < b) a to b else b to a
            uses[key] = (uses[key] ?: 0) + 1
        }
    }
    return uses.filterValues { it == 1 }.keys.toList()
}
