package dev.chieppa.squash.screens.draw

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.geom.StrokeShape
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.samples

/** How long a stroke's pointer rests before the stroke snaps to a shape. */
const val HOLD_SNAP_MILLIS = 450L

/** A pointer that stays within this many pixels counts as resting, as a held pen's jitter does. */
internal const val HOLD_SLOP_PIXELS = 3f

/** The shape a stroke snapped to, as the pointer has adjusted it since. */
internal class HeldShape(private val shape: StrokeShape, private val heldAt: CurvePoint) {
    var pointer by mutableStateOf(heldAt)

    /** The shape as stroke points, for a screen pixel of size [pixel] in the plane. */
    fun points(pixel: Float): List<CurvePoint> =
        shape.adjusted(heldAt.vec(), pointer.vec()).samples(pixel).map { CurvePoint(it.x, it.y) }

    private fun CurvePoint.vec() = Vec2(x, y)
}

/**
 * [followToRelease], calling [onHold] each time the pointer comes to rest for [HOLD_SNAP_MILLIS]. The rest is timed around
 * the wait for the next pointer event; moving past [HOLD_SLOP_PIXELS] starts it over.
 */
internal suspend fun AwaitPointerEventScope.followHolding(
    id: PointerId,
    from: PointerInputChange,
    onMove: (PointerInputChange) -> Unit,
    onHold: () -> Unit,
    onPoint: (Offset) -> Unit,
): Boolean {
    var restingAt: Offset = from.position
    var restingSince = from.uptimeMillis
    var latest = from.uptimeMillis
    var timing = true
    while (true) {
        val event = if (timing) {
            withTimeoutOrNull((HOLD_SNAP_MILLIS - (latest - restingSince)).coerceAtLeast(1L)) { awaitPointerEvent() }
        } else {
            awaitPointerEvent()
        }
        if (event == null) {
            timing = false
            onHold()
            continue
        }
        val change = event.changes.firstOrNull { it.id == id } ?: return false
        onMove(change)
        change.historical.forEach { onPoint(it.position) }
        onPoint(change.position)
        change.consume()
        if (isToolRelease(event, change)) return true
        latest = change.uptimeMillis
        if ((change.position - restingAt).getDistance() > HOLD_SLOP_PIXELS) {
            restingAt = change.position
            restingSince = latest
            timing = true
        }
    }
}
