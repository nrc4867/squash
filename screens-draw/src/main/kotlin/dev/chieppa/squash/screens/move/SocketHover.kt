package dev.chieppa.squash.screens.move

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import dev.chieppa.squash.character.catalog.twin
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.stretch.frame.OverlayBuilder

/** The key Full's pane goes by in a [SocketHover], beside the locked panes' views. */
internal object FULL

/** The place on a part's surface under the pointer, in the pane the pointer is over, for a tool that previews what a press places. */
class SocketHover {
    private var hover by mutableStateOf<Entry?>(null)

    private class Entry(val pane: Any, val place: SocketPlace?)

    /** The place under the pointer in [pane], or null when the pointer is elsewhere or over no part. */
    fun placeIn(pane: Any): SocketPlace? = hover?.takeIf { it.pane == pane }?.place

    /** Follows the pointer over [pane] while [active], finding the place under it with [placeAt]. */
    fun track(owner: Any, pane: Any, key: Any, active: () -> Boolean, placeAt: (Offset) -> SocketPlace?): Modifier = Modifier.pointerInput(owner, pane, key) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val following = active() && event.type != PointerEventType.Exit
                val pointer = event.changes.firstOrNull()?.position
                hover = when {
                    following && pointer != null -> Entry(pane, placeAt(pointer))
                    hover?.pane == pane -> null
                    else -> hover
                }
            }
        }
    }
}

/** A socket about to be placed at [place] with the Move tool's outward angle, and its twin while Pair is on. */
fun OverlayBuilder.socketPreview(state: AppState, character: AssembledCharacter, place: SocketPlace, sight: SurfaceView, look: SheetLook) {
    val document = state.session.value?.document?.value ?: return
    val twin = placedTwin(character, place.part) { id -> document.character.part(id)?.let { document.twin(it)?.id } }
    val (own, other) = socketMarks(place, state.move.outward.value, state.move.pair.value, twin) ?: return
    other?.let { socketMark(it, sight, look, twin = true) }
    socketMark(own, sight, look, twin = false)
}
