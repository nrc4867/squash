package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve

/**
 * The view's plane as it shows on a shortened part: an outline's x multiplied by [LockedCamera.xFactor], so a pixel is one
 * [LockedCamera.planePixel] both ways. A tool that measures reach on a curve works here, so what it touches is what shows.
 */
fun LockedCamera.shownPoint(stored: CurvePoint): CurvePoint = if (xFactor == 1f) stored else CurvePoint(stored.x * xFactor, stored.y)

/** The stored point showing at [shown]. */
fun LockedCamera.storedPoint(shown: CurvePoint): CurvePoint = if (xFactor == 1f) shown else CurvePoint(shown.x / xFactor, shown.y)

/** The point of the shown plane under [pixel]. */
fun LockedCamera.toShown(pixel: Offset): CurvePoint = shownPoint(toPlane(pixel))

/** [stored] in the shown plane. */
fun LockedCamera.shownCurve(stored: OutlineCurve): OutlineCurve = if (xFactor == 1f) stored else OutlineCurve(stored.points.map(::shownPoint))

/** [shown] back in stored coordinates, with the runs at each end it left of [original] keeping [original]'s exact values. */
fun LockedCamera.storedCurve(shown: OutlineCurve, original: OutlineCurve): OutlineCurve {
    if (xFactor == 1f) return shown
    val before = original.points.map(::shownPoint)
    val head = shown.points.zip(before).takeWhile { (a, b) -> a == b }.size
    val tail = shown.points.asReversed().zip(before.asReversed()).takeWhile { (a, b) -> a == b }.size
    return OutlineCurve(
        shown.points.mapIndexed { i, point ->
            val fromEnd = shown.points.size - 1 - i
            when {
                i < head -> original.points[i]
                fromEnd < tail -> original.points[original.points.size - 1 - fromEnd]
                else -> storedPoint(point)
            }
        },
    )
}
