package dev.chieppa.squash.screens.sheet

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.draw.isToolPress

/**
 * What Full does with the pointer under its orbit: a [tool] that takes the free camera gets it, and in Full first any other
 * armed tool is refused, with [onRefused] true for as long as its press is held. Writes nothing.
 */
internal fun freePaneInput(tool: PaneTool?, view: FullView, layout: SheetLayout, onRefused: (Boolean) -> Unit): Modifier = when {
    tool == null -> Modifier
    tool.takesFreeCamera -> tool.fullInput(view)
    layout == SheetLayout.FULL_FIRST -> Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (!isToolPress(down)) return@awaitEachGesture
            onRefused(true)
            try {
                followToRelease(down.id, {}, {})
            } finally {
                onRefused(false)
            }
        }
    }
    else -> Modifier
}
