package dev.chieppa.squash.character.keys

import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.OutlineScale
import dev.chieppa.squash.document.PartChange
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.WidthScale

/**
 * The scale [places] give at [x]: eased smoothly from each place to the next, so the scaled outline keeps its slope where they meet,
 * and held level past either end; 1 with no place.
 */
fun scaleAt(places: List<WidthScale>, x: Float): Float {
    if (places.isEmpty()) return 1f
    if (x <= places.first().x) return places.first().scale
    if (x >= places.last().x) return places.last().scale
    val after = places.indexOfFirst { it.x > x }
    val a = places[after - 1]
    val b = places[after]
    val t = (x - a.x) / (b.x - a.x)
    val eased = t * t * (3f - 2f * t)
    return a.scale + (b.scale - a.scale) * eased
}

/** [curve] with each point's distance from the axis scaled by [scale] at its x: the side above the axis by its plus run, below by its minus. */
fun scaled(curve: OutlineCurve, scale: OutlineScale): OutlineCurve = OutlineCurve(
    curve.points.map { p ->
        when {
            p.y > 0f -> CurvePoint(p.x, p.y * scaleAt(scale.plus, p.x))
            p.y < 0f -> CurvePoint(p.x, p.y * scaleAt(scale.minus, p.x))
            else -> p
        }
    },
)

/** [data] with [change]'s widths read over its outlines; a view the part has no outline in is left out. */
fun keyed(data: PartData, change: PartChange): PartData =
    data.copy(outlines = data.outlines.mapValues { (view, curve) -> change.widths[view]?.let { scaled(curve, it) } ?: curve })
