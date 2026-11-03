package dev.chieppa.squash.screens.draw

import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed

/** Whether [down] is a press the curve tools answer to: a mouse's primary button, or a pen or finger tip. */
internal fun AwaitPointerEventScope.isToolPress(down: PointerInputChange): Boolean = when (down.type) {
    PointerType.Eraser -> false
    PointerType.Mouse -> currentEvent.buttons.isPrimaryPressed
    else -> true
}

/** Whether [change], in [event], has let go of what it held. */
internal fun isToolRelease(event: PointerEvent, change: PointerInputChange): Boolean =
    if (change.type == PointerType.Mouse) !event.buttons.isPrimaryPressed else !change.pressed
