package dev.chieppa.squash.screens.look.compare

import dev.chieppa.squash.geom.Vec3
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/** How near the eye, along its view, a point still projects. */
private const val NEAR = 1e-4f

/**
 * A perspective camera in body space as the scene draws one: looking from [eye] at [target] with +y up and a vertical field of
 * [fovRadians].
 */
data class BodyCamera(val eye: Vec3, val target: Vec3, val fovRadians: Float) {
    val forward: Vec3 = (target - eye).normalized()
    val right: Vec3 = (forward cross Vec3(0f, 1f, 0f)).normalized()
    val up: Vec3 = right cross forward
}

/** Which pixels of a [width] by [height] image [model]'s triangles cover through [camera], filled on the CPU. */
fun rasterSilhouette(model: ModelTriangles, camera: BodyCamera, width: Int, height: Int): Silhouette {
    val covered = BooleanArray(width * height)
    val lens = tan(camera.fovRadians / 2f)
    val aspect = width.toFloat() / height
    val count = model.pointCount
    val xs = FloatArray(count)
    val ys = FloatArray(count)
    val ahead = BooleanArray(count)
    val p = model.positions
    val e = camera.eye
    for (i in 0 until count) {
        val dx = p[i * 3] - e.x
        val dy = p[i * 3 + 1] - e.y
        val dz = p[i * 3 + 2] - e.z
        val depth = dx * camera.forward.x + dy * camera.forward.y + dz * camera.forward.z
        if (depth <= NEAR) continue
        ahead[i] = true
        val sx = (dx * camera.right.x + dy * camera.right.y + dz * camera.right.z) / (depth * lens * aspect)
        val sy = (dx * camera.up.x + dy * camera.up.y + dz * camera.up.z) / (depth * lens)
        xs[i] = (sx + 1f) / 2f * width
        ys[i] = (1f - sy) / 2f * height
    }
    val idx = model.indices
    for (t in 0 until idx.size / 3) {
        val a = idx[t * 3]
        val b = idx[t * 3 + 1]
        val c = idx[t * 3 + 2]
        if (!ahead[a] || !ahead[b] || !ahead[c]) continue
        fill(xs[a], ys[a], xs[b], ys[b], xs[c], ys[c], covered, width, height)
    }
    return Silhouette(width, height, covered)
}

/** Marks the pixels whose middles lie inside the triangle, either winding. */
private fun fill(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, covered: BooleanArray, width: Int, height: Int) {
    val area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
    if (area == 0f) return
    val x0 = max(0, floor(min(ax, min(bx, cx)) - 0.5f).toInt())
    val x1 = min(width - 1, ceil(max(ax, max(bx, cx)) - 0.5f).toInt())
    val y0 = max(0, floor(min(ay, min(by, cy)) - 0.5f).toInt())
    val y1 = min(height - 1, ceil(max(ay, max(by, cy)) - 0.5f).toInt())
    if (x0 > x1 || y0 > y1) return
    val sign = if (area > 0f) 1f else -1f
    for (y in y0..y1) {
        val py = y + 0.5f
        for (x in x0..x1) {
            val px = x + 0.5f
            val w0 = ((bx - ax) * (py - ay) - (by - ay) * (px - ax)) * sign
            val w1 = ((cx - bx) * (py - by) - (cy - by) * (px - bx)) * sign
            val w2 = ((ax - cx) * (py - cy) - (ay - cy) * (px - cx)) * sign
            if (w0 >= 0f && w1 >= 0f && w2 >= 0f) covered[y * width + x] = true
        }
    }
}
