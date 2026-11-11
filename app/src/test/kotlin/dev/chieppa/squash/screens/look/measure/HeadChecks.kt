package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3

/** How many heights the head's profile is read at, and how far up the head from the chin it runs: chin to brow. */
private const val PROFILE_BINS = 30
private const val PROFILE_REACH = 0.75f

/** How far the cut runs off the head's middle, so it never passes exactly through the vertices on it. */
private const val OFF_MIDDLE = 1e-4f

/** How far ahead of the head's middle, as a share of its half depth, the face begins; the skull is above the middle and behind that. */
private const val FACE_SHARE = 0.3f

/** The head's profile in Side, chin to brow: the deepest notch below its front's hull, in body units and as a share of the head's height. */
class Profile(val notch: Float, val share: Float, val forward: List<Float>)

/**
 * The head's profile seen from its side, chin to brow: at each height, how far forward the faces it shows cross its middle plane; the notch is the
 * furthest that curve falls behind the hull over its front, which a sphere never does and a brow, nose, lips and chin do.
 */
fun profile(head: PlacedPart): Profile? {
    val box = head.box ?: return null
    val height = box.size.y
    val mid = box.center.z + OFF_MIDDLE
    val forward = FloatArray(PROFILE_BINS) { Float.NEGATIVE_INFINITY }
    val binHeight = height * PROFILE_REACH / PROFILE_BINS
    val faces = head.surface.mesh.indices
    for (t in 0 until faces.size / 3) {
        val cut = (0..2).mapNotNull { e ->
            val a = head.point(faces[t * 3 + e])
            val b = head.point(faces[t * 3 + (e + 1) % 3])
            if ((a.z > mid) == (b.z > mid)) null else a + (b - a) * ((mid - a.z) / (b.z - a.z))
        }
        if (cut.size != 2) continue
        val (low, high) = if (cut[0].y <= cut[1].y) cut[0] to cut[1] else cut[1] to cut[0]
        val first = ((low.y - box.low.y) / binHeight).toInt().coerceAtLeast(0)
        val last = ((high.y - box.low.y) / binHeight).toInt().coerceAtMost(PROFILE_BINS - 1)
        for (bin in first..last) {
            val y = (box.low.y + (bin + 0.5f) * binHeight).coerceIn(low.y, high.y)
            val x = if (high.y == low.y) maxOf(low.x, high.x) else low.x + (high.x - low.x) * ((y - low.y) / (high.y - low.y))
            if (x > forward[bin]) forward[bin] = x
        }
    }
    val heights = (0 until PROFILE_BINS).filter { forward[it].isFinite() }
    if (heights.size < 3) return null
    val hull = upperHull(heights.map { it.toFloat() to forward[it] })
    var notch = 0f
    for (bin in heights) {
        val y = bin.toFloat()
        val segment = hull.zipWithNext().firstOrNull { (a, b) -> y >= a.first && y <= b.first } ?: continue
        val (a, b) = segment
        val t = if (b.first == a.first) 0f else (y - a.first) / (b.first - a.first)
        notch = maxOf(notch, a.second + (b.second - a.second) * t - forward[bin])
    }
    return Profile(notch, notch / height, heights.map { forward[it] - box.center.x })
}

/** The hull of points over a line, from above: the chain no point rises over, in order along it. */
private fun upperHull(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
    val hull = mutableListOf<Pair<Float, Float>>()
    for (p in points) {
        while (hull.size >= 2) {
            val a = hull[hull.size - 2]
            val b = hull[hull.size - 1]
            val cross = (b.first - a.first) * (p.second - a.second) - (b.second - a.second) * (p.first - a.first)
            if (cross >= 0f) hull.removeAt(hull.lastIndex) else break
        }
        hull += p
    }
    return hull
}

/** How many of the head's shown vertices over the skull, above its middle and behind its face, are seen, and how many there are. */
fun skullShown(head: PlacedPart, seen: (Vec3) -> Boolean): Pair<Int, Int> {
    val box = head.box ?: return 0 to 0
    val faceFrom = box.center.x + box.size.x / 2f * FACE_SHARE
    val used = head.usedVertices()
    val skull = used.indices.filter { used[it] }.map(head::point).filter { it.y > box.center.y && it.x < faceFrom }
    return skull.count(seen) to skull.size
}

/** How square to a view a face of the head must turn to count as seen face-on. */
private const val FACING_VIEW = 0.3f

/** How far off the surface a ray toward the viewer starts, so it does not hit the head it starts on. */
private const val SHADE_LIFT = 2e-3f

/**
 * How much of the face each of [views] sees falls in two-tone's shade under [toLight], as a share of the face's vertices it sees:
 * the head's shown vertices in front of its middle, turned to the view and with nothing in front. A sphere lit from above and the side
 * shades a wide band across the lower face; a face lit as drawn keeps its shade under the jaw and on the far cheek's edge.
 */
fun faceShade(head: PlacedPart, toLight: Vec3, views: List<Vec3>, escapes: (Vec3, Vec3) -> Boolean): List<Float> {
    val box = head.box ?: return views.map { 0f }
    val mesh = head.surface.mesh
    val used = head.usedVertices()
    val light = toLight.normalized()
    return views.map { view ->
        val toView = view.normalized()
        var seen = 0
        var shaded = 0
        for (v in 0 until mesh.vertexCount) {
            if (!used[v]) continue
            val point = head.point(v)
            if (point.x < box.center.x) continue
            val normal = mesh.normalAt(v, RenderMesh.NORMAL_OFFSET_FLOATS)
            if ((normal dot toView) < FACING_VIEW || !escapes(point + normal * SHADE_LIFT, toView)) continue
            seen++
            if ((mesh.normalAt(v, RenderMesh.PROXY_OFFSET_FLOATS) dot light) <= 0f) shaded++
        }
        if (seen == 0) 0f else shaded.toFloat() / seen
    }
}

private fun RenderMesh.normalAt(vertex: Int, offset: Int): Vec3 {
    val at = vertex * RenderMesh.FLOATS_PER_VERTEX + offset
    return Vec3(vertices[at], vertices[at + 1], vertices[at + 2]).normalized()
}
