package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.chieppa.stretch.frame.OverlayDot
import dev.chieppa.stretch.frame.OverlayLine
import dev.chieppa.stretch.frame.OverlayRect
import dev.chieppa.stretch.frame.ViewportOverlay

/** This overlay moved by [by]. */
fun ViewportOverlay.translated(by: Offset): ViewportOverlay = ViewportOverlay(
    rects.map { OverlayRect(it.rect.translate(by), it.color) },
    lines.map { it.copy(from = it.from + by, to = it.to + by) },
    dots.map { OverlayDot(it.center + by, it.radius, it.color) },
)

/** What of this overlay lies inside [area]. The surface draws one overlay for all its panes, so each keeps to its own this way. */
fun ViewportOverlay.clippedTo(area: Rect): ViewportOverlay = ViewportOverlay(
    rects.filter { it.rect.overlaps(area) }.map { it.copy(rect = it.rect.intersect(area)) },
    lines.mapNotNull { it.clippedTo(area) },
    dots.filter { area.contains(it.center) },
)

/** Liang-Barsky: narrows the line's range of t by each edge of [area]. */
private fun OverlayLine.clippedTo(area: Rect): OverlayLine? {
    val along = to - from
    var enter = 0f
    var leave = 1f
    fun narrow(toward: Float, room: Float): Boolean {
        if (toward == 0f) return room >= 0f
        val t = room / toward
        if (toward < 0f) enter = maxOf(enter, t) else leave = minOf(leave, t)
        return enter <= leave
    }
    val inside = narrow(-along.x, from.x - area.left) && narrow(along.x, area.right - from.x) &&
        narrow(-along.y, from.y - area.top) && narrow(along.y, area.bottom - from.y)
    return if (inside) copy(from = from + along * enter, to = from + along * leave) else null
}
