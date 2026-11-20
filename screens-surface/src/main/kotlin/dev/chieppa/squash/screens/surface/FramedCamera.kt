package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.sheet.toGpu
import dev.chieppa.stretch.gpu.CameraView
import kotlin.math.max
import kotlin.math.tan

/** How far in front of the camera, as a share of the framed depth and distance, a part it must stay clear of keeps. */
private const val CLEARANCE = 0.1f

/** A camera and how many pixels a body unit spans at what it looks at. */
class FramedCamera(val camera: CameraView, val pixelsPerUnit: Float)

/**
 * A camera from [toViewer], the given parts as large as a [width] by [height] view holds them with [margin] of it left free, standing back
 * far enough that every part in [clearOf] lies in front of it as well.
 */
fun framedCamera(parts: List<PlacedPart>, toViewer: Vec3, width: Int, height: Int, margin: Float, clearOf: List<PlacedPart> = emptyList()): FramedCamera =
    framedPointsCamera(parts.flatMap { it.shownPoints() }, toViewer, width, height, margin, clearOf.flatMap { it.shownPoints() })

/**
 * A camera from [toViewer], [points] as large as a [width] by [height] view holds them with [margin] of it left free, standing back
 * far enough that [clear] lies in front of it by [CLEARANCE] of the framed points' depth.
 */
fun framedPointsCamera(points: List<Vec3>, toViewer: Vec3, width: Int, height: Int, margin: Float, clear: List<Vec3> = emptyList()): FramedCamera {
    val back = toViewer.normalized()
    val right = (Vec3.Y cross back).normalized()
    val up = back cross right
    fun span(axis: Vec3): Pair<Float, Float> = points.minOf { it dot axis } to points.maxOf { it dot axis }
    val (leftmost, rightmost) = span(right)
    val (lowest, highest) = span(up)
    val (farthest, nearest) = span(back)
    val target = right * ((leftmost + rightmost) / 2f) + up * ((lowest + highest) / 2f) + back * ((farthest + nearest) / 2f)
    val lens = tan(CameraView.DEFAULT_FOV / 2f)
    val free = 1f - margin
    val aspect = width.toFloat() / height
    val framing = max((rightmost - leftmost) / (2f * lens * aspect * free), (highest - lowest) / (2f * lens * free)) + (nearest - farthest) / 2f
    val clearing = clear.maxOfOrNull { it dot back }?.let { it - (target dot back) + (nearest - farthest + framing) * CLEARANCE } ?: 0f
    val distance = max(framing, clearing)
    return FramedCamera(CameraView(eye = (target + back * distance).toGpu(), target = target.toGpu()), height / (2f * lens * distance))
}
