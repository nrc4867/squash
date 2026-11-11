package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

private const val RINGS = 24
private const val SEGMENTS = 32

/** How far a bump on the sphere stands out, as a share of its radius, and over what angle from the bump's middle. */
private const val BUMP = 0.25f
private const val BUMP_REACH = 0.5f

class SmoothedShadingTest {
    /** A unit ball with a bump on its side, as a part's drawing might have, shading by its own mesh normals. */
    private fun bumpedBall(): RenderMesh {
        val points = ArrayList<Vec3>()
        for (ring in 0..RINGS) for (segment in 0 until SEGMENTS) {
            val polar = PI * ring / RINGS
            val turn = 2 * PI * segment / SEGMENTS
            val radial = Vec3((sin(polar) * cos(turn)).toFloat(), cos(polar).toFloat(), (sin(polar) * sin(turn)).toFloat())
            val fromBump = acos((radial dot Vec3.X).coerceIn(-1f, 1f))
            val lift = if (fromBump < BUMP_REACH) BUMP * cos(fromBump / BUMP_REACH * PI.toFloat() / 2f) else 0f
            points += radial * (1f + lift)
        }
        val indices = ArrayList<Int>()
        for (ring in 0 until RINGS) for (segment in 0 until SEGMENTS) {
            val a = ring * SEGMENTS + segment
            val b = ring * SEGMENTS + (segment + 1) % SEGMENTS
            val c = b + SEGMENTS
            val d = a + SEGMENTS
            indices += listOf(a, b, c, a, c, d)
        }
        val normals = Array(points.size) { Vec3.ZERO }
        for (t in 0 until indices.size / 3) {
            val (a, b, c) = Triple(indices[t * 3], indices[t * 3 + 1], indices[t * 3 + 2])
            val face = (points[b] - points[a]) cross (points[c] - points[a])
            normals[a] += face; normals[b] += face; normals[c] += face
        }
        val vertices = FloatArray(points.size * RenderMesh.FLOATS_PER_VERTEX)
        points.forEachIndexed { i, p ->
            val n = if (normals[i].length > 0f) normals[i].normalized() else p.normalized()
            val at = i * RenderMesh.FLOATS_PER_VERTEX
            floatArrayOf(p.x, p.y, p.z, n.x, n.y, n.z, n.x, n.y, n.z).copyInto(vertices, at)
        }
        return RenderMesh(vertices, indices.toIntArray())
    }

    private fun RenderMesh.shading(vertex: Int): Vec3 {
        val at = vertex * RenderMesh.FLOATS_PER_VERTEX + RenderMesh.PROXY_OFFSET_FLOATS
        return Vec3(vertices[at], vertices[at + 1], vertices[at + 2])
    }

    /** The mean angle, in radians, between a vertex's shading normal and the ball's own normal there, over the bump's edge. */
    private fun RenderMesh.meanOverBump(): Float = (0 until vertexCount).filter {
        val radial = position(it).normalized()
        acos((radial dot Vec3.X).coerceIn(-1f, 1f)) in BUMP_REACH * 0.4f..BUMP_REACH
    }.map { acos((shading(it) dot position(it).normalized()).coerceIn(-1f, 1f)) }.average().toFloat()

    @Test
    fun aBumpShadesNearlyAsTheBallUnderIt() {
        val drawn = bumpedBall()
        val smoothed = drawn.withSmoothedShading()
        assertTrue(smoothed.meanOverBump() < drawn.meanOverBump() * 0.5f, "${smoothed.meanOverBump()} against ${drawn.meanOverBump()}")
    }

    @Test
    fun theBallAwayFromTheBumpKeepsItsShading() {
        val smoothed = bumpedBall().withSmoothedShading()
        val farSide = (0 until smoothed.vertexCount).filter { smoothed.position(it).x < -0.5f }
        assertTrue(farSide.all { (smoothed.shading(it) dot smoothed.position(it).normalized()) > 0.98f })
    }

    @Test
    fun positionsAndMeshNormalsStay() {
        val drawn = bumpedBall()
        val smoothed = drawn.withSmoothedShading()
        for (v in 0 until drawn.vertexCount) for (i in 0 until RenderMesh.PROXY_OFFSET_FLOATS) {
            assertTrue(drawn.vertices[v * RenderMesh.FLOATS_PER_VERTEX + i] == smoothed.vertices[v * RenderMesh.FLOATS_PER_VERTEX + i])
        }
    }
}
