package dev.chieppa.squash.screens.edit

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.anchorOf
import dev.chieppa.squash.document.anchors
import dev.chieppa.squash.document.closed
import dev.chieppa.squash.document.isAnchor
import dev.chieppa.squash.document.sectionOf
import dev.chieppa.squash.document.sections

private const val PER_SEGMENT = 3

/** An anchor wins over a handle this many pixels closer, since handles sit near the anchors they hang from. */
private const val ANCHOR_BIAS_PIXELS = 2f

/** The sections a selection works on: a selected handle's own, or both either side of a selected anchor. */
fun OutlineCurve.selectedSections(index: Int?): Set<Int> {
    if (index == null) return emptySet()
    if (!isAnchor(index)) return setOf(sectionOf(index, sections))
    val after = index / PER_SEGMENT
    return buildSet {
        if (after < sections) add(after) else if (closed) add(0)
        if (after > 0) add(after - 1) else if (closed) add(sections - 1)
    }
}

/** The handles on show: every section's with nothing selected, otherwise only the selected sections'. */
fun OutlineCurve.shownHandles(selected: Int?): List<Int> {
    val chosen = selectedSections(selected)
    val shown = if (chosen.isEmpty()) (0 until sections).toSet() else chosen
    return shown.sorted().flatMap { listOf(it * PER_SEGMENT + 1, it * PER_SEGMENT + 2) }
}

/** The point under a press at [pixel] within [reach] pixels, of the anchors and the handles on show, or null. */
fun OutlineCurve.pointAt(pixel: Offset, reach: Float, selected: Int?, toPixel: (CurvePoint) -> Offset): Int? {
    var best: Int? = null
    var bestDistance = Float.POSITIVE_INFINITY
    fun consider(index: Int, bias: Float) {
        val distance = (toPixel(points[index]) - pixel).getDistance()
        if (distance <= reach && distance - bias < bestDistance) {
            best = index
            bestDistance = distance - bias
        }
    }
    shownHandles(selected).forEach { consider(it, 0f) }
    anchors.forEach { consider(it, ANCHOR_BIAS_PIXELS) }
    return best
}

/** The anchor a point belongs to: itself, or the one a handle hangs from, with a closed curve's last point as its first. */
fun OutlineCurve.owningAnchor(index: Int): Int = anchorOf(index).let { if (closed && it == points.lastIndex) 0 else it }
