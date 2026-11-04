package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.sheet.toGpu
import dev.chieppa.stretch.gpu.CameraView
import kotlin.math.max
import kotlin.math.tan

/** A camera and how many pixels a body unit spans at what it looks at. */
class FramedCamera(val camera: CameraView, val pixelsPerUnit: Float)

/** A camera from [toViewer], the given parts as large as a [width] by [height] view holds them with [margin] of it left free. */
fun framedCamera(parts: List<PlacedPart>, toViewer: Vec3, width: Int, height: Int, margin: Float): FramedCamera {
    val back = toViewer.normalized()
    val right = (Vec3.Y cross back).normalized()
    val up = back cross right
    val points = parts.flatMap { part -> List(part.surface.mesh.vertexCount) { part.origin + part.surface.mesh.position(it) } }
    fun span(axis: Vec3): Pair<Float, Float> = points.minOf { it dot axis } to points.maxOf { it dot axis }
    val (leftmost, rightmost) = span(right)
    val (lowest, highest) = span(up)
    val (farthest, nearest) = span(back)
    val target = right * ((leftmost + rightmost) / 2f) + up * ((lowest + highest) / 2f) + back * ((farthest + nearest) / 2f)
    val lens = tan(CameraView.DEFAULT_FOV / 2f)
    val free = 1f - margin
    val aspect = width.toFloat() / height
    val distance = max((rightmost - leftmost) / (2f * lens * aspect * free), (highest - lowest) / (2f * lens * free)) + (nearest - farthest) / 2f
    return FramedCamera(CameraView(eye = (target + back * distance).toGpu(), target = target.toGpu()), height / (2f * lens * distance))
}
