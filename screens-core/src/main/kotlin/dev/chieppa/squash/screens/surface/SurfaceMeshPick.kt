package dev.chieppa.squash.screens.surface

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.surface.HELD
import dev.chieppa.squash.screens.sheet.SurfaceView

/**
 * A character's one surface as a press reads it: each vertex where it shows on screen and how far it lies from the viewer, found once.
 * A hit's part is the member holding the most of the surface there, weighed across the hit triangle's corners, and its (u, v) that
 * member's, interpolated across the triangle with v unwrapped across its seam.
 */
class SurfaceMeshPick(private val surface: CharacterSurface, private val sight: SurfaceView) {
    private val mesh = surface.mesh
    private val count = mesh.vertexCount
    private val xs = FloatArray(count)
    private val ys = FloatArray(count)
    private val depths = FloatArray(count)
    private val shown = BooleanArray(count)

    init {
        for (v in 0 until count) {
            val point = at(v)
            val pixel = sight.toPixel(point) ?: continue
            xs[v] = pixel.x
            ys[v] = pixel.y
            depths[v] = sight.depthOf(point)
            shown[v] = true
        }
    }

    /** Where vertex [v] lies, posed as the surface is. */
    fun at(v: Int): Vec3 = Vec3(surface.positions[v * 3], surface.positions[v * 3 + 1], surface.positions[v * 3 + 2])

    /** The nearest place of the surface showing at [pixel] nearer than [limit], with its depth, on [only] alone when it names a part; null where none does. */
    fun hit(pixel: Offset, limit: Float = Float.POSITIVE_INFINITY, only: PartId? = null): Pair<SurfaceHit, Float>? {
        val t = mesh.triangles
        var best = -1
        var nearest = limit
        var weights = FloatArray(3)
        for (tri in 0 until mesh.triangleCount) {
            val a = t[tri * 3]
            val b = t[tri * 3 + 1]
            val c = t[tri * 3 + 2]
            if (!shown[a] || !shown[b] || !shown[c]) continue
            if (pixel.x < minOf(xs[a], xs[b], xs[c]) || pixel.x > maxOf(xs[a], xs[b], xs[c])) continue
            if (pixel.y < minOf(ys[a], ys[b], ys[c]) || pixel.y > maxOf(ys[a], ys[b], ys[c])) continue
            val w = barycentric(pixel, a, b, c) ?: continue
            val depth = depths[a] * w[0] + depths[b] * w[1] + depths[c] * w[2]
            if (depth >= nearest) continue
            if (only != null && heaviestMember(intArrayOf(a, b, c), w)?.let { surface.field.runs[it].partId } != only) continue
            nearest = depth
            best = tri
            weights = w
        }
        if (best < 0) return null
        return hitOn(best, weights)?.let { it to nearest }
    }

    /** The vertex nearest [pixel] on screen within [pixels], or null. */
    fun nearestShown(pixel: Offset, pixels: (Vec3) -> Float): Offset? {
        var closest: Offset? = null
        var least = Float.POSITIVE_INFINITY
        for (v in 0 until count) {
            if (!shown[v]) continue
            val dx = xs[v] - pixel.x
            val dy = ys[v] - pixel.y
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            if (distance < least && distance <= pixels(at(v))) {
                least = distance
                closest = Offset(xs[v], ys[v])
            }
        }
        return closest
    }

    private fun hitOn(tri: Int, weights: FloatArray): SurfaceHit? {
        val corners = IntArray(3) { mesh.triangles[tri * 3 + it] }
        val member = heaviestMember(corners, weights) ?: return null
        val run = surface.field.runs[member]
        var u = 0f
        var total = 0f
        val vs = FloatArray(3)
        val taken = FloatArray(3)
        var first = -1
        for (k in 0 until 3) {
            val slot = slotOf(corners[k], member) ?: continue
            if (first < 0) first = k
            taken[k] = weights[k]
            u += mesh.heldU[corners[k] * HELD + slot] * weights[k]
            vs[k] = mesh.heldV[corners[k] * HELD + slot]
            total += weights[k]
        }
        if (total <= 0f) return null
        var v = 0f
        for (k in 0 until 3) {
            if (taken[k] <= 0f) continue
            var value = vs[k]
            if (run.patch.closedV) {
                if (value - vs[first] > 0.5f) value -= 1f
                if (vs[first] - value > 0.5f) value += 1f
            }
            v += value * taken[k]
        }
        v /= total
        if (run.patch.closedV) v -= kotlin.math.floor(v)
        val position = at(corners[0]) * weights[0] + at(corners[1]) * weights[1] + at(corners[2]) * weights[2]
        return SurfaceHit(run.partId, run.patch.name, u / total, v.coerceIn(0f, 1f), position)
    }

    /** The member holding the most of the surface across [corners], each weighed by its share of the hit in [weights]. */
    private fun heaviestMember(corners: IntArray, weights: FloatArray): Int? {
        val members = IntArray(3 * HELD) { -1 }
        val sums = FloatArray(3 * HELD)
        var used = 0
        for (k in 0 until 3) for (h in 0 until HELD) {
            val member = mesh.held[corners[k] * HELD + h]
            if (member < 0) continue
            var at = (0 until used).firstOrNull { members[it] == member }
            if (at == null) {
                at = used++
                members[at] = member
            }
            sums[at] += mesh.heldShares[corners[k] * HELD + h] * weights[k]
        }
        return (0 until used).maxByOrNull { sums[it] }?.let { members[it] }
    }

    private fun slotOf(v: Int, member: Int): Int? = (0 until HELD).firstOrNull { mesh.held[v * HELD + it] == member }

    private fun barycentric(point: Offset, a: Int, b: Int, c: Int): FloatArray? {
        val area = (xs[b] - xs[a]) * (ys[c] - ys[a]) - (ys[b] - ys[a]) * (xs[c] - xs[a])
        if (area == 0f) return null
        val second = ((point.x - xs[a]) * (ys[c] - ys[a]) - (point.y - ys[a]) * (xs[c] - xs[a])) / area
        val third = ((xs[b] - xs[a]) * (point.y - ys[a]) - (ys[b] - ys[a]) * (point.x - xs[a])) / area
        val first = 1f - second - third
        return if (first < 0f || second < 0f || third < 0f) null else floatArrayOf(first, second, third)
    }
}
