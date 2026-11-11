package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.smoke.RayGrid
import kotlin.math.PI

/**
 * A piece of terminator shorter than this share of a half turn round the part's thinnest extent is an island of shade or light on its
 * surface, a blotch, rather than the edge of a form's shade, which runs at least half round the form.
 */
private const val BLOTCH_SHARE = 1f

/** How far off a face a ray toward the viewer starts, so it does not hit the face it starts on. */
private const val LIFT = 1e-4f

/** The shade's edge as one view sees it: its length across the view, how many pieces of it show, and the blotches among them by part. */
class TerminatorView(val length: Float, val pieces: Int, val blotches: Map<String, Int>)

/** One stretch of terminator on a face: the two edges it crosses, its ends, and which views see it. */
private class Segment(val from: Long, val to: Long, val a: Vec3, val b: Vec3, val seenBy: BooleanArray)

/**
 * Where two-tone shading turns from lit to shade on every face [body] shows, from each of [views]: the line on each face where its
 * shading normal, as the fill interpolates it, turns from [toLight]. A piece is a run of that line across neighbouring faces of one
 * part; a view sees a stretch on a face turned to it with nothing in front.
 */
fun terminator(body: AssembledCharacter, grid: RayGrid, toLight: Vec3, views: List<Vec3>): List<TerminatorView> {
    val units = views.map { it.normalized() }
    val length = FloatArray(units.size)
    val pieces = IntArray(units.size)
    val blotches = List(units.size) { mutableMapOf<String, Int>() }
    for (part in body.parts.filter { it.id != null }) {
        val segments = segmentsOf(part, grid, toLight, units)
        val sets = PieceSets()
        segments.forEach { sets.join(it.from, it.to) }
        val byPiece = segments.groupBy { sets.root(it.from) }
        val blotchUnder = blotchLength(part)
        for ((_, run) in byPiece) {
            val whole = run.sumOf { (it.b - it.a).length.toDouble() }.toFloat()
            for (view in units.indices) {
                val seen = run.filter { it.seenBy[view] }
                if (seen.isEmpty()) continue
                length[view] += seen.sumOf { s -> (s.b - s.a).let { it - units[view] * (it dot units[view]) }.length.toDouble() }.toFloat()
                pieces[view]++
                if (whole < blotchUnder) blotches[view].merge(requireNotNull(part.id).value, 1, Int::plus)
            }
        }
    }
    return units.indices.map { TerminatorView(length[it], pieces[it], blotches[it]) }
}

/** Half a turn round the part's thinnest extent, times [BLOTCH_SHARE]. */
private fun blotchLength(part: PlacedPart): Float {
    val size = part.box?.size ?: return 0f
    return minOf(size.x, size.y, size.z) * (PI.toFloat() / 2f) * BLOTCH_SHARE
}

private fun segmentsOf(part: PlacedPart, grid: RayGrid, toLight: Vec3, views: List<Vec3>): List<Segment> {
    val mesh = part.surface.mesh
    val shade = FloatArray(mesh.vertexCount) { proxy(mesh, it) dot toLight }
    val faces = mesh.indices
    val out = ArrayList<Segment>()
    for (t in 0 until faces.size / 3) {
        val corners = IntArray(3) { faces[t * 3 + it] }
        val crossings = (0..2).mapNotNull { e ->
            val from = corners[e]
            val to = corners[(e + 1) % 3]
            val s0 = shade[from]
            val s1 = shade[to]
            if ((s0 > 0f) == (s1 > 0f)) null else {
                val k = s0 / (s0 - s1)
                (minOf(from, to).toLong() * mesh.vertexCount + maxOf(from, to)) to part.point(from) * (1f - k) + part.point(to) * k
            }
        }
        if (crossings.size != 2) continue
        val normal = (part.point(corners[1]) - part.point(corners[0])) cross (part.point(corners[2]) - part.point(corners[0]))
        val middle = (crossings[0].second + crossings[1].second) * 0.5f
        val lifted = if (normal.length > 0f) middle + normal.normalized() * LIFT else middle
        val seenBy = BooleanArray(views.size) { normal dot views[it] > 0f && grid.escapes(lifted, views[it]) }
        out += Segment(crossings[0].first, crossings[1].first, crossings[0].second, crossings[1].second, seenBy)
    }
    return out
}

private fun proxy(mesh: RenderMesh, vertex: Int): Vec3 {
    val at = vertex * RenderMesh.FLOATS_PER_VERTEX + RenderMesh.PROXY_OFFSET_FLOATS
    return Vec3(mesh.vertices[at], mesh.vertices[at + 1], mesh.vertices[at + 2])
}

/** Edges joined into runs. */
private class PieceSets {
    private val parent = HashMap<Long, Long>()

    fun root(key: Long): Long {
        var at = parent.getOrPut(key) { key }
        while (at != parent.getValue(at)) at = parent.getValue(at)
        parent[key] = at
        return at
    }

    fun join(a: Long, b: Long) {
        val ra = root(a)
        val rb = root(b)
        if (ra != rb) parent[ra] = rb
    }
}
