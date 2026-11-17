package dev.chieppa.squash.screens.move

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import dev.chieppa.squash.character.build.HandJoints
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.SNAP_GUIDE_PIXELS
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.stretch.core.History

/** What a handle drag in [pane] shows while it lasts: the line through [start] it is held to along [direction], and the [snap] it lies on. */
class DragShown(val pane: Any, val start: Offset, val direction: Offset?, val snap: Snap?)

/** The [DragKeys] these held modifiers make: Shift holds the drag to an axis, Ctrl slows it, Alt leaves out the snaps. */
@OptIn(ExperimentalComposeUiApi::class)
fun PointerKeyboardModifiers.dragKeys() = DragKeys(axis = isShiftPressed, fine = isCtrlPressed, free = isAltPressed)

/**
 * A socket's or a joint's drag, shared by Move, Add part and Add joint. The handle keeps where the pointer took it from, so its
 * dot moves with the pointer rather than jumping under it. Shift holds it to the pane's axis it has moved along most, and X, Y
 * and Z, while it drags, to a body axis as the pane shows it; held, the coordinate across that line stays exactly where it
 * started wherever the parent's surface lets it, and to a fraction of a pixel elsewhere. Ctrl moves it at a fifth of the
 * pointer's speed. Within [SNAP_GUIDE_PIXELS] of the parent's midline, its middle or end rings, or the character's centre line,
 * a socket lands on that line, and a joint on its part's middle; Alt held leaves the snaps out. The document follows through
 * one gesture on the history, so the drag is one undo step, and a drag the pane loses restores what was there.
 */
class HandleDrag(private val state: AppState) {
    /** What the drag shows now, or null while nothing drags. */
    var shown by mutableStateOf<DragShown?>(null)
        private set

    /** Drags [handle] in [pane] through [view] with [id], pressed at [pointer], until it lets go; a release that never moved calls [pressed]. */
    suspend fun AwaitPointerEventScope.follow(handle: MoveHandle, id: PointerId, pointer: Offset, pane: Any, view: HandleView, pressed: () -> Unit) {
        val session = state.session.value ?: return
        val from = view.toPixel(handle.position) ?: return
        val aim = DragAim(from, pointer)
        val start = (handle as? SocketHandle)?.startPlace(view)
        val lines = when (handle) {
            is SocketHandle -> socketSnapLines(handle.parent, view)
            is JointHandle -> if (0.5f in handle.range) jointSnapLines(handle.part, view) else emptyList()
        }
        state.move.axis.value = null
        state.move.dragging.value = true
        var gesture: History<SquashDocument>.Transaction? = null
        var released = false
        var keys = DragKeys()
        try {
            var moved = false
            released = followToRelease(id, { keys = currentEvent.keyboardModifiers.dragKeys() }) { pixel ->
                moved = moved || (pixel - pointer).getDistance() > viewConfiguration.touchSlop
                if (!moved) return@followToRelease
                val keyed = state.move.axis.value?.let { DragAim.directionOf(it, handle.position, view) }
                val target = aim.follow(pixel, keys, keyed)
                val direction = aim.direction
                val snap = if (keys.free) null else snapTo(target, lines, SNAP_GUIDE_PIXELS, from.takeIf { direction != null }, direction)
                shown = DragShown(pane, from, direction, snap)
                val command = commandFor(session, handle, target, snap, direction != null, start, view) ?: return@followToRelease
                val open = gesture ?: session.history.begin().also { gesture = it }
                open.update(command)
            }
            if (released && !moved) pressed()
        } finally {
            shown = null
            state.move.dragging.value = false
            state.move.axis.value = null
            val open = gesture
            if (open != null) if (released) open.commit() else open.cancel()
        }
    }

    private fun commandFor(
        session: CharacterSession,
        handle: MoveHandle,
        target: Offset,
        snap: Snap?,
        held: Boolean,
        start: SurfacePlace?,
        view: HandleView,
    ): DocumentCommand? = when (handle) {
        is SocketHandle -> handle.placeFor(target, view, snap, held, start)?.let { handle.moveTo(session.document.value.character, it, state.thisSide) }
        is JointHandle -> {
            val part = handle.part.id
            if (part == null) {
                null
            } else {
                val thisSide = state.thisSide && session.document.value.character.part(part)?.pair != null
                val name = HandJoints.idOf(handle.name)?.let(HandJoints::u) ?: PlacementParams.joint(handle.name)
                val u = snap?.u ?: jointUnder(handle, target, view)
                SetParameter(part, name, u, thisSide)
            }
        }
    }
}
