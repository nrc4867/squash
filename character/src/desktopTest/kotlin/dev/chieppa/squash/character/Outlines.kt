package dev.chieppa.squash.character

import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.geom.Patch

/** An outline running straight between [corners], each edge as one cubic. */
fun outline(vararg corners: Pair<Float, Float>): OutlineCurve {
    val points = mutableListOf(CurvePoint(corners[0].first, corners[0].second))
    for (i in 1 until corners.size) {
        val (x0, y0) = corners[i - 1]
        val (x1, y1) = corners[i]
        points += CurvePoint(x0 + (x1 - x0) / 3f, y0 + (y1 - y0) / 3f)
        points += CurvePoint(x0 + (x1 - x0) * 2f / 3f, y0 + (y1 - y0) * 2f / 3f)
        points += CurvePoint(x1, y1)
    }
    return OutlineCurve(points)
}

fun template(id: String): PartTemplate = shipped.parts.first { it.id.value == id }

fun Patch.vertices() = (0 until nu * nv).map { point(it) }

fun Patch.ring(ring: Int) = (0 until nv).map { point(index(ring, it)) }
