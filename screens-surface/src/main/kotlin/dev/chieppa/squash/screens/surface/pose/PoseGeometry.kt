package dev.chieppa.squash.screens.surface.pose

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.rig.PosedCharacter
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.rig.BoneName
import dev.chieppa.squash.screens.sheet.SurfaceView
import kotlin.math.abs

/** How far a probe steps in body units to read how the camera turns a move into pixels. */
private const val PROBE = 1e-3f

/** How near a vertex must show to a press to be picked, in pixels. */
const val VERTEX_REACH_PIXELS = 10f

/**
 * Where [point] goes when the pointer moves from [from] to [to] across [sight]: moved in the plane through it that faces the viewer,
 * so it stays under the pointer. Null when the camera cannot place it.
 */
fun SurfaceView.dragged(point: Vec3, from: Offset, to: Offset): Vec3? {
    val at = toPixel(point) ?: return null
    val normal = toViewer(point).normalized()
    val a = (normal cross (if (abs(normal.y) < 0.9f) Vec3.Y else Vec3.X)).normalized()
    val b = normal cross a
    val pa = (toPixel(point + a * PROBE) ?: return null) - at
    val pb = (toPixel(point + b * PROBE) ?: return null) - at
    val move = to - from
    val determinant = pa.x * pb.y - pa.y * pb.x
    if (abs(determinant) < 1e-12f) return null
    val alpha = (move.x * pb.y - move.y * pb.x) / determinant
    val beta = (pa.x * move.y - pa.y * move.x) / determinant
    return point + (a * alpha + b * beta) * PROBE
}

/**
 * The bone that moves the skin under [pixel] most, read from the weights of the posed vertex shown nearest to the viewer within
 * [VERTEX_REACH_PIXELS] of it, or null over no part.
 */
fun boneUnder(rig: Rig, posed: PosedCharacter, pixel: Offset, sight: SurfaceView): BoneName? {
    var best: Pair<Int, Int>? = null
    var nearest = Float.MAX_VALUE
    rig.parts.forEachIndexed { p, part ->
        val positions = posed.parts[part.id] ?: return@forEachIndexed
        for (v in 0 until part.vertexCount) {
            val point = Vec3(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2])
            val shown = sight.toPixel(point) ?: continue
            if ((shown - pixel).getDistance() > VERTEX_REACH_PIXELS) continue
            val depth = sight.depthOf(point)
            if (depth < nearest) {
                nearest = depth
                best = p to v
            }
        }
    }
    val (p, v) = best ?: return null
    val bone = rig.parts[p].weights.of(v).maxByOrNull { it.second }?.first ?: return null
    return rig.bones[bone].name
}
