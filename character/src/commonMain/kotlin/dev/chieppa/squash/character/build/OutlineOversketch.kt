package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.geom.BezierPath
import dev.chieppa.squash.geom.OversketchOptions
import dev.chieppa.squash.geom.StrokeEnds
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.oversketch

/** This path as an outline's points. */
fun BezierPath.toOutline(): OutlineCurve = OutlineCurve(
    buildList {
        add(segments.first().p0.toCurvePoint())
        for (segment in segments) {
            add(segment.p1.toCurvePoint())
            add(segment.p2.toCurvePoint())
            add(segment.p3.toCurvePoint())
        }
    },
)

private fun Vec2.toCurvePoint() = CurvePoint(x, y)

/**
 * An outline after an oversketch: [replaced] sections gave way to new ones starting at section [firstSection] of [curve],
 * which stray at worst [errorPixels] screen pixels from the stroke.
 */
data class OversketchedOutline(val curve: OutlineCurve, val firstSection: Int, val replaced: Int, val errorPixels: Float)

/**
 * [curve] with the span a stroke covers redrawn, or null if the stroke is too far from it. [samples] lie in the view's
 * plane and [pixel] is a screen pixel's size there. Every point outside the span keeps its exact value.
 */
fun oversketchedOutline(
    curve: OutlineCurve,
    samples: List<CurvePoint>,
    ends: StrokeEnds,
    pixel: Float,
    options: OversketchOptions = OversketchOptions(),
): OversketchedOutline? {
    val result = oversketch(curve.toPath(), samples.map { Vec2(it.x, it.y) }, ends, pixel, options) ?: return null
    return OversketchedOutline(result.path.toOutline(), result.firstSection, result.replaced, result.maxError / pixel)
}
