package dev.chieppa.squash.screens.draw

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed

/** Whether [down] is a press the curve tools answer to: a mouse's primary button, or a pen or finger tip. */
internal fun AwaitPointerEventScope.isToolPress(down: PointerInputChange): Boolean = when (down.type) {
    PointerType.Eraser -> false
    PointerType.Mouse -> currentEvent.buttons.isPrimaryPressed
    else -> true
}

/** How hard a pen is pressing in [change], 0 to 1; a mouse or a finger reports none. */
fun penPressure(change: PointerInputChange): Float? = change.pressure.takeIf { change.type == PointerType.Stylus }

/**
 * Follows the stroke of pointer [id] until it lets go, which returns true, or goes away, which returns false. [onMove] gets
 * each change, and [onPoint] each position it passed through.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal suspend fun AwaitPointerEventScope.followToRelease(id: PointerId, onMove: (PointerInputChange) -> Unit, onPoint: (Offset) -> Unit): Boolean {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: return false
        onMove(change)
        change.historical.forEach { onPoint(it.position) }
        onPoint(change.position)
        change.consume()
        if (isToolRelease(event, change)) return true
    }
}

/** Whether [change], in [event], has let go of what it held. */
internal fun isToolRelease(event: PointerEvent, change: PointerInputChange): Boolean =
    if (change.type == PointerType.Mouse) !event.buttons.isPrimaryPressed else !change.pressed
